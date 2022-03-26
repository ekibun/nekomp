package soko.ekibun.nekomp.common

import android.webkit.CookieManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.http.BridgeInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object Http {
  class WebViewCookieHandler : CookieJar {
    private val mCookieManager: CookieManager = CookieManager.getInstance()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
      /* no-op */
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
      val urlString = url.toString()
      val cookiesString: String? = mCookieManager.getCookie(urlString)
      if (cookiesString?.isNotEmpty() == true) {
        val cookieHeaders = cookiesString.split(";").toTypedArray()
        val cookies: MutableList<Cookie> = ArrayList(cookieHeaders.size)
        for (header in cookieHeaders) {
          cookies.add(Cookie.parse(url, header)!!)
        }
        return cookies
      }
      return listOf()
    }
  }

  private val cookieHandler by lazy { WebViewCookieHandler() }

  private val httpClientBuilder by lazy {
    OkHttpClient.Builder()
      .readTimeout(30, TimeUnit.SECONDS)
      .addInterceptor(BrotliInterceptor)
      .addNetworkInterceptor(HttpLoggingInterceptor().apply {
        this.level = HttpLoggingInterceptor.Level.BASIC
      })
  }

  private val httpClients: Array<OkHttpClient> by lazy {
    arrayOf(
      httpClientBuilder.addInterceptor(BridgeInterceptor(cookieHandler))
        .followRedirects(false)
        .followSslRedirects(false).build(),
      httpClientBuilder.addInterceptor(BridgeInterceptor(cookieHandler)).build(),
      httpClientBuilder.followRedirects(false)
        .followSslRedirects(false).build(),
      httpClientBuilder.build()
    )
  }

  fun request(options: Map<Any, Any?>): Call {
    val url = options["url"] as String
    val headerBuilder = Headers.Builder()
    (options["headers"] as? Map<*, *>)?.forEach { entry ->
      val k = entry.key.toString()
      val v = entry.value
      if (v is Iterable<*>) v.forEach {
        headerBuilder.add(k, it.toString())
      } else {
        headerBuilder.add(k, v.toString())
      }
    }
    val request = Request.Builder()
      .url(url)
      .headers(headerBuilder.build())
    when (options["cache"]) {
      "no-cache" -> request.cacheControl(CacheControl.FORCE_NETWORK)
      "force-cache" -> request.cacheControl(CacheControl.FORCE_CACHE)
    }
    val body = options["body"]
    if (body != null) {
      val requestBody: RequestBody = if (body is Map<*, *> && body["__js_proto__"] == "FormData") {
        val formData = MultipartBody.Builder()
        (body["__items__"] as? Iterable<*>)?.forEach {
          val formItem = it as? Map<*, *> ?: return@forEach
          val name = formItem["name"] as? String ?: return@forEach
          val value = formItem["value"]
          val type = formItem["type"]
          if (type is String && value is ByteArray) {
            val fileName = formItem["fileName"] as? String
            formData.addFormDataPart(name, fileName, value.toRequestBody(type.toMediaType()))
          } else {
            formData.addFormDataPart(name, value.toString())
          }
        }
        formData.build()
      } else if (body is ByteArray) {
        body.toRequestBody()
      } else {
        body.toString().toRequestBody()
      }
      request.post(requestBody)
    }
    return httpClients[
        (if (options["credentials"] == "omit") 2 else 0) +
            (if (options["redirect"] == "follow") 1 else 0)].newCall(
      request.build()
    )
  }
}