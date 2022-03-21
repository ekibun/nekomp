package soko.ekibun.quickjs

interface JSInvokable {
  operator fun invoke(vararg argv: Any?, thisVal: Any? = null): Any?
}