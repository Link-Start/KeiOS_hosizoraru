package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface McpServerToolPlugin {
    val toolNames: List<String>

    fun registerTools(
        server: Server,
        environment: McpToolEnvironment,
    )
}
