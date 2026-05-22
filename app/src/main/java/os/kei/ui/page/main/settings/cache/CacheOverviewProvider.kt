package os.kei.ui.page.main.settings.cache

import android.content.Context
import os.kei.R

internal fun buildCacheOverview(
    context: Context,
    entries: List<CacheEntrySummary>,
): CacheEntrySummary {
    val cacheBytes = entries.sumOf(CacheEntrySummary::cacheBytes)
    val configBytes = entries.sumOf(CacheEntrySummary::configBytes)
    val diskBytes = entries.sumOf(CacheEntrySummary::diskBytes)
    val memoryBytes = entries.sumOf(CacheEntrySummary::memoryBytes)
    val updatedAtMs = entries.maxOfOrNull(CacheEntrySummary::updatedAtMs)?.takeIf { it > 0L } ?: 0L
    val clearedAtMs = entries.maxOfOrNull(CacheEntrySummary::clearedAtMs)?.takeIf { it > 0L } ?: 0L
    return CacheEntrySummary(
        id = "cache_overview",
        title = context.getString(R.string.settings_cache_entry_overview_title),
        summary = context.getString(R.string.settings_cache_entry_overview_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_overview_detail,
                formatBytes(cacheBytes),
                formatBytes(configBytes),
                formatBytes(cacheBytes + configBytes),
            ),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_disk_memory,
                formatBytes(diskBytes),
                formatBytes(memoryBytes),
            ),
        clearLabel = "",
        cacheBytes = cacheBytes,
        configBytes = configBytes,
        diskBytes = diskBytes,
        memoryBytes = memoryBytes,
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
