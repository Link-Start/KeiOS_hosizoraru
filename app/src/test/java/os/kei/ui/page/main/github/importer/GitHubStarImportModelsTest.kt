package os.kei.ui.page.main.github.importer

import org.junit.Test
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubStarImportModelsTest {
    @Test
    fun `username input accepts raw username and github profile links`() {
        assertEquals("hosizoraru", "hosizoraru".toGitHubUsernameInput())
        assertEquals("hosizoraru", "@hosizoraru".toGitHubUsernameInput())
        assertEquals("hosizoraru", "https://github.com/hosizoraru".toGitHubUsernameInput())
        assertEquals(
            "hosizoraru",
            "https://github.com/hosizoraru?tab=stars".toGitHubUsernameInput()
        )
        assertEquals("hosizoraru", "github.com/hosizoraru?tab=stars".toGitHubUsernameInput())
    }

    @Test
    fun `username input accepts github stars paths`() {
        assertEquals("hosizoraru", "https://github.com/stars/hosizoraru".toGitHubUsernameInput())
        assertEquals("hosizoraru", "/stars/hosizoraru".toGitHubUsernameInput())
        assertEquals(
            "hosizoraru",
            "/stars/hosizoraru/lists/android-apk".toGitHubUsernameInput()
        )
        assertEquals(
            "hosizoraru",
            "stars/hosizoraru/lists/android-apk".toGitHubUsernameInput()
        )
    }

    @Test
    fun `username input rejects repository links and reserved github routes`() {
        assertEquals("", "https://github.com/hosizoraru/KeiOS".toGitHubUsernameInput())
        assertEquals("", "https://github.com/settings".toGitHubUsernameInput())
        assertEquals("", "https://example.com/hosizoraru".toGitHubUsernameInput())
        assertFalse("https://github.com/hosizoraru/KeiOS".isValidGitHubUsernameInput())
        assertTrue("https://github.com/hosizoraru?tab=stars".isValidGitHubUsernameInput())
    }

    @Test
    fun `star import quality separates android candidates from other platforms`() {
        assertEquals(
            StarImportQualityFilter.LikelyAndroid,
            importCandidate(
                repo = "KeiOS",
                description = "Android utility with Jetpack Compose release APK",
                language = "Kotlin"
            ).starImportQualityFilter()
        )
        assertEquals(
            StarImportQualityFilter.OtherPlatform,
            importCandidate(
                repo = "desktop-tool",
                description = "Windows macOS Linux CLI release",
                language = "Rust"
            ).starImportQualityFilter()
        )
        assertEquals(
            StarImportQualityFilter.ArchivedOrFork,
            importCandidate(
                repo = "old-android-app",
                description = "Android APK",
                language = "Java",
                archived = true
            ).starImportQualityFilter()
        )
    }

    @Test
    fun `default star import selection keeps only likely android candidates`() {
        assertTrue(
            importCandidate(
                repo = "android-app",
                description = "Android APK",
                language = "Kotlin"
            ).isDefaultSelectedStarImportCandidate()
        )
        assertFalse(
            importCandidate(
                repo = "desktop-tool",
                description = "Windows desktop utility",
                language = "C#"
            ).isDefaultSelectedStarImportCandidate()
        )
        assertFalse(
            importCandidate(
                repo = "tracked-android-app",
                description = "Android APK",
                language = "Kotlin",
                alreadyTracked = true
            ).isDefaultSelectedStarImportCandidate()
        )
    }

    private fun importCandidate(
        repo: String,
        description: String,
        language: String,
        archived: Boolean = false,
        fork: Boolean = false,
        alreadyTracked: Boolean = false
    ): GitHubRepositoryImportCandidate {
        val candidate = GitHubRepositoryCandidate(
            owner = "demo",
            repo = repo,
            repoUrl = "https://github.com/demo/$repo",
            description = description,
            language = language,
            archived = archived,
            fork = fork,
            sourceType = GitHubRepositoryDiscoverySourceType.AuthenticatedStars,
            matchReason = GitHubRepositoryCandidateMatchReason.Starred
        )
        return GitHubRepositoryImportCandidate(
            repository = candidate,
            trackedApp = GitHubTrackedApp(
                repoUrl = candidate.repoUrl,
                owner = candidate.owner,
                repo = candidate.repo,
                packageName = "",
                appLabel = candidate.fullName
            ),
            alreadyTracked = alreadyTracked,
            score = 60
        )
    }
}
