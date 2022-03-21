package soko.ekibun.nekomp

import android.app.Application
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import soko.ekibun.quickjs.JSInvokable
import soko.ekibun.quickjs.QuickJS

class App: Application() {
  companion object {
    init {
      System.loadLibrary("ffmpeg")
      System.loadLibrary("quickjs")
      runBlocking {
        val promise = async {
          delay(1000)
          "hello"
        }
        run {
          val qjs = object: QuickJS.Context() {
            override fun loadModule(name: String): String? {
              return null
            }
          }
          val wrapper = qjs.evaluate("(a)=>a") as JSInvokable
          val testWrap = arrayOf(1, 0.1, true, "test", promise)
          val ret = wrapper.invoke(testWrap)
          assert(((ret as Array<*>)[4] as Deferred<*>).await() == "hello")
        }
        System.gc()
        delay(1000)
      }
    }
  }
}