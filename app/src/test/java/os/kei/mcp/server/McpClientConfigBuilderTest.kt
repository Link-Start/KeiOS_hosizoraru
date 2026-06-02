package os.kei.mcp.server

import org.junit.Test
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import kotlin.test.assertEquals

class McpClientConfigBuilderTest {
    @Test
    fun singleServerConfigKeepsStreamableHttpShape() {
        val raw = McpClientConfigBuilder.buildSingleServerConfig(
            serverName = "KeiOS MCP",
            endpoint = "http://127.0.0.1:38888/mcp",
            authToken = "token",
            includeJsonContentTypeHeader = false
        )

        val server = raw.parseJsonObjectOrNull()
            ?.optObject("mcpServers")
            ?.optObject("KeiOS MCP")
            ?: error("single server config should include KeiOS MCP")

        assertEquals("streamablehttp", server.optString("type"))
        assertEquals("http://127.0.0.1:38888/mcp", server.optString("url"))
        assertEquals("Bearer token", server.optObject("headers")?.optString("Authorization"))
    }

    @Test
    fun runtimeAutoConfigIncludesLanWhenAvailable() {
        val raw = McpClientConfigBuilder.buildRuntimeConfigJson(
            state = McpServerUiState(
                port = 38888,
                addresses = listOf("192.168.1.10"),
                authToken = "token",
                serverName = "KeiOS MCP"
            ),
            mode = "auto",
            endpointOverride = "",
            serverNameOverride = ""
        )

        val servers = raw.parseJsonObjectOrNull()
            ?.optObject("mcpServers")
            ?: error("runtime config should include mcpServers")
        val local = servers.optObject("KeiOS MCP Local")
            ?: error("runtime config should include local endpoint")
        val lan = servers.optObject("KeiOS MCP LAN")
            ?: error("runtime config should include lan endpoint")

        assertEquals("http://127.0.0.1:38888/mcp", local.optString("url"))
        assertEquals("http://192.168.1.10:38888/mcp", lan.optString("url"))
        assertEquals(
            "application/json",
            lan.optObject("headers")?.optString("Content-Type")
        )
    }
}
