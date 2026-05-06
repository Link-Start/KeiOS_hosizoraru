package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubStarImportClassifierTest {
    @Test
    fun `quality separates android candidates from other platforms`() {
        assertEquals(
            GitHubStarImportQuality.LikelyAndroid,
            GitHubStarImportClassifier.classify(
                importCandidate(
                    repo = "KeiOS",
                    description = "Android utility with Jetpack Compose release APK",
                    language = "Kotlin"
                )
            )
        )
        assertEquals(
            GitHubStarImportQuality.OtherPlatform,
            GitHubStarImportClassifier.classify(
                importCandidate(
                    repo = "desktop-tool",
                    description = "Windows macOS Linux CLI release",
                    language = "Rust"
                )
            )
        )
        assertEquals(
            GitHubStarImportQuality.ArchivedOrFork,
            GitHubStarImportClassifier.classify(
                importCandidate(
                    repo = "old-android-app",
                    description = "Android APK",
                    language = "Java",
                    archived = true
                )
            )
        )
    }

    @Test
    fun `default selection keeps only likely android candidates`() {
        assertTrue(
            GitHubStarImportClassifier.isDefaultSelected(
                importCandidate(
                    repo = "android-app",
                    description = "Android APK",
                    language = "Kotlin"
                )
            )
        )
        assertFalse(
            GitHubStarImportClassifier.isDefaultSelected(
                importCandidate(
                    repo = "desktop-tool",
                    description = "Windows desktop utility",
                    language = "C#"
                )
            )
        )
        assertFalse(
            GitHubStarImportClassifier.isDefaultSelected(
                importCandidate(
                    repo = "tracked-android-app",
                    description = "Android APK",
                    language = "Kotlin",
                    alreadyTracked = true
                )
            )
        )
    }
}

internal fun importCandidate(
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
