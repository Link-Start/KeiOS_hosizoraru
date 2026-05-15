package os.kei.core.background

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogger
import java.util.concurrent.atomic.AtomicBoolean

object BackgroundAsyncReceiverRunner {
    private const val DEFAULT_RECEIVER_TIMEOUT_MS = 45_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(
        receiver: BroadcastReceiver,
        context: Context,
        tag: String,
        timeoutMs: Long = DEFAULT_RECEIVER_TIMEOUT_MS,
        onTimeout: suspend (Context) -> Unit = {},
        block: suspend (Context) -> Unit
    ) {
        val pendingResult = receiver.goAsync()
        val appContext = context.applicationContext
        launchWithPendingResult(
            context = appContext,
            tag = tag,
            timeoutMs = timeoutMs,
            finishPending = pendingResult::finish,
            onTimeout = onTimeout,
            block = block
        )
    }

    internal fun launchWithPendingResult(
        context: Context,
        tag: String,
        timeoutMs: Long = DEFAULT_RECEIVER_TIMEOUT_MS,
        finishPending: () -> Unit,
        runnerScope: CoroutineScope = scope,
        onTimeout: suspend (Context) -> Unit = {},
        block: suspend (Context) -> Unit
    ): Job {
        val finished = AtomicBoolean(false)
        fun finishPendingOnce() {
            if (finished.compareAndSet(false, true)) {
                finishPending()
            }
        }

        val appContext = context.applicationContext
        val worker = runnerScope.launch {
            try {
                block(appContext)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLogger.w(tag, "async receiver failed", error)
            } finally {
                finishPendingOnce()
            }
        }
        val watchdog = runnerScope.launch {
            delay(timeoutMs.coerceAtLeast(1L))
            if (!worker.isActive) return@launch
            finishPendingOnce()
            AppLogger.w(tag, "async receiver timed out after ${timeoutMs}ms")
            worker.cancel(
                CancellationException("Async receiver timed out after ${timeoutMs}ms")
            )
            try {
                onTimeout(appContext)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLogger.w(tag, "async receiver timeout recovery failed", error)
            }
        }
        worker.invokeOnCompletion {
            watchdog.cancel()
            finishPendingOnce()
        }
        return worker
    }
}
