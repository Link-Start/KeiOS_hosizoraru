package os.kei.feature.os.mcp

import kotlin.test.assertEquals
import org.junit.Test
import os.kei.mcp.server.McpToolCatalog

class McpSystemOsToolPluginsTest {
    @Test
    fun exportedToolsMatchCatalog() {
        val plugins =
            McpSystemOsToolPlugins.create(
                delegateFactory = McpSystemOsToolDelegateFactory {
                    FakeSystemOsToolDelegate
                },
            )

        assertEquals(
            McpToolCatalog.systemToolNames + McpToolCatalog.osToolNames,
            plugins.flatMap { it.toolNames },
        )
    }

    private data object FakeSystemOsToolDelegate : McpSystemOsToolDelegate {
        override fun buildTopInfoText(query: String, limit: Int): String = ""
        override fun buildOsCardsSnapshotText(): String = ""
        override fun buildOsActivityCardsText(query: String, onlyVisible: Boolean, limit: Int): String = ""
        override fun buildOsShellCardsText(query: String, onlyVisible: Boolean, includeOutput: Boolean, limit: Int): String = ""
        override fun buildOsCardsExportText(target: String): String = ""
        override fun buildOsCardsImportText(target: String, rawJson: String, apply: Boolean): String = ""
    }
}
