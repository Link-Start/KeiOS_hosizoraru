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

        // One listing tool over both card domains. `keios.os.activity.cards` and
        // `keios.os.shell.cards` differed only by which builder they called and by shell's
        // `includeOutput`, so a client had to know the domain split before it could ask a question
        // about "the cards". `target` reuses the exact vocabulary `keios.os.cards.export` and
        // `keios.os.cards.import` already use, rather than inventing a third word for the same idea.
        server.addMcpTextTool(environment, name = "keios.os.cards.list") { request ->
            val target = argString(request.arguments?.get("target")).trim().lowercase().ifBlank { "all" }
            val query = argString(request.arguments?.get("query")).trim()
            val onlyVisible = argBoolean(request.arguments?.get("onlyVisible"), false)
            val includeOutput = argBoolean(request.arguments?.get("includeOutput"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(1, MAX_TRACK_LIMIT)
            val wantsActivity = target == "activity" || target == "all"
            val wantsShell = target == "shell" || target == "all"
            if (!wantsActivity && !wantsShell) {
                return@addMcpTextTool "error=unknown_target target=$target expected=activity|shell|all"
            }
            buildString {
                if (wantsActivity) {
                    appendLine("[activity]")
                    appendLine(
                        delegate.buildOsActivityCardsText(
                            query = query,
                            onlyVisible = onlyVisible,
                            limit = limit
                        )
                    )
                }
                if (wantsShell) {
                    if (wantsActivity) appendLine()
                    appendLine("[shell]")
                    appendLine(
                        delegate.buildOsShellCardsText(
                            query = query,
                            onlyVisible = onlyVisible,
                            includeOutput = includeOutput,
                            limit = limit
                        )
                    )
                }
            }.trim()
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
