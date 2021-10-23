package soko.ekibun.ffmpeg

class AvPacket {
  var ptr: Long? = null
  var streamIndex: Int = -1

  private external fun initNative(): Long
  init {
    ptr = initNative()
  }

  external fun closeNative(ptr: Long)
  fun close() {
    ptr?.let { closeNative(it) }
    ptr = null
  }
}