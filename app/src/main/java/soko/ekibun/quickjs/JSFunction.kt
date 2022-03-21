package soko.ekibun.quickjs

class JSFunction(ptr: Long, ctx: QuickJS.Context) : QuickJS.Context.JSObject(ptr, ctx), JSInvokable {
  override fun invoke(vararg argv: Any?, thisVal: Any?): Any? {
    return jsCall(*argv, thisVal = thisVal)
  }
}