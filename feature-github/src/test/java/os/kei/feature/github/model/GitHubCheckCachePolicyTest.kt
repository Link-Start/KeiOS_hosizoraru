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
        val directSignature = item.directApkCheckSourceSignature(
            lookupConfig.checkAllTrackedPreReleases
        )

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
    fun `direct apk cache signature changes with global subscription pre-release check`() {
        val item = tracked(sourceMode = GitHubTrackedSourceMode.DirectApk)
        val singleChannelConfig = GitHubLookupConfig(
            checkAllDirectApkPreReleases = false
        ).forTrackedItem(item)
        val allPreConfig = GitHubLookupConfig(
            checkAllDirectApkPreReleases = true
        ).forTrackedItem(item)

        assertFalse(
            item.checkSourceSignature(singleChannelConfig) ==
                    item.checkSourceSignature(allPreConfig)
        )
        assertFalse(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_DIRECT_APK_STRATEGY_ID,
                latestStableRawTag = "10.1.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = allPreConfig,
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

    @Test
    fun `git repository cache uses git source signature`() {
        val item = tracked(sourceMode = GitHubTrackedSourceMode.GitRepository)
        val lookupConfig = GitHubLookupConfig().forTrackedItem(item)
        val signature = item.gitRepositoryCheckSourceSignature(lookupConfig)

        assertEquals(signature, item.checkSourceSignature(lookupConfig))
        assertTrue(signature.contains("git_repository-v1|gitee|gitee.com|"))
        assertTrue(
            GitHubCheckCacheEntry(
                sourceStrategyId = "git_repository",
                sourceConfigSignature = signature,
                latestStableRawTag = "v1.0.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = lookupConfig.selectedStrategy.storageId
            )
        )
        assertFalse(
            GitHubCheckCacheEntry(
                sourceStrategyId = "git_repository",
                latestStableRawTag = "v1.0.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = lookupConfig.selectedStrategy.storageId
            )
        )
    }

    @Test
    fun `fdroid repository cache uses fdroid source signature`() {
        val item = tracked(sourceMode = GitHubTrackedSourceMode.FdroidRepository).copy(
            fdroidConfig = FdroidTrackedAppConfig(
                selectionMode = FdroidVersionSelectionMode.HighestCompatibleVersionCode,
                versionNameRegex = "^1\\.",
                apkNameRegex = "arm64",
                repoFingerprint = "AA:BB",
                indexFormat = FdroidIndexFormat.V2,
                trustPolicy = FdroidTrustPolicy.RequireApkHash,
                antiFeaturePolicy = FdroidAntiFeaturePolicy.Custom,
                blockedAntiFeatures = listOf("Tracking", "KnownVuln")
            )
        )
        val lookupConfig = GitHubLookupConfig().forTrackedItem(item)
        val signature = item.fdroidRepositoryCheckSourceSignature()

        assertEquals(signature, item.checkSourceSignature(lookupConfig))
        assertTrue(signature.contains("fdroid_repository-v1|https://f-droid.org/repo|demo.app|"))
        assertTrue(signature.contains("|highest_compatible_version_code|"))
        assertTrue(signature.contains("|aa:bb|v2|require_apk_hash|custom|knownvuln,tracking|"))
        assertTrue(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_FDROID_STRATEGY_ID,
                sourceConfigSignature = signature,
                latestStableRawTag = "1.0.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = GitHubLookupConfig().selectedStrategy.storageId
            )
        )
        assertFalse(
            GitHubCheckCacheEntry(
                sourceStrategyId = GITHUB_FDROID_STRATEGY_ID,
                latestStableRawTag = "1.0.0"
            ).isValidForTrackedItem(
                item = item,
                lookupConfig = lookupConfig,
                activeStrategyId = GitHubLookupConfig().selectedStrategy.storageId
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
                GitHubTrackedSourceMode.GitRepository -> "https://gitee.com/demo/repo"
                GitHubTrackedSourceMode.DirectApk -> "https://example.com/download/app.apk"
                GitHubTrackedSourceMode.FdroidRepository -> "https://f-droid.org/repo"
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
