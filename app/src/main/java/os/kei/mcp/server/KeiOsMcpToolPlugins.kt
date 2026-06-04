package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.mcp.McpGitHubRefreshScheduler
import os.kei.feature.github.mcp.McpGitHubToolPlugins

object KeiOsMcpToolPlugins {
    fun create(): List<McpServerToolPlugin> =
        listOf(
            HomePlugin,
            SystemOsPlugin,
        ) +
            McpGitHubToolPlugins.create(
                refreshScheduler = McpGitHubRefreshScheduler { context ->
                    AppBackgroundScheduler.scheduleGitHubRefresh(context)
                }
            ) +
            listOf(BaPlugin)

    private data object HomePlugin : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.homeToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpHomeTools(environment).register(server)
        }
    }

    private data object SystemOsPlugin : McpServerToolPlugin {
        override val toolNames: List<String> =
            McpToolCatalog.systemToolNames + McpToolCatalog.osToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpSystemOsTools(environment).register(server)
        }
    }

    private data object BaPlugin : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.baToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpBaTools(environment).register(server)
        }
    }
}
