package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import kotlin.math.min
import kotlin.math.sqrt

internal interface GitHubRepositoryDiscoverySource {
    fun fetchAuthenticatedStarredRepositories(limit: Int): Result<List<GitHubRepositoryCandidate>>
    fun fetchUserStarredRepositories(
        username: String,
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>>

    fun searchRepositories(query: String, limit: Int): Result<List<GitHubRepositoryCandidate>>
}

internal class GitHubRepositoryDiscoveryService(
    private val source: GitHubRepositoryDiscoverySource
) {
    fun previewStarredRepositoryImport(
        request: GitHubStarredRepositoryImportRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubStarredRepositoryImportPreview> = runCatching {
        val limit = request.limit.coerceIn(1, MAX_IMPORT_LIMIT)
        val username = request.username.trim()
        val rawCandidates = if (username.isBlank()) {
            check(request.apiToken.isNotBlank()) { "A GitHub token is required to import the signed-in account stars." }
            source.fetchAuthenticatedStarredRepositories(limit).getOrThrow()
        } else {
            source.fetchUserStarredRepositories(username, limit).getOrThrow()
        }
        val candidates = rawCandidates
            .dedupeByRepo()
            .map { candidate ->
                candidate.toImportCandidate(
                    packageName = "",
                    appLabel = candidate.fullName,
                    existingItems = existingItems,
                    score = candidate.starImportScore()
                )
            }
            .sortedWith(
                compareBy<GitHubRepositoryImportCandidate> { it.alreadyTracked }
                    .thenByDescending { it.score }
                    .thenBy { it.repository.fullName.lowercase() }
            )

        GitHubStarredRepositoryImportPreview(
            sourceLabel = username.ifBlank { "authenticated" },
            totalFetchedCount = rawCandidates.size,
            importableCount = candidates.count { !it.alreadyTracked },
            alreadyTrackedCount = candidates.count { it.alreadyTracked },
            candidates = candidates
        )
    }

    fun searchRepositoriesForApp(
        request: GitHubAppRepositorySearchRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubAppRepositorySearchResult> = runCatching {
        val limit = request.limit.coerceIn(1, MAX_SEARCH_LIMIT)
        val queries = GitHubRepositoryDiscoveryQueries.forInstalledApp(request.app)
        val fetched = queries.flatMap { query ->
            source.searchRepositories(query, limit).getOrThrow()
        }
        val candidates = fetched
            .dedupeByRepo()
            .map { candidate ->
                val scored = candidate.withAppSearchScore(request.app)
                scored.first.toImportCandidate(
                    packageName = request.app.packageName,
                    appLabel = request.app.label,
                    existingItems = existingItems,
                    score = scored.second
                )
            }
            .sortedWith(
                compareBy<GitHubRepositoryImportCandidate> { it.alreadyTracked }
                    .thenByDescending { it.score }
                    .thenByDescending { it.repository.starCount }
                    .thenBy { it.repository.fullName.lowercase() }
            )
            .take(limit)

        GitHubAppRepositorySearchResult(
            app = request.app,
            queryCount = queries.size,
            candidates = candidates
        )
    }

    private fun GitHubRepositoryCandidate.toImportCandidate(
        packageName: String,
        appLabel: String,
        existingItems: List<GitHubTrackedApp>,
        score: Int
    ): GitHubRepositoryImportCandidate {
        val normalizedPackageName = packageName.trim()
        val normalizedAppLabel = appLabel.trim().ifBlank { fullName }
        val trackedApp = GitHubTrackedApp(
            repoUrl = repoUrl.ifBlank { "https://github.com/$owner/$repo" },
            owner = owner,
            repo = repo,
            packageName = normalizedPackageName,
            appLabel = normalizedAppLabel
        )
        return GitHubRepositoryImportCandidate(
            repository = this,
            trackedApp = trackedApp,
            alreadyTracked = existingItems.any { existing ->
                existing.owner.equals(owner, ignoreCase = true) &&
                        existing.repo.equals(repo, ignoreCase = true) &&
                        (
                                normalizedPackageName.isBlank() ||
                                        existing.packageName.equals(
                                            normalizedPackageName,
                                            ignoreCase = true
                                        )
                                )
            },
            score = score
        )
    }

    private fun GitHubRepositoryCandidate.starImportScore(): Int {
        val sourceScore = when (sourceType) {
            GitHubRepositoryDiscoverySourceType.AuthenticatedStars -> 40
            GitHubRepositoryDiscoverySourceType.PublicUserStars -> 36
            GitHubRepositoryDiscoverySourceType.PreferredRepository -> 32
            GitHubRepositoryDiscoverySourceType.RepositorySearch -> 20
        }
        val activityScore = if (updatedAtMillis > 0L) 8 else 0
        val repositoryScore = if (!archived) 10 else -20
        val forkScore = if (fork) -8 else 0
        return sourceScore + activityScore + repositoryScore + forkScore + min(
            20,
            sqrt(starCount.toDouble()).toInt()
        )
    }

    private fun GitHubRepositoryCandidate.withAppSearchScore(
        app: InstalledAppItem
    ): Pair<GitHubRepositoryCandidate, Int> {
        val labelTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(app.label)
        val packageName = app.packageName.lowercase()
        val packageTail = packageName.substringAfterLast('.')
        val searchable = listOf(fullName, repo, description).joinToString(" ").lowercase()
        val hasPackageName = packageName.isNotBlank() && searchable.contains(packageName)
        val hasFullLabel = app.label.isNotBlank() && searchable.contains(app.label.lowercase())
        val tokenHits = labelTokens.count { token -> searchable.contains(token) }
        val matchReason = when {
            hasPackageName -> GitHubRepositoryCandidateMatchReason.PackageName
            hasFullLabel || tokenHits >= 2 -> GitHubRepositoryCandidateMatchReason.AppLabel
            packageTail.length >= 3 && repo.lowercase().contains(packageTail) ->
                GitHubRepositoryCandidateMatchReason.RepositoryName

            else -> matchReason
        }
        val score = when (matchReason) {
            GitHubRepositoryCandidateMatchReason.PackageName -> 120
            GitHubRepositoryCandidateMatchReason.AppLabel -> 90
            GitHubRepositoryCandidateMatchReason.RepositoryName -> 70
            GitHubRepositoryCandidateMatchReason.Starred -> 45
        } +
                tokenHits * 8 +
                min(24, sqrt(starCount.toDouble()).toInt()) +
                if (archived) -30 else 0 +
                        if (fork) -10 else 0

        return copy(matchReason = matchReason) to score
    }

    private fun List<GitHubRepositoryCandidate>.dedupeByRepo(): List<GitHubRepositoryCandidate> {
        return distinctBy { "${it.owner.lowercase()}/${it.repo.lowercase()}" }
    }

    companion object {
        private const val MAX_IMPORT_LIMIT = 1_000
        private const val MAX_SEARCH_LIMIT = 50
    }
}

internal object GitHubRepositoryDiscoveryQueries {
    fun forInstalledApp(app: InstalledAppItem): List<String> {
        val labelTokens = normalizedTokens(app.label)
        val packageName = app.packageName.trim()
        val packageTail = packageName.substringAfterLast('.').trim()
        return buildList {
            if (app.label.isNotBlank()) {
                add("${app.label.trim()} android in:name,description,readme")
            }
            if (packageName.isNotBlank()) {
                add("$packageName in:description,readme")
            }
            if (labelTokens.isNotEmpty()) {
                add("${labelTokens.take(3).joinToString(" ")} android")
            }
            if (packageTail.length >= 3) {
                add("$packageTail android in:name,description,readme")
            }
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun normalizedTokens(value: String): List<String> {
        return value
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .split(' ')
            .map { it.trim() }
            .filter { token -> token.length >= 2 && token !in ignoredTokens }
            .distinct()
    }

    private val ignoredTokens = setOf(
        "app",
        "android",
        "mobile",
        "client",
        "beta",
        "debug",
        "release"
    )
}
