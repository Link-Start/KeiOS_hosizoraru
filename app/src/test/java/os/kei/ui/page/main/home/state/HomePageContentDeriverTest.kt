package os.kei.ui.page.main.home.state

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.core.privilege.PrivilegeMode
import os.kei.core.privilege.PrivilegeStatus
import os.kei.core.privilege.PrivilegeStatusCode
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeWebDavOverview

class HomePageContentDeriverTest {
    @Test
    fun disabledPrivilegeModeHidesHomeStatusPill() {
        val content = privilegeContentState(PrivilegeMode.Disabled, PrivilegeStatusCode.Disabled)

        assertNull(content.homeStatusPrivilege)
        assertNull(
            homePrivilegeStatusPill(
                label = content.homeStatusPrivilege,
                privilegeGranted = content.privilegeGranted,
                runningColor = content.runningColor,
                stoppedColor = content.stoppedColor,
            ),
        )
    }

    @Test
    fun rootPrivilegeModeShowsRootReadiness() {
        val content = privilegeContentState(PrivilegeMode.Root, PrivilegeStatusCode.Ready)
        val pill =
            requireNotNull(
                homePrivilegeStatusPill(
                    label = content.homeStatusPrivilege,
                    privilegeGranted = content.privilegeGranted,
                    runningColor = content.runningColor,
                    stoppedColor = content.stoppedColor,
                ),
            )

        assertEquals("Root", pill.label)
        assertEquals(Color.Green, pill.color)
    }

    @Test
    fun shizukuPrivilegeModeKeepsShizukuFailureState() {
        val content =
            privilegeContentState(
                PrivilegeMode.Shizuku,
                PrivilegeStatusCode.PermissionNotGranted,
            )
        val pill =
            requireNotNull(
                homePrivilegeStatusPill(
                    label = content.homeStatusPrivilege,
                    privilegeGranted = content.privilegeGranted,
                    runningColor = content.runningColor,
                    stoppedColor = content.stoppedColor,
                ),
            )

        assertEquals("Shizuku", pill.label)
        assertEquals(Color.Red, pill.color)
    }

    @Test
    fun enabledBaAccountUsesEnabledColorEvenWithoutLegacyActivation() {
        val enabledColor = Color.Green
        val inactiveColor = Color.Gray
        val disabledColor = Color.Red

        val color =
            homeBaActiveAccountColor(
                overview =
                    HomeBaOverview(
                        loaded = true,
                        activated = false,
                        activeAccountName = "Kei",
                        activeAccountEnabled = true,
                    ),
                enabledColor = enabledColor,
                inactiveColor = inactiveColor,
                disabledColor = disabledColor,
            )

        assertEquals(enabledColor, color)
    }

    @Test
    fun mcpRuntimeUsesProvidedRuntimeClock() {
        val state =
            derive(
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
                        githubRepositoryCount = 1,
                        gitRepositoryCount = 1,
                        directApkCount = 0,
                        actionsTrackedCount = 1,
                        preciseApkVersionCount = 2,
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
                        accountCount = 3,
                        enabledAccountCount = 2,
                        activeAccountName = "Hoshino",
                        apCurrent = 120,
                        apLimit = 240,
                        cacheFreshness = staleCacheSnapshot(),
                    ),
                runtimeNowMs = 3_660_000L,
            )

        assertEquals("1h", state.mcpRuntimeText)
        assertEquals("failed 1", state.githubFocusLine)
        assertEquals("2/3", state.baAccountsLine)
        assertEquals("Hoshino", state.baActiveAccountLine)
        assertEquals("fresh", state.githubCacheFreshnessLine)
        assertEquals("3h 1h", state.githubLastUpdateLine)
        assertEquals("stale", state.baCacheFreshnessLine)
        assertEquals("auto sync", state.webDavStatusLine)
        assertEquals("4/5", state.webDavSyncItemsLine)
        assertEquals("never", state.webDavLastAutoSyncLine)
        assertEquals("v1.8.0 (80)", state.appVersionText)
    }

    @Test
    fun webDavAutoSyncIssuesOverrideNormalAutoSyncStatus() {
        val state =
            derive(
                webDavOverview =
                    HomeWebDavOverview(
                        configured = true,
                        autoSyncEnabled = true,
                        enabledItemCount = 4,
                        totalItemCount = 6,
                        lastAutoSyncTimeMs = 60_000L,
                        autoSyncNeedsReview = true,
                    ),
                runtimeNowMs = 120_000L,
            )

        assertEquals("review", state.webDavStatusLine)
    }

    @Test
    fun githubRuntimeRefreshOverridesCachedHomeLines() {
        val state =
            derive(
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
                runtimeNowMs = 10_000L,
            )

        assertEquals("refreshing 0/1 tracked 75", state.githubFocusLine)
        assertEquals("refreshing 0/1 tracked 75", state.githubLastUpdateLine)
        assertEquals(Color.Green, state.cacheStateColor)
    }

    @Test
    fun githubCacheAgeUsesRuntimeClock() {
        val state =
            derive(
                githubOverview =
                    HomeGitHubOverview(
                        loaded = true,
                        trackedCount = 1,
                        cacheHitCount = 1,
                        cachedRefreshMs = 1_000L,
                        cacheLabelNowMs = 1_000L,
                        refreshIntervalHours = 3,
                    ),
                runtimeNowMs = 121_000L,
            )

        assertEquals("3h 2m", state.githubLastUpdateLine)
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

    private fun privilegeContentState(
        mode: PrivilegeMode,
        code: PrivilegeStatusCode,
    ): HomePageContentState =
        derive(
            privilegeStatus = PrivilegeStatus(mode = mode, code = code),
            appOverview = HomeAppOverview(),
            githubOverview = HomeGitHubOverview(),
            baOverview = HomeBaOverview(),
            runtimeNowMs = 0L,
        )

    private fun derive(
        privilegeStatus: PrivilegeStatus =
            PrivilegeStatus(mode = PrivilegeMode.Shizuku, code = PrivilegeStatusCode.Ready, detail = "shell"),
        appOverview: HomeAppOverview = HomeAppOverview(loaded = true),
        mcpOverview: HomeMcpOverview = HomeMcpOverview(),
        githubOverview: HomeGitHubOverview = HomeGitHubOverview(loaded = true),
        webDavOverview: HomeWebDavOverview = HomeWebDavOverview(),
        baOverview: HomeBaOverview = HomeBaOverview(loaded = true),
        runtimeNowMs: Long,
    ): HomePageContentState =
        deriveHomePageContentState(
            privilegeStatus = privilegeStatus,
            appOverview = appOverview,
            mcpOverview = mcpOverview,
            githubOverview = githubOverview,
            webDavOverview = webDavOverview,
            baOverview = baOverview,
            runtimeNowMs = runtimeNowMs,
            text = testTextBundle(),
            colors =
                HomePageContentColors(
                    runningColor = Color.Green,
                    stoppedColor = Color.Red,
                    inactiveColor = Color.Gray,
                    githubCacheColor = Color.Yellow,
                ),
        )

    private fun testTextBundle(): HomePageContentTextBundle =
        HomePageContentTextBundle(
            commonNa = "N/A",
            appName = "KeiOS",
            tagline = "tagline",
            mcpTitle = "MCP",
            githubTitle = "GitHub",
            webDavTitle = "WebDAV",
            shizukuPrivilegeTitle = "Shizuku",
            rootPrivilegeTitle = "Root",
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
            webDavAutoSyncNeedsReview = "review",
            webDavAutoSyncFailed = "auto failed",
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
            githubStrategyAtom = "Atom",
            githubStrategyApi = "API",
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
            statGitHubSources = "sources",
            statActions = "actions",
            statPreciseVersion = "precise",
            statCached = "cached",
            statCacheState = "cache state",
            statShare = "share",
            statLastUpdate = "last",
            statSyncItems = "datasets",
            statLastFullSync = "full sync",
            statLastAutoSync = "auto",
            statAp = "ap",
            statCafeAp = "cafe",
            statApRemaining = "remaining",
            statBaAccounts = "accounts",
            statBaActiveAccount = "current",
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
            githubSourcesPattern = "GH%d Git%d APK%d",
            fractionPattern = "%d/%d",
            cafeFractionPattern = "Lv.%d %d/%d",
            thresholdPlusPattern = "%d+",
        )
}
