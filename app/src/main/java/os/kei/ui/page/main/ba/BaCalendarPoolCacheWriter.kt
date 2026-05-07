package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.encodeBaCalendarEntries
import os.kei.ui.page.main.ba.support.encodeBaPoolEntries
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget

internal object BaCalendarPoolCacheWriter {
    suspend fun hydrateCalendarImages(
        context: Context,
        serverIndex: Int,
        entries: List<BaCalendarEntry>,
        localOnly: Boolean
    ): List<BaCalendarEntry> {
        if (entries.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            BaCalendarPoolImageCache.applyCachedCalendarImageUrls(
                context = context,
                serverIndex = serverIndex,
                entries = entries,
                localOnly = localOnly
            )
        }
    }

    suspend fun hydratePoolImages(
        context: Context,
        serverIndex: Int,
        entries: List<BaPoolEntry>,
        localOnly: Boolean
    ): List<BaPoolEntry> {
        if (entries.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            BaCalendarPoolImageCache.applyCachedPoolImageUrls(
                context = context,
                serverIndex = serverIndex,
                entries = entries,
                localOnly = localOnly
            )
        }
    }

    suspend fun saveCalendarAndHydrateImages(
        context: Context,
        serverIndex: Int,
        entries: List<BaCalendarEntry>,
        nowMs: Long
    ): List<BaCalendarEntry> {
        return withContext(Dispatchers.IO) {
            BASettingsStore.saveCalendarCache(
                serverIndex,
                encodeBaCalendarEntries(entries),
                nowMs
            )
            BaCalendarPoolImageCache.prefetchForCalendar(
                context = context,
                serverIndex = serverIndex,
                entries = entries.take(UiPerformanceBudget.baCalendarPoolPriorityPrefetchCount)
            )
            BaCalendarPoolImageCache.applyCachedCalendarImageUrls(
                context = context,
                serverIndex = serverIndex,
                entries = entries,
                localOnly = false
            )
        }
    }

    suspend fun savePoolAndHydrateImages(
        context: Context,
        serverIndex: Int,
        entries: List<BaPoolEntry>,
        nowMs: Long
    ): List<BaPoolEntry> {
        return withContext(Dispatchers.IO) {
            BASettingsStore.savePoolCache(
                serverIndex,
                encodeBaPoolEntries(entries),
                nowMs
            )
            BaCalendarPoolImageCache.prefetchForPool(
                context = context,
                serverIndex = serverIndex,
                entries = entries.take(UiPerformanceBudget.baCalendarPoolPriorityPrefetchCount)
            )
            BaCalendarPoolImageCache.applyCachedPoolImageUrls(
                context = context,
                serverIndex = serverIndex,
                entries = entries,
                localOnly = false
            )
        }
    }

    fun scheduleCalendarWarm(
        context: Context,
        serverIndex: Int,
        entries: List<BaCalendarEntry>
    ) {
        BaCalendarPoolImageCache.scheduleCalendarWarm(
            context = context,
            serverIndex = serverIndex,
            entries = entries
        )
    }

    fun schedulePoolWarm(
        context: Context,
        serverIndex: Int,
        entries: List<BaPoolEntry>
    ) {
        BaCalendarPoolImageCache.schedulePoolWarm(
            context = context,
            serverIndex = serverIndex,
            entries = entries
        )
    }
}
