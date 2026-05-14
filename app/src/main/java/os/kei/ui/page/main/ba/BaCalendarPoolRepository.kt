package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_CALENDAR_CACHE_SCHEMA_VERSION
import os.kei.ui.page.main.ba.support.BA_POOL_CACHE_SCHEMA_VERSION
import os.kei.ui.page.main.ba.support.BaPoolStudentGuideUrlResolver
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.decodeBaCalendarEntries
import os.kei.ui.page.main.ba.support.decodeBaPoolEntries
import os.kei.ui.page.main.ba.support.fetchBaCalendarRemoteResult
import os.kei.ui.page.main.ba.support.fetchBaPoolRemoteResult
import os.kei.ui.page.main.ba.support.runWithHardTimeout
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.fetchBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.loadCachedBaGuideCatalogBundle

@Immutable
internal data class BaCalendarSyncSnapshot(
    val entries: List<BaCalendarEntry>,
    val loading: Boolean,
    val error: String?,
    val lastSyncMs: Long
)

@Immutable
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
                    fetchBaCalendarRemoteResult(serverIndex, now)
                }
            }
        }
        if (result.isSuccess) {
            val entries = result.getOrThrow().entries
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
        val decodedCachedEntries = if (hasCache) {
            runCatching { decodeBaPoolEntries(cacheSnapshot.raw, now) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val cachedEntries = resolvePoolStudentGuideUrls(
            serverIndex = serverIndex,
            entries = decodedCachedEntries,
            allowCatalogNetwork = false
        )
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
                    fetchBaPoolRemoteResult(serverIndex, now)
                }
            }
        }
        if (result.isSuccess) {
            val entries = resolvePoolStudentGuideUrls(
                serverIndex = serverIndex,
                entries = result.getOrThrow().entries,
                allowCatalogNetwork = true
            )
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

    private suspend fun resolvePoolStudentGuideUrls(
        serverIndex: Int,
        entries: List<BaPoolEntry>,
        allowCatalogNetwork: Boolean,
    ): List<BaPoolEntry> {
        if (entries.isEmpty()) return entries
        val directlyResolved = entries.map { BaPoolStudentGuideUrlResolver.Empty.resolve(it) }
        if (serverIndex != 0 || directlyResolved.all { it.studentGuideUrl.isNotBlank() }) {
            return directlyResolved
        }

        val cachedCatalogEntries = withContext(Dispatchers.IO) {
            loadCachedBaGuideCatalogBundle()?.entries(BaGuideCatalogTab.Student).orEmpty()
        }
        val cachedResolver = BaPoolStudentGuideUrlResolver.fromCatalogEntries(cachedCatalogEntries)
        val resolvedFromCache = directlyResolved.map(cachedResolver::resolve)
        if (!allowCatalogNetwork || resolvedFromCache.all { it.studentGuideUrl.isNotBlank() }) {
            return resolvedFromCache
        }

        val networkCatalogEntries = withContext(Dispatchers.IO) {
            runCatching {
                fetchBaGuideCatalogBundle(forceRefresh = false)
                    .entries(BaGuideCatalogTab.Student)
            }.getOrDefault(emptyList())
        }
        val networkResolver = BaPoolStudentGuideUrlResolver.fromCatalogEntries(networkCatalogEntries)
        return resolvedFromCache.map(networkResolver::resolve)
    }
}
