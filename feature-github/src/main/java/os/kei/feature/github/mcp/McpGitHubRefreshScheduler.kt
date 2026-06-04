package os.kei.feature.github.mcp

import android.content.Context

fun interface McpGitHubRefreshScheduler {
    fun scheduleGitHubRefresh(context: Context)
}
