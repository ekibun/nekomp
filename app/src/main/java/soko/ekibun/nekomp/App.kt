package soko.ekibun.nekomp

import android.app.Application
import soko.ekibun.nekomp.engine.JsEngine

class App: Application() {
  val jsEngine by lazy { JsEngine(app) }

  override fun onCreate() {
    super.onCreate()
    app = this
  }
  companion object {
    private lateinit var app: App
    val ctx get() = app
  }
}