package os.kei.feature.github.mcp

import os.kei.mcp.server.McpToolCatalog
import org.junit.Test
import kotlin.test.assertEquals

class McpGitHubToolPluginsTest {
    @Test
    fun pluginToolNamesMatchCatalog() {
        val plugins = McpGitHubToolPlugins.create(
            refreshScheduler = McpGitHubRefreshScheduler { }
        )

        assertEquals(
            McpToolCatalog.githubToolNames,
            plugins.flatMap { it.toolNames }
        )
    }
}
