package os.kei.feature.home.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.feature.github.domain.GitHubTrackService
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.home.data.HomeOverviewPrefs
import os.kei.mcp.server.McpToolEnvironment
import os.kei.mcp.server.addMcpTextTool

internal class McpHomeTools(
    private val environment: McpToolEnvironment,
    private val baSnapshotProvider: McpHomeBaSnapshotProvider,
) {
    private val githubTrackService = GitHubTrackService()

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.home.overview.snapshot") { _ ->
            buildHomeOverviewSnapshotText()
        }
    }

    private fun buildHomeOverviewSnapshotText(): String {
        val mcpState = environment.currentState()
        val githubSnapshot = githubTrackService.loadTrackSnapshotBlocking()
        val activeStrategyId = githubSnapshot.lookupConfig.selectedStrategy.storageId
        val matchedCacheByTrackId = githubSnapshot.items.associate { item ->
            val cache = githubSnapshot.checkCache[item.id]
                ?.takeIf { entry ->
                    entry.sourceStrategyId.ifBlank { GitHubLookupStrategyOption.AtomFeed.storageId } == activeStrategyId
                }
            item.id to cache
        }
        val cacheHitCount = matchedCacheByTrackId.count { it.value != null }
        val baSnapshot = baSnapshotProvider.loadSnapshot()

        return buildString {
            appendLine(
                "visibleCards=${
                    HomeOverviewPrefs.loadVisibleOverviewCards().joinToString(",") { it.name }
                }"
            )
            appendLine("mcp.running=${mcpState?.running ?: false}")
            appendLine("mcp.serverName=${mcpState?.serverName.orEmpty()}")
            appendLine("mcp.port=${mcpState?.port ?: 0}")
            appendLine("mcp.path=${mcpState?.endpointPath.orEmpty()}")
            appendLine("mcp.allowExternal=${mcpState?.allowExternal ?: false}")
            appendLine("mcp.connectedClients=${mcpState?.connectedClients ?: 0}")
            appendLine("mcp.authTokenConfigured=${mcpState?.authToken?.isNotBlank() == true}")
            appendLine("github.trackedCount=${githubSnapshot.items.size}")
            appendLine("github.cacheHitCount=$cacheHitCount")
            appendLine("github.updatableCount=${matchedCacheByTrackId.count { it.value?.hasUpdate == true }}")
            appendLine("github.preReleaseUpdateCount=${matchedCacheByTrackId.count { it.value?.hasPreReleaseUpdate == true }}")
            appendLine("github.strategy=${githubSnapshot.lookupConfig.selectedStrategy.storageId}")
            appendLine("github.apiTokenConfigured=${githubSnapshot.lookupConfig.apiToken.isNotBlank()}")
            appendLine("github.lastRefreshMs=${if (cacheHitCount > 0) githubSnapshot.lastRefreshMs else 0L}")
            appendLine("github.shareImportLinkageEnabled=${githubSnapshot.lookupConfig.shareImportLinkageEnabled}")
            appendLine("github.shareImportFlowMode=${githubSnapshot.lookupConfig.shareImportFlowMode.storageId}")
            appendLine("ba.activated=${baSnapshot.activated}")
            appendLine("ba.apCurrent=${baSnapshot.apCurrent}")
            appendLine("ba.apLimit=${baSnapshot.apLimit}")
            appendLine("ba.cafeLevel=${baSnapshot.cafeLevel}")
            appendLine("ba.cafeStored=${baSnapshot.cafeStored}")
            appendLine("ba.cafeCap=${baSnapshot.cafeCap}")
        }.trim()
    }
}
