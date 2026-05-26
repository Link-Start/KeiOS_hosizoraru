package os.kei.ui.page.main.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class HomePageContentState(
    val homeNa: String,
    val homeAppName: String,
    val homeTagline: String,
    val homeStatusMcp: String,
    val homeStatusGitHub: String,
    val homeStatusBa: String,
    val homeStatusShizuku: String,
    val homeCardMcp: String,
    val homeCardGitHub: String,
    val homeCardBa: String,
    val shizukuGranted: Boolean,
    val runningColor: Color,
    val stoppedColor: Color,
    val inactiveColor: Color,
    val cacheStateColor: Color,
    val appVersionText: String,
    val shizukuStatusLine: String,
    val mcpFocusLine: String,
    val githubFocusLine: String,
    val baFocusLine: String,
    val homeStatStatus: String,
    val mcpStatusText: String,
    val homeStatRuntime: String,
    val mcpRuntimeText: String,
    val homeStatClients: String,
    val mcpConnectedClients: Int,
    val homeStatNetwork: String,
    val networkModeText: String,
    val homeStatPort: String,
    val mcpPort: Int,
    val homeStatToken: String,
    val mcpTokenStatusText: String,
    val homeStatStableUpdates: String,
    val githubUpdatableLine: String,
    val homeStatPreReleaseUpdates: String,
    val githubPreReleaseUpdateLine: String,
    val homeStatFailed: String,
    val githubFailedLine: String,
    val homeStatTracked: String,
    val trackedCountLine: String,
    val homeStatCached: String,
    val cacheHitCountLine: String,
    val homeStatCacheState: String,
    val githubCacheFreshnessLine: String,
    val homeStatShare: String,
    val githubShareLine: String,
    val homeStatLastUpdate: String,
    val githubLastUpdateLine: String,
    val baActivationLine: String,
    val homeStatAp: String,
    val baApLine: String,
    val homeStatCafeAp: String,
    val baCafeApLine: String,
    val homeStatApRemaining: String,
    val baApRemainingLine: String,
    val homeStatBaServer: String,
    val baServerLine: String,
    val homeStatBaNotify: String,
    val baNotifyLine: String,
    val baCacheFreshnessLine: String,
)

@Immutable
internal data class HomePageContentColors(
    val runningColor: Color,
    val stoppedColor: Color,
    val inactiveColor: Color,
    val githubCacheColor: Color,
)

@Composable
internal fun rememberHomePageContentState(
    shizukuStatus: String,
    appOverview: HomeAppOverview,
    mcpOverview: HomeMcpOverview,
    githubOverview: HomeGitHubOverview,
    baOverview: HomeBaOverview,
    runtimeNowMs: Long,
): HomePageContentState {
    val text = rememberHomePageContentTextBundle()
    val colors =
        HomePageContentColors(
            runningColor = AppStatusColors.Fresh,
            stoppedColor = AppStatusColors.Failed,
            inactiveColor = MiuixTheme.colorScheme.onBackgroundVariant,
            githubCacheColor = AppStatusColors.Cached,
        )
    return remember(shizukuStatus, appOverview, mcpOverview, githubOverview, baOverview, runtimeNowMs, text, colors) {
        deriveHomePageContentState(
            shizukuStatus = shizukuStatus,
            appOverview = appOverview,
            mcpOverview = mcpOverview,
            githubOverview = githubOverview,
            baOverview = baOverview,
            runtimeNowMs = runtimeNowMs,
            text = text,
            colors = colors,
        )
    }
}
