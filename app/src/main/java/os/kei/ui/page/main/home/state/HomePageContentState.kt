package os.kei.ui.page.main.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.ui.page.main.home.model.formatGitHubCacheAgo
import os.kei.ui.page.main.mcp.util.formatMcpUptimeText
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
    val baCacheFreshnessLine: String
)

@Composable
internal fun rememberHomePageContentState(
    shizukuStatus: String,
    mcpOverview: HomeMcpOverview,
    githubOverview: HomeGitHubOverview,
    baOverview: HomeBaOverview
): HomePageContentState {
    val context = LocalContext.current
    val runningColor = AppStatusColors.Fresh
    val stoppedColor = AppStatusColors.Failed
    val inactiveColor = MiuixTheme.colorScheme.onBackgroundVariant
    val githubCacheColor = AppStatusColors.Cached
    val trackedCount = githubOverview.trackedCount
    val cacheHitCount = githubOverview.cacheHitCount
    val updatableCount = githubOverview.updatableCount
    val preReleaseUpdateCount = githubOverview.preReleaseUpdateCount
    val failedCount = githubOverview.failedCount
    val cacheStateColor = when {
        !githubOverview.loaded -> inactiveColor
        githubOverview.cacheFreshness.stale -> AppStatusColors.Failed
        githubOverview.cacheFreshness.fresh -> AppStatusColors.Fresh
        cacheHitCount > 0 -> githubCacheColor
        else -> inactiveColor
    }
    val homeStatusLoading = stringResource(R.string.home_status_loading)
    val homeGitHubUnconfigured = stringResource(R.string.home_github_status_unconfigured)
    val homeGitHubNoCache = stringResource(R.string.home_github_status_no_cache)
    val homeGitHubPendingRefresh = stringResource(R.string.home_github_status_pending_refresh)
    val homeJustNow = stringResource(R.string.home_time_just_now)
    val homeCommonFilled = stringResource(R.string.common_filled)
    val homeCommonNotUsed = stringResource(R.string.common_not_used)
    val homeMcpRuntimePending = stringResource(R.string.mcp_runtime_pending)
    val homeBaStatusActive = stringResource(R.string.home_ba_status_active)
    val homeBaStatusInactive = stringResource(R.string.home_ba_status_inactive)
    val homeValueOn = stringResource(R.string.home_value_on)
    val homeValueOff = stringResource(R.string.home_value_off)
    val homeValueAuthorized = stringResource(R.string.home_value_authorized)
    val homeValueUnauthorized = stringResource(R.string.home_value_unauthorized)
    val homeCacheStateFresh = stringResource(R.string.home_cache_state_fresh)
    val homeCacheStateStale = stringResource(R.string.home_cache_state_stale)
    val homeCacheStateEmpty = stringResource(R.string.home_cache_state_empty)
    val homeAppVersionUnknownFallback = stringResource(R.string.home_app_version_unknown_fallback)
    val homeAppVersionUnknown = stringResource(R.string.home_app_version_unknown)
    val appVersionText = remember(homeAppVersionUnknownFallback, homeAppVersionUnknown) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${info.versionName ?: homeAppVersionUnknownFallback} (${info.longVersionCode})"
        }.getOrDefault(homeAppVersionUnknown)
    }
    val networkModeText = if (mcpOverview.allowExternal) {
        stringResource(R.string.mcp_network_mode_lan_short)
    } else {
        stringResource(R.string.mcp_network_mode_local_only_short)
    }
    val mcpRuntimeText = if (!mcpOverview.running || mcpOverview.runningSinceEpochMs <= 0L) {
        homeMcpRuntimePending
    } else {
        formatMcpUptimeText(System.currentTimeMillis() - mcpOverview.runningSinceEpochMs)
    }
    val mcpStatusText = if (mcpOverview.running) {
        stringResource(R.string.home_mcp_status_running)
    } else {
        stringResource(R.string.home_mcp_status_stopped)
    }
    val mcpFocusLine = if (mcpOverview.running && mcpOverview.connectedClients > 0) {
        stringResource(R.string.home_value_devices_count, mcpOverview.connectedClients)
    } else {
        mcpStatusText
    }
    val mcpTokenStatusText = if (mcpOverview.authTokenConfigured) {
        mcpOverview.authTokenPreview.ifBlank { homeCommonFilled }
    } else {
        homeCommonNotUsed
    }
    val githubRefreshIntervalLine = stringResource(
        R.string.home_value_short_hours,
        githubOverview.refreshIntervalHours.coerceAtLeast(1)
    )
    val cacheRefreshLine = formatGitHubCacheAgo(
        lastRefreshMs = githubOverview.cachedRefreshMs,
        notRefreshedText = stringResource(R.string.github_refresh_ago_not_refreshed),
        justNowText = homeJustNow
    )
    val githubLastUpdateLine = when {
        !githubOverview.loaded -> homeStatusLoading
        trackedCount == 0 -> homeGitHubUnconfigured
        cacheHitCount == 0 -> stringResource(R.string.home_value_refresh_pair, githubRefreshIntervalLine, homeGitHubNoCache)
        else -> stringResource(R.string.home_value_refresh_pair, githubRefreshIntervalLine, cacheRefreshLine)
    }
    val githubUpdatableLine = when {
        !githubOverview.loaded -> homeStatusLoading
        trackedCount == 0 -> stringResource(R.string.github_overview_value_count, 0)
        cacheHitCount == 0 -> homeGitHubPendingRefresh
        else -> stringResource(R.string.github_overview_value_count, updatableCount)
    }
    val githubPreReleaseUpdateLine = when {
        !githubOverview.loaded || trackedCount == 0 || cacheHitCount == 0 ->
            stringResource(R.string.github_overview_value_count, 0)
        else -> stringResource(R.string.github_overview_value_count, preReleaseUpdateCount)
    }
    val githubFailedLine = when {
        !githubOverview.loaded || trackedCount == 0 || cacheHitCount == 0 ->
            stringResource(R.string.github_overview_value_count, 0)
        else -> stringResource(R.string.github_overview_value_count, failedCount)
    }
    val githubFocusLine = when {
        !githubOverview.loaded -> homeStatusLoading
        githubOverview.pendingShareImport -> stringResource(R.string.home_github_share_pending)
        trackedCount == 0 -> homeGitHubUnconfigured
        cacheHitCount == 0 -> homeGitHubPendingRefresh
        failedCount > 0 -> stringResource(R.string.home_value_failed_count, failedCount)
        else -> stringResource(
            R.string.github_overview_value_count,
            updatableCount + preReleaseUpdateCount
        )
    }
    val githubShareLine = when {
        !githubOverview.loaded -> homeStatusLoading
        githubOverview.pendingShareImport -> stringResource(R.string.home_github_share_pending)
        githubOverview.shareImportLinkageEnabled -> homeValueOn
        else -> homeValueOff
    }
    val trackedCountLine = stringResource(R.string.github_overview_value_count, trackedCount)
    val cacheHitCountLine = stringResource(R.string.github_overview_value_count, cacheHitCount)
    val githubCacheFreshnessLine = rememberHomeCacheFreshnessLine(
        freshness = githubOverview.cacheFreshness,
        freshText = homeCacheStateFresh,
        staleText = homeCacheStateStale,
        emptyText = homeCacheStateEmpty
    )
    val baApLine = if (baOverview.loaded) {
        stringResource(R.string.home_value_fraction, baOverview.apCurrent, baOverview.apLimit)
    } else {
        homeStatusLoading
    }
    val baCafeApLine = if (baOverview.loaded) {
        stringResource(
            R.string.home_value_cafe_fraction,
            baOverview.cafeLevel,
            baOverview.cafeStored,
            baOverview.cafeCap
        )
    } else {
        homeStatusLoading
    }
    val baActivationLine = if (baOverview.loaded) {
        if (baOverview.activated) homeBaStatusActive else homeBaStatusInactive
    } else {
        homeStatusLoading
    }
    val baFocusLine = if (baOverview.loaded && baOverview.activated) {
        stringResource(R.string.home_value_fraction, baOverview.apCurrent, baOverview.apLimit)
    } else {
        baActivationLine
    }
    val baApRemainingLine = if (baOverview.loaded) {
        (baOverview.apLimit - baOverview.apCurrent).coerceAtLeast(0).toString()
    } else {
        homeStatusLoading
    }
    val baServerLine = if (baOverview.loaded) {
        when (baOverview.serverIndex.coerceIn(0, 2)) {
            0 -> stringResource(R.string.home_ba_server_cn)
            1 -> stringResource(R.string.home_ba_server_global)
            else -> stringResource(R.string.home_ba_server_jp)
        }
    } else {
        homeStatusLoading
    }
    val baNotifyLine = if (baOverview.loaded) {
        if (baOverview.apNotifyEnabled) {
            stringResource(R.string.home_value_threshold_plus, baOverview.apNotifyThreshold)
        } else {
            homeValueOff
        }
    } else {
        homeStatusLoading
    }
    val baCacheFreshnessLine = rememberHomeCacheFreshnessLine(
        freshness = baOverview.cacheFreshness,
        freshText = homeCacheStateFresh,
        staleText = homeCacheStateStale,
        emptyText = homeCacheStateEmpty
    )
    return HomePageContentState(
        homeNa = stringResource(R.string.common_na),
        homeAppName = stringResource(R.string.app_name),
        homeTagline = stringResource(R.string.home_header_tagline),
        homeStatusMcp = stringResource(R.string.page_mcp_title),
        homeStatusGitHub = stringResource(R.string.github_page_title),
        homeStatusBa = stringResource(R.string.home_status_ba),
        homeStatusShizuku = stringResource(R.string.home_status_shizuku),
        homeCardMcp = stringResource(R.string.home_card_title_mcp),
        homeCardGitHub = stringResource(R.string.home_card_title_github_cache),
        homeCardBa = stringResource(R.string.home_card_title_ba),
        shizukuGranted = shizukuStatus.contains("granted", ignoreCase = true),
        runningColor = runningColor,
        stoppedColor = stoppedColor,
        inactiveColor = inactiveColor,
        cacheStateColor = cacheStateColor,
        appVersionText = appVersionText,
        shizukuStatusLine = if (shizukuStatus.contains("granted", ignoreCase = true)) {
            homeValueAuthorized
        } else {
            homeValueUnauthorized
        },
        mcpFocusLine = mcpFocusLine,
        githubFocusLine = githubFocusLine,
        baFocusLine = baFocusLine,
        homeStatStatus = stringResource(R.string.home_stat_status),
        mcpStatusText = mcpStatusText,
        homeStatRuntime = stringResource(R.string.home_stat_runtime),
        mcpRuntimeText = mcpRuntimeText,
        homeStatClients = stringResource(R.string.home_stat_clients),
        mcpConnectedClients = mcpOverview.connectedClients,
        homeStatNetwork = stringResource(R.string.home_stat_network),
        networkModeText = networkModeText,
        homeStatPort = stringResource(R.string.home_stat_port),
        mcpPort = mcpOverview.port,
        homeStatToken = stringResource(R.string.home_stat_token),
        mcpTokenStatusText = mcpTokenStatusText,
        homeStatStableUpdates = stringResource(R.string.home_stat_stable_updates),
        githubUpdatableLine = githubUpdatableLine,
        homeStatPreReleaseUpdates = stringResource(R.string.home_stat_prerelease_updates),
        githubPreReleaseUpdateLine = githubPreReleaseUpdateLine,
        homeStatFailed = stringResource(R.string.home_stat_failed),
        githubFailedLine = githubFailedLine,
        homeStatTracked = stringResource(R.string.home_stat_tracked),
        trackedCountLine = trackedCountLine,
        homeStatCached = stringResource(R.string.home_stat_cached),
        cacheHitCountLine = cacheHitCountLine,
        homeStatCacheState = stringResource(R.string.home_stat_cache_state),
        githubCacheFreshnessLine = githubCacheFreshnessLine,
        homeStatShare = stringResource(R.string.home_stat_share),
        githubShareLine = githubShareLine,
        homeStatLastUpdate = stringResource(R.string.home_stat_last_update),
        githubLastUpdateLine = githubLastUpdateLine,
        baActivationLine = baActivationLine,
        homeStatAp = stringResource(R.string.home_stat_ap),
        baApLine = baApLine,
        homeStatCafeAp = stringResource(R.string.home_stat_cafe_ap),
        baCafeApLine = baCafeApLine,
        homeStatApRemaining = stringResource(R.string.home_stat_ap_remaining),
        baApRemainingLine = baApRemainingLine,
        homeStatBaServer = stringResource(R.string.home_stat_ba_server),
        baServerLine = baServerLine,
        homeStatBaNotify = stringResource(R.string.home_stat_ba_notify),
        baNotifyLine = baNotifyLine,
        baCacheFreshnessLine = baCacheFreshnessLine
    )
}

@Composable
private fun rememberHomeCacheFreshnessLine(
    freshness: CacheFreshnessSnapshot,
    freshText: String,
    staleText: String,
    emptyText: String
): String {
    return remember(freshness, freshText, staleText, emptyText) {
        when {
            freshness.fresh -> freshText
            freshness.stale -> staleText
            else -> emptyText
        }
    }
}
