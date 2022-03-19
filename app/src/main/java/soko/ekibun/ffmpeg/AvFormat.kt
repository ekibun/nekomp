package soko.ekibun.ffmpeg

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

open class AvFormat(val url: String, val io: AvIO.Handler) {
  companion object {
    const val AVSEEK_SIZE = 0x10000
    const val AV_TIME_BASE = 1000000
  }

  private var pctx: Long? = null
  private val dispatcher by lazy {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }
  private var streams: List<AvStream>? = null

  private external fun initNative(url: String): Long
  suspend fun<T> runWithContext(create: Boolean = false, cb: (ctx: Long) -> T) : T {
    return withContext(dispatcher) {
      if (create && pctx == null) pctx = initNative(url)
      if ((pctx ?: 0L) == 0L) throw Exception("AvFormat closed")
      cb(pctx!!)
    }
  }

  private external fun getStreamsNative(pctx: Long): Array<AvStream>
  suspend fun getStreams(): List<AvStream> = runWithContext(true) { ctx ->
    if (streams == null) {
      streams = getStreamsNative(ctx).toList()
    }
    streams!!
  }

  private external fun seekToNative(
    pctx: Long,
    ts: Long,
    streamIndex: Int,
    minTs: Long,
    maxTs: Long,
    flags: Int
  ): Int

  open suspend fun seekTo(
    ts: Long,
    stream: AvStream? = null,
    minTs: Long = Long.MIN_VALUE,
    maxTs: Long = Long.MAX_VALUE,
    flags: Int = 0,
  ): Unit = runWithContext { ctx ->
    seekToNative(ctx, ts, stream?.index ?: -1, minTs, maxTs, flags)
  }

  // < 0: error
  // >=0: stream index
  private external fun getPacketNative(
    ctx: Long,
    packet: Long,
  ): Int

  suspend fun getPacket(streams: Collection<AvStream>): AvPacket? = runWithContext { ctx ->
    val packet = AvPacket()
    while (true) {
      val ret = getPacketNative(ctx, packet.ptr!!)
      if (ret < 0) {
        return@runWithContext null
      }
      if (streams.isEmpty() || streams.firstOrNull { it.index == ret } != null) {
        packet.streamIndex = ret
        break
      }
    }
    packet
  }

  private external fun destroyNative(ctx: Long)
  open suspend fun close() {
    withContext(dispatcher) {
      pctx?.let { ctx ->
        destroyNative(ctx)
      }
      pctx = null
    }
  }

  protected fun finalize() = runBlocking {
    close()
  }
}