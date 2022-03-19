package soko.ekibun.nekomp

import android.app.Application

class App: Application() {
  companion object {
    init {
      System.loadLibrary("ffmpeg")
    }
  }
}