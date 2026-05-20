package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.core.shizuku.ShizukuApiUtils

internal class McpRuntimeTools(
    private val environment: McpToolEnvironment
) {
    private val appVersionName: String get() = environment.appVersionName
    private val appVersionCode: Long get() = environment.appVersionCode
    private val appPackageName: String get() = environment.appPackageName
    private val appLabel: String get() = environment.appLabel

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.health.ping") { _ ->
            "pong"
        }

        server.addMcpTextTool(environment, name = "keios.app.info") { _ ->
            buildString {
                appendLine("label=$appLabel")
                appendLine("package=$appPackageName")
                appendLine("versionName=$appVersionName")
                appendLine("versionCode=$appVersionCode")
                appendLine("shizukuApi=${ShizukuApiUtils.API_VERSION}")
            }.trim()
        }

        server.addMcpTextTool(environment, name = "keios.app.version") { _ ->
            "versionName=$appVersionName\nversionCode=$appVersionCode"
        }

        server.addMcpTextTool(environment, name = "keios.shizuku.status") { _ ->
            environment.shizukuApiUtils.currentStatus()
        }

        server.addMcpTextTool(environment, name = "keios.mcp.runtime.status") { _ ->
            buildRuntimeStatusText(environment.currentState())
        }

        server.addMcpTextTool(environment, name = "keios.mcp.runtime.logs") { request ->
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_LOG_LIMIT).coerceIn(1, MAX_LOG_LIMIT)
            buildRuntimeLogsText(environment.currentState(), limit)
        }

        server.addMcpTextTool(environment, name = "keios.mcp.runtime.config") { request ->
            val state = environment.currentState()
            val mode = argString(request.arguments?.get("mode"))
            val endpoint = argString(request.arguments?.get("endpoint")).trim()
            val serverName = argString(request.arguments?.get("serverName")).trim()
            buildRuntimeConfigJson(
                state = state,
                mode = mode,
                endpointOverride = endpoint,
                serverNameOverride = serverName
            )
        }
    }

    fun buildRuntimeConfigJson(
        state: McpServerUiState?,
        mode: String,
        endpointOverride: String,
        serverNameOverride: String
    ): String {
        return McpClientConfigBuilder.buildRuntimeConfigJson(
            state = state,
            mode = mode,
            endpointOverride = endpointOverride,
            serverNameOverride = serverNameOverride
        )
    }

    private fun buildRuntimeStatusText(state: McpServerUiState?): String {
        if (state == null) return "runtimeState=unavailable"
        return buildString {
            appendLine("running=${state.running}")
            appendLine("serverName=${state.serverName}")
            appendLine("host=${state.host}")
            appendLine("port=${state.port}")
            appendLine("path=${state.endpointPath}")
            appendLine("allowExternal=${state.allowExternal}")
            appendLine("connectedClients=${state.connectedClients}")
            appendLine("localEndpoint=${state.localEndpoint}")
            appendLine("authTokenPresent=${state.authToken.isNotBlank()}")
            if (state.lanEndpoints.isNotEmpty()) {
                appendLine("lanEndpoints=${state.lanEndpoints.joinToString(",")}")
            }
            if (!state.lastError.isNullOrBlank()) {
                appendLine("lastError=${state.lastError}")
            }
        }.trim()
    }

    private fun buildRuntimeLogsText(state: McpServerUiState?, limit: Int): String {
        if (state == null) return "runtimeState=unavailable"
        if (state.logs.isEmpty()) return "No logs."
        return state.logs.asReversed()
            .take(limit)
            .joinToString("\n") { row -> "[${row.time}] [${row.level}] ${row.message}" }
    }

}
