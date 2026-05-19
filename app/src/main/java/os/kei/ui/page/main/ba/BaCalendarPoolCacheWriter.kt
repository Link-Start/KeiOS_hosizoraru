package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.encodeBaCalendarEntries
import os.kei.ui.page.main.ba.support.encodeBaPoolEntries

internal object BaCalendarPoolCacheWriter {
    suspend fun hydrateCalendarImages(
        context: Context,
        serverIndex: Int,
        entries: List<BaCalendarEntry>,
        localOnly: Boolean
    ): List<BaCalendarEntry> {
        if (entries.isEmpty()) return emptyList()
        return withContext(AppDispatchers.baFetch) {
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
        return withContext(AppDispatchers.baFetch) {
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
        return withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCalendarCache(
                serverIndex,
                encodeBaCalendarEntries(entries),
                nowMs
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
        return withContext(AppDispatchers.baFetch) {
            BASettingsStore.savePoolCache(
                serverIndex,
                encodeBaPoolEntries(entries),
                nowMs
            )
            BaCalendarPoolImageCache.applyCachedPoolImageUrls(
                context = context,
                serverIndex = serverIndex,
                entries = entries,
                localOnly = false
            )
        }
    }
}
