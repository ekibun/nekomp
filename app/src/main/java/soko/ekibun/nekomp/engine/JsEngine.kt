package soko.ekibun.nekomp.engine

import android.content.Context
import android.widget.Toast
import androidx.annotation.Keep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import soko.ekibun.nekomp.App
import soko.ekibun.nekomp.common.Http
import soko.ekibun.quickjs.JSInvokable
import soko.ekibun.quickjs.JSObject
import soko.ekibun.quickjs.QuickJS
import java.io.IOException
import java.nio.charset.Charset

class JsEngine(private val context: Context) {
  private var quickjsDelegate: QuickJS.Context? = null
  private val quickjs: QuickJS.Context
    get() = quickjsDelegate ?: run {
      val moduleHandler = { module: String ->
        if (module.startsWith("@source/")) {
          context.assets.open("")
        }
        val modulePath = if (module == "@init") "js/init.js" else
          "js/module" + module.replaceFirst(".js$".toRegex(), "") + ".js"
        context.assets.open(modulePath).reader().use { reader ->
          reader.readText()
        }
      }
      val ctx1 = QuickJS.Context(
        moduleHandler = moduleHandler
      )
      quickjsDelegate = ctx1
      val init = ctx1.evaluate(moduleHandler("@init"), "<init>") as JSInvokable
      init(object : JSInvokable {
        override fun invoke(vararg argv: Any?, thisVal: Any?): Any? {
          val obj = argv[0]
          return if (obj is String) {
            val cls = javaClass.classLoader?.loadClass("soko.ekibun.nekomp.engine.$obj")
            cls!!.constructors[0].newInstance(*argv.sliceArray(1 until argv.size))
          } else {
            val methodName = argv[1] as String
            val objWrap = (obj ?: this@JsEngine)
            val method = objWrap.javaClass.declaredMethods.first { it.name == methodName }
            method.isAccessible = true
            method.invoke(objWrap, *argv.sliceArray(2 until argv.size))
          }
        }
      })
      ctx1
    }

  fun evaluate(cmd: String, name: String = "<eval>"): Any? {
    return quickjs.evaluate(cmd, name)
  }

  fun reset() {
    quickjsDelegate = null
    System.gc()
  }

  @Keep
  private fun console(type: String, data: Array<Any?>) {
    MainScope().launch {
      Toast.makeText(App.ctx, "$type\n${data.toList()}", Toast.LENGTH_SHORT).show()
    }
  }

  @Keep
  private fun encode(input: String, to: String?): ByteArray {
    return input.toByteArray(Charset.forName(to?:"utf-8"))
  }

  @Keep
  private fun decode(input: ByteArray, from: String?): String {
    return String(input, Charset.forName(from?:"utf-8"))
  }

  @Keep
  private fun fetchAsync(options: JSObject): Deferred<Any?> {
    val ret = CompletableDeferred<Any?>()
    Http.request(options).enqueue(object: Callback {
      override fun onFailure(call: Call, e: IOException) {
        if(ret.isActive) ret.completeExceptionally(e)
      }

      override fun onResponse(call: Call, response: Response) {
        if(ret.isActive) ret.complete(mapOf(
          "url" to response.request.url.toString(),
          "headers" to response.headers.toMultimap(),
          "ok" to response.isSuccessful,
          "redirected" to response.isRedirect,
          "status" to response.code,
          "body" to response.body?.bytes(),
        ))
      }
    })
    return ret
  }
}