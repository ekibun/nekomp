package soko.ekibun.ffmpeg

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AvCodec(
  private val stream: AvStream
) {
  private val dispatcher by lazy {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }
  companion object {
    init {
      System.loadLibrary("ffmpeg")
    }
  }

  private var pctx: Long? = null
  private external fun initNative(stream: Long): Long
  private suspend fun ensureContext(create: Boolean): Long {
    return withContext(dispatcher) {
      if (create && pctx == null) pctx = initNative(stream.ptr)
      if ((pctx ?: 0L) == 0L) throw Exception("AvCodec closed")
      pctx!!
    }
  }

  private external fun sendPacketAndGetFrameNative(ctx: Long, stream: Long, packet: Long): AvFrame?
  suspend fun sendPacketAndGetFrame(packet: AvPacket): AvFrame? = withContext(dispatcher) {
    val ctx = ensureContext(true)
    sendPacketAndGetFrameNative(ctx, stream.ptr, packet.ptr)
  }

  private external fun flushNative(ctx: Long)
  suspend fun flush() = withContext(dispatcher) {
    pctx?.let { flushNative(it) }
  }

  private external fun closeNative(ctx: Long)
  suspend fun close() = withContext(dispatcher) {
    pctx?.let { closeNative(it) }
  }
}