package soko.ekibun.ffmpeg

interface AvIOContext {
  companion object {
    const val AVSEEK_SIZE = 0x10000
  }
  fun getBufferSize(): Long
  interface Handler {
    fun open(url: String) : AvIOContext
  }
  fun read(buf: ByteArray): Int
  fun seek(offset: Int, whence: Int): Int
  fun destroy()
}