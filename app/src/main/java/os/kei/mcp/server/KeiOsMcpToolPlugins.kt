package os.kei.mcp.server

import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.ba.mcp.McpBaToolDelegateFactory
import os.kei.feature.ba.mcp.McpBaToolPlugins
import os.kei.feature.github.mcp.McpGitHubRefreshScheduler
import os.kei.feature.github.mcp.McpGitHubToolPlugins
import os.kei.feature.home.mcp.McpHomeToolPlugins
import os.kei.feature.os.mcp.McpSystemOsToolDelegateFactory
import os.kei.feature.os.mcp.McpSystemOsToolPlugins
import os.kei.mcp.bridge.AppMcpBaToolDelegate
import os.kei.mcp.bridge.AppMcpHomeBaSnapshotProvider
import os.kei.mcp.bridge.AppMcpSystemOsToolDelegate

object KeiOsMcpToolPlugins {
    fun create(): List<McpServerToolPlugin> =
        McpHomeToolPlugins.create(
            baSnapshotProvider = AppMcpHomeBaSnapshotProvider,
        ) +
            McpSystemOsToolPlugins.create(
                delegateFactory = McpSystemOsToolDelegateFactory { environment ->
                    AppMcpSystemOsToolDelegate(environment)
                },
            ) +
            McpGitHubToolPlugins.create(
                refreshScheduler = McpGitHubRefreshScheduler { context ->
                    AppBackgroundScheduler.scheduleGitHubRefresh(context)
                }
            ) +
            McpBaToolPlugins.create(
                delegateFactory = McpBaToolDelegateFactory { environment ->
                    AppMcpBaToolDelegate(environment)
                },
            )
}
