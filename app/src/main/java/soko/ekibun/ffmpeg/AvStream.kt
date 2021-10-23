package soko.ekibun.ffmpeg

data class AvStream(
  val ptr: Long,
  val index: Int,
  val codecType: Int,
  val sampleRate: Int,
  val channels: Int,
  val width: Int,
  val height: Int,
  val duration: Long,
  val metadata: Map<String, String>
)