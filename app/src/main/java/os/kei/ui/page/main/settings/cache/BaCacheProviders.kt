package os.kei.ui.page.main.settings.cache

import android.content.Context
import os.kei.R
import os.kei.ui.page.main.ba.BaCalendarPoolImageCache
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.baGuideStudentDetailCacheStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.clearBaGuideCatalogCache
import os.kei.ui.page.main.student.clearGameKeeMediaPlaybackCache
import os.kei.ui.page.main.student.loadGameKeeMediaCacheDiagnostics
import os.kei.ui.page.main.student.BaGuideSpineWebCache
import os.kei.ui.page.main.student.BA_GUIDE_SPINE_CACHE_MAX_BYTES

internal fun baCalendarCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "ba_calendar",
        summary = ::baCalendarSummary,
        clear = { context ->
            BASettingsStore.clearCalendarAndPoolCaches()
            BaCalendarPoolImageCache.clearAll(context)
        },
    )

internal fun baStudentGuideCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "ba_student_guide",
        summary = ::baStudentGuideSummary,
        clear = { context ->
            BaStudentGuideStore.clearAllCachedInfo()
            baGuideStudentDetailCacheStore(context).clear()
            clearBaGuideCatalogCache(context)
        },
    )

internal fun baMediaPlaybackCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "ba_media_playback",
        summary = ::baMediaPlaybackSummary,
        clear = { context -> clearGameKeeMediaPlaybackCache(context) },
    )

internal fun baTempMediaCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "ba_temp_media",
        summary = ::baTempMediaSummary,
        clear = { context -> BaGuideTempMediaCache.clearAll(context) },
    )

internal fun baSpineCacheEntryProvider(): CacheEntryProvider = CacheEntryProvider(
    id = "ba_spine_playback",
    summary = { context ->
        val stats = BaGuideSpineWebCache.get(context).stats()
        val clearedAt = CacheEventStore.loadClearedAt("ba_spine_playback")
        CacheEntrySummary(
            id = "ba_spine_playback",
            title = context.getString(R.string.settings_cache_entry_ba_spine_title),
            summary = context.getString(R.string.settings_cache_entry_ba_spine_summary),
            detail = context.getString(R.string.settings_cache_entry_ba_spine_detail, stats.entries, formatBytes(BA_GUIDE_SPINE_CACHE_MAX_BYTES)),
            activity = formatActivity(context, stats.latestModifiedAtMs, clearedAt),
            storage = context.getString(R.string.settings_cache_storage_cache_config_disk, formatBytes(stats.bytes), formatBytes(0), formatBytes(stats.bytes)),
            clearLabel = context.getString(R.string.common_clear),
            cacheBytes = stats.bytes, configBytes = 0, diskBytes = stats.bytes,
            updatedAtMs = stats.latestModifiedAtMs, clearedAtMs = clearedAt,
            freshness = cacheFreshness(stats.latestModifiedAtMs, stats.bytes, rebuildable = true),
        )
    },
    clear = { context -> BaGuideSpineWebCache.get(context).clear() },
)

private fun baCalendarSummary(context: Context): CacheEntrySummary {
    val snapshot = BASettingsStore.loadSnapshot()
    val calendar = BASettingsStore.loadCalendarCacheSnapshot(snapshot.serverIndex)
    val pool = BASettingsStore.loadPoolCacheSnapshot(snapshot.serverIndex)
    val mediaBytes = BaCalendarPoolImageCache.cacheTotalBytes(context)
    val mediaFiles = BaCalendarPoolImageCache.cacheFileCount(context)
    val mediaUpdatedAtMs = BaCalendarPoolImageCache.latestModifiedAtMs(context)
    val mergedCacheBytes = BASettingsStore.cacheBytesEstimated() + mediaBytes
    val configBytes = BASettingsStore.configBytesEstimated()
    val updatedAtMs =
        maxOf(calendar.syncMs, pool.syncMs, mediaUpdatedAtMs).takeIf { it > 0L }
            ?: mmkvLastModified(context, "ba_page_settings")
    val clearedAtMs = CacheEventStore.loadClearedAt("ba_calendar")
    val cachedState = context.getString(R.string.common_status_cached)
    val emptyState = context.getString(R.string.settings_cache_state_empty)
    return CacheEntrySummary(
        id = "ba_calendar",
        title = context.getString(R.string.settings_cache_entry_ba_page_title),
        summary = context.getString(R.string.settings_cache_entry_ba_page_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_ba_page_detail,
                snapshot.serverIndex,
                if (calendar.raw.isNotBlank()) cachedState else emptyState,
                if (pool.raw.isNotBlank()) cachedState else emptyState,
                mediaFiles,
            ),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_mmkv_media,
                formatBytes(mergedCacheBytes),
                formatBytes(configBytes),
                formatBytes(BASettingsStore.actualDataBytes()),
                formatBytes(mediaBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = mergedCacheBytes,
        configBytes = configBytes,
        diskBytes = BASettingsStore.actualDataBytes() + mediaBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = mergedCacheBytes + configBytes,
                rebuildable = true,
                ttlMs = snapshot.calendarRefreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L,
            ),
    )
}

private fun baStudentGuideSummary(context: Context): CacheEntrySummary {
    val detailCount = BaStudentGuideStore.cachedEntryCount()
    val studentDetailCacheStore = baGuideStudentDetailCacheStore(context)
    val studentDetailStats = studentDetailCacheStore.stats()
    val studentDetailFileBytes = studentDetailCacheStore.storageBytes()
    val payloadStats = BaStudentGuideStore.payloadStorageStats(context)
    val catalogCounts = BaGuideCatalogStore.cachedEntryCounts()
    val studentCount = catalogCounts[BaGuideCatalogTab.Student] ?: 0
    val npcSatelliteCount = catalogCounts[BaGuideCatalogTab.NpcSatellite] ?: 0
    val cacheBytes =
            BaStudentGuideStore.cacheBytesEstimated() +
            BaGuideCatalogStore.cacheBytesEstimated() +
            studentDetailFileBytes
    val configBytes = BaStudentGuideStore.configBytesEstimated() + BaGuideCatalogStore.configBytesEstimated()
    val diskBytes =
        BaStudentGuideStore.actualDataBytes() +
            BaGuideCatalogStore.actualDataBytes() +
            studentDetailFileBytes
    val updatedAtMs =
        maxOf(
            BaStudentGuideStore.latestSyncedAtMs(),
            BaGuideCatalogStore.latestSyncedAtMs(),
            studentDetailStats.latestCachedAtMs,
            studentDetailStats.latestValidatedAtMs,
        ).takeIf { it > 0L }
            ?: maxOf(
                mmkvLastModified(context, "ba_student_guide"),
                mmkvLastModified(context, "ba_guide_catalog"),
            )
    val clearedAtMs = CacheEventStore.loadClearedAt("ba_student_guide")
    return CacheEntrySummary(
        id = "ba_student_guide",
        title = context.getString(R.string.settings_cache_entry_ba_guide_title),
        summary = context.getString(R.string.settings_cache_entry_ba_guide_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_ba_guide_detail,
                detailCount,
                studentDetailStats.studentCount,
                studentDetailStats.hotUpdateCount,
                studentDetailStats.longTermCount,
                studentDetailStats.archivedCount,
                studentCount,
                npcSatelliteCount,
            ),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_ba_guide_payloads,
                formatBytes(cacheBytes),
                formatBytes(configBytes),
                formatBytes(diskBytes),
                formatBytes(studentDetailFileBytes),
                formatBytes(payloadStats.mmkvPayloadBytes),
                formatBytes(payloadStats.filePayloadBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = cacheBytes,
        configBytes = configBytes,
        diskBytes = diskBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = cacheBytes + configBytes,
                rebuildable = true,
            ),
    )
}

private fun baTempMediaSummary(context: Context): CacheEntrySummary {
    val fileCount = BaGuideTempMediaCache.cacheFileCount(context)
    val diskBytes = BaGuideTempMediaCache.cacheTotalBytes(context)
    val updatedAtMs = BaGuideTempMediaCache.latestModifiedAtMs(context)
    val clearedAtMs = CacheEventStore.loadClearedAt("ba_temp_media")
    return CacheEntrySummary(
        id = "ba_temp_media",
        title = context.getString(R.string.settings_cache_entry_ba_temp_media_title),
        summary = context.getString(R.string.settings_cache_entry_ba_temp_media_summary),
        detail = context.getString(R.string.settings_cache_entry_file_count_detail, fileCount),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_disk,
                formatBytes(diskBytes),
                formatBytes(0L),
                formatBytes(diskBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = diskBytes,
        configBytes = 0L,
        diskBytes = diskBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = diskBytes,
                rebuildable = true,
            ),
    )
}

private fun baMediaPlaybackSummary(context: Context): CacheEntrySummary {
    val diagnostics = loadGameKeeMediaCacheDiagnostics(context)
    val updatedAtMs =
        maxOf(
            diagnostics.latestModifiedAtMs,
            diagnostics.lastCleanupAtMs,
        ).takeIf { it > 0L } ?: 0L
    val clearedAtMs = CacheEventStore.loadClearedAt("ba_media_playback")
    return CacheEntrySummary(
        id = "ba_media_playback",
        title = context.getString(R.string.settings_cache_entry_ba_playback_title),
        summary = context.getString(R.string.settings_cache_entry_ba_playback_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_ba_playback_detail,
                diagnostics.fileCount,
                diagnostics.cleanupRunCount,
                diagnostics.lastRemovedResourceCount,
                formatBytes(diagnostics.lastRemovedBytes),
            ),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_ba_playback,
                formatBytes(diagnostics.diskBytes),
                formatBytes(0L),
                formatBytes(diagnostics.diskBytes),
                diagnostics.scannedResourceCount,
                diagnostics.removedResourceCount,
                diagnostics.removedSpanCount,
                formatBytes(diagnostics.removedBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = diagnostics.diskBytes,
        configBytes = 0L,
        diskBytes = diagnostics.diskBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = diagnostics.diskBytes,
                rebuildable = true,
            ),
    )
}
