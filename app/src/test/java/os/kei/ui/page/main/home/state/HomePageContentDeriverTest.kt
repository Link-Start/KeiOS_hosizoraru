package os.kei.ui.page.main.home.state

import androidx.compose.ui.graphics.Color
import org.junit.Test
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeWebDavOverview
import kotlin.test.assertEquals

class HomePageContentDeriverTest {
    @Test
    fun mcpRuntimeUsesProvidedRuntimeClock() {
        val state =
            deriveHomePageContentState(
                shizukuStatus = "granted",
                appOverview =
                    HomeAppOverview(
                        versionName = "1.8.0",
                        versionCode = 80L,
                        loaded = true,
                    ),
                mcpOverview =
                    HomeMcpOverview(
                        running = true,
                        runningSinceEpochMs = 60_000L,
                        port = 38888,
                        authTokenConfigured = true,
                        authTokenPreview = "token",
                        connectedClients = 2,
                        allowExternal = true,
                    ),
                githubOverview =
                    HomeGitHubOverview(
                        loaded = true,
                        trackedCount = 2,
                        cacheHitCount = 2,
                        updatableCount = 1,
                        preReleaseUpdateCount = 1,
                        failedCount = 1,
                        cachedRefreshMs = 3_600_000L,
                        cacheLabelNowMs = 7_200_000L,
                        cacheFreshness = freshCacheSnapshot(),
                    ),
                webDavOverview =
                    HomeWebDavOverview(
                        configured = true,
                        autoSyncEnabled = true,
                        enabledItemCount = 4,
                        totalItemCount = 5,
                    ),
                baOverview =
                    HomeBaOverview(
                        loaded = true,
                        activated = true,
                        apCurrent = 120,
                        apLimit = 240,
                        cacheFreshness = staleCacheSnapshot(),
                    ),
                runtimeNowMs = 3_660_000L,
                text = testTextBundle(),
                colors =
                    HomePageContentColors(
                        runningColor = Color.Green,
                        stoppedColor = Color.Red,
                        inactiveColor = Color.Gray,
                        githubCacheColor = Color.Yellow,
                    ),
            )

        assertEquals("1h", state.mcpRuntimeText)
        assertEquals("2 devices", state.mcpFocusLine)
        assertEquals("failed 1", state.githubFocusLine)
        assertEquals("120/240", state.baFocusLine)
        assertEquals("fresh", state.githubCacheFreshnessLine)
        assertEquals("3h 1h", state.githubLastUpdateLine)
        assertEquals("stale", state.baCacheFreshnessLine)
        assertEquals("auto sync", state.webDavStatusLine)
        assertEquals("4/5", state.webDavSyncItemsLine)
        assertEquals("v1.8.0 (80)", state.appVersionText)
    }

    @Test
    fun githubRuntimeRefreshOverridesCachedHomeLines() {
        val state =
            deriveHomePageContentState(
                shizukuStatus = "granted",
                appOverview = HomeAppOverview(loaded = true),
                mcpOverview = HomeMcpOverview(),
                githubOverview =
                    HomeGitHubOverview(
                        loaded = true,
                        trackedCount = 75,
                        cacheHitCount = 75,
                        updatableCount = 3,
                        cachedRefreshMs = 1_000L,
                        cacheLabelNowMs = 10_000L,
                        refreshing = true,
                        refreshTargetCount = 1,
                        refreshTotalTrackedCount = 75,
                        refreshCompletedCount = 0,
                    ),
                webDavOverview = HomeWebDavOverview(),
                baOverview = HomeBaOverview(loaded = true),
                runtimeNowMs = 10_000L,
                text = testTextBundle(),
                colors =
                    HomePageContentColors(
                        runningColor = Color.Green,
                        stoppedColor = Color.Red,
                        inactiveColor = Color.Gray,
                        githubCacheColor = Color.Yellow,
                    ),
            )

        assertEquals("refreshing 0/1 tracked 75", state.githubFocusLine)
        assertEquals("refreshing 0/1 tracked 75", state.githubLastUpdateLine)
        assertEquals(Color.Green, state.cacheStateColor)
    }

    private fun freshCacheSnapshot(): CacheFreshnessSnapshot =
        CacheFreshnessSnapshot(
            hasData = true,
            fresh = true,
            stale = false,
            lastUpdatedAtMs = 1_000L,
            bytes = 1L,
            rebuildable = true,
        )

    private fun staleCacheSnapshot(): CacheFreshnessSnapshot =
        CacheFreshnessSnapshot(
            hasData = true,
            fresh = false,
            stale = true,
            lastUpdatedAtMs = 1_000L,
            bytes = 1L,
            rebuildable = true,
        )

    private fun testTextBundle(): HomePageContentTextBundle =
        HomePageContentTextBundle(
            commonNa = "N/A",
            appName = "KeiOS",
            tagline = "tagline",
            mcpTitle = "MCP",
            githubTitle = "GitHub",
            webDavTitle = "WebDAV",
            shizukuTitle = "Shizuku",
            mcpCardTitle = "MCP card",
            githubCardTitle = "GitHub card",
            webDavCardTitle = "WebDAV card",
            baCardTitle = "BA card",
            loading = "loading",
            githubUnconfigured = "unconfigured",
            githubNoCache = "no cache",
            githubPendingRefresh = "pending",
            githubSharePending = "share pending",
            githubRefreshing = "refreshing",
            justNow = "just now",
            githubNotRefreshed = "never",
            commonFilled = "filled",
            commonNotUsed = "unused",
            mcpRuntimePending = "pending runtime",
            baStatusActive = "active",
            baStatusInactive = "inactive",
            webDavUnconfigured = "unconfigured",
            webDavAutoSync = "auto sync",
            webDavManualSync = "manual sync",
            webDavNeverSynced = "never",
            valueOn = "on",
            valueOff = "off",
            valueAuthorized = "authorized",
            valueUnauthorized = "unauthorized",
            cacheStateFresh = "fresh",
            cacheStateStale = "stale",
            cacheStateEmpty = "empty",
            appVersionUnknownFallback = "unknown version",
            appVersionUnknown = "unknown",
            networkLanShort = "LAN",
            networkLocalOnlyShort = "local",
            mcpStatusRunning = "running",
            mcpStatusStopped = "stopped",
            statStatus = "status",
            statRuntime = "runtime",
            statClients = "clients",
            statNetwork = "network",
            statPort = "port",
            statToken = "token",
            statStableUpdates = "stable",
            statPreReleaseUpdates = "pre",
            statFailed = "failed",
            statTracked = "tracked",
            statCached = "cached",
            statCacheState = "cache state",
            statShare = "share",
            statLastUpdate = "last",
            statSyncItems = "datasets",
            statLastFullSync = "full sync",
            statAp = "ap",
            statCafeAp = "cafe",
            statApRemaining = "remaining",
            statBaServer = "server",
            statBaNotify = "notify",
            baServerCn = "CN",
            baServerGlobal = "Global",
            baServerJp = "JP",
            uptimeDaysHoursPattern = "%dd %dh",
            uptimeDaysHoursMinutesPattern = "%dd %dh %dm",
            uptimeHoursPattern = "%dh",
            uptimeHoursMinutesPattern = "%dh %dm",
            uptimeMinutesPattern = "%dm",
            githubCountPattern = "%d",
            shortHoursPattern = "%dh",
            refreshPairPattern = "%s %s",
            githubRefreshingProgressPattern = "refreshing %d/%d tracked %d",
            devicesCountPattern = "%d devices",
            failedCountPattern = "failed %d",
            fractionPattern = "%d/%d",
            cafeFractionPattern = "Lv.%d %d/%d",
            thresholdPlusPattern = "%d+",
        )
}
