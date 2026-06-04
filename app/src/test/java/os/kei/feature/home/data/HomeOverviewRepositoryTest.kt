package os.kei.feature.home.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.model.GITHUB_DIRECT_APK_STRATEGY_ID
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.checkSourceSignature
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.ba.support.BaCacheSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeOverviewRepositoryTest {
    @Test
    fun `store refresh flow emits manual github ba and webdav reasons`() = runBlocking {
        val reasons = buildHomeOverviewStoreRefreshFlow(
            refreshRequests = flowOf("manual"),
            githubVersions = flowOf(0L, 10L),
            baHomeOverviewVersions = flowOf(0L, 20L),
            webDavVersions = flowOf(0L, 30L),
        ).toList()

        assertEquals(
            setOf("manual", "github_store_10", "ba_store_20", "webdav_store_30"),
            reasons.toSet()
        )
    }

    @Test
    fun githubOverviewCountsPerItemValidCacheEntries() {
        val lookupConfig =
            GitHubLookupConfig(
                selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
                preciseApkVersionEnabled = false,
            )
        val githubItem =
            trackedApp(
                repoUrl = "https://github.com/demo/stable",
                owner = "demo",
                repo = "stable",
                packageName = "demo.stable",
                checkActionsUpdates = true,
            )
        val gitItem =
            trackedApp(
                repoUrl = "https://gitee.com/hugedog233/Pronto",
                owner = "gitee.com/hugedog233",
                repo = "Pronto",
                packageName = "com.mt.pronto",
                sourceMode = GitHubTrackedSourceMode.GitRepository,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Enabled,
            )
        val directApkItem =
            trackedApp(
                repoUrl = "https://example.com/releases/app.apk",
                owner = "example.com",
                repo = "releases-app-apk",
                packageName = "example.direct",
                sourceMode = GitHubTrackedSourceMode.DirectApk,
            )
        val preReleaseItem =
            trackedApp(
                repoUrl = "https://github.com/demo/pre",
                owner = "demo",
                repo = "pre",
                packageName = "demo.pre",
                preferPreRelease = true,
            )
        val snapshot =
            GitHubTrackSnapshot(
                items = listOf(githubItem, gitItem, directApkItem, preReleaseItem),
                checkCache =
                    mapOf(
                        githubItem.id to
                            GitHubCheckCacheEntry(
                                sourceStrategyId = GitHubLookupStrategyOption.AtomFeed.storageId,
                            ),
                        gitItem.id to
                            GitHubCheckCacheEntry(
                                sourceConfigSignature =
                                    gitItem.checkSourceSignature(lookupConfig.forTrackedItem(gitItem)),
                                hasUpdate = true,
                            ),
                        directApkItem.id to
                            GitHubCheckCacheEntry(
                                sourceStrategyId = GITHUB_DIRECT_APK_STRATEGY_ID,
                                sourceConfigSignature =
                                    directApkItem.checkSourceSignature(
                                        lookupConfig.forTrackedItem(directApkItem),
                                    ),
                                message = GitHubTrackedReleaseStatus.Failed.failureMessage("network"),
                            ),
                        preReleaseItem.id to
                            GitHubCheckCacheEntry(
                                sourceConfigSignature =
                                    preReleaseItem.checkSourceSignature(
                                        lookupConfig.forTrackedItem(preReleaseItem),
                                    ),
                                hasPreReleaseUpdate = true,
                            ),
                    ),
                lastRefreshMs = 1_000L,
                refreshIntervalHours = 12,
                lookupConfig = lookupConfig,
            )

        val overview =
            loadHomeGitHubOverview(
                snapshot = snapshot,
                cacheFreshness = CacheFreshnessSnapshot.Empty,
                nowMs = 2_000L,
            )

        assertEquals(4, overview.trackedCount)
        assertEquals(2, overview.githubRepositoryCount)
        assertEquals(1, overview.gitRepositoryCount)
        assertEquals(1, overview.directApkCount)
        assertEquals(1, overview.actionsTrackedCount)
        assertEquals(2, overview.preciseApkVersionCount)
        assertEquals(4, overview.cacheHitCount)
        assertEquals(1, overview.updatableCount)
        assertEquals(1, overview.preReleaseUpdateCount)
        assertEquals(1, overview.failedCount)
        assertEquals(1_000L, overview.cachedRefreshMs)
    }

    @Test
    fun githubCacheFreshnessUsesSnapshotRefreshInterval() {
        val snapshot =
            GitHubTrackSnapshot(
                checkCache = mapOf("tracked" to GitHubCheckCacheEntry(latestTag = "v1.0.0")),
                lastRefreshMs = 1_000L,
                refreshIntervalHours = 12,
            )

        val fresh =
            buildHomeGitHubCacheFreshness(
                snapshot = snapshot,
                nowMs = 1_000L + 11 * HOUR_MS,
            )
        val stale =
            buildHomeGitHubCacheFreshness(
                snapshot = snapshot,
                nowMs = 1_000L + 13 * HOUR_MS,
            )

        assertTrue(fresh.hasData)
        assertTrue(fresh.fresh)
        assertFalse(fresh.stale)
        assertTrue(stale.hasData)
        assertFalse(stale.fresh)
        assertTrue(stale.stale)
    }

    @Test
    fun baCalendarPoolFreshnessUsesCurrentServerSnapshots() {
        val freshness =
            buildHomeBaCalendarPoolCacheFreshness(
                calendar = BaCacheSnapshot(raw = "[activity]", syncMs = 2_000L),
                pool = BaCacheSnapshot(raw = "[pool]", syncMs = 1_000L),
                refreshIntervalHours = 12,
                nowMs = 2_000L + 6 * HOUR_MS,
            )
        val empty =
            buildHomeBaCalendarPoolCacheFreshness(
                calendar = BaCacheSnapshot(),
                pool = BaCacheSnapshot(),
                refreshIntervalHours = 12,
                nowMs = 2_000L,
            )

        assertTrue(freshness.hasData)
        assertTrue(freshness.fresh)
        assertEquals(2_000L, freshness.lastUpdatedAtMs)
        assertTrue(freshness.bytes > 0L)
        assertFalse(empty.hasData)
        assertEquals(0L, empty.bytes)
    }
}

private const val HOUR_MS = 60L * 60L * 1000L

private fun trackedApp(
    repoUrl: String,
    owner: String,
    repo: String,
    packageName: String,
    sourceMode: GitHubTrackedSourceMode = GitHubTrackedSourceMode.GitHubRepository,
    preferPreRelease: Boolean = false,
    checkActionsUpdates: Boolean = false,
    preciseApkVersionMode: GitHubTrackedPreciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.FollowGlobal,
): GitHubTrackedApp =
    GitHubTrackedApp(
        repoUrl = repoUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = repo,
        sourceMode = sourceMode,
        preferPreRelease = preferPreRelease,
        checkActionsUpdates = checkActionsUpdates,
        preciseApkVersionMode = preciseApkVersionMode,
    )
