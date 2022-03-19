package soko.ekibun.quickjs

interface JSInvokable {
  fun invoke(vararg argv: Any?, thisVal: Any? = null): Any?
}