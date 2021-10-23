package soko.ekibun.ffmpeg

abstract class AvPlayback(
  val onFrame: (Long?) -> Unit
) {
  companion object {
    init {
      System.loadLibrary("ffmpeg")
    }
  }
  abstract val sampleRate: Int
  abstract val channels: Int
  abstract val audioFormat: Int
  abstract val videoFormat: Int

  private external fun speedRatioNative(ctx: Long, new: Float): Float
  var speedRatio: Float
    get() = speedRatioNative(ctx, 0f)
    set(value) { speedRatioNative(ctx, value) }

  private var isClosed = false

  private external fun initNative(
    sampleRate: Int,
    channels: Int,
    audioFormat: Int,
    videoFormat: Int
  ): Long

  private val ctx: Long by lazy {
    initNative(sampleRate, channels, audioFormat, videoFormat)
  }

  private external fun postFrameNative(ctx: Long, codecType: Int, frame: Long): Int
  fun postFrame(codecType: Int, frame: AvFrame): Int {
    if (isClosed) return -1
    return postFrameNative(ctx, codecType, frame.ptr)
  }

  private external fun getBuffer(ctx: Long, codecType: Int): ByteArray
  suspend fun flushFrame(codecType: Int, frame: AvFrame): Long {
    if (isClosed) return -1
    when (codecType) {
      AVMediaType.AUDIO -> {
        val offset = flushAudioBuffer(getBuffer(ctx, codecType))
        println("PTS ${frame.timeStamp} ${frame.timeStamp - offset * AvFormat.AV_TIME_BASE / sampleRate}")
        return if (offset < 0) -1 else frame.timeStamp - offset * AvFormat.AV_TIME_BASE / sampleRate
      }
      AVMediaType.VIDEO -> flushVideoBuffer(getBuffer(ctx, codecType), frame.width, frame.height)
    }
    return -1
  }

  abstract suspend fun flushAudioBuffer(buf: ByteArray): Int
  abstract fun flushVideoBuffer(buf: ByteArray, width: Int, height: Int)
  abstract suspend fun resume()
  abstract suspend fun pause()
  abstract suspend fun stop()

  private external fun closeNative(ctx: Long)
  open fun close() {
    isClosed = true
    closeNative(ctx)
  }
}