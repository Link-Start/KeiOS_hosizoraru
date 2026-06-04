package os.kei.feature.os.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.mcp.server.McpServerToolPlugin
import os.kei.mcp.server.McpToolCatalog
import os.kei.mcp.server.McpToolEnvironment

object McpSystemOsToolPlugins {
    fun create(delegateFactory: McpSystemOsToolDelegateFactory): List<McpServerToolPlugin> =
        listOf(SystemOsPlugin(delegateFactory))

    private data class SystemOsPlugin(
        private val delegateFactory: McpSystemOsToolDelegateFactory,
    ) : McpServerToolPlugin {
        override val toolNames: List<String> =
            McpToolCatalog.systemToolNames + McpToolCatalog.osToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpSystemOsTools(
                environment = environment,
                delegate = delegateFactory.create(environment),
            ).register(server)
        }
    }
}
