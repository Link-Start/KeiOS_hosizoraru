package os.kei.ui.page.main.github.importer

import org.junit.Test
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
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
    fun `candidate list ui state filters and keeps selected verification targets`() {
        val android = starImportCandidate(
            repo = "android-app",
            description = "Android app with release APK",
            language = "Kotlin"
        )
        val desktop = starImportCandidate(
            repo = "desktop-tool",
            description = "Windows macOS Linux utility",
            language = "Rust"
        )
        val tracked = starImportCandidate(
            repo = "tracked-app",
            description = "Android APK",
            language = "Java",
            alreadyTracked = true
        )

        val state = buildStarImportCandidateListUiState(
            candidates = listOf(android, desktop, tracked),
            filterInput = "app",
            viewFilter = StarImportViewFilter.All,
            qualityFilters = setOf(GitHubStarImportQuality.LikelyAndroid),
            selectedIds = setOf(android.trackedApp.id, desktop.trackedApp.id),
            verificationStates = mapOf(
                android.trackedApp.id to StarImportApkVerificationUiState(
                    verification = GitHubStarImportApkVerification(
                        owner = android.repository.owner,
                        repo = android.repository.repo,
                        status = GitHubStarImportApkVerificationStatus.Failed
                    )
                )
            )
        )

        assertEquals(listOf(android, tracked), state.filteredCandidates)
        assertEquals(2, state.selectedImportableCount)
        assertEquals(setOf(android.trackedApp.id), state.visibleRecommendedIds)
        assertEquals(listOf(android, desktop), state.selectedCandidates)
        assertEquals(listOf(android, desktop), state.selectedVerificationTargets)
    }

}

private fun starImportCandidate(
    repo: String,
    description: String,
    language: String,
    alreadyTracked: Boolean = false
): GitHubRepositoryImportCandidate {
    val candidate = GitHubRepositoryCandidate(
        owner = "demo",
        repo = repo,
        repoUrl = "https://github.com/demo/$repo",
        description = description,
        language = language,
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
