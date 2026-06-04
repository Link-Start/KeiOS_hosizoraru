package os.kei.ui.page.main.home.state

import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeWebDavOverview
import os.kei.ui.page.main.home.model.formatGitHubCacheAgo
import os.kei.ui.page.main.widget.status.AppStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun deriveHomePageContentState(
    shizukuStatus: String,
    appOverview: HomeAppOverview,
    mcpOverview: HomeMcpOverview,
    githubOverview: HomeGitHubOverview,
    webDavOverview: HomeWebDavOverview,
    baOverview: HomeBaOverview,
    runtimeNowMs: Long,
    text: HomePageContentTextBundle,
    colors: HomePageContentColors,
): HomePageContentState {
    val trackedCount = githubOverview.trackedCount
    val cacheHitCount = githubOverview.cacheHitCount
    val updatableCount = githubOverview.updatableCount
    val preReleaseUpdateCount = githubOverview.preReleaseUpdateCount
    val failedCount = githubOverview.failedCount
    val cacheStateColor =
        when {
            githubOverview.refreshing -> colors.runningColor
            !githubOverview.loaded -> colors.inactiveColor
            githubOverview.cacheFreshness.stale -> AppStatusColors.Failed
            githubOverview.cacheFreshness.fresh -> AppStatusColors.Fresh
            cacheHitCount > 0 -> colors.githubCacheColor
            else -> colors.inactiveColor
        }
    val networkModeText =
        if (mcpOverview.allowExternal) {
            text.networkLanShort
        } else {
            text.networkLocalOnlyShort
        }
    val mcpRuntimeText =
        if (!mcpOverview.running || mcpOverview.runningSinceEpochMs <= 0L) {
            text.mcpRuntimePending
        } else {
            text.uptime(runtimeNowMs - mcpOverview.runningSinceEpochMs)
        }
    val mcpStatusText =
        if (mcpOverview.running) {
            text.mcpStatusRunning
        } else {
            text.mcpStatusStopped
        }
    val mcpFocusLine =
        if (mcpOverview.running && mcpOverview.connectedClients > 0) {
            text.devicesCount(mcpOverview.connectedClients)
        } else {
            mcpStatusText
        }
    val mcpTokenStatusText =
        if (mcpOverview.authTokenConfigured) {
            mcpOverview.authTokenPreview.ifBlank { text.commonFilled }
        } else {
            text.commonNotUsed
        }
    val githubRefreshIntervalLine = text.shortHours(githubOverview.refreshIntervalHours.coerceAtLeast(1))
    val githubSourcesLine =
        text.githubSources(
            github = githubOverview.githubRepositoryCount,
            git = githubOverview.gitRepositoryCount,
            directApk = githubOverview.directApkCount,
        )
    val githubActionsLine = text.githubCount(githubOverview.actionsTrackedCount)
    val githubPreciseVersionLine = text.githubCount(githubOverview.preciseApkVersionCount)
    val cacheRefreshLine =
        formatGitHubCacheAgo(
            lastRefreshMs = githubOverview.cachedRefreshMs,
            notRefreshedText = text.githubNotRefreshed,
            justNowText = text.justNow,
            nowMs = maxOf(runtimeNowMs, githubOverview.cacheLabelNowMs),
        )
    val githubRefreshProgressLine =
        text.githubRefreshingProgress(
            completed = githubOverview.refreshCompletedCount,
            target = githubOverview.refreshTargetCount.coerceAtLeast(1),
            totalTracked = githubOverview.refreshTotalTrackedCount.coerceAtLeast(trackedCount),
        )
    val githubLastUpdateLine =
        when {
            !githubOverview.loaded -> text.loading
            githubOverview.refreshing -> githubRefreshProgressLine
            trackedCount == 0 -> text.githubUnconfigured
            cacheHitCount == 0 -> text.refreshPair(githubRefreshIntervalLine, text.githubNoCache)
            else -> text.refreshPair(githubRefreshIntervalLine, cacheRefreshLine)
        }
    val githubUpdatableLine =
        when {
            !githubOverview.loaded -> text.loading
            trackedCount == 0 -> text.githubCount(0)
            cacheHitCount == 0 -> text.githubPendingRefresh
            else -> text.githubCount(updatableCount)
        }
    val githubPreReleaseUpdateLine =
        when {
            !githubOverview.loaded || trackedCount == 0 || cacheHitCount == 0 -> text.githubCount(0)
            else -> text.githubCount(preReleaseUpdateCount)
        }
    val githubFailedLine =
        when {
            !githubOverview.loaded || trackedCount == 0 || cacheHitCount == 0 -> text.githubCount(0)
            else -> text.githubCount(failedCount)
        }
    val githubFocusLine =
        when {
            !githubOverview.loaded -> text.loading
            githubOverview.refreshing -> githubRefreshProgressLine
            githubOverview.pendingShareImport -> text.githubSharePending
            trackedCount == 0 -> text.githubUnconfigured
            cacheHitCount == 0 -> text.githubPendingRefresh
            failedCount > 0 -> text.failedCount(failedCount)
            else -> text.githubCount(updatableCount + preReleaseUpdateCount)
        }
    val githubShareLine =
        when {
            !githubOverview.loaded -> text.loading
            githubOverview.pendingShareImport -> text.githubSharePending
            githubOverview.shareImportLinkageEnabled -> text.valueOn
            else -> text.valueOff
        }
    val webDavStatusLine =
        when {
            !webDavOverview.configured -> text.webDavUnconfigured
            webDavOverview.autoSyncEnabled -> text.webDavAutoSync
            else -> text.webDavManualSync
        }
    val webDavSyncItemsLine =
        text.fraction(webDavOverview.enabledItemCount, webDavOverview.totalItemCount)
    val webDavLastFullSyncLine =
        webDavOverview.lastFullSyncTimeMs
            .takeIf { it > 0L }
            ?.let(::formatHomeWebDavSyncTime)
            ?: text.webDavNeverSynced
    val baApLine =
        if (baOverview.loaded) {
            text.fraction(baOverview.apCurrent, baOverview.apLimit)
        } else {
            text.loading
        }
    val baCafeApLine =
        if (baOverview.loaded) {
            text.cafeFraction(baOverview.cafeLevel, baOverview.cafeStored, baOverview.cafeCap)
        } else {
            text.loading
        }
    val baActivationLine =
        if (baOverview.loaded) {
            if (baOverview.activated) text.baStatusActive else text.baStatusInactive
        } else {
            text.loading
        }
    val baAccountsLine =
        if (baOverview.loaded) {
            text.fraction(baOverview.enabledAccountCount, baOverview.accountCount)
        } else {
            text.loading
        }
    val baActiveAccountLine =
        if (baOverview.loaded) {
            baOverview.activeAccountName.ifBlank { baActivationLine }
        } else {
            text.loading
        }
    val baFocusLine =
        if (baOverview.loaded && baOverview.activated) {
            text.fraction(baOverview.apCurrent, baOverview.apLimit)
        } else {
            baActivationLine
        }
    val baApRemainingLine =
        if (baOverview.loaded) {
            (baOverview.apLimit - baOverview.apCurrent).coerceAtLeast(0).toString()
        } else {
            text.loading
        }
    val baServerLine =
        if (baOverview.loaded) {
            when (baOverview.serverIndex.coerceIn(0, 2)) {
                0 -> text.baServerCn
                1 -> text.baServerGlobal
                else -> text.baServerJp
            }
        } else {
            text.loading
        }
    val baNotifyLine =
        if (baOverview.loaded) {
            if (baOverview.apNotifyEnabled) {
                text.thresholdPlus(baOverview.apNotifyThreshold)
            } else {
                text.valueOff
            }
        } else {
            text.loading
        }
    val shizukuGranted = ShizukuApiUtils.isCommandReadyStatusText(shizukuStatus)
    return HomePageContentState(
        homeNa = text.commonNa,
        homeAppName = text.appName,
        homeTagline = text.tagline,
        homeStatusMcp = text.mcpTitle,
        homeStatusGitHub = text.githubTitle,
        homeStatusWebDav = text.webDavTitle,
        homeStatusShizuku = text.shizukuTitle,
        homeCardMcp = text.mcpCardTitle,
        homeCardGitHub = text.githubCardTitle,
        homeCardWebDav = text.webDavCardTitle,
        homeCardBa = text.baCardTitle,
        shizukuGranted = shizukuGranted,
        runningColor = colors.runningColor,
        stoppedColor = colors.stoppedColor,
        inactiveColor = colors.inactiveColor,
        cacheStateColor = cacheStateColor,
        appVersionText = homeAppVersionText(appOverview, text),
        shizukuStatusLine = if (shizukuGranted) text.valueAuthorized else text.valueUnauthorized,
        mcpFocusLine = mcpFocusLine,
        githubFocusLine = githubFocusLine,
        baFocusLine = baFocusLine,
        homeStatStatus = text.statStatus,
        mcpStatusText = mcpStatusText,
        homeStatRuntime = text.statRuntime,
        mcpRuntimeText = mcpRuntimeText,
        homeStatClients = text.statClients,
        mcpConnectedClients = mcpOverview.connectedClients,
        homeStatNetwork = text.statNetwork,
        networkModeText = networkModeText,
        homeStatPort = text.statPort,
        mcpPort = mcpOverview.port,
        homeStatToken = text.statToken,
        mcpTokenStatusText = mcpTokenStatusText,
        homeStatStableUpdates = text.statStableUpdates,
        githubUpdatableLine = githubUpdatableLine,
        homeStatPreReleaseUpdates = text.statPreReleaseUpdates,
        githubPreReleaseUpdateLine = githubPreReleaseUpdateLine,
        homeStatFailed = text.statFailed,
        githubFailedLine = githubFailedLine,
        homeStatTracked = text.statTracked,
        trackedCountLine = text.githubCount(trackedCount),
        homeStatGitHubSources = text.statGitHubSources,
        githubSourcesLine = githubSourcesLine,
        homeStatActions = text.statActions,
        githubActionsLine = githubActionsLine,
        homeStatPreciseVersion = text.statPreciseVersion,
        githubPreciseVersionLine = githubPreciseVersionLine,
        homeStatCached = text.statCached,
        cacheHitCountLine = text.githubCount(cacheHitCount),
        homeStatCacheState = text.statCacheState,
        githubCacheFreshnessLine = homeCacheFreshnessLine(githubOverview.cacheFreshness, text),
        homeStatShare = text.statShare,
        githubShareLine = githubShareLine,
        homeStatLastUpdate = text.statLastUpdate,
        githubLastUpdateLine = githubLastUpdateLine,
        webDavStatusLine = webDavStatusLine,
        webDavSyncItemsLine = webDavSyncItemsLine,
        webDavLastFullSyncLine = webDavLastFullSyncLine,
        homeStatSyncItems = text.statSyncItems,
        homeStatLastFullSync = text.statLastFullSync,
        baActivationLine = baActivationLine,
        homeStatAp = text.statAp,
        baApLine = baApLine,
        homeStatCafeAp = text.statCafeAp,
        baCafeApLine = baCafeApLine,
        homeStatApRemaining = text.statApRemaining,
        baApRemainingLine = baApRemainingLine,
        homeStatBaAccounts = text.statBaAccounts,
        baAccountsLine = baAccountsLine,
        homeStatBaActiveAccount = text.statBaActiveAccount,
        baActiveAccountLine = baActiveAccountLine,
        homeStatBaServer = text.statBaServer,
        baServerLine = baServerLine,
        homeStatBaNotify = text.statBaNotify,
        baNotifyLine = baNotifyLine,
        baCacheFreshnessLine = homeCacheFreshnessLine(baOverview.cacheFreshness, text),
    )
}

private fun formatHomeWebDavSyncTime(timeMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMs))

private fun homeCacheFreshnessLine(
    freshness: CacheFreshnessSnapshot,
    text: HomePageContentTextBundle,
): String =
    when {
        freshness.fresh -> text.cacheStateFresh
        freshness.stale -> text.cacheStateStale
        else -> text.cacheStateEmpty
    }

private fun homeAppVersionText(
    appOverview: HomeAppOverview,
    text: HomePageContentTextBundle,
): String {
    if (!appOverview.loaded) return text.appVersionUnknown
    val versionName = appOverview.versionName.ifBlank { text.appVersionUnknownFallback }
    return "v$versionName (${appOverview.versionCode})"
}
