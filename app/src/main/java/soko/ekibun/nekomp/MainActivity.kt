package soko.ekibun.nekomp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Request
import soko.ekibun.ffmpeg.AvFormat
import soko.ekibun.ffmpeg.io.HttpIOContext
import soko.ekibun.nekomp.ui.theme.NekompTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    GlobalScope.launch {
      Log.e("STREAM", AvFormat(
        "https://media.w3.org/2010/05/sintel/trailer.mp4",
        HttpIOContext.Handler()
      ).getStreams().toList().toString())
    }

    setContent {
      NekompTheme {
        // A surface container using the 'background' color from the theme
        Surface(color = MaterialTheme.colors.background) {
          Greeting("Android")
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String) {
  Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  NekompTheme {
    Greeting("Android")
  }
}