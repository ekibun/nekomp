package soko.ekibun.quickjs

open class JSObject(val ptr: Long, protected val ctx: QuickJS.Context) {
  init {
    ctx.ref[ptr] = this
  }

  protected open fun finalize() {
    ctx.freeValue(this)
  }
}