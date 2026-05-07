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
import os.kei.ui.page.main.ba.support.encodeBaCalendarEntries
import os.kei.ui.page.main.ba.support.encodeBaPoolEntries
import os.kei.ui.page.main.ba.support.fetchBaCalendarEntries
import os.kei.ui.page.main.ba.support.fetchBaPoolEntries
import os.kei.ui.page.main.ba.support.isNetworkAvailable
import os.kei.ui.page.main.ba.support.runWithHardTimeout
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget

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
        val now = System.currentTimeMillis()
        val networkAvailable = isNetworkAvailable(context)
        val cacheSnapshot = withContext(Dispatchers.IO) {
            BASettingsStore.loadCalendarCacheSnapshot(serverIndex)
        }
        val hasCache = cacheSnapshot.raw.isNotBlank()
        val cachedEntries = if (hasCache) {
            runCatching { decodeBaCalendarEntries(cacheSnapshot.raw, now) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val cachedEntriesWithLocalImages = if (cachedEntries.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                BaCalendarPoolImageCache.applyCachedCalendarImageUrls(
                    context = context,
                    serverIndex = serverIndex,
                    entries = cachedEntries,
                    localOnly = !networkAvailable
                )
            }
        } else {
            emptyList()
        }
        val intervalMs = calendarRefreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val cacheExpired = !hasCache ||
            cacheSnapshot.syncMs <= 0L ||
            (now - cacheSnapshot.syncMs).coerceAtLeast(0L) >= intervalMs
        val cacheSchemaExpired = cacheSnapshot.version < BA_CALENDAR_CACHE_SCHEMA_VERSION
        val forceRefresh = reloadSignal > 0
        val shouldRequestNetwork = forceRefresh || cacheExpired || cacheSchemaExpired

        if (!shouldRequestNetwork) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!isPageActive && hasCache) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!networkAvailable) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (hasCache) {
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
                val entriesWithLocalImages = withContext(Dispatchers.IO) {
                    BASettingsStore.saveCalendarCache(
                        serverIndex,
                        encodeBaCalendarEntries(entries),
                        now
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
                dispatchCalendarSyncNotifications(
                    context = context,
                    serverIndex = serverIndex,
                    previousEntries = cachedEntries,
                    nextEntries = entries,
                    nowMs = now,
                    hadCache = hasCache
                )
                BaCalendarPoolImageCache.scheduleCalendarWarm(
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
                error = if (hasCache) context.getString(R.string.ba_calendar_pool_error_empty_keep_cached) else null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        return BaCalendarSyncSnapshot(
            entries = cachedEntriesWithLocalImages,
            loading = false,
            error = if (hasCache) {
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
        val now = System.currentTimeMillis()
        val networkAvailable = isNetworkAvailable(context)
        val cacheSnapshot = withContext(Dispatchers.IO) {
            BASettingsStore.loadPoolCacheSnapshot(serverIndex)
        }
        val hasCache = cacheSnapshot.raw.isNotBlank()
        val cachedEntries = if (hasCache) {
            runCatching { decodeBaPoolEntries(cacheSnapshot.raw, now) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val cachedEntriesWithLocalImages = if (cachedEntries.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                BaCalendarPoolImageCache.applyCachedPoolImageUrls(
                    context = context,
                    serverIndex = serverIndex,
                    entries = cachedEntries,
                    localOnly = !networkAvailable
                )
            }
        } else {
            emptyList()
        }
        val intervalMs = calendarRefreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val cacheExpired = !hasCache ||
            cacheSnapshot.syncMs <= 0L ||
            (now - cacheSnapshot.syncMs).coerceAtLeast(0L) >= intervalMs
        val cacheSchemaExpired = cacheSnapshot.version < BA_POOL_CACHE_SCHEMA_VERSION
        val forceRefresh = reloadSignal > 0
        val shouldRequestNetwork = forceRefresh || cacheExpired || cacheSchemaExpired

        if (!shouldRequestNetwork) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!isPageActive && hasCache) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        if (!networkAvailable) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (hasCache) {
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
                val entriesWithLocalImages = withContext(Dispatchers.IO) {
                    BASettingsStore.savePoolCache(
                        serverIndex,
                        encodeBaPoolEntries(entries),
                        now
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
                dispatchPoolSyncNotifications(
                    context = context,
                    serverIndex = serverIndex,
                    previousEntries = cachedEntries,
                    nextEntries = entries,
                    nowMs = now,
                    hadCache = hasCache
                )
                BaCalendarPoolImageCache.schedulePoolWarm(
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
                error = if (hasCache) context.getString(R.string.ba_calendar_pool_error_empty_keep_cached) else null,
                lastSyncMs = cacheSnapshot.syncMs
            )
        }

        return BaPoolSyncSnapshot(
            entries = cachedEntriesWithLocalImages,
            loading = false,
            error = if (hasCache) {
                context.getString(R.string.ba_calendar_pool_error_sync_failed_cached)
            } else {
                context.getString(R.string.ba_pool_error_sync_failed)
            },
            lastSyncMs = cacheSnapshot.syncMs
        )
    }
}

private fun dispatchCalendarSyncNotifications(
    context: Context,
    serverIndex: Int,
    previousEntries: List<BaCalendarEntry>,
    nextEntries: List<BaCalendarEntry>,
    nowMs: Long,
    hadCache: Boolean,
) {
    val leadHours = BASettingsStore.loadCalendarPoolNotifyLeadHours()
    val notifiedKeys = BASettingsStore.loadCalendarPoolNotifiedKeys()

    if (BASettingsStore.loadCalendarUpcomingNotifyEnabled()) {
        val groups = BaReminderCoordinator.calendarUpcomingGroups(
            entries = nextEntries,
            nowMs = nowMs,
            serverIndex = serverIndex,
            leadHours = leadHours,
            notifiedKeys = notifiedKeys
        )
        groups.forEach { group ->
            val targets = group.entries
            if (BaCalendarPoolNotificationDispatcher.sendCalendarUpcomingGroup(
                    context,
                    serverIndex,
                    targets
                )
            ) {
                group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
            }
        }
    }

    if (BASettingsStore.loadCalendarEndingNotifyEnabled()) {
        val groups = BaReminderCoordinator.calendarEndingGroups(
            entries = nextEntries,
            nowMs = nowMs,
            serverIndex = serverIndex,
            leadHours = leadHours,
            notifiedKeys = notifiedKeys
        )
        groups.forEach { group ->
            val targets = group.entries
            if (BaCalendarPoolNotificationDispatcher.sendCalendarEndingGroup(
                    context,
                    serverIndex,
                    targets
                )
            ) {
                group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
            }
        }
    }

    if (hadCache && BASettingsStore.loadCalendarPoolChangeNotifyEnabled()) {
        val changedCount = countCalendarEntryChanges(previousEntries, nextEntries)
        val changeKey = BaReminderCoordinator.changeKey(
            serverIndex = serverIndex,
            type = "calendar_change",
            changedCount = changedCount,
            fingerprint = calendarEntriesFingerprint(nextEntries)
        )
        if (changedCount > 0 &&
            changeKey !in notifiedKeys &&
            BaCalendarPoolNotificationDispatcher.sendDataChanged(
                context = context,
                calendarChangeCount = changedCount,
                poolChangeCount = 0,
                detail = firstChangedCalendarTitle(previousEntries, nextEntries)
            )
        ) {
            BASettingsStore.markCalendarPoolNotified(changeKey)
        }
    }
}

private fun dispatchPoolSyncNotifications(
    context: Context,
    serverIndex: Int,
    previousEntries: List<BaPoolEntry>,
    nextEntries: List<BaPoolEntry>,
    nowMs: Long,
    hadCache: Boolean,
) {
    val leadHours = BASettingsStore.loadCalendarPoolNotifyLeadHours()
    val notifiedKeys = BASettingsStore.loadCalendarPoolNotifiedKeys()

    if (BASettingsStore.loadPoolUpcomingNotifyEnabled()) {
        val groups = BaReminderCoordinator.poolUpcomingGroups(
            entries = nextEntries,
            nowMs = nowMs,
            serverIndex = serverIndex,
            leadHours = leadHours,
            notifiedKeys = notifiedKeys
        )
        groups.forEach { group ->
            val targets = group.entries
            if (BaCalendarPoolNotificationDispatcher.sendPoolUpcomingGroup(
                    context,
                    serverIndex,
                    targets
                )
            ) {
                group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
            }
        }
    }

    if (BASettingsStore.loadPoolEndingNotifyEnabled()) {
        val groups = BaReminderCoordinator.poolEndingGroups(
            entries = nextEntries,
            nowMs = nowMs,
            serverIndex = serverIndex,
            leadHours = leadHours,
            notifiedKeys = notifiedKeys
        )
        groups.forEach { group ->
            val targets = group.entries
            if (BaCalendarPoolNotificationDispatcher.sendPoolEndingGroup(
                    context,
                    serverIndex,
                    targets
                )
            ) {
                group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
            }
        }
    }

    if (hadCache && BASettingsStore.loadCalendarPoolChangeNotifyEnabled()) {
        val changedCount = countPoolEntryChanges(previousEntries, nextEntries)
        val changeKey = BaReminderCoordinator.changeKey(
            serverIndex = serverIndex,
            type = "pool_change",
            changedCount = changedCount,
            fingerprint = poolEntriesFingerprint(nextEntries)
        )
        if (changedCount > 0 &&
            changeKey !in notifiedKeys &&
            BaCalendarPoolNotificationDispatcher.sendDataChanged(
                context = context,
                calendarChangeCount = 0,
                poolChangeCount = changedCount,
                detail = firstChangedPoolTitle(previousEntries, nextEntries)
            )
        ) {
            BASettingsStore.markCalendarPoolNotified(changeKey)
        }
    }
}

private fun calendarEntriesFingerprint(entries: List<BaCalendarEntry>): Long {
    return entries
        .sortedBy { it.id }
        .joinToString(separator = "\n") { "${it.id}|${it.title}|${it.kindId}|${it.beginAtMs}|${it.endAtMs}|${it.linkUrl}" }
        .hashCode()
        .toLong()
        .and(0xffffffffL)
}

private fun poolEntriesFingerprint(entries: List<BaPoolEntry>): Long {
    return entries
        .sortedBy { it.id }
        .joinToString(separator = "\n") { "${it.id}|${it.name}|${it.tagId}|${it.startAtMs}|${it.endAtMs}|${it.linkUrl}" }
        .hashCode()
        .toLong()
        .and(0xffffffffL)
}

private fun countCalendarEntryChanges(
    previousEntries: List<BaCalendarEntry>,
    nextEntries: List<BaCalendarEntry>,
): Int {
    val previousSignatures = previousEntries.associateBy(
        keySelector = { it.id },
        valueTransform = { "${it.title}|${it.kindId}|${it.beginAtMs}|${it.endAtMs}|${it.linkUrl}" }
    )
    return nextEntries.count { entry ->
        previousSignatures[entry.id] != "${entry.title}|${entry.kindId}|${entry.beginAtMs}|${entry.endAtMs}|${entry.linkUrl}"
    }
}

private fun firstChangedCalendarTitle(
    previousEntries: List<BaCalendarEntry>,
    nextEntries: List<BaCalendarEntry>,
): String {
    val previousSignatures = previousEntries.associateBy(
        keySelector = { it.id },
        valueTransform = { "${it.title}|${it.kindId}|${it.beginAtMs}|${it.endAtMs}|${it.linkUrl}" }
    )
    return nextEntries.firstOrNull { entry ->
        previousSignatures[entry.id] != "${entry.title}|${entry.kindId}|${entry.beginAtMs}|${entry.endAtMs}|${entry.linkUrl}"
    }?.title.orEmpty()
}

private fun countPoolEntryChanges(
    previousEntries: List<BaPoolEntry>,
    nextEntries: List<BaPoolEntry>,
): Int {
    val previousSignatures = previousEntries.associateBy(
        keySelector = { it.id },
        valueTransform = { "${it.name}|${it.tagId}|${it.startAtMs}|${it.endAtMs}|${it.linkUrl}" }
    )
    return nextEntries.count { entry ->
        previousSignatures[entry.id] != "${entry.name}|${entry.tagId}|${entry.startAtMs}|${entry.endAtMs}|${entry.linkUrl}"
    }
}

private fun firstChangedPoolTitle(
    previousEntries: List<BaPoolEntry>,
    nextEntries: List<BaPoolEntry>,
): String {
    val previousSignatures = previousEntries.associateBy(
        keySelector = { it.id },
        valueTransform = { "${it.name}|${it.tagId}|${it.startAtMs}|${it.endAtMs}|${it.linkUrl}" }
    )
    return nextEntries.firstOrNull { entry ->
        previousSignatures[entry.id] != "${entry.name}|${entry.tagId}|${entry.startAtMs}|${entry.endAtMs}|${entry.linkUrl}"
    }?.name.orEmpty()
}
