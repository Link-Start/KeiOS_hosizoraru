package os.kei.feature.ba.mcp

import kotlin.test.assertEquals
import org.junit.Test
import os.kei.mcp.server.McpToolCatalog

class McpBaToolPluginsTest {
    @Test
    fun exportedToolsMatchCatalog() {
        val plugins =
            McpBaToolPlugins.create(
                delegateFactory = McpBaToolDelegateFactory {
                    FakeBaToolDelegate
                },
            )

        assertEquals(McpToolCatalog.baToolNames, plugins.flatMap { it.toolNames })
    }

    private data object FakeBaToolDelegate : McpBaToolDelegate {
        override fun defaultGuideRefreshIntervalHours(): Int = 12
        override fun buildBaSnapshotText(): String = ""
        override fun buildBaCalendarCacheText(requestedServerIndex: Int?, includeEntries: Boolean, limit: Int): String = ""
        override fun buildBaPoolCacheText(requestedServerIndex: Int?, includeEntries: Boolean, limit: Int): String = ""
        override fun buildGuideCatalogCacheText(tab: String, includeEntries: Boolean, limit: Int): String = ""
        override fun buildGuideCacheOverviewText(): String = ""
        override fun buildGuideCacheInspectText(url: String, includeSections: Boolean, refreshIntervalHours: Int): String = ""
        override fun buildGuideMediaListText(url: String, kind: String, limit: Int): String = ""
        override suspend fun buildGuideBgmFavoritesText(action: String, query: String, limit: Int, rawJson: String, apply: Boolean): String = ""
        override fun buildCacheClearText(scope: String, url: String): String = ""
    }
}
