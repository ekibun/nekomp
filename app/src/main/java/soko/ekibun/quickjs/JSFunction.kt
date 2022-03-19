package soko.ekibun.quickjs

class JSFunction(ptr: Long, ctx: QuickJS.Context) : JSObject(ptr, ctx), JSInvokable {
  override fun invoke(vararg argv: Any?, thisVal: Any?): Any? {
    return ctx.jsToJava(ctx.jsCall(this, *argv, thisVal = thisVal))
  }
}