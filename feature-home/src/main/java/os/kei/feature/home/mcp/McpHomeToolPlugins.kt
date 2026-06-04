package os.kei.feature.home.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.mcp.server.McpServerToolPlugin
import os.kei.mcp.server.McpToolCatalog
import os.kei.mcp.server.McpToolEnvironment

object McpHomeToolPlugins {
    fun create(baSnapshotProvider: McpHomeBaSnapshotProvider): List<McpServerToolPlugin> =
        listOf(HomePlugin(baSnapshotProvider))

    private data class HomePlugin(
        private val baSnapshotProvider: McpHomeBaSnapshotProvider,
    ) : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.homeToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpHomeTools(
                environment = environment,
                baSnapshotProvider = baSnapshotProvider,
            ).register(server)
        }
    }
}
