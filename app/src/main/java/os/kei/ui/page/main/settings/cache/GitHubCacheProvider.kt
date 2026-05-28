package os.kei.ui.page.main.settings.cache

import android.content.Context
import os.kei.R
import os.kei.feature.github.data.local.AppIconCache
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry

internal fun githubCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "github",
        summary = ::githubSummary,
        clear = {
            GitHubReleaseStrategyRegistry.clearAllCaches()
            GitHubTrackStore.clearCheckCache()
            GitHubTrackStoreSignals.notifyChanged()
            GitHubReleaseAssetCacheStore.clearAll()
            AppIconCache.clear()
            CacheEventStore.markCleared("app_icon")
        },
    )

internal fun appIconCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "app_icon",
        summary = ::appIconSummary,
        clear = { AppIconCache.clear() },
    )

private fun githubSummary(context: Context): CacheEntrySummary {
    val snapshot = GitHubTrackStore.loadSnapshot()
    val updatedAtMs =
        snapshot.lastRefreshMs.takeIf { it > 0L }
            ?: mmkvLastModified(context, "github_track_store")
    val clearedAtMs = CacheEventStore.loadClearedAt("github")
    val iconMemory = AppIconCache.estimatedMemoryBytes()
    val assetCacheCount = GitHubReleaseAssetCacheStore.cachedEntryCount()
    val refreshState =
        context.getString(
            if (snapshot.lastRefreshMs > 0L) {
                R.string.settings_cache_refresh_record_present
            } else {
                R.string.settings_cache_refresh_record_empty
            },
        )
    val cacheBytes = GitHubTrackStore.cacheBytesEstimated()
    val configBytes = GitHubTrackStore.configBytesEstimated()
    val diskBytes = GitHubTrackStore.actualDataBytes()
    return CacheEntrySummary(
        id = "github",
        title = context.getString(R.string.settings_cache_entry_github_title),
        summary = context.getString(R.string.settings_cache_entry_github_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_github_detail,
                snapshot.items.size,
                snapshot.checkCache.size,
                assetCacheCount,
                refreshState,
            ),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_mmkv_icon,
                formatBytes(cacheBytes),
                formatBytes(configBytes),
                formatBytes(diskBytes),
                formatBytes(iconMemory),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = cacheBytes,
        configBytes = configBytes,
        diskBytes = diskBytes,
        memoryBytes = iconMemory,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = cacheBytes + configBytes,
                rebuildable = true,
                ttlMs = snapshot.refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L,
            ),
    )
}

private fun appIconSummary(context: Context): CacheEntrySummary {
    val memoryBytes = AppIconCache.estimatedMemoryBytes()
    val updatedAtMs = AppIconCache.lastUpdatedAtMs()
    val clearedAtMs = CacheEventStore.loadClearedAt("app_icon")
    return CacheEntrySummary(
        id = "app_icon",
        title = context.getString(R.string.settings_cache_entry_app_icon_title),
        summary = context.getString(R.string.settings_cache_entry_app_icon_summary),
        detail = context.getString(R.string.settings_cache_entry_app_icon_detail, AppIconCache.size()),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_memory,
                formatBytes(memoryBytes),
                formatBytes(0L),
                formatBytes(memoryBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = memoryBytes,
        configBytes = 0L,
        memoryBytes = memoryBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = memoryBytes,
                rebuildable = true,
            ),
    )
}
