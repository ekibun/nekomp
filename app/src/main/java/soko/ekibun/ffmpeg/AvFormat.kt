package soko.ekibun.ffmpeg

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AvFormat(val url: String, val io: AvIOContext.Handler) {
  companion object {
    init {
      System.loadLibrary("ffmpeg")
    }
  }

  private var pctx: Long? = null
  private val dispatcher by lazy {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }
  private var streams: Array<AvStream>? = null

  private external fun initNative(url: String): Long
  private suspend fun ensureContext(): Long {
    return withContext(dispatcher) {
      if (pctx == null) pctx = initNative(url)
      if ((pctx ?: 0L) == 0L) throw Exception("AvFormat open failed")
      pctx!!
    }
  }

  private external fun getStreamsNative(pctx: Long): Array<AvStream>
  suspend fun getStreams(): Array<AvStream> {
    return withContext(dispatcher) {
      val pctx = ensureContext()
      if (streams == null) {
        streams = getStreamsNative(pctx)
      }
      streams!!
    }
  }

  private external fun destroyNative(pctx: Long)
  suspend fun destroy() {
    withContext(dispatcher) {
      pctx?.let { pctx ->
        destroyNative(pctx)
      }
      pctx = null
    }
  }
}