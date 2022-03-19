package soko.ekibun.quickjs

abstract class JSObject(val ptr: Long, protected val ctx: QuickJS.Context) {

  protected fun finalize() {
    ctx.freeValue(this)
  }
}