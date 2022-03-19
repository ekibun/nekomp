package soko.ekibun.quickjs

class JSError(message: String, val stack: String? = null) : Throwable(message) {
  override fun toString(): String {
    return if(stack == null) "JSError($message)" else "JSError($message)\n$stack"
  }
}