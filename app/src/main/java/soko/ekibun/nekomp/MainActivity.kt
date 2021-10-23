package soko.ekibun.nekomp

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import soko.ekibun.ffmpeg.AVMediaType
import soko.ekibun.ffmpeg.AvFormat
import soko.ekibun.ffmpeg.AvStream
import soko.ekibun.ffmpeg.FFPlayer
import soko.ekibun.nekomp.ui.theme.NekompTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MainPage()
    }
  }

  @Preview(showBackground = true)
  @Composable
  fun MainPage() {
    val playback = remember { mutableStateOf<Playback?>(null) }
    val url = remember { mutableStateOf("https://media.w3.org/2010/05/sintel/trailer.mp4") }
    val player = remember { mutableStateOf<FFPlayer?>(null) }
    val duration = remember { mutableStateOf(0f) }
    val pts = remember { mutableStateOf(0f) }
    val seeking = remember { mutableStateOf(false) }
    val playing = remember { mutableStateOf(false) }
    NekompTheme {
      Surface {
        Column {
          TextField(
            maxLines = 1,
            trailingIcon = {
              TextButton(
                enabled = playback.value != null,
                onClick = {
                  MainScope().launch {
                    player.value?.close()
                    val newPlayer = FFPlayer(
                      url.value, HttpIO.Handler(), playback.value
                    )
                    player.value = newPlayer
                    val streams = newPlayer.getStreams()
                    val ptsStreams = HashMap<Int, AvStream>()
                    for (type in arrayOf(AVMediaType.VIDEO, AVMediaType.AUDIO))
                      streams.firstOrNull { it.codecType == type }?.let {
                        ptsStreams[type] = it
                      }
                    duration.value = ptsStreams.values.map { it.duration }.average().toFloat()
                    newPlayer.play(ptsStreams)
                  }
                }) {
                Text("play>")
              }
            },
            value = url.value,
            onValueChange = {
              url.value = it
            })
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AndroidView(
              modifier = Modifier.aspectRatio(playback.value?.aspectRatio ?: 1f),
              factory = {
                TextureView(it).also { view ->
                  view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                      playback.value = Playback(p0) { p ->
                        playing.value = p != null
                        if (p != null && !seeking.value) pts.value = p.toFloat()
                      }
                    }

                    override fun onSurfaceTextureSizeChanged(
                      p0: SurfaceTexture,
                      videoWidth: Int,
                      videoHeight: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                      return false
                    }

                    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {}

                  }
                }
              })
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(
              enabled = player.value != null,
              onClick = {
                MainScope().launch {
                  playing.value = !playing.value
                  if (playing.value) player.value?.resume()
                  else player.value?.pause()
                }
              }) {
              Text(if (playing.value) "pause" else "play")
            }
            Slider(value = pts.value,
              modifier = Modifier.weight(1f),
              valueRange = 0f..duration.value,
              onValueChange = {
                seeking.value = true
                pts.value = it
              },
              onValueChangeFinished = {
                MainScope().launch {
                  seeking.value = false
                  player.value?.seekTo(pts.value.toLong())
                }
              }
            )
            Text(
              modifier = Modifier.padding(horizontal = 8.dp),
              text = formatTime(pts.value.toLong()) + "/" + formatTime(duration.value.toLong())
            )
          }
        }
      }
    }
  }

  fun formatTime(time: Long): String {
    val s = time / AvFormat.AV_TIME_BASE
    val m = s / 60
    val h = m / 60
    val ms = (m % 60).toString().padStart(2, '0') + ":" + (s % 60).toString().padStart(2, '0')
    return if (h == 0L) ms else "$h:$ms"
  }
}