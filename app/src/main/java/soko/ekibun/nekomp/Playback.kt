package soko.ekibun.nekomp

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.media.*
import android.view.Surface
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import soko.ekibun.ffmpeg.AvFormat
import soko.ekibun.ffmpeg.AvPlayback
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class Playback(
  var surfaceTexture: SurfaceTexture,
  onFrame: (Long?) -> Unit,
) : AvPlayback(
  onFrame
) {
  private val dispatcher by lazy {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }
  val aspectRatio get() = bitmap?.let { it.width.toFloat() / it.height } ?: 1f

  companion object {
    const val defaultRate = 48000
    const val defaultChannel = AudioFormat.CHANNEL_OUT_STEREO
    const val defaultFormat = AudioFormat.ENCODING_PCM_8BIT
  }

  override val sampleRate: Int by lazy { audio.sampleRate }
  override val channels: Int by lazy { audio.channelCount }
  override val audioFormat: Int = 0 // AV_SAMPLE_FMT_U8
  override val videoFormat: Int = 25 // AV_PIX_FMT_RGBA

  val audio by lazy {
    val audioMode = AudioTrack.MODE_STREAM
    AudioTrack(
      AudioAttributes.Builder().build(),
      AudioFormat.Builder()
        .setSampleRate(defaultRate)
        .setChannelMask(defaultChannel)
        .setEncoding(defaultFormat)
        .build(),
      AudioTrack.getMinBufferSize(
        defaultRate,
        defaultChannel,
        defaultFormat
      ),
      audioMode,
      AudioManager.AUDIO_SESSION_ID_GENERATE
    )
  }

  var frameWrite = 0L
  override suspend fun flushAudioBuffer(buf: ByteArray): Int = withContext(dispatcher) {
    if (buf.isNotEmpty()) audio.write(buf, 0, buf.size)
    frameWrite += buf.size / channels
    val timestamp = AudioTimestamp()
    if (audio.getTimestamp(timestamp))
      (frameWrite - timestamp.framePosition).toInt()
    else -1
  }

  var bitmap: Bitmap? = null

  val surface by lazy { Surface(surfaceTexture) }

  val paint by lazy { Paint() }

  override fun flushVideoBuffer(buf: ByteArray, width: Int, height: Int) {
    surfaceTexture.setDefaultBufferSize(width, height)
    if (bitmap == null || bitmap?.width != width || bitmap?.height != height) {
      val oldBitmap = bitmap
      bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      oldBitmap?.recycle()
    }
    bitmap!!.copyPixelsFromBuffer(ByteBuffer.wrap(buf))
    val canvas = surface.lockCanvas(null)
    canvas.drawBitmap(bitmap!!, 0f, 0f, paint)
    surface.unlockCanvasAndPost(canvas)
  }

  override suspend fun resume() = withContext(dispatcher) {
    audio.play()
  }

  override suspend fun pause() = withContext(dispatcher) {
    audio.pause()
  }

  override suspend fun stop() = withContext(dispatcher) {
    audio.pause()
    frameWrite = 0
    audio.flush()
  }

  override fun close() {
    super.close()
    MainScope().launch {
      stop()
      audio.release()
    }
  }
}