package os.kei.ui.page.main.settings.cache

import android.content.Context
import os.kei.R
import os.kei.feature.github.domain.GitHubCacheService

internal fun githubCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "github",
        summary = ::githubSummary,
        clear = {
            GitHubCacheService.clearGitHubCaches()
            CacheEventStore.markCleared("app_icon")
        },
    )

internal fun appIconCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "app_icon",
        summary = ::appIconSummary,
        clear = { GitHubCacheService.clearAppIconCache() },
    )

private fun githubSummary(context: Context): CacheEntrySummary {
    val data = GitHubCacheService.loadGitHubSummaryData()
    val updatedAtMs = data.lastRefreshMs
    val clearedAtMs = CacheEventStore.loadClearedAt("github")
    val refreshState =
        context.getString(
            if (data.lastRefreshMs > 0L) {
                R.string.settings_cache_refresh_record_present
            } else {
                R.string.settings_cache_refresh_record_empty
            },
        )
    return CacheEntrySummary(
        id = "github",
        title = context.getString(R.string.settings_cache_entry_github_title),
        summary = context.getString(R.string.settings_cache_entry_github_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_github_detail,
                data.trackedCount,
                data.checkCacheCount,
                data.releaseAssetCacheCount,
                refreshState,
            ),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_mmkv_icon,
                formatBytes(data.cacheBytes),
                formatBytes(data.configBytes),
                formatBytes(data.diskBytes),
                formatBytes(data.iconMemoryBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = data.cacheBytes,
        configBytes = data.configBytes,
        diskBytes = data.diskBytes,
        memoryBytes = data.iconMemoryBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = data.cacheBytes,
                rebuildable = true,
                ttlMs = data.refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L,
            ),
    )
}

private fun appIconSummary(context: Context): CacheEntrySummary {
    val data = GitHubCacheService.loadAppIconSummaryData()
    val clearedAtMs = CacheEventStore.loadClearedAt("app_icon")
    return CacheEntrySummary(
        id = "app_icon",
        title = context.getString(R.string.settings_cache_entry_app_icon_title),
        summary = context.getString(R.string.settings_cache_entry_app_icon_summary),
        detail = context.getString(R.string.settings_cache_entry_app_icon_detail, data.iconCount),
        activity = formatActivity(context, data.updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_memory,
                formatBytes(data.memoryBytes),
                formatBytes(0L),
                formatBytes(data.memoryBytes),
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = data.memoryBytes,
        configBytes = 0L,
        memoryBytes = data.memoryBytes,
        updatedAtMs = data.updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = data.updatedAtMs,
                bytes = data.memoryBytes,
                rebuildable = true,
            ),
    )
}
