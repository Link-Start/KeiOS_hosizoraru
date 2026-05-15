package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

internal class BaCalendarPoolImageWarmCoordinator(
    private val scope: CoroutineScope,
    context: Context,
) {
    private val appContext = context.applicationContext
    private val jobs = ConcurrentHashMap<String, Job>()

    fun scheduleCalendar(
        serverIndex: Int,
        entries: List<BaCalendarEntry>,
        delayMs: Long = UiPerformanceBudget.baCalendarPoolDeferredWarmDelayMs,
    ) {
        schedule(
            key = "calendar:$serverIndex",
            delayMs = delayMs,
        ) {
            BaCalendarPoolImageCache.warmAndPruneCalendar(
                context = appContext,
                serverIndex = serverIndex,
                entries = entries,
            )
        }
    }

    fun schedulePool(
        serverIndex: Int,
        entries: List<BaPoolEntry>,
        delayMs: Long = UiPerformanceBudget.baCalendarPoolDeferredWarmDelayMs,
    ) {
        schedule(
            key = "pool:$serverIndex",
            delayMs = delayMs,
        ) {
            BaCalendarPoolImageCache.warmAndPrunePool(
                context = appContext,
                serverIndex = serverIndex,
                entries = entries,
            )
        }
    }

    private fun schedule(
        key: String,
        delayMs: Long,
        block: suspend () -> Unit,
    ) {
        jobs.remove(key)?.cancel()
        jobs[key] = scope.launch {
            if (delayMs > 0L) {
                delay(delayMs.milliseconds)
            }
            block()
        }
    }
}
