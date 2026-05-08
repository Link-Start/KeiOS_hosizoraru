package os.kei.ui.page.main.github.section

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.ui.page.main.github.VersionCheckUi

class GitHubAssetSummaryDisplayTest {
    @Test
    fun preciseVersionModeHidesReleaseNameTagAndVersionChip() {
        val display = buildGitHubAssetSummaryReleaseDisplay(
            state = state(),
            assetBundle = bundle(),
            targetRawTag = "Version.26.5.Preview_C421",
            preciseApkVersionEnabled = true
        )

        assertFalse(display.showReleaseMeta)
        assertNull(display.apkVersionLabel)
        assertEquals("Version.26.5.Preview_C421", display.releaseName)
        assertEquals("Version.26.5.Preview_C421", display.releaseTag)
    }

    @Test
    fun standardModeKeepsReleaseNameTagAndVersionChip() {
        val display = buildGitHubAssetSummaryReleaseDisplay(
            state = state(),
            assetBundle = bundle(),
            targetRawTag = "Version.26.5.Preview_C421",
            preciseApkVersionEnabled = false
        )

        assertTrue(display.showReleaseMeta)
        assertEquals("Version.26.5.Preview_C421 1 (421)", display.apkVersionLabel)
    }

    private fun state(): VersionCheckUi {
        return VersionCheckUi(
            latestPreName = "Version.26.5.Preview_C421",
            latestPreRawTag = "Version.26.5.Preview_C421",
            latestPreApkVersion = GitHubRemoteApkVersionInfo(
                releaseName = "Version.26.5.Preview_C421",
                releaseTag = "Version.26.5.Preview_C421",
                assetName = "demo.apk",
                packageName = "os.kei.demo",
                versionName = "Version.26.5.Preview_C421 1",
                versionCode = "421"
            )
        )
    }

    private fun bundle(): GitHubReleaseAssetBundle {
        return GitHubReleaseAssetBundle(
            releaseName = "Version.26.5.Preview_C421",
            tagName = "Version.26.5.Preview_C421",
            htmlUrl = "https://github.com/demo/app/releases/tag/Version.26.5.Preview_C421",
            assets = listOf(
                GitHubReleaseAssetFile(
                    name = "demo.apk",
                    downloadUrl = "https://example.com/demo.apk",
                    sizeBytes = 1024L,
                    downloadCount = 1
                )
            )
        )
    }
}
