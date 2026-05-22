package os.kei.ui.page.main.settings.cache

import android.content.Context
import os.kei.R
import os.kei.core.system.AppBuildEnv
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.os.OsCardVisibilityStore
import os.kei.ui.page.main.os.OsInfoCache
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.OsUiStateStore

internal fun osInfoCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "os_info",
        summary = ::osSummary,
        clear = { OsInfoCache.clearAll() },
    )

internal fun debugUiDumpCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "debug_ui_dump",
        summary = ::debugUiDumpSummary,
        clear = { context -> clearDebugUiDump(context) },
    )

internal fun mcpPrefsCacheEntryProvider(): CacheEntryProvider =
    CacheEntryProvider(
        id = "mcp_prefs",
        summary = ::mcpSummary,
        clear = { McpServerManager.clearSavedCacheOnly() },
    )

private fun osSummary(context: Context): CacheEntrySummary {
    val visible = OsCardVisibilityStore.loadVisibleCards().size
    val cachedSections = OsInfoCache.cachedSectionCount(OsSectionCard.entries.toSet())
    val cacheBytes = OsInfoCache.cacheBytesEstimated()
    val configBytes = OsUiStateStore.configBytesEstimated()
    val diskBytes = OsInfoCache.actualDataBytes() + OsUiStateStore.actualDataBytes()
    val updatedAtMs =
        maxOf(
            mmkvLastModified(context, "os_info_cache"),
            mmkvLastModified(context, "system_info_cache"),
            mmkvLastModified(context, "os_ui_state"),
            mmkvLastModified(context, "system_ui_state"),
        ).takeIf { it > 0L } ?: 0L
    val clearedAtMs = CacheEventStore.loadClearedAt("os_info")
    return CacheEntrySummary(
        id = "os_info",
        title = context.getString(R.string.settings_cache_entry_os_title),
        summary = context.getString(R.string.settings_cache_entry_os_summary),
        detail = context.getString(R.string.settings_cache_entry_os_detail, visible, cachedSections),
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_mmkv,
                formatBytes(cacheBytes),
                formatBytes(configBytes),
                formatBytes(diskBytes),
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

private fun debugUiDumpSummary(context: Context): CacheEntrySummary {
    val targetDir = AppBuildEnv.uiDumpDirectory(context)
    val stats = collectDirectoryStats(targetDir)
    val clearedAtMs = CacheEventStore.loadClearedAt("debug_ui_dump")
    return CacheEntrySummary(
        id = "debug_ui_dump",
        title = context.getString(R.string.settings_cache_entry_debug_ui_dump_title),
        summary = context.getString(R.string.settings_cache_entry_debug_ui_dump_summary),
        detail =
            context.getString(
                R.string.settings_cache_entry_debug_ui_dump_detail,
                AppBuildEnv.displayName,
                stats.fileCount,
            ),
        activity = formatActivity(context, stats.latestModifiedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_debug_ui_dump,
                formatBytes(stats.totalBytes),
                formatBytes(0L),
                formatBytes(stats.totalBytes),
                targetDir.absolutePath,
            ),
        clearLabel = context.getString(R.string.common_clear),
        cacheBytes = stats.totalBytes,
        configBytes = 0L,
        diskBytes = stats.totalBytes,
        updatedAtMs = stats.latestModifiedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = stats.latestModifiedAtMs,
                bytes = stats.totalBytes,
                rebuildable = false,
            ),
    )
}

private fun mcpSummary(context: Context): CacheEntrySummary {
    val snapshot = McpServerManager.loadSavedCacheSummary(context)
    val updatedAtMs = mmkvLastModified(context, "mcp_server_prefs")
    val clearedAtMs = CacheEventStore.loadClearedAt("mcp_prefs")
    val configBytes = McpServerManager.configBytesEstimated()
    val diskBytes = McpServerManager.actualDataBytes()
    return CacheEntrySummary(
        id = "mcp_prefs",
        title = context.getString(R.string.settings_cache_entry_mcp_title),
        summary = context.getString(R.string.settings_cache_entry_mcp_summary),
        detail = snapshot,
        activity = formatActivity(context, updatedAtMs, clearedAtMs),
        storage =
            context.getString(
                R.string.settings_cache_storage_cache_config_mmkv,
                formatBytes(0L),
                formatBytes(configBytes),
                formatBytes(diskBytes),
            ),
        clearLabel = context.getString(R.string.common_reset),
        cacheBytes = 0L,
        configBytes = configBytes,
        diskBytes = diskBytes,
        updatedAtMs = updatedAtMs,
        clearedAtMs = clearedAtMs,
        freshness =
            cacheFreshness(
                updatedAtMs = updatedAtMs,
                bytes = configBytes,
                rebuildable = true,
            ),
    )
}
