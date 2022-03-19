package soko.ekibun.nekomp

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import soko.ekibun.ffmpeg.FFPlayer
import soko.ekibun.quickjs.Engine
import soko.ekibun.quickjs.JSFunction
import soko.ekibun.quickjs.JSInvokable
import soko.ekibun.quickjs.QuickJS

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun ffmpeg(): Unit = runBlocking {
    System.loadLibrary("ffmpeg")
    val ctx = FFPlayer(
      "https://media.w3.org/2010/05/sintel/trailer.mp4",
      HttpIO.Handler()
    )
    val streams = ctx.getStreams()
    println(streams)
    ctx.play(mapOf())
  }

  @Test
  fun quickjs() {
    System.loadLibrary("quickjs")
    val qjs = Engine()
    runBlocking {
      qjs.runWithContext { ctx ->
        val wrapper = ctx.evaluate("(a)=>a") as JSFunction
        val testWrap = arrayOf(1, 0.1, true, "test")
        val ret = wrapper.invoke(testWrap)
        println(ret)
      }
      System.gc()
      delay(1000)
      qjs.close()
      System.gc()
      delay(1000)
    }
  }
}