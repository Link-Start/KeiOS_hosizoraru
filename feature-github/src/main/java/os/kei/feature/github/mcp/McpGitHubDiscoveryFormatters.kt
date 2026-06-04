package os.kei.feature.github.mcp

import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubTrackedApp

internal fun StringBuilder.appendStarImportQualityCounts(
    candidates: List<GitHubRepositoryImportCandidate>
) {
    val counts = candidates.groupingBy { candidate ->
        GitHubStarImportClassifier.classify(candidate)
    }.eachCount()
    GitHubStarImportQuality.entries.forEach { quality ->
        appendLine("quality.${quality.name}=${counts[quality] ?: 0}")
    }
}

internal fun GitHubRepositoryCandidate.toMcpRepositoryRow(prefix: String): String {
    return "$prefix=repo:$fullName | stars:$starCount | forks:$forkCount | archived:$archived | fork:$fork | language:$language | url:${repoUrl.ifBlank { "https://github.com/$owner/$repo" }} | description:$description"
}

internal fun GitHubRepositoryImportCandidate.toMcpImportCandidateRow(prefix: String): String {
    val quality = GitHubStarImportClassifier.classify(this)
    return "$prefix=repo:${repository.fullName} | quality:${quality.name} | score:$score | tracked:$alreadyTracked | stars:${repository.starCount} | package:${trackedApp.packageName} | label:${trackedApp.appLabel} | url:${trackedApp.repoUrl}"
}

internal fun String.parseGitHubRepoUrls(): List<String> {
    return split('\n', ',', ';', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun String.toSyntheticGitHubImportCandidate(): GitHubRepositoryImportCandidate? {
    val parsed = GitHubVersionUtils.parseOwnerRepo(this) ?: return null
    val owner = parsed.first.trim()
    val repo = parsed.second.trim().removeSuffix(".git")
    if (owner.isBlank() || repo.isBlank()) return null
    val repoUrl = "https://github.com/$owner/$repo"
    val repository = GitHubRepositoryCandidate(
        owner = owner,
        repo = repo,
        repoUrl = repoUrl,
        sourceType = GitHubRepositoryDiscoverySourceType.PreferredRepository,
        matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName
    )
    return GitHubRepositoryImportCandidate(
        repository = repository,
        trackedApp = GitHubTrackedApp(
            repoUrl = repoUrl,
            owner = owner,
            repo = repo,
            packageName = "",
            appLabel = repository.fullName
        ),
        alreadyTracked = false,
        score = 0
    )
}
