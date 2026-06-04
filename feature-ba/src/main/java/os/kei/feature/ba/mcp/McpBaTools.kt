package os.kei.feature.ba.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.mcp.server.DEFAULT_ENTRY_LIMIT
import os.kei.mcp.server.MAX_ENTRY_LIMIT
import os.kei.mcp.server.McpToolEnvironment
import os.kei.mcp.server.addMcpTextTool
import os.kei.mcp.server.argBoolean
import os.kei.mcp.server.argInt
import os.kei.mcp.server.argIntOrNull
import os.kei.mcp.server.argString

internal class McpBaTools(
    private val environment: McpToolEnvironment,
    private val delegate: McpBaToolDelegate,
) {
    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.ba.snapshot") { _ ->
            delegate.buildBaSnapshotText()
        }

        server.addMcpTextTool(environment, name = "keios.ba.calendar.cache") { request ->
            val serverIndexArg = argIntOrNull(request.arguments?.get("serverIndex"))
            val includeEntries = argBoolean(request.arguments?.get("includeEntries"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, MAX_ENTRY_LIMIT)
            delegate.buildBaCalendarCacheText(
                requestedServerIndex = serverIndexArg,
                includeEntries = includeEntries,
                limit = limit
            )
        }

        server.addMcpTextTool(environment, name = "keios.ba.pool.cache") { request ->
            val serverIndexArg = argIntOrNull(request.arguments?.get("serverIndex"))
            val includeEntries = argBoolean(request.arguments?.get("includeEntries"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, MAX_ENTRY_LIMIT)
            delegate.buildBaPoolCacheText(
                requestedServerIndex = serverIndexArg,
                includeEntries = includeEntries,
                limit = limit
            )
        }

        server.addMcpTextTool(environment, name = "keios.ba.guide.catalog.cache") { request ->
            val tab = argString(request.arguments?.get("tab")).trim()
            val includeEntries = argBoolean(request.arguments?.get("includeEntries"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, MAX_ENTRY_LIMIT)
            delegate.buildGuideCatalogCacheText(tab = tab, includeEntries = includeEntries, limit = limit)
        }

        server.addMcpTextTool(environment, name = "keios.ba.guide.cache.overview") { _ ->
            delegate.buildGuideCacheOverviewText()
        }

        server.addMcpTextTool(environment, name = "keios.ba.guide.cache.inspect") { request ->
            val url = argString(request.arguments?.get("url")).trim()
            val includeSections = argBoolean(request.arguments?.get("includeSections"), false)
            val refreshHours = argInt(
                request.arguments?.get("refreshIntervalHours"),
                delegate.defaultGuideRefreshIntervalHours()
            ).coerceAtLeast(1)
            delegate.buildGuideCacheInspectText(
                url = url,
                includeSections = includeSections,
                refreshIntervalHours = refreshHours
            )
        }

        server.addMcpTextTool(environment, name = "keios.ba.guide.media.list") { request ->
            val url = argString(request.arguments?.get("url")).trim()
            val kind = argString(request.arguments?.get("kind")).trim()
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, MAX_ENTRY_LIMIT)
            delegate.buildGuideMediaListText(url = url, kind = kind, limit = limit)
        }

        server.addMcpTextTool(environment, name = "keios.ba.guide.bgm.favorites") { request ->
            val action = argString(request.arguments?.get("action")).trim()
            val query = argString(request.arguments?.get("query")).trim()
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, MAX_ENTRY_LIMIT)
            val rawJson = argString(request.arguments?.get("json"))
            val apply = argBoolean(request.arguments?.get("apply"), false)
            delegate.buildGuideBgmFavoritesText(
                action = action,
                query = query,
                limit = limit,
                rawJson = rawJson,
                apply = apply
            )
        }

        server.addMcpTextTool(environment, name = "keios.ba.cache.clear") { request ->
            val scope = argString(request.arguments?.get("scope"))
            val url = argString(request.arguments?.get("url"))
            delegate.buildCacheClearText(scope = scope, url = url)
        }
    }
}
