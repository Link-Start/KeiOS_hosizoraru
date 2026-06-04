package os.kei.feature.home.mcp

import kotlin.test.assertEquals
import org.junit.Test
import os.kei.mcp.server.McpToolCatalog

class McpHomeToolPluginsTest {
    @Test
    fun exportedToolsMatchCatalog() {
        val plugins =
            McpHomeToolPlugins.create(
                baSnapshotProvider =
                    McpHomeBaSnapshotProvider {
                        McpHomeBaSnapshot(
                            activated = false,
                            apCurrent = 0,
                            apLimit = 240,
                            cafeLevel = 10,
                            cafeStored = 0,
                            cafeCap = 740,
                        )
                    },
            )

        assertEquals(McpToolCatalog.homeToolNames, plugins.flatMap { it.toolNames })
    }
}
