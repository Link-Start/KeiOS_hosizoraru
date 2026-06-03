package os.kei.mcp.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import os.kei.core.json.KeiJson
import java.net.URI

internal object McpClientConfigBuilder {
    fun buildSingleServerConfig(
        serverName: String,
        endpoint: String,
        authToken: String,
        includeJsonContentTypeHeader: Boolean
    ): String {
        return buildConfigJson(
            servers = listOf(
                McpConfigServerEntry(
                    name = serverName,
                    endpoint = endpoint,
                    includeJsonContentTypeHeader = includeJsonContentTypeHeader
                )
            ),
            token = authToken
        )
    }

    fun buildRuntimeConfigJson(
        state: McpServerUiState?,
        mode: String,
        endpointOverride: String,
        serverNameOverride: String
    ): String {
        val fixedMode = normalizeMcpConfigMode(mode)
        val fixedServerName = serverNameOverride.ifBlank {
            state?.serverName ?: McpServerDefaults.SERVER_NAME
        }
        val overrideEndpoint = endpointOverride.trim()
        if (overrideEndpoint.isNotBlank()) {
            return buildConfigJson(
                servers = listOf(
                    McpConfigServerEntry(
                        name = fixedServerName,
                        endpoint = overrideEndpoint,
                        includeJsonContentTypeHeader = shouldIncludeJsonContentTypeHeader(
                            endpoint = overrideEndpoint,
                            mode = fixedMode
                        )
                    )
                ),
                token = state?.authToken ?: "YOUR_TOKEN"
            )
        }

        val localEndpoint = state?.localEndpoint ?: DEFAULT_ENDPOINT
        val lanEndpoint = state?.lanEndpoints?.firstOrNull() ?: localEndpoint
        val servers = when (fixedMode) {
            "local" -> listOf(
                McpConfigServerEntry(
                    name = fixedServerName,
                    endpoint = localEndpoint,
                    includeJsonContentTypeHeader = false
                )
            )

            "lan" -> listOf(
                McpConfigServerEntry(
                    name = fixedServerName,
                    endpoint = lanEndpoint,
                    includeJsonContentTypeHeader = true
                )
            )

            else -> buildList {
                add(
                    McpConfigServerEntry(
                        name = "$fixedServerName Local",
                        endpoint = localEndpoint,
                        includeJsonContentTypeHeader = false
                    )
                )
                if (lanEndpoint != localEndpoint) {
                    add(
                        McpConfigServerEntry(
                            name = "$fixedServerName LAN",
                            endpoint = lanEndpoint,
                            includeJsonContentTypeHeader = true
                        )
                    )
                }
            }
        }
        return buildConfigJson(
            servers = servers,
            token = state?.authToken ?: "YOUR_TOKEN"
        )
    }

    private fun buildConfigJson(
        servers: List<McpConfigServerEntry>,
        token: String
    ): String {
        val fixedServers = servers.ifEmpty {
            listOf(
                McpConfigServerEntry(
                    name = McpServerDefaults.SERVER_NAME,
                    endpoint = DEFAULT_ENDPOINT,
                    includeJsonContentTypeHeader = false
                )
            )
        }
        val payload =
            buildJsonObject {
                putJsonObject("mcpServers") {
                    fixedServers.forEach { server ->
                        putJsonObject(server.name) {
                            put("type", "streamablehttp")
                            put("url", server.endpoint.ifBlank { DEFAULT_ENDPOINT })
                            putJsonObject("headers") {
                                put("Authorization", "Bearer $token")
                                if (server.includeJsonContentTypeHeader) {
                                    put("Content-Type", "application/json")
                                }
                            }
                        }
                    }
                }
            }
        return KeiJson.pretty.encodeToString(payload)
    }

    private fun shouldIncludeJsonContentTypeHeader(endpoint: String, mode: String): Boolean {
        if (mode == "lan") return true
        val host = runCatching { URI(endpoint).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return false
        val lowerHost = host.lowercase()
        return lowerHost != "127.0.0.1" && lowerHost != "localhost"
    }

    private data class McpConfigServerEntry(
        val name: String,
        val endpoint: String,
        val includeJsonContentTypeHeader: Boolean
    )
}
