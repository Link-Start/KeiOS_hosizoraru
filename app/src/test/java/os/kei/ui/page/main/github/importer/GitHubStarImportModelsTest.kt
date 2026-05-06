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
            conflictStrategy = StarImportConflictStrategy.NewOnly,
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

    @Test
    fun `candidate list ui state can include tracked conflicts by strategy`() {
        val tracked = starImportCandidate(
            repo = "tracked-app",
            description = "Android APK",
            language = "Kotlin",
            alreadyTracked = true
        )

        val state = buildStarImportCandidateListUiState(
            candidates = listOf(tracked),
            filterInput = "",
            viewFilter = StarImportViewFilter.Tracked,
            qualityFilters = setOf(GitHubStarImportQuality.LikelyAndroid),
            conflictStrategy = StarImportConflictStrategy.IncludeTracked,
            selectedIds = setOf(tracked.trackedApp.id),
            verificationStates = emptyMap()
        )

        assertEquals(setOf(tracked.trackedApp.id), state.visibleImportableIds)
        assertEquals(listOf(tracked), state.selectedCandidates)
        assertEquals(1, state.selectedImportableCount)
    }

    @Test
    fun `candidate list ui state exposes verified apk filter and batch ids`() {
        val verified = starImportCandidate(
            repo = "verified-app",
            description = "Android APK release",
            language = "Kotlin"
        )
        val unchecked = starImportCandidate(
            repo = "unchecked-app",
            description = "Android APK release",
            language = "Java"
        )
        val checking = starImportCandidate(
            repo = "checking-app",
            description = "Android APK release",
            language = "Kotlin"
        )

        val state = buildStarImportCandidateListUiState(
            candidates = listOf(verified, unchecked, checking),
            filterInput = "",
            viewFilter = StarImportViewFilter.VerifiedApk,
            qualityFilters = setOf(GitHubStarImportQuality.LikelyAndroid),
            conflictStrategy = StarImportConflictStrategy.NewOnly,
            selectedIds = emptySet(),
            verificationStates = mapOf(
                verified.trackedApp.id to StarImportApkVerificationUiState(
                    verification = GitHubStarImportApkVerification(
                        owner = verified.repository.owner,
                        repo = verified.repository.repo,
                        status = GitHubStarImportApkVerificationStatus.HasApk,
                        apkAssetCount = 2,
                        sampleAssetName = "app-arm64-v8a.apk"
                    )
                ),
                checking.trackedApp.id to StarImportApkVerificationUiState(checking = true)
            )
        )

        assertEquals(listOf(verified), state.filteredCandidates)
        assertEquals(setOf(verified.trackedApp.id), state.visibleVerifiedApkIds)
        assertEquals(1, state.verifiedApkCount)
        assertEquals(1, state.checkingCount)
        assertEquals(emptyList<GitHubRepositoryImportCandidate>(), state.visibleVerificationTargets)
    }

    @Test
    fun `verified apk package name fills blank tracked package before import`() {
        val candidate = starImportCandidate(
            repo = "verified-app",
            description = "Android APK release",
            language = "Kotlin"
        )

        val mapped = applyVerifiedPackageNamesToStarImportCandidates(
            candidates = listOf(candidate),
            verificationStates = mapOf(
                candidate.trackedApp.id to StarImportApkVerificationUiState(
                    verification = GitHubStarImportApkVerification(
                        owner = candidate.repository.owner,
                        repo = candidate.repository.repo,
                        status = GitHubStarImportApkVerificationStatus.HasApk,
                        packageName = "demo.verified"
                    )
                )
            )
        )

        assertEquals("demo.verified", mapped.single().trackedApp.packageName)
        assertEquals("demo/verified-app|demo.verified", mapped.single().trackedApp.id)
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
