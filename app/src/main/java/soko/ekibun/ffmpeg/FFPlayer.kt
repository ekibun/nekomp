package soko.ekibun.ffmpeg

import kotlinx.coroutines.*
import java.util.concurrent.Executors

class FFPlayer(
  url: String,
  io: AvIO.Handler,
  val playback: AvPlayback? = null
) : AvFormat(url, io) {
  class PTS(
    val streams: Map<Int, AvStream>,
    private var relate: Long = 0,
  ) {
    private var absolute: Long = System.currentTimeMillis()
    var playing: Boolean = false

    fun update(relate: Long) {
      this.relate = relate
      absolute = System.currentTimeMillis()
    }

    fun now(speedRatio: Float): Long =
      ((System.currentTimeMillis() - absolute) * speedRatio * AV_TIME_BASE / 1000).toLong() + relate
  }

  private var pts: PTS? = null

  private val dispatcher by lazy {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }

  suspend fun play(streams: Map<Int, AvStream>, seek: Long? = null) = withContext(dispatcher) {
    pause()
    val p = seek ?: pts?.now(playback?.speedRatio ?: 1f) ?: 0
    pts = PTS(streams).also { it.playing = true }
    seekTo(p)
  }

  suspend fun pause() = withContext(dispatcher) {
    pts?.playing = false
    playback?.pause()
    playingJob?.join()
  }

  private var playingJob: Deferred<Unit>? = null
  override suspend fun seekTo(
    ts: Long,
    stream: AvStream?,
    minTs: Long,
    maxTs: Long,
    flags: Int,
  ) = withContext(dispatcher) {
    val oldPts = pts ?: throw Exception("no pts data")
    val pauseAtSeekTo = oldPts.playing
    pause()
    if (pts != oldPts) return@withContext
    val newPts = PTS(oldPts.streams, ts)
    pts = newPts
    // remove cache frame
    frames.forEach {
      it.value.forEach { frame ->
        frame.close()
      }
    }
    frames.clear()
    // flush codec
    codecs.forEach {
      it.value.flush()
    }
    if (pts != newPts) return@withContext
    playback?.stop()
    // seek
    if (pts != newPts) return@withContext
    super.seekTo(ts, stream, minTs, maxTs, flags)
    // seek to next frame
    if (pts != newPts) return@withContext
    if (newPts.streams.containsKey(AVMediaType.VIDEO)) {
      val hitFrame = suspendCancellableCoroutine<Boolean> {
        if (pts != newPts) return@suspendCancellableCoroutine
        playingJob = async(dispatcher) { resumeImpl(it) }
      }
      if (pts != newPts) return@withContext
      if (hitFrame && !pauseAtSeekTo) pause()
    } else if (pauseAtSeekTo) {
      resume()
    }
  }

  suspend fun resume() = withContext(dispatcher) {
    pause()
    playingJob = async(dispatcher) { resumeImpl(null) }
    playingJob?.join()
  }

  private val codecs = HashMap<Int, AvCodec>()
  private val frames = HashMap<Int, ArrayList<AvFrame>>()

  private suspend fun resumeImpl(onNextFrame: CancellableContinuation<Boolean>?) =
    withContext(dispatcher) {
      val pts = pts ?: return@withContext
      val isPlaying = { pts == this@FFPlayer.pts && pts.playing }
      @Suppress("DeferredResultUnused") val playJobs = pts.streams.map { (codecType, stream) ->
        async(dispatcher) {
          var lastUpdateJob: Job? = null
          while (isPlaying()) {
            val frame = frames[stream.index]?.firstOrNull { frame ->
              frame.processing != pts
            }
            if (frame == null) {
              delay(1)
              continue
            }
            frame.processing = pts
            val lastUpdate = lastUpdateJob
            lastUpdateJob = async(dispatcher) updateJob@{
              if (!isPlaying()) return@updateJob
              val muteOnNextFrame = {
                codecType == AVMediaType.AUDIO && onNextFrame?.isActive == true
              }
              // decode frame
              if (!muteOnNextFrame()) playback?.postFrame(codecType, frame)
              lastUpdate?.join()
              if (!isPlaying()) return@updateJob
              // wait video
              if (codecType == AVMediaType.VIDEO && onNextFrame?.isActive != true) {
                while (frame.timeStamp > pts.now(playback?.speedRatio ?: 1f)) {
                  delay(1)
                  if (!isPlaying()) return@updateJob
                }
              }
              if (muteOnNextFrame()) return@updateJob
              val timeStamp = playback?.flushFrame(codecType, frame) ?: -1
              if (!isPlaying()) return@updateJob
              if (timeStamp >= 0) pts.update(timeStamp)
              if (codecType == AVMediaType.VIDEO && onNextFrame?.isActive == true) {
                pts.update(frame.timeStamp)
                onNextFrame.resumeWith(Result.success(true))
              }
              playback?.onFrame?.invoke(pts.now(playback.speedRatio))
            }.also { job ->
              job.invokeOnCompletion {
                frames[stream.index]?.remove(frame)
                frame.close()
              }
            }
            lastUpdate?.join()
          }
        }
      }
      try {
        pts.playing = true
        playback?.resume()
        var sendingPacket = 0
        while (isPlaying()) {
          if (sendingPacket > 10 || frames.map { it.value.size }.sum() > 100) {
            delay(1)
            continue
          }
          val packet = getPacket(pts.streams.values)
          if (packet == null) {
            if (frames.map { it.value.size }.sum() == 0)
              break
            delay(100)
            continue
          }
          if (!isPlaying()) {
            packet.close()
            break
          }
          sendingPacket++
          // for downloading
          if (pts.streams.isEmpty()) {
            packet.close()
            sendingPacket--
            continue
          }
          val stream = pts.streams.values.first { it.index == packet.streamIndex }
          val codec = codecs.getOrPut(stream.index) {
            AvCodec(stream)
          }
          async(dispatcher) {
            val frame = if (this@FFPlayer.pts == pts) codec.sendPacketAndGetFrame(packet) else null
            packet.close()
            sendingPacket--
            if (frame == null) return@async
            if (this@FFPlayer.pts != pts) {
              frame.close()
              return@async
            }
            frames.getOrPut(stream.index) { arrayListOf() }.add(frame)
          }
        }
      } finally {
        if (onNextFrame?.isActive == true) onNextFrame.resumeWith(Result.success(false))
        pts.playing = false
        if(pts == this@FFPlayer.pts) playback?.onFrame?.invoke(null)
      }
      playJobs.joinAll()
    }

  override suspend fun close() = withContext(dispatcher) {
    pause()
    super.close()
    codecs.map {
      async(dispatcher) {
        it.value.close()
      }
    }.awaitAll()
    codecs.clear()
  }
}