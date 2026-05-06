package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality

internal object GitHubStarImportClassifier {
    fun classify(candidate: GitHubRepositoryImportCandidate): GitHubStarImportQuality {
        return classify(candidate.repository)
    }

    fun classify(repository: GitHubRepositoryCandidate): GitHubStarImportQuality {
        if (repository.archived || repository.fork) {
            return GitHubStarImportQuality.ArchivedOrFork
        }
        val searchable = listOf(
            repository.fullName,
            repository.description,
            repository.language
        ).joinToString(" ").lowercase()
        val androidScore = androidSignals.sumOf { signal ->
            if (searchable.contains(signal)) 2 else 0
        } + androidLanguageScore(repository.language)
        val otherPlatformScore = otherPlatformSignals.sumOf { signal ->
            if (searchable.contains(signal)) 1 else 0
        }
        return when {
            androidScore >= 3 && otherPlatformScore <= 2 -> GitHubStarImportQuality.LikelyAndroid
            androidScore >= 2 -> GitHubStarImportQuality.NeedsReview
            otherPlatformScore >= 2 -> GitHubStarImportQuality.OtherPlatform
            else -> GitHubStarImportQuality.NeedsReview
        }
    }

    fun isDefaultSelected(candidate: GitHubRepositoryImportCandidate): Boolean {
        return !candidate.alreadyTracked && classify(candidate) == GitHubStarImportQuality.LikelyAndroid
    }

    private fun androidLanguageScore(language: String): Int {
        return when (language.trim().lowercase()) {
            "kotlin" -> 2
            "java" -> 1
            else -> 0
        }
    }

    private val androidSignals = listOf(
        "android",
        "apk",
        "fdroid",
        "f-droid",
        "jetpack compose",
        "compose android",
        "material you",
        "shizuku",
        "magisk",
        "xposed",
        "lsposed",
        "termux",
        "miui"
    )

    private val otherPlatformSignals = listOf(
        "windows",
        "win32",
        "macos",
        "linux",
        "desktop",
        "server",
        "backend",
        "cli",
        "command line",
        "powershell",
        "vscode",
        "browser extension",
        "chrome extension",
        "npm",
        "nodejs",
        "python package",
        "docker",
        "homebrew"
    )
}
