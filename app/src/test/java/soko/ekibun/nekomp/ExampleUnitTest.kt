package soko.ekibun.nekomp

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import soko.ekibun.ffmpeg.FFPlayer
import soko.ekibun.nekomp.player.HttpIO
import soko.ekibun.quickjs.JSEvalFlag
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
      "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8",
      HttpIO.Handler()
    )
    val streams = ctx.getStreams()
    println(streams)
    ctx.play(mapOf())
  }

  @Test
  fun quickjs() {
    System.loadLibrary("quickjs")
    runBlocking {
      run {
        val qjs = object:QuickJS.Context() {
          override fun loadModule(name: String): String {
            return "export default \"test module\""
          }
        }
        qjs.evaluate("" +
            "import handlerData from 'test';\n" +
            "      export default {\n" +
            "        data: handlerData\n" +
            "      };", "evalModule", JSEvalFlag.MODULE)
        val ret = (qjs.evaluate("import(\"evalModule\")") as Deferred<*>).await()
        assert(((ret as Map<*, *>)["default"] as Map<*, *>)["data"] == "test module")
      }
      System.gc()
      delay(1000)
    }
  }
}