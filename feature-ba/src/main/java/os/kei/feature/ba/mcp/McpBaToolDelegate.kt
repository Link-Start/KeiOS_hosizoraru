package os.kei.feature.ba.mcp

import os.kei.mcp.server.McpToolEnvironment

interface McpBaToolDelegate {
    fun defaultGuideRefreshIntervalHours(): Int

    fun buildBaSnapshotText(): String

    fun buildBaCalendarCacheText(
        requestedServerIndex: Int?,
        includeEntries: Boolean,
        limit: Int,
    ): String

    fun buildBaPoolCacheText(
        requestedServerIndex: Int?,
        includeEntries: Boolean,
        limit: Int,
    ): String

    fun buildGuideCatalogCacheText(
        tab: String,
        includeEntries: Boolean,
        limit: Int,
    ): String

    fun buildGuideCacheOverviewText(): String

    fun buildGuideCacheInspectText(
        url: String,
        includeSections: Boolean,
        refreshIntervalHours: Int,
    ): String

    fun buildGuideMediaListText(
        url: String,
        kind: String,
        limit: Int,
    ): String

    suspend fun buildGuideBgmFavoritesText(
        action: String,
        query: String,
        limit: Int,
        rawJson: String,
        apply: Boolean,
    ): String

    fun buildCacheClearText(scope: String, url: String): String
}

fun interface McpBaToolDelegateFactory {
    fun create(environment: McpToolEnvironment): McpBaToolDelegate
}
