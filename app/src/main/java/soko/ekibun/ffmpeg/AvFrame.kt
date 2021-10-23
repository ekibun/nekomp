package soko.ekibun.ffmpeg

class AvFrame(
  var ptr: Long,
  val timeStamp: Long,
  val width: Int,
  val height: Int
) {
  var processing: FFPlayer.PTS? = null

  private external fun closeNative(ptr: Long)
  fun close() {
    if(ptr != 0L) closeNative(ptr)
    ptr = 0
  }
}