package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_CALENDAR_CACHE_SCHEMA_VERSION
import os.kei.ui.page.main.ba.support.BA_POOL_CACHE_SCHEMA_VERSION
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.decodeBaCalendarEntries
import os.kei.ui.page.main.ba.support.decodeBaPoolEntries
import os.kei.ui.page.main.ba.support.fetchBaCalendarEntries
import os.kei.ui.page.main.ba.support.fetchBaPoolEntries
import os.kei.ui.page.main.ba.support.runWithHardTimeout

internal data class BaCalendarSyncSnapshot(
    val entries: List<BaCalendarEntry>,
    val loading: Boolean,
    val error: String?,
    val lastSyncMs: Long
)

internal data class BaPoolSyncSnapshot(
    val entries: List<BaPoolEntry>,
    val loading: Boolean,
    val error: String?,
    val lastSyncMs: Long
)

internal object BaCalendarPoolRepository {
    suspend fun syncCalendar(
        context: Context,
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean,
    ): BaCalendarSyncSnapshot {
        if (!hydrationReady) {
            return BaCalendarSyncSnapshot(
                entries = emptyList(),
                loading = true,
                error = null,
                lastSyncMs = 0L
            )
        }
        val cacheSnapshot = withContext(Dispatchers.IO) {
            BASettingsStore.loadCalendarCacheSnapshot(serverIndex)
        }
        val hasCache = cacheSnapshot.raw.isNotBlank()
        val plan = BaCalendarPoolSyncPlanner.build(
            context = context,
            cacheSyncMs = cacheSnapshot.syncMs,
            hasCache = hasCache,
            cacheSchemaVersion = cacheSnapshot.version,
            expectedSchemaVersion = BA_CALENDAR_CACHE_SCHEMA_VERSION,
            reloadSignal = reloadSignal,
            refreshIntervalHours = calendarRefreshIntervalHours
        )
        val now = plan.nowMs
        val cachedEntries = if (hasCache) {
            runCatching { decodeBaCalendarEntries(cacheSnapshot.raw, now) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val cachedEntriesWithLocalImages = BaCalendarPoolCacheWriter.hydrateCalendarImages(
            context = context,
            serverIndex = serverIndex,
            entries = cachedEntries,
            localOnly = !plan.networkAvailable
        )

        if (!plan.shouldRequestNetwork) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!isPageActive && plan.hasCache) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!plan.networkAvailable) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (plan.hasCache) {
                    context.getString(R.string.ba_calendar_pool_error_offline_cached)
                } else {
                    context.getString(R.string.ba_calendar_pool_error_offline_no_cache)
                },
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                runWithHardTimeout(15_000L) {
                    fetchBaCalendarEntries(serverIndex, now)
                }
            }
        }
        if (result.isSuccess) {
            val entries = result.getOrThrow()
            if (entries.isNotEmpty()) {
                val entriesWithLocalImages = BaCalendarPoolCacheWriter.saveCalendarAndHydrateImages(
                    context = context,
                    serverIndex = serverIndex,
                    entries = entries,
                    nowMs = now
                )
                BaCalendarPoolSyncNotifier.dispatchCalendarSyncNotifications(
                    context = context,
                    serverIndex = serverIndex,
                    previousEntries = cachedEntries,
                    nextEntries = entries,
                    nowMs = now,
                    hadCache = plan.hasCache
                )
                BaCalendarPoolCacheWriter.scheduleCalendarWarm(
                    context = context,
                    serverIndex = serverIndex,
                    entries = entries
                )
                return BaCalendarSyncSnapshot(
                    entries = entriesWithLocalImages,
                    loading = false,
                    error = null,
                    lastSyncMs = now
                )
            }
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (plan.hasCache) context.getString(R.string.ba_calendar_pool_error_empty_keep_cached) else null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        return BaCalendarSyncSnapshot(
            entries = cachedEntriesWithLocalImages,
            loading = false,
            error = if (plan.hasCache) {
                context.getString(R.string.ba_calendar_pool_error_sync_failed_cached)
            } else {
                context.getString(R.string.ba_calendar_error_sync_failed)
            },
            lastSyncMs = cacheSnapshot.syncMs
        )
    }

    suspend fun syncPool(
        context: Context,
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean,
    ): BaPoolSyncSnapshot {
        if (!hydrationReady) {
            return BaPoolSyncSnapshot(
                entries = emptyList(),
                loading = true,
                error = null,
                lastSyncMs = 0L
            )
        }
        val cacheSnapshot = withContext(Dispatchers.IO) {
            BASettingsStore.loadPoolCacheSnapshot(serverIndex)
        }
        val hasCache = cacheSnapshot.raw.isNotBlank()
        val plan = BaCalendarPoolSyncPlanner.build(
            context = context,
            cacheSyncMs = cacheSnapshot.syncMs,
            hasCache = hasCache,
            cacheSchemaVersion = cacheSnapshot.version,
            expectedSchemaVersion = BA_POOL_CACHE_SCHEMA_VERSION,
            reloadSignal = reloadSignal,
            refreshIntervalHours = calendarRefreshIntervalHours
        )
        val now = plan.nowMs
        val cachedEntries = if (hasCache) {
            runCatching { decodeBaPoolEntries(cacheSnapshot.raw, now) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val cachedEntriesWithLocalImages = BaCalendarPoolCacheWriter.hydratePoolImages(
            context = context,
            serverIndex = serverIndex,
            entries = cachedEntries,
            localOnly = !plan.networkAvailable
        )

        if (!plan.shouldRequestNetwork) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!isPageActive && plan.hasCache) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!plan.networkAvailable) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (plan.hasCache) {
                    context.getString(R.string.ba_calendar_pool_error_offline_cached)
                } else {
                    context.getString(R.string.ba_calendar_pool_error_offline_no_cache)
                },
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                runWithHardTimeout(15_000L) {
                    fetchBaPoolEntries(serverIndex, now)
                }
            }
        }
        if (result.isSuccess) {
            val entries = result.getOrThrow()
            if (entries.isNotEmpty()) {
                val entriesWithLocalImages = BaCalendarPoolCacheWriter.savePoolAndHydrateImages(
                    context = context,
                    serverIndex = serverIndex,
                    entries = entries,
                    nowMs = now
                )
                BaCalendarPoolSyncNotifier.dispatchPoolSyncNotifications(
                    context = context,
                    serverIndex = serverIndex,
                    previousEntries = cachedEntries,
                    nextEntries = entries,
                    nowMs = now,
                    hadCache = plan.hasCache
                )
                BaCalendarPoolCacheWriter.schedulePoolWarm(
                    context = context,
                    serverIndex = serverIndex,
                    entries = entries
                )
                return BaPoolSyncSnapshot(
                    entries = entriesWithLocalImages,
                    loading = false,
                    error = null,
                    lastSyncMs = now
                )
            }
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (plan.hasCache) context.getString(R.string.ba_calendar_pool_error_empty_keep_cached) else null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        return BaPoolSyncSnapshot(
            entries = cachedEntriesWithLocalImages,
            loading = false,
            error = if (plan.hasCache) {
                context.getString(R.string.ba_calendar_pool_error_sync_failed_cached)
            } else {
                context.getString(R.string.ba_pool_error_sync_failed)
            },
            lastSyncMs = cacheSnapshot.syncMs
        )
    }
}
