package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal class McpSystemOsTools(
    environment: McpToolEnvironment
) {
    private val responseBuilder = McpSystemOsResponseBuilder(environment)

    fun register(server: Server) {
        server.addTool(
            name = "keios.system.topinfo.query",
            description = "Query TopInfo rows from cached system data. Args: query(optional), limit(optional).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("query", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
                }
            )
        ) { request ->
            val query = argString(request.arguments?.get("query"))
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TOPINFO_LIMIT).coerceIn(1, MAX_TOPINFO_LIMIT)
            callText(responseBuilder.buildTopInfoText(query = query, limit = limit))
        }

        server.addTool(
            name = "keios.os.cards.snapshot",
            description = "Get OS page card snapshot (visibility, expanded state, cache sizes).",
            inputSchema = ToolSchema(properties = buildJsonObject { })
        ) { _ ->
            callText(responseBuilder.buildOsCardsSnapshotText())
        }

        server.addTool(
            name = "keios.os.activity.cards",
            description = "List OS activity cards. Args: query(optional), onlyVisible(optional), limit(optional).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("query", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("onlyVisible", buildJsonObject { put("type", JsonPrimitive("boolean")) })
                    put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
                }
            )
        ) { request ->
            val query = argString(request.arguments?.get("query")).trim()
            val onlyVisible = argBoolean(request.arguments?.get("onlyVisible"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(1, MAX_TRACK_LIMIT)
            callText(
                responseBuilder.buildOsActivityCardsText(
                    query = query,
                    onlyVisible = onlyVisible,
                    limit = limit
                )
            )
        }

        server.addTool(
            name = "keios.os.shell.cards",
            description = "List OS shell cards. Args: query(optional), onlyVisible(optional), includeOutput(optional), limit(optional).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("query", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("onlyVisible", buildJsonObject { put("type", JsonPrimitive("boolean")) })
                    put("includeOutput", buildJsonObject { put("type", JsonPrimitive("boolean")) })
                    put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
                }
            )
        ) { request ->
            val query = argString(request.arguments?.get("query")).trim()
            val onlyVisible = argBoolean(request.arguments?.get("onlyVisible"), false)
            val includeOutput = argBoolean(request.arguments?.get("includeOutput"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(1, MAX_TRACK_LIMIT)
            callText(
                responseBuilder.buildOsShellCardsText(
                    query = query,
                    onlyVisible = onlyVisible,
                    includeOutput = includeOutput,
                    limit = limit
                )
            )
        }

        server.addTool(
            name = "keios.os.cards.export",
            description = "Export OS activity/shell cards JSON. Args: target(activity|shell|all, default=all).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("target", buildJsonObject { put("type", JsonPrimitive("string")) })
                }
            )
        ) { request ->
            val target = argString(request.arguments?.get("target")).trim()
            callText(responseBuilder.buildOsCardsExportText(target))
        }

        server.addTool(
            name = "keios.os.cards.import",
            description = "Preview or apply OS activity/shell cards JSON import. Args: target(activity|shell), json(required), apply(optional, default=false).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("target", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("json", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("apply", buildJsonObject { put("type", JsonPrimitive("boolean")) })
                }
            )
        ) { request ->
            val target = argString(request.arguments?.get("target")).trim()
            val rawJson = argString(request.arguments?.get("json"))
            val apply = argBoolean(request.arguments?.get("apply"), false)
            callText(
                responseBuilder.buildOsCardsImportText(
                    target = target,
                    rawJson = rawJson,
                    apply = apply
                )
            )
        }
    }
}
