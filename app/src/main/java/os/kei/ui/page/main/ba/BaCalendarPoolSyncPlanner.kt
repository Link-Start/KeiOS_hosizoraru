package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.ui.page.main.ba.support.isNetworkAvailable

internal data class BaCalendarPoolSyncPlan(
    val nowMs: Long,
    val networkAvailable: Boolean,
    val hasCache: Boolean,
    val cacheExpired: Boolean,
    val cacheSchemaExpired: Boolean,
    val forceRefresh: Boolean
) {
    val shouldRequestNetwork: Boolean
        get() = forceRefresh || cacheExpired || cacheSchemaExpired
}

internal object BaCalendarPoolSyncPlanner {
    fun build(
        context: Context,
        cacheSyncMs: Long,
        hasCache: Boolean,
        cacheSchemaVersion: Int,
        expectedSchemaVersion: Int,
        reloadSignal: Int,
        refreshIntervalHours: Int,
        nowMs: Long = System.currentTimeMillis()
    ): BaCalendarPoolSyncPlan {
        val intervalMs = refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        return BaCalendarPoolSyncPlan(
            nowMs = nowMs,
            networkAvailable = isNetworkAvailable(context),
            hasCache = hasCache,
            cacheExpired = !hasCache ||
                    cacheSyncMs <= 0L ||
                    (nowMs - cacheSyncMs).coerceAtLeast(0L) >= intervalMs,
            cacheSchemaExpired = cacheSchemaVersion < expectedSchemaVersion,
            forceRefresh = reloadSignal > 0
        )
    }
}
