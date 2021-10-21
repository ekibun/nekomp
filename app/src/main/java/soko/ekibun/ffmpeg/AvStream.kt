package soko.ekibun.ffmpeg

data class AvStream(
  private val ptr: Long,
  private val index: Int,
  private val codecType: Int,
  private val metadata: Map<String, String>
)