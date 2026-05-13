package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubCheckCachePolicyTest {
    @Test
    fun `direct apk cache validates against direct apk source signature`() {
        val item = tracked(sourceMode = GitHubTrackedSourceMode.DirectApk)
        val lookupConfig = GitHubLookupConfig().forTrackedItem(item)
        val directSignature = item.directApkCheckSourceSignature()

        assertEquals(directSignature, item.checkSourceSignature(lookupConfig))
        assertTrue(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_DIRECT_APK_STRATEGY_ID,
                sourceConfigSignature = directSignature,
                latestStableRawTag = "10.1.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = GitHubLookupConfig().selectedStrategy.storageId
            )
        )
        assertFalse(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_DIRECT_APK_STRATEGY_ID,
                sourceConfigSignature = GitHubLookupConfig()
                    .copy(preciseApkVersionEnabled = true)
                    .githubCheckSourceSignature(),
                latestStableRawTag = "10.1.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = GitHubLookupConfig().selectedStrategy.storageId
            )
        )
    }

    @Test
    fun `direct apk cache without signature keeps legacy direct apk result`() {
        val item = tracked(sourceMode = GitHubTrackedSourceMode.DirectApk)
        val lookupConfig = GitHubLookupConfig().forTrackedItem(item)

        assertTrue(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_DIRECT_APK_STRATEGY_ID,
                latestStableRawTag = "10.1.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = GitHubLookupConfig().selectedStrategy.storageId
            )
        )
    }

    @Test
    fun `direct apk cache signature changes with pre-release preference`() {
        val stableItem = tracked(
            sourceMode = GitHubTrackedSourceMode.DirectApk,
            preferPreRelease = false
        )
        val preItem = tracked(
            sourceMode = GitHubTrackedSourceMode.DirectApk,
            preferPreRelease = true
        )

        assertTrue(stableItem.directApkCheckSourceSignature().endsWith("|stable"))
        assertTrue(preItem.directApkCheckSourceSignature().endsWith("|pre"))
        assertFalse(stableItem.directApkCheckSourceSignature() == preItem.directApkCheckSourceSignature())
        assertFalse(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_DIRECT_APK_STRATEGY_ID,
                latestStableRawTag = "10.1.0"
            ).isValidForTrackedItem(
                item = preItem,
                lookupConfig = GitHubLookupConfig().forTrackedItem(preItem),
                activeStrategyId = GitHubLookupConfig().selectedStrategy.storageId
            )
        )
    }

    @Test
    fun `github repository cache keeps global check source signature`() {
        val item = tracked(sourceMode = GitHubTrackedSourceMode.GitHubRepository)
        val lookupConfig = GitHubLookupConfig()
        val signature = lookupConfig.githubCheckSourceSignature()

        assertEquals(signature, item.checkSourceSignature(lookupConfig))
        assertTrue(
            GitHubCheckCacheEntry(
                sourceStrategyId = GitHubLookupStrategyOption.AtomFeed.storageId,
                sourceConfigSignature = signature,
                latestStableRawTag = "v1.0.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = lookupConfig.selectedStrategy.storageId
            )
        )
    }

    private fun tracked(
        sourceMode: GitHubTrackedSourceMode,
        preferPreRelease: Boolean = false
    ): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = when (sourceMode) {
                GitHubTrackedSourceMode.GitHubRepository -> "https://github.com/demo/repo"
                GitHubTrackedSourceMode.DirectApk -> "https://example.com/download/app.apk"
            },
            owner = "demo",
            repo = "repo",
            packageName = "demo.app",
            appLabel = "Demo",
            sourceMode = sourceMode,
            preferPreRelease = preferPreRelease
        )
    }
}
