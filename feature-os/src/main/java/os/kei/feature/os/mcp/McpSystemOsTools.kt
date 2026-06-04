package os.kei.feature.os.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.mcp.server.DEFAULT_TOPINFO_LIMIT
import os.kei.mcp.server.DEFAULT_TRACK_LIMIT
import os.kei.mcp.server.MAX_TOPINFO_LIMIT
import os.kei.mcp.server.MAX_TRACK_LIMIT
import os.kei.mcp.server.McpToolEnvironment
import os.kei.mcp.server.addMcpTextTool
import os.kei.mcp.server.argBoolean
import os.kei.mcp.server.argInt
import os.kei.mcp.server.argString

internal class McpSystemOsTools(
    private val environment: McpToolEnvironment,
    private val delegate: McpSystemOsToolDelegate,
) {
    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.system.topinfo.query") { request ->
            val query = argString(request.arguments?.get("query"))
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TOPINFO_LIMIT).coerceIn(1, MAX_TOPINFO_LIMIT)
            delegate.buildTopInfoText(query = query, limit = limit)
        }

        server.addMcpTextTool(environment, name = "keios.os.cards.snapshot") { _ ->
            delegate.buildOsCardsSnapshotText()
        }

        server.addMcpTextTool(environment, name = "keios.os.activity.cards") { request ->
            val query = argString(request.arguments?.get("query")).trim()
            val onlyVisible = argBoolean(request.arguments?.get("onlyVisible"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(1, MAX_TRACK_LIMIT)
            delegate.buildOsActivityCardsText(
                query = query,
                onlyVisible = onlyVisible,
                limit = limit
            )
        }

        server.addMcpTextTool(environment, name = "keios.os.shell.cards") { request ->
            val query = argString(request.arguments?.get("query")).trim()
            val onlyVisible = argBoolean(request.arguments?.get("onlyVisible"), false)
            val includeOutput = argBoolean(request.arguments?.get("includeOutput"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(1, MAX_TRACK_LIMIT)
            delegate.buildOsShellCardsText(
                query = query,
                onlyVisible = onlyVisible,
                includeOutput = includeOutput,
                limit = limit
            )
        }

        server.addMcpTextTool(environment, name = "keios.os.cards.export") { request ->
            val target = argString(request.arguments?.get("target")).trim()
            delegate.buildOsCardsExportText(target)
        }

        server.addMcpTextTool(environment, name = "keios.os.cards.import") { request ->
            val target = argString(request.arguments?.get("target")).trim()
            val rawJson = argString(request.arguments?.get("json"))
            val apply = argBoolean(request.arguments?.get("apply"), false)
            delegate.buildOsCardsImportText(
                target = target,
                rawJson = rawJson,
                apply = apply
            )
        }
    }
}
