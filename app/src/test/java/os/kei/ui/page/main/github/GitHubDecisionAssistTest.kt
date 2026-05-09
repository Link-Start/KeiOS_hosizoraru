package os.kei.ui.page.main.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubReleaseNotesMode
import os.kei.feature.github.model.GitHubTrackedApp

class GitHubDecisionAssistTest {
    @Test
    fun releaseNotesModeDefaultsToOff() {
        assertEquals(GitHubReleaseNotesMode.Off, GitHubReleaseNotesMode.fromStorageId(""))
        assertEquals(
            GitHubReleaseNotesMode.Compact,
            GitHubReleaseNotesMode.fromStorageId("compact")
        )
    }

    @Test
    fun repositoryHealthMarksFailedCheckAsRisk() {
        val health = buildGitHubRepositoryHealth(
            item = trackedItem(packageName = ""),
            state = VersionCheckUi(
                hasUpdate = null,
                message = "network failed",
                hasStableRelease = false,
                localVersionCode = -1L
            ),
            nowMillis = 1_700_000_000_000L
        )

        assertEquals(GitHubDecisionLevel.Risk, health.level)
        assertTrue(GitHubRepositoryHealthReason.CheckFailed in health.reasons)
        assertTrue(GitHubRepositoryHealthReason.MissingPackageName in health.reasons)
    }

    @Test
    fun repositoryHealthRewardsStableFreshRelease() {
        val now = 1_700_000_000_000L
        val health = buildGitHubRepositoryHealth(
            item = trackedItem(),
            state = VersionCheckUi(
                localVersion = "1.0",
                localVersionCode = 10L,
                latestStableRawTag = "v1.1",
                latestStableUpdatedAtMillis = now - 24L * 60L * 60L * 1000L,
                hasUpdate = true
            ),
            nowMillis = now
        )

        assertEquals(GitHubDecisionLevel.Review, health.level)
        assertTrue(health.score < 80)
        assertTrue(GitHubRepositoryHealthReason.StableDetected in health.reasons)
        assertTrue(GitHubRepositoryHealthReason.FreshRelease in health.reasons)
    }

    @Test
    fun repositoryHealthMarksArchivedRepositoryAsRisk() {
        val now = 1_700_000_000_000L
        val health = buildGitHubRepositoryHealth(
            item = trackedItem(),
            state = VersionCheckUi(
                localVersion = "1.0",
                localVersionCode = 10L,
                latestStableRawTag = "v1.0",
                latestStableUpdatedAtMillis = now - 24L * 60L * 60L * 1000L,
                repositoryArchived = true
            ),
            nowMillis = now
        )

        assertEquals(GitHubDecisionLevel.Risk, health.level)
        assertTrue(health.score < 55)
        assertTrue(GitHubRepositoryHealthReason.RepositoryArchived in health.reasons)
    }

    @Test
    fun repositoryHealthPenalizesForkBehindActiveUpstream() {
        val now = 1_700_000_000_000L
        val health = buildGitHubRepositoryHealth(
            item = trackedItem(),
            state = VersionCheckUi(
                localVersion = "1.0",
                localVersionCode = 10L,
                latestStableRawTag = "v1.0",
                repositoryFork = true,
                repositoryPushedAtMillis = now - 120L * 24L * 60L * 60L * 1000L,
                upstreamFullName = "upstream/app",
                upstreamPushedAtMillis = now - 5L * 24L * 60L * 60L * 1000L
            ),
            nowMillis = now
        )

        assertEquals(GitHubDecisionLevel.Review, health.level)
        assertTrue(GitHubRepositoryHealthReason.RepositoryFork in health.reasons)
        assertTrue(GitHubRepositoryHealthReason.ForkBehindUpstream in health.reasons)
    }

    @Test
    fun repositoryHealthSoftensForkPenaltyWhenMaintainedIndependently() {
        val now = 1_700_000_000_000L
        val health = buildGitHubRepositoryHealth(
            item = trackedItem(),
            state = VersionCheckUi(
                localVersion = "1.0",
                localVersionCode = 10L,
                latestStableRawTag = "v1.0",
                repositoryFork = true,
                repositoryPushedAtMillis = now - 5L * 24L * 60L * 60L * 1000L,
                upstreamFullName = "upstream/app",
                upstreamArchived = true,
                upstreamPushedAtMillis = now - 240L * 24L * 60L * 60L * 1000L
            ),
            nowMillis = now
        )

        assertEquals(GitHubDecisionLevel.Review, health.level)
        assertTrue(GitHubRepositoryHealthReason.ForkUpstreamArchived in health.reasons)
        assertTrue(GitHubRepositoryHealthReason.ForkMaintainedIndependently in health.reasons)
    }

    @Test
    fun apkTrustMarksUnsignedDebugAsRisk() {
        val signal = buildGitHubApkTrustSignal(
            asset = asset("demo-arm64-v8a-debug-unsigned.apk"),
            supportedAbis = listOf("arm64-v8a")
        )

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.UnsignedBuild in signal.reasons)
        assertTrue(GitHubApkTrustReason.DebugBuild in signal.reasons)
    }

    @Test
    fun apkTrustMarksCompatibleReleaseApkAsGood() {
        val signal = buildGitHubApkTrustSignal(
            asset = asset("demo-arm64-v8a-release.apk"),
            supportedAbis = listOf("arm64-v8a")
        )

        assertEquals(GitHubDecisionLevel.Good, signal.level)
        assertTrue(GitHubApkTrustReason.PreferredAbi in signal.reasons)
        assertTrue(GitHubApkTrustReason.ApkLike in signal.reasons)
    }

    @Test
    fun releaseNotesLinesRespectCompactLimit() {
        val lines = buildGitHubReleaseNotesLines(
            item = trackedItem(),
            state = VersionCheckUi(
                releaseHint = "Stable release found",
                showPreReleaseInfo = true,
                preReleaseInfo = "beta available",
                latestStableRawTag = "v1.0",
                latestPreRawTag = "v1.1-beta"
            ),
            assetBundle = GitHubReleaseAssetBundle(
                releaseName = "Version 1.0",
                tagName = "v1.0",
                htmlUrl = "",
                releaseNotesBody = "",
                assets = listOf(asset("demo.apk"))
            ),
            expanded = false
        )

        assertEquals(2, lines.size)
        assertEquals("Version 1.0", lines.first())
    }

    @Test
    fun releaseNotesLinesPreferRealBody() {
        val lines = buildGitHubReleaseNotesLines(
            item = trackedItem(),
            state = VersionCheckUi(releaseHint = "fallback hint"),
            assetBundle = GitHubReleaseAssetBundle(
                releaseName = "Version 1.0",
                tagName = "v1.0",
                htmlUrl = "",
                releaseNotesBody = """
                    # Release Notes
                    - Added installer flow
                    - Fixed cache refresh
                    - Improved APK selection
                """.trimIndent(),
                assets = listOf(asset("demo.apk"))
            ),
            expanded = false
        )

        assertEquals(listOf("Added installer flow", "Fixed cache refresh"), lines)
    }

    @Test
    fun releaseNotesLinesKeepApiSingleLineItemsReadable() {
        val lines = buildGitHubReleaseNotesLines(
            item = trackedItem(),
            state = VersionCheckUi(releaseHint = "fallback hint"),
            assetBundle = GitHubReleaseAssetBundle(
                releaseName = "Version 1.0",
                tagName = "v1.0",
                htmlUrl = "",
                releaseNotesBody = """
                    Added media notification support
                    Fixed release notes markdown rendering
                    Improved GitHub share import flow
                """.trimIndent(),
                assets = listOf(asset("demo.apk"))
            ),
            expanded = true
        )

        assertEquals(
            listOf(
                "Added media notification support",
                "Fixed release notes markdown rendering",
                "Improved GitHub share import flow"
            ),
            lines
        )
    }

    @Test
    fun releaseNotesLinesSkipGenericHeadingsAndKeepAtomMarkdownItems() {
        val lines = buildGitHubReleaseNotesLines(
            item = trackedItem(),
            state = VersionCheckUi(releaseHint = "fallback hint"),
            assetBundle = GitHubReleaseAssetBundle(
                releaseName = "Version 1.0",
                tagName = "v1.0",
                htmlUrl = "",
                releaseNotesBody = """
                    # Release Notes

                    ## What's Changed

                    - Added installer flow
                    - Fixed cache refresh
                """.trimIndent(),
                assets = listOf(asset("demo.apk"))
            ),
            expanded = false
        )

        assertEquals(listOf("Added installer flow", "Fixed cache refresh"), lines)
    }

    private fun trackedItem(packageName: String = "os.kei.demo"): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://github.com/demo/app",
            owner = "demo",
            repo = "app",
            packageName = packageName,
            appLabel = "Demo"
        )
    }

    private fun asset(name: String): GitHubReleaseAssetFile {
        return GitHubReleaseAssetFile(
            name = name,
            downloadUrl = "https://example.com/$name",
            sizeBytes = 1024L,
            downloadCount = 1
        )
    }
}
