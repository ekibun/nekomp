package soko.ekibun.nekomp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import soko.ekibun.ffmpeg.FFPlayer

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun ffmpeg(): Unit = runBlocking {
        val ctx = FFPlayer(
            "https://media.w3.org/2010/05/sintel/trailer.mp4",
            HttpIO.Handler()
        )
        val streams = ctx.getStreams()
        println(streams)
        ctx.play(mapOf())
    }
}