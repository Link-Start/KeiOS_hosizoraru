package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.home.data.HomeOverviewPrefs
import os.kei.feature.home.model.HOME_BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.displayAp
import java.util.Locale

internal class McpHomeTools(
    private val environment: McpToolEnvironment
) {
    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.home.overview.snapshot") { _ ->
            buildHomeOverviewSnapshotText()
        }
    }

    private fun buildHomeOverviewSnapshotText(): String {
        val mcpState = environment.currentState()
        val githubSnapshot = GitHubTrackStore.loadSnapshot()
        val activeStrategyId = githubSnapshot.lookupConfig.selectedStrategy.storageId
        val matchedCacheByTrackId = githubSnapshot.items.associate { item ->
            val cache = githubSnapshot.checkCache[item.id]
                ?.takeIf { entry ->
                    entry.sourceStrategyId.ifBlank { GitHubLookupStrategyOption.AtomFeed.storageId } == activeStrategyId
                }
            item.id to cache
        }
        val cacheHitCount = matchedCacheByTrackId.count { it.value != null }

        val baSnapshot = BASettingsStore.loadSnapshot()
        val friendCode = baSnapshot.idFriendCode
            .uppercase(Locale.ROOT)
            .filter { it in 'A'..'Z' }
            .take(8)
            .let { if (it.length == 8) it else HOME_BA_DEFAULT_FRIEND_CODE }
        val cafeLevel = baSnapshot.cafeLevel.coerceIn(1, 10)
        val cafeCap = cafeStorageCap(cafeLevel)
        val cafeStored = baSnapshot.cafeStoredAp.coerceIn(0.0, cafeCap)

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
            appendLine("ba.activated=${friendCode != HOME_BA_DEFAULT_FRIEND_CODE}")
            appendLine("ba.apCurrent=${displayAp(baSnapshot.apCurrent)}")
            appendLine("ba.apLimit=${baSnapshot.apLimit}")
            appendLine("ba.cafeLevel=$cafeLevel")
            appendLine("ba.cafeStored=${cafeStored.toInt()}")
            appendLine("ba.cafeCap=${cafeCap.toInt()}")
        }.trim()
    }

}
