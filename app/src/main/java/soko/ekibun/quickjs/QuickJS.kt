package soko.ekibun.quickjs

import androidx.annotation.Keep
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.Executors

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

  abstract class Context {

    @Suppress("LeakingThis")
    open class JSObject(val ptr: Long, protected val ctx: Context) {
      init {
        ctx.ref[ptr] = this
      }
      protected open fun finalize() {
        ctx.freeValue(this)
      }

      protected fun jsCall(vararg argv: Any?, thisVal: Any?): Any? {
        return jsToJava(ctx.ptr, ctx.jsCallImpl(this, *argv, thisVal = thisVal))
      }
    }

    private val ref = WeakHashMap<Long, JSObject>()
    private val ctxDelegate = lazy { initContext(this) }
    private val ptr by ctxDelegate
    private val updateChannel = Channel<Unit>()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val dispatcherThread = runBlocking(dispatcher) { Thread.currentThread() }

    private fun<T> runOnDispatcher(block: ()->T): T {
      if(Thread.currentThread() == dispatcherThread) return block()
      return runBlocking(dispatcher) { block() }
    }

    init {
      MainScope().launch(dispatcher) {
        for (v in updateChannel) {
          while (true) {
            val err: Int = executePendingJob(ptr)
            if (err <= 0) {
              if (err < 0) print(getException(ptr))
              break
            }
          }
        }
      }
    }

    private fun javaToJs(obj: Any?): Long {
      return javaToJsImpl(obj)
    }

    @Keep
    private fun wrapJSPromiseAsync(obj: Long, then: JSFunction): Deferred<Any?> {
      val ret = CompletableDeferred<Any?>()
      val jsRet = jsCallImpl(
        then,
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

    abstract fun loadModule(name: String): String?

    @Keep
    private fun handleJSInvokable(obj: JSInvokable, argv: Array<Any>, thisVal: Any?): Long {
      return try {
        javaToJs(obj.invoke(*argv, thisVal))
      } catch (e: Throwable) {
        jsThrowError(ptr, javaToJs(e))
      }
    }

    private fun jsCallImpl(
      obj: JSObject,
      vararg argv: Any?,
      thisVal: Any? = null
    ): Long = runOnDispatcher {
      val argvJs = argv.map { javaToJs(it) }.toLongArray()
      val thisJs = javaToJs(thisVal)
      val ret = jsCall(ptr, obj.ptr, thisJs, argvJs.size, argvJs)
      jsFreeValue(ptr, thisJs)
      argvJs.forEach {
        jsFreeValue(ptr, it)
      }
      updateChannel.trySend(Unit)
      if (isException(ret)) {
        throw getException(ptr)
      }
      ret
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
        MainScope().launch(dispatcher) {
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

    fun evaluate(cmd: String, name: String = "<eval>", flag: Int = JSEvalFlag.GLOBAL): Any? =
      runOnDispatcher {
        val ret = evaluate(ptr, cmd, name, flag)
        updateChannel.trySend(Unit)
        if (isException(ret)) {
          throw getException(ptr)
        }
        jsToJava(ptr, ret)
      }

    private fun freeValue(obj: JSObject) {
      runOnDispatcher {
        ref.remove(obj.ptr)?.let {
          jsFreeValue(ptr, it.ptr)
        }
      }
    }

    protected fun finalize() {
      runOnDispatcher {
        updateChannel.close()
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