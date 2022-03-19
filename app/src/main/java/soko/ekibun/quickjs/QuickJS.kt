package soko.ekibun.quickjs

import androidx.annotation.Keep
import kotlinx.coroutines.*
import java.util.*

object QuickJS {
  @JvmStatic
  private external fun initContext(ctx: Context): Long

  @JvmStatic
  private external fun destroyContext(ctx: Long)

  @JvmStatic
  private external fun jsNewError(ctx: Long): Long

  @JvmStatic
  private external fun jsNewString(ctx: Long, obj: String): Long

  @JvmStatic
  private external fun jsNewBool(ctx: Long, obj: Boolean): Long

  @JvmStatic
  private external fun jsNewInt64(ctx: Long, obj: Long): Long

  @JvmStatic
  private external fun jsNewFloat64(ctx: Long, obj: Double): Long

  @JvmStatic
  private external fun jsNewArrayBuffer(ctx: Long, obj: ByteArray): Long

  @JvmStatic
  private external fun jsNewObject(ctx: Long): Long

  @JvmStatic
  private external fun jsNewArray(ctx: Long): Long

  @JvmStatic
  private external fun jsNULL(): Long

  @JvmStatic
  private external fun definePropertyValue(
    ctx: Long,
    obj: Long,
    k: Long,
    v: Long,
    flags: Int = JSProp.C_W_E
  ): Int

  @JvmStatic
  private external fun jsDupValue(ctx: Long, obj: Long): Long

  @JvmStatic
  private external fun jsNewCFunction(ctx: Long, obj: Long): Long

  @JvmStatic
  private external fun jsWrapObject(ctx: Long, obj: Any): Long

  @JvmStatic
  private external fun isException(obj: Long): Boolean

  @JvmStatic
  private external fun jsFreeValue(ctx: Long, obj: Long)

  @JvmStatic
  private external fun evaluate(ctx: Long, cmd: String, name: String, flag: Int): Long

  @JvmStatic
  private external fun getException(ctx: Long): JSError

  @JvmStatic
  private external fun jsThrowError(ctx: Long, err: Long): Long

  @JvmStatic
  private external fun jsToJava(ctx: Long, obj: Long): Any?

  @JvmStatic
  private external fun executePendingJob(ctx: Long): Int

  @JvmStatic
  private external fun jsCall(ctx: Long, obj: Long, thisVal: Long, argc: Int, argv: LongArray): Long

  @JvmStatic
  private external fun jsNewPromise(ctx: Long): LongArray

  class Context(private val engine: Engine) {
    val ref = WeakHashMap<Long, JSObject>()

    private val ctxDelegate = lazy { initContext(this) }
    val ptr by ctxDelegate

    fun javaToJs(obj: Any?): Long {
      return javaToJsImpl(obj)
    }

    fun jsToJava(obj: Long): Any? {
      return jsToJava(ptr, obj)
    }

    @Keep
    fun wrapJSPromiseAsync(obj: Long, then: JSFunction): Deferred<Any?> {
      val ret = CompletableDeferred<Any?>()
      val jsRet = jsCall(then,
        object : JSInvokable {
          override fun invoke(vararg argv: Any?, thisVal: Any?) {
            ret.complete(argv[0])
          }
        },
        object : JSInvokable {
          override fun invoke(vararg argv: Any?, thisVal: Any?) {
            ret.completeExceptionally(argv[0] as? Throwable ?: JSError(argv.toString()))
          }
        },
        thisVal = JSObject(obj, this)
      )
      jsFreeValue(ptr, jsRet)
      return ret
    }

    @Keep
    fun handleJSInvokable(obj: JSInvokable, argv: Array<Any>, thisVal: Any?): Long {
      return try {
        javaToJs(obj.invoke(*argv, thisVal))
      } catch (e: Throwable) {
        jsThrowError(ptr, javaToJs(e))
      }
    }

    fun jsCall(obj: JSFunction, vararg argv: Any?, thisVal: Any?): Long {
      val argvJs = argv.map { javaToJs(it) }.toLongArray()
      val thisJs = javaToJs(thisVal)
      val ret = jsCall(ptr, obj.ptr, thisJs, argvJs.size, argvJs)
      jsFreeValue(ptr, thisJs)
      argvJs.forEach {
        jsFreeValue(ptr, it)
      }
      engine.updateChannel.trySend(Unit)
      if (isException(ret)) {
        throw getException()
      }
      return ret
    }

    fun javaToJsImpl(obj: Any?, cache: MutableMap<Any, Long> = mutableMapOf()): Long {
      if (obj == null || obj is Unit) return jsNULL()
      if (obj is Throwable) {
        val ret = jsNewError(ptr)
        definePropertyValue(
          ptr, ret,
          jsNewString(ptr, "name"),
          jsNewString(ptr, obj.javaClass.name)
        )
        definePropertyValue(
          ptr, ret,
          jsNewString(ptr, "message"),
          jsNewString(ptr, obj.message ?: "")
        )
        definePropertyValue(
          ptr, ret,
          jsNewString(ptr, "stack"),
          jsNewString(ptr, obj.stackTraceToString())
        )
        return ret
      }
      if (obj is JSObject) {
        return jsDupValue(ptr, obj.ptr)
      }
      if (obj is Deferred<Any?>) {
        val (ret, jsRes, jsRej) = jsNewPromise(ptr)
        val resolve = jsToJava(ptr, jsRes) as JSFunction
        val reject = jsToJava(ptr, jsRej) as JSFunction
        MainScope().launch(engine.dispatcher) {
          try {
            resolve.invoke(obj.await())
          } catch (e: Throwable) {
            reject.invoke(e)
          }
        }
        return ret
      }
      if (obj is Boolean) return jsNewBool(ptr, obj)
      if (obj is Byte) return jsNewInt64(ptr, obj.toLong())
      if (obj is Char) return jsNewInt64(ptr, obj.code.toLong())
      if (obj is Short) return jsNewInt64(ptr, obj.toLong())
      if (obj is Int) return jsNewInt64(ptr, obj.toLong())
      if (obj is Long) return jsNewInt64(ptr, obj)
      if (obj is Number) return jsNewFloat64(ptr, obj.toDouble())
      if (obj is String) return jsNewString(ptr, obj)
      if (obj is ByteArray) {
        return jsNewArrayBuffer(ptr, obj)
      }
      cache[obj]?.let {
        return jsDupValue(ptr, it)
      }
      if (obj is Map<*, *>) {
        val ret = jsNewObject(ptr)
        cache[obj] = ret
        obj.forEach { entry ->
          definePropertyValue(
            ptr, ret,
            javaToJsImpl(entry.key, cache),
            javaToJsImpl(entry.value, cache)
          )
        }
        return ret
      }
      val arrayObj = if (obj is Array<*>) obj.toList() else obj
      if (arrayObj is Iterable<*>) {
        val ret = jsNewArray(ptr)
        cache[obj] = ret
        arrayObj.forEachIndexed { i, v ->
          definePropertyValue(
            ptr, ret,
            jsNewInt64(ptr, i.toLong()),
            javaToJsImpl(v, cache)
          )
        }
        return ret
      }
      val ret = jsWrapObject(ptr, obj)
      return if (obj is JSInvokable) {
        val func = jsNewCFunction(ptr, ret)
        jsFreeValue(ptr, ret)
        func
      } else {
        ret
      }
    }

    fun executePendingJob(): Int {
      return executePendingJob(ptr)
    }

    fun getException(): JSError {
      return getException(ptr)
    }

    fun evaluate(cmd: String, name: String = "<eval>", flag: Int = JSEvalFlag.GLOBAL): Any? {
      val ret = evaluate(ptr, cmd, name, flag)
      engine.updateChannel.trySend(Unit)
      if (isException(ret)) {
        throw getException()
      }
      return jsToJava(ptr, ret)
    }

    fun freeValue(obj: JSObject) {
      runBlocking(engine.dispatcher) {
        ref.remove(obj.ptr)?.let {
          jsFreeValue(ptr, it.ptr)
        }
      }
    }

    protected fun finalize() {
      runBlocking(engine.dispatcher) {
        if (ctxDelegate.isInitialized()) {
          ref.values.forEach {
            jsFreeValue(ptr, it.ptr)
          }
          ref.clear()
          destroyContext(ptr)
        }
      }
    }
  }
}