package soko.ekibun.ffmpeg

class AvFrame(
  val ptr: Long,
  val timeStamp: Long,
  val width: Int,
  val height: Int
) {
  var processing: FFPlayer.PTS? = null

  private external fun closeNative(ptr: Long)
  protected fun finalize() {
    if(ptr != 0L) closeNative(ptr)
  }
}