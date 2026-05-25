package os.kei.ui.page.main.home.state

import androidx.compose.ui.graphics.Color
import org.junit.Test
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import kotlin.test.assertEquals

class HomePageContentDeriverTest {
    @Test
    fun derivedContentSurfacesFocusAndCacheFields() {
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
                        cacheFreshness = freshCacheSnapshot(),
                    ),
                baOverview =
                    HomeBaOverview(
                        loaded = true,
                        activated = true,
                        apCurrent = 120,
                        apLimit = 240,
                        cacheFreshness = staleCacheSnapshot(),
                    ),
                text = testTextBundle(),
                colors =
                    HomePageContentColors(
                        runningColor = Color.Green,
                        stoppedColor = Color.Red,
                        inactiveColor = Color.Gray,
                        githubCacheColor = Color.Yellow,
                    ),
            )

        assertEquals("2 devices", state.mcpFocusLine)
        assertEquals("failed 1", state.githubFocusLine)
        assertEquals("120/240", state.baFocusLine)
        assertEquals("fresh", state.githubCacheFreshnessLine)
        assertEquals("stale", state.baCacheFreshnessLine)
        assertEquals("v1.8.0 (80)", state.appVersionText)
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
            baTitle = "BA",
            shizukuTitle = "Shizuku",
            mcpCardTitle = "MCP card",
            githubCardTitle = "GitHub card",
            baCardTitle = "BA card",
            loading = "loading",
            githubUnconfigured = "unconfigured",
            githubNoCache = "no cache",
            githubPendingRefresh = "pending",
            githubSharePending = "share pending",
            justNow = "just now",
            githubNotRefreshed = "never",
            commonFilled = "filled",
            commonNotUsed = "unused",
            mcpRuntimePending = "pending runtime",
            baStatusActive = "active",
            baStatusInactive = "inactive",
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
            devicesCountPattern = "%d devices",
            failedCountPattern = "failed %d",
            fractionPattern = "%d/%d",
            cafeFractionPattern = "Lv.%d %d/%d",
            thresholdPlusPattern = "%d+",
        )
}
