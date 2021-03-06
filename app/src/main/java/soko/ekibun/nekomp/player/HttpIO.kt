package soko.ekibun.nekomp.player

import okhttp3.Response
import okhttp3.internal.headersContentLength
import soko.ekibun.ffmpeg.AvFormat
import soko.ekibun.ffmpeg.AvIO
import soko.ekibun.nekomp.common.Http

class HttpIO(val options: Map<String, Any>) : AvIO {

  private data class HttpResponse(
    private val rsp: Response,
    var offset: Int
  ) {
    private val stream by lazy { rsp.body!!.byteStream() }

    val headersContentLength get() = rsp.headersContentLength().toInt()

    val available
      get() = try {
        stream.available()
      } catch (e: java.io.IOException) {
        0
      }

    fun read(buf: ByteArray): Int {
      val ret = stream.read(buf)
      if (ret > 0) offset += ret
      return ret
    }

    fun takeOut(count: Int) {
      if (count <= 0) return
      stream.skip(count.toLong())
      offset += count
    }

    fun close() {
      stream.close()
      rsp.close()
    }
  }

  class Handler(private val options: Map<String, Any>? = null) : AvIO.Handler {
    override fun open(url: String): AvIO {
      println("open: $url")
      return HttpIO(
        (options ?: mapOf()) + mapOf(
          "url" to url
        )
      )
    }
  }

  override fun getBufferSize() = 32768L

  private var offset = 0
  private var _rsp: HttpResponse? = null
  private fun getRange(start: Int): HttpResponse {
    val rsp = Http.request(
      options + mapOf(
        "headers" to (options["headers"] as? Map<*, *> ?: mapOf<Any, Any>()) + mapOf(
          "range" to "bytes=$start-"
        )
      )
    ).execute()
    return HttpResponse(rsp, if (rsp.code == 206) start else 0)
  }

  private fun getResponse(): HttpResponse {
    var rsp = _rsp ?: getRange(offset)
    if (rsp.offset + rsp.available < offset) {
      /*
       * [////buffer////]   |
       *                  offset
       */
      val newRsp = getRange(offset)
      if (newRsp.offset + newRsp.available >
        rsp.offset + rsp.available
      ) {
        rsp.close()
        rsp = newRsp
      } else {
        // not support content-range
        newRsp.close()
      }
      // consume
      while (true) {
        if (rsp.offset + rsp.available >= offset) {
          rsp.takeOut(offset - rsp.offset)
          break
        }
        rsp.takeOut(rsp.available)
        Thread.sleep(100)
      }
    } else if (rsp.offset <= offset) {
      /*
       * [///|///buffer///////]
       *   offset
       */
      rsp.takeOut(offset - rsp.offset)
    } else {
      /*
       *   |    [////buffer////]
       * offset
       */
      rsp.close()
      _rsp = null
      return getResponse()
    }
    _rsp = rsp
    return rsp
  }

  override fun read(buf: ByteArray): Int {
    return try {
      val ret = getResponse().read(buf)
      println("READ: $offset+$ret")
      if (ret > 0) offset += ret
      ret
    } catch (e: Throwable) {
      e.printStackTrace()
      -1
    }
  }

  override fun seek(offset: Int, whence: Int): Int {
    return try {
      println("SEEK: $offset $whence")
      when (whence) {
        AvFormat.AVSEEK_SIZE -> return getResponse().headersContentLength
        else -> this.offset = offset
      }
      return 0
    } catch (e: Throwable) {
      e.printStackTrace()
      -1
    }
  }

  override fun close() {
    println("CLOSE: ${options["url"]}")
    _rsp?.close()
    _rsp = null
  }
}