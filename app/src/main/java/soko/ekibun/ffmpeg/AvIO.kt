package soko.ekibun.ffmpeg

interface AvIO {
  fun getBufferSize(): Long
  interface Handler {
    fun open(url: String) : AvIO
  }
  fun read(buf: ByteArray): Int
  fun seek(offset: Int, whence: Int): Int
  fun close()
}