package os.kei.mcp.server

import org.json.JSONObject
import org.junit.Test
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

        val server = JSONObject(raw)
            .getJSONObject("mcpServers")
            .getJSONObject("KeiOS MCP")

        assertEquals("streamablehttp", server.getString("type"))
        assertEquals("http://127.0.0.1:38888/mcp", server.getString("url"))
        assertEquals("Bearer token", server.getJSONObject("headers").getString("Authorization"))
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

        val servers = JSONObject(raw).getJSONObject("mcpServers")
        assertEquals("http://127.0.0.1:38888/mcp", servers.getJSONObject("KeiOS MCP Local").getString("url"))
        assertEquals("http://192.168.1.10:38888/mcp", servers.getJSONObject("KeiOS MCP LAN").getString("url"))
        assertEquals(
            "application/json",
            servers.getJSONObject("KeiOS MCP LAN").getJSONObject("headers").getString("Content-Type")
        )
    }
}
