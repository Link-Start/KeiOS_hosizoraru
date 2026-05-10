package os.kei.ui.page.main

import androidx.compose.ui.graphics.Color
import org.junit.Test
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.formatReleaseValue
import os.kei.ui.page.main.github.githubPreReleaseLinkUrl
import os.kei.ui.page.main.github.githubStableReleaseLinkUrl
import os.kei.ui.page.main.github.githubTrackedDisplaySubtitle
import os.kei.ui.page.main.github.githubTrackedDisplayTitle
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseExpansionState
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseUiStateStore
import os.kei.ui.page.main.github.statusActionUrl
import os.kei.ui.page.main.github.statusColor
import os.kei.ui.page.main.widget.status.AppStatusColors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubPageModelsTest {
    @Test
    fun `format release value keeps tag when release name is placeholder`() {
        assertEquals("Release · 0.9.100", formatReleaseValue("Release", "0.9.100"))
        assertEquals("随风而行 · v0.5.1", formatReleaseValue("随风而行", "v0.5.1"))
    }

    @Test
    fun `format release value avoids duplicate tag when name and tag match`() {
        assertEquals("v0.0.8", formatReleaseValue("v0.0.8", "v0.0.8"))
        assertEquals("3.8.0", formatReleaseValue("3.8.0", "3.8.0"))
    }

    @Test
    fun `format release value prefers compact tag when name carries project label`() {
        assertEquals("1.1.0", formatReleaseValue("KeiOS v1.1.0", "v1.1.0"))
    }

    @Test
    fun `status action url only exists for real remote update targets`() {
        val preTrackedNoRemoteUpdate = VersionCheckUi(
            hasUpdate = false,
            isPreRelease = true,
            latestPreRawTag = "10.9.0-alpha03-2025070901",
            latestPreUrl = "https://github.com/demo/app/releases/tag/10.9.0-alpha03-2025070901"
        )
        val stableUpdate = VersionCheckUi(
            hasUpdate = true,
            latestStableRawTag = "v1.2.3"
        )
        val preUpdate = VersionCheckUi(
            hasPreReleaseUpdate = true,
            latestPreRawTag = "v1.3.0-beta1"
        )

        assertEquals("", preTrackedNoRemoteUpdate.statusActionUrl("demo", "app"))
        assertEquals(
            "https://github.com/demo/app/releases/tag/v1.2.3",
            stableUpdate.statusActionUrl("demo", "app")
        )
        assertEquals(
            "https://github.com/demo/app/releases/tag/v1.3.0-beta1",
            preUpdate.statusActionUrl("demo", "app")
        )
    }

    @Test
    fun `local prerelease without remote update keeps prerelease status color`() {
        val state = VersionCheckUi(
            hasUpdate = false,
            isPreRelease = true
        )

        assertEquals(AppStatusColors.Cached, state.statusColor(Color.Gray))
    }

    @Test
    fun `uninstalled generated track uses remote repository display identity`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/demo/remote-app",
            owner = "demo",
            repo = "remote-app",
            packageName = "com.demo.remote",
            appLabel = "com.demo.remote"
        )
        val state = VersionCheckUi(
            repositoryProfile = GitHubRepositoryProfileSnapshot(
                owner = "demo",
                repo = "remote-app",
                sourceConfigSignature = "profile-v1|fixture",
                fetchedAtMillis = 1_700_000_000_000L,
                identity = GitHubRepositoryIdentityProfile(
                    name = profileField("Remote App"),
                    ownerAvatarUrl = profileField("https://avatars.githubusercontent.com/u/42?v=4")
                )
            )
        )

        val title = item.githubTrackedDisplayTitle(state)

        assertEquals("Remote App", title)
        assertEquals("com.demo.remote", item.githubTrackedDisplaySubtitle(state, title))
    }

    @Test
    fun `release link urls prefer explicit release urls and fall back to tags`() {
        val state = VersionCheckUi(
            latestStableRawTag = "v1.2.3",
            latestPreRawTag = "v1.3.0-beta1",
            latestPreApkVersion = GitHubRemoteApkVersionInfo(
                releaseUrl = "https://github.com/demo/app/releases/tag/v1.3.0-beta1"
            )
        )

        assertEquals(
            "https://github.com/demo/app/releases/tag/v1.2.3",
            state.githubStableReleaseLinkUrl("demo", "app")
        )
        assertEquals(
            "https://github.com/demo/app/releases/tag/v1.3.0-beta1",
            state.githubPreReleaseLinkUrl("demo", "app")
        )
    }

    @Test
    fun `empty repository parts keep subtitle readable`() {
        val item = GitHubTrackedApp(
            repoUrl = "",
            owner = "",
            repo = "",
            packageName = "",
            appLabel = "Demo"
        )

        assertEquals("", item.githubTrackedDisplaySubtitle(state = null, title = "Demo"))
    }

    @Test
    fun `tracked release expansion codec persists expanded items only`() {
        val raw = GitHubTrackedReleaseUiStateStore.encodeExpansionState(
            GitHubTrackedReleaseExpansionState(
                stableVersionExpanded = mapOf("owner/app" to true, "owner/closed" to false),
                preReleaseVersionExpanded = mapOf("owner/beta" to true)
            )
        )

        val decoded = GitHubTrackedReleaseUiStateStore.decodeExpansionState(raw)

        assertTrue(decoded.stableVersionExpanded["owner/app"] == true)
        assertFalse(decoded.stableVersionExpanded.containsKey("owner/closed"))
        assertTrue(decoded.preReleaseVersionExpanded["owner/beta"] == true)
    }

    private fun profileField(value: String): GitHubProfileField<String> {
        return GitHubProfileField(
            value = value,
            source = GitHubRepositoryProfileSource.GitHubApiRepository,
            fetchedAtMillis = 1_700_000_000_000L,
            confidence = GitHubRepositoryProfileConfidence.High
        )
    }
}
