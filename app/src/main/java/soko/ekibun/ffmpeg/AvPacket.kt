package soko.ekibun.ffmpeg

class AvPacket {
  val ptr: Long = initNative()
  var streamIndex: Int = -1

  companion object {
    init {
      System.loadLibrary("ffmpeg")
    }
  }

  private external fun initNative(): Long

  external fun closeNative(ptr: Long)
  protected fun finalize() {
    closeNative(ptr)
  }
}