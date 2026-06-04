package os.kei.feature.ba.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.mcp.server.McpServerToolPlugin
import os.kei.mcp.server.McpToolCatalog
import os.kei.mcp.server.McpToolEnvironment

object McpBaToolPlugins {
    fun create(delegateFactory: McpBaToolDelegateFactory): List<McpServerToolPlugin> =
        listOf(BaPlugin(delegateFactory))

    private data class BaPlugin(
        private val delegateFactory: McpBaToolDelegateFactory,
    ) : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.baToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpBaTools(
                environment = environment,
                delegate = delegateFactory.create(environment),
            ).register(server)
        }
    }
}
