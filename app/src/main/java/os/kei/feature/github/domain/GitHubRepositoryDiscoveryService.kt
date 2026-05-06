package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.sqrt

internal interface GitHubRepositoryDiscoverySource {
    val supportsParallelSearch: Boolean
        get() = false

    fun fetchAuthenticatedStarredRepositories(limit: Int): Result<List<GitHubRepositoryCandidate>>
    fun fetchUserStarredRepositories(
        username: String,
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>>

    fun searchRepositories(query: String, limit: Int): Result<List<GitHubRepositoryCandidate>>
    fun fetchStarListRepositories(
        starListUrl: String,
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>>

    fun fetchStarLists(starListsUrl: String): Result<List<GitHubStarListSummary>> {
        return Result.success(emptyList())
    }
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
        val starListUrl = request.starListUrl.trim()
        val resolvedSource = request.source.resolve(username, starListUrl)
        val existingIndex = TrackedRepoIndex.from(existingItems)
        val rawCandidates = when (resolvedSource) {
            GitHubStarredRepositoryImportSource.AuthenticatedUser -> {
                check(request.apiToken.isNotBlank()) {
                    "A GitHub token is required to import the signed-in account stars."
                }
                source.fetchAuthenticatedStarredRepositories(limit).getOrThrow()
            }

            GitHubStarredRepositoryImportSource.PublicUser -> {
                source.fetchUserStarredRepositories(username, limit).getOrThrow()
            }

            GitHubStarredRepositoryImportSource.StarListUrl -> {
                source.fetchStarListRepositories(starListUrl, limit).getOrThrow()
            }

            GitHubStarredRepositoryImportSource.Auto -> error("Unresolved starred import source")
        }
        val candidates = rawCandidates
            .dedupeByRepo()
            .map { candidate ->
                candidate.toImportCandidate(
                    packageName = "",
                    appLabel = candidate.fullName,
                    existingIndex = existingIndex,
                    score = candidate.starImportScore()
                )
            }
            .sortedWith(
                compareBy<GitHubRepositoryImportCandidate> { it.alreadyTracked }
                    .thenByDescending { it.score }
                    .thenBy { it.repository.fullName.lowercase() }
            )

        GitHubStarredRepositoryImportPreview(
            sourceLabel = when (resolvedSource) {
                GitHubStarredRepositoryImportSource.AuthenticatedUser -> "authenticated"
                GitHubStarredRepositoryImportSource.PublicUser -> username
                GitHubStarredRepositoryImportSource.StarListUrl -> starListUrl
                GitHubStarredRepositoryImportSource.Auto -> "stars"
            },
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
        val searchContext = AppRepositorySearchContext.create(request.app)
        val queries = searchContext.queries
        val existingIndex = TrackedRepoIndex.from(existingItems)
        val fetched = fetchAppSearchCandidates(searchContext, limit)
        val candidates = fetched
            .dedupeByRepo()
            .map { candidate ->
                val scored = candidate.withAppSearchScore(searchContext)
                scored.first.toImportCandidate(
                    packageName = searchContext.packageName,
                    appLabel = searchContext.appLabel,
                    existingIndex = existingIndex,
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

    private fun fetchAppSearchCandidates(
        searchContext: AppRepositorySearchContext,
        limit: Int
    ): List<GitHubRepositoryCandidate> {
        val queries = searchContext.queries
        if (queries.isEmpty()) return emptyList()
        if (!source.supportsParallelSearch) {
            return queries.flatMap { query ->
                source.searchRepositories(query, limit).getOrThrow()
            }
        }
        if (queries.size == 1) {
            return source.searchRepositories(queries.single(), limit).getOrThrow()
        }
        val scheduledQueries = queries
            .mapIndexed { index, query ->
                RepositorySearchQuery(
                    query = query,
                    originalIndex = index,
                    priority = if (query == searchContext.packageQuery) 0 else 1
                )
            }
            .sortedWith(
                compareBy<RepositorySearchQuery> { it.priority }
                    .thenBy { it.originalIndex }
            )
        return fetchQueriesConcurrently(
            scheduledQueries = scheduledQueries,
            limit = limit
        )
    }

    private fun fetchQueriesConcurrently(
        scheduledQueries: List<RepositorySearchQuery>,
        limit: Int
    ): List<GitHubRepositoryCandidate> {
        val workerCount = min(MAX_PARALLEL_SEARCH_QUERIES, scheduledQueries.size)
        val executor = Executors.newFixedThreadPool(workerCount) { runnable ->
            Thread(runnable, "github-app-repo-search").apply {
                isDaemon = true
            }
        }
        return try {
            val completionService =
                ExecutorCompletionService<RepositorySearchResult>(executor)
            scheduledQueries.forEach { scheduledQuery ->
                completionService.submit(
                    Callable {
                        RepositorySearchResult(
                            originalIndex = scheduledQuery.originalIndex,
                            candidates = source.searchRepositories(
                                query = scheduledQuery.query,
                                limit = limit
                            ).getOrThrow()
                        )
                    }
                )
            }
            buildList {
                repeat(scheduledQueries.size) {
                    add(completionService.takeResult())
                }
            }
                .sortedBy { it.originalIndex }
                .flatMap { it.candidates }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun ExecutorCompletionService<RepositorySearchResult>.takeResult(): RepositorySearchResult {
        return try {
            take().get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun GitHubRepositoryCandidate.toImportCandidate(
        packageName: String,
        appLabel: String,
        existingIndex: TrackedRepoIndex,
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
            alreadyTracked = existingIndex.contains(
                owner = owner,
                repo = repo,
                packageName = normalizedPackageName
            ),
            score = score
        )
    }

    private fun GitHubRepositoryCandidate.starImportScore(): Int {
        val sourceScore = when (sourceType) {
            GitHubRepositoryDiscoverySourceType.AuthenticatedStars -> 40
            GitHubRepositoryDiscoverySourceType.PublicUserStars -> 36
            GitHubRepositoryDiscoverySourceType.PreferredRepository -> 32
            GitHubRepositoryDiscoverySourceType.StarList -> 34
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
        searchContext: AppRepositorySearchContext
    ): Pair<GitHubRepositoryCandidate, Int> {
        val searchable = listOf(fullName, repo, description)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val hasPackageName = searchContext.normalizedPackageName.isNotBlank() &&
                searchable.contains(searchContext.normalizedPackageName)
        val hasFullLabel = searchContext.normalizedAppLabel.isNotBlank() &&
                searchable.contains(searchContext.normalizedAppLabel)
        val tokenHits = searchContext.labelTokens.count { token -> searchable.contains(token) }
        val matchReason = when {
            hasPackageName -> GitHubRepositoryCandidateMatchReason.PackageName
            hasFullLabel || tokenHits >= 2 -> GitHubRepositoryCandidateMatchReason.AppLabel
            searchContext.packageTail.length >= 3 &&
                    repo.lowercase(Locale.ROOT).contains(searchContext.packageTail) ->
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
        return distinctBy { it.repoKey() }
    }

    private fun GitHubRepositoryCandidate.repoKey(): String {
        return repoKey(owner, repo)
    }

    companion object {
        private const val MAX_IMPORT_LIMIT = 1_000
        private const val MAX_SEARCH_LIMIT = 50
        private const val MAX_PARALLEL_SEARCH_QUERIES = 2
    }
}

private fun GitHubStarredRepositoryImportSource.resolve(
    username: String,
    starListUrl: String
): GitHubStarredRepositoryImportSource {
    return when (this) {
        GitHubStarredRepositoryImportSource.Auto -> when {
            starListUrl.isNotBlank() -> GitHubStarredRepositoryImportSource.StarListUrl
            username.isNotBlank() -> GitHubStarredRepositoryImportSource.PublicUser
            else -> GitHubStarredRepositoryImportSource.AuthenticatedUser
        }

        else -> this
    }
}

internal object GitHubRepositoryDiscoveryQueries {
    fun forInstalledApp(app: InstalledAppItem): List<String> {
        val packageName = app.packageName.trim()
        return forInstalledApp(
            appLabel = app.label.trim(),
            packageName = packageName,
            labelTokens = normalizedTokens(app.label)
        )
    }

    fun forInstalledApp(
        appLabel: String,
        packageName: String,
        labelTokens: List<String>
    ): List<String> {
        val packageTail = packageName.substringAfterLast('.').trim()
        return buildList {
            if (appLabel.isNotBlank()) {
                add("$appLabel android in:name,description,readme")
            }
            exactPackageQuery(packageName).takeIf { it.isNotBlank() }?.let { query ->
                add(query)
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

    fun exactPackageQuery(packageName: String): String {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return ""
        return "$normalizedPackageName in:description,readme"
    }

    fun normalizedTokens(value: String): List<String> {
        return value
            .lowercase(Locale.ROOT)
            .replace(nonTokenRegex, " ")
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
    private val nonTokenRegex = Regex("""[^a-z0-9]+""")
}

private data class AppRepositorySearchContext(
    val appLabel: String,
    val packageName: String,
    val normalizedPackageName: String,
    val normalizedAppLabel: String,
    val packageTail: String,
    val labelTokens: List<String>,
    val packageQuery: String,
    val queries: List<String>
) {
    companion object {
        fun create(app: InstalledAppItem): AppRepositorySearchContext {
            val appLabel = app.label.trim()
            val packageName = app.packageName.trim()
            val normalizedPackageName = packageName.lowercase(Locale.ROOT)
            val labelTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(appLabel)
            return AppRepositorySearchContext(
                appLabel = appLabel,
                packageName = packageName,
                normalizedPackageName = normalizedPackageName,
                normalizedAppLabel = appLabel.lowercase(Locale.ROOT),
                packageTail = normalizedPackageName.substringAfterLast('.'),
                labelTokens = labelTokens,
                packageQuery = GitHubRepositoryDiscoveryQueries.exactPackageQuery(packageName),
                queries = GitHubRepositoryDiscoveryQueries.forInstalledApp(
                    appLabel = appLabel,
                    packageName = packageName,
                    labelTokens = labelTokens
                )
            )
        }
    }
}

private data class RepositorySearchQuery(
    val query: String,
    val originalIndex: Int,
    val priority: Int
)

private data class RepositorySearchResult(
    val originalIndex: Int,
    val candidates: List<GitHubRepositoryCandidate>
)

private data class TrackedRepoIndex(
    private val repoKeys: Set<String>,
    private val repoPackageKeys: Set<String>
) {
    fun contains(
        owner: String,
        repo: String,
        packageName: String
    ): Boolean {
        val key = repoKey(owner, repo)
        if (packageName.isBlank()) {
            return key in repoKeys
        }
        return "$key|${packageName.lowercase(Locale.ROOT)}" in repoPackageKeys
    }

    companion object {
        fun from(items: List<GitHubTrackedApp>): TrackedRepoIndex {
            return TrackedRepoIndex(
                repoKeys = items.mapTo(mutableSetOf()) { item ->
                    repoKey(item.owner, item.repo)
                },
                repoPackageKeys = items.mapTo(mutableSetOf()) { item ->
                    val packageName = item.packageName.trim().lowercase(Locale.ROOT)
                    "${repoKey(item.owner, item.repo)}|$packageName"
                }
            )
        }
    }
}

private fun repoKey(owner: String, repo: String): String {
    return "${owner.lowercase(Locale.ROOT)}/${repo.lowercase(Locale.ROOT)}"
}
