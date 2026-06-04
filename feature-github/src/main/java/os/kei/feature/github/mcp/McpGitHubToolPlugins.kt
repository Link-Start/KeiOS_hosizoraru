package os.kei.feature.github.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.mcp.server.McpServerToolPlugin
import os.kei.mcp.server.McpToolCatalog
import os.kei.mcp.server.McpToolEnvironment

object McpGitHubToolPlugins {
    fun create(refreshScheduler: McpGitHubRefreshScheduler): List<McpServerToolPlugin> =
        listOf(GitHubPlugin(refreshScheduler))

    private data class GitHubPlugin(
        private val refreshScheduler: McpGitHubRefreshScheduler
    ) : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.githubToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpGitHubDiscoveryTools(
                environment = environment,
                refreshScheduler = refreshScheduler,
            ).register(server)
            McpGitHubTrackingTools(
                environment = environment,
                refreshScheduler = refreshScheduler,
            ).register(server)
            McpGitHubActionsTools(environment).register(server)
        }
    }
}
