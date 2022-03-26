package soko.ekibun.quickjs

class JSObject(ptr: Long, ctx: QuickJS.Context) : QuickJS.Context.JSValue(ptr, ctx),
  Map<Any, Any?> {
  override operator fun get(key: Any): Any? {
    return getPropertyValue(key)
  }
  override val entries: Set<Map.Entry<Any, Any?>>
      by lazy {
        mapOf(*keys.map { it to get(it) }.toTypedArray()).entries
      }
  override val keys: Set<Any> by lazy { getObjectKeys().toSet() }
  override val size: Int by lazy { keys.size }
  override val values: Collection<Any?> by lazy { entries.map { it.value } }

  override fun containsKey(key: Any): Boolean = keys.contains(key)

  override fun containsValue(value: Any?): Boolean = values.contains(value)

  override fun isEmpty(): Boolean = size == 0
}