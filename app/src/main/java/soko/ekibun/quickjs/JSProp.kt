package soko.ekibun.quickjs

object JSProp {
  const val CONFIGURABLE = 1 shl 0
  const val WRITABLE = 1 shl 1
  const val ENUMERABLE = 1 shl 2
  const val C_W_E = CONFIGURABLE or WRITABLE or ENUMERABLE
}