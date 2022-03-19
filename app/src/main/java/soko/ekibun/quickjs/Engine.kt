package soko.ekibun.quickjs

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors

class Engine {
  private var ctx: QuickJS.Context? = null
  private val dispatcher by lazy {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }

  private val updateChannel = Channel<Unit>()
  init {
    MainScope().launch(dispatcher) {
      for(v in updateChannel) {
        ctx?.let { ctx ->
          while (true) {
            val err: Int = ctx.executePendingJob()
            if (err <= 0) {
              if (err < 0) print(ctx.getException())
              break
            }
          }
        }
      }
    }
  }

  suspend fun <T> runWithContext(create: Boolean = true, cb: suspend (ctx: QuickJS.Context) -> T): T {
    return withContext(dispatcher) {
      if (create && ctx == null) ctx = QuickJS.Context(updateChannel)
      cb(ctx?:throw Exception("QuickJS closed"))
    }
  }

  suspend fun close() = withContext(dispatcher) {
    ctx?.executePendingJob()
    ctx = null
  }

  protected fun finalize() {
    updateChannel.close()
  }
}