package os.kei.feature.github.domain

import os.kei.feature.github.GitHubBoundedRunner
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanResult
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

internal class GitHubPackageRepositoryResolver(
    private val discoverySource: GitHubRepositoryDiscoverySource,
    private val packageNameScanner: GitHubApkPackageNameScanner
) {
    fun scanRepositoriesForPackage(
        request: GitHubPackageRepositoryScanRequest
    ): Result<GitHubPackageRepositoryScanResult> = runCatching {
        val packageName = request.packageName.trim()
        check(packageName.isNotBlank()) { "Package name is required." }
        check(GitHubPackageNameValidator.isValid(packageName)) {
            "Invalid package name: $packageName"
        }

        val appLabel = request.appLabel.trim()
        val searchContext = PackageSearchContext.create(packageName, appLabel)
        val queries = searchContext.queries
        val candidateLimit = request.candidateLimit.coerceIn(1, MAX_CANDIDATE_LIMIT)
        val verificationLimit = request.verificationLimit.coerceIn(1, candidateLimit)
        var executedQueryCount = 0
        var mismatchedCount = 0
        var failedCount = 0
        val rawCandidates = mutableListOf<GitHubRepositoryCandidate>()
        val scannedRepoKeys = mutableSetOf<String>()
        val matchedCandidates = mutableListOf<GitHubPackageRepositoryScanCandidate>()
        val scoreCache = mutableMapOf<String, Pair<GitHubRepositoryCandidate, Int>>()
        var firstSearchFailure: Throwable? = null
        var successfulSearchCount = 0

        preferredRepositoryCandidate(request.preferredRepoUrl)?.let { preferredCandidate ->
            rawCandidates += preferredCandidate
            val preferredScored = preferredCandidate.withPackageSearchScore(searchContext)
            scoreCache[preferredCandidate.repoKey()] = preferredScored
            val preferredTargets = listOf(
                preferredScored
            ).filter { (candidate, _) ->
                scannedRepoKeys.add(candidate.repoKey())
            }
            val batchResult = scanCandidates(
                targets = preferredTargets,
                packageName = packageName,
                appLabel = appLabel,
                lookupConfig = request.lookupConfig
            )
            matchedCandidates += batchResult.matchedCandidates
            mismatchedCount += batchResult.mismatchedCount
            failedCount += batchResult.failedCount
            if (matchedCandidates.isNotEmpty()) {
                return@runCatching buildScanResult(
                    packageName = packageName,
                    appLabel = appLabel,
                    queryCount = executedQueryCount,
                    rawCandidates = rawCandidates,
                    scannedRepoKeys = scannedRepoKeys,
                    matchedCandidates = matchedCandidates,
                    searchContext = searchContext,
                    scoreCache = scoreCache,
                    mismatchedCount = mismatchedCount,
                    failedCount = failedCount,
                    candidateLimit = candidateLimit
                )
            }
        }

        for ((queryIndex, query) in queries.withIndex()) {
            val searchResult = discoverySource.searchRepositories(
                query = query,
                limit = searchLimitForQuery(
                    queryIndex = queryIndex,
                    candidateLimit = candidateLimit
                )
            )
            executedQueryCount += 1
            if (searchResult.isFailure) {
                if (firstSearchFailure == null) firstSearchFailure = searchResult.exceptionOrNull()
                continue
            }
            val searchCandidates = searchResult.getOrThrow()
            successfulSearchCount += 1
            rawCandidates += searchCandidates

            val rankedCandidates = rawCandidates.rankedPackageCandidates(
                searchContext = searchContext,
                candidateLimit = candidateLimit,
                scoreCache = scoreCache
            )
            val queryVerificationLimit = verificationLimitForQuery(
                queryIndex = queryIndex,
                queryCount = queries.size,
                verificationLimit = verificationLimit
            )
            val scanTargets = rankedCandidates
                .take(queryVerificationLimit)
                .filter { (candidate, _) ->
                    val repoKey = candidate.repoKey()
                    scannedRepoKeys.add(repoKey)
                }
            val batchResult = scanCandidates(
                targets = scanTargets,
                packageName = packageName,
                appLabel = appLabel,
                lookupConfig = request.lookupConfig
            )
            matchedCandidates += batchResult.matchedCandidates
            mismatchedCount += batchResult.mismatchedCount
            failedCount += batchResult.failedCount

            if (matchedCandidates.isNotEmpty()) break
        }

        if (successfulSearchCount == 0 && firstSearchFailure != null) {
            throw firstSearchFailure
        }

        buildScanResult(
            packageName = packageName,
            appLabel = appLabel,
            queryCount = executedQueryCount,
            rawCandidates = rawCandidates,
            scannedRepoKeys = scannedRepoKeys,
            matchedCandidates = matchedCandidates,
            searchContext = searchContext,
            scoreCache = scoreCache,
            mismatchedCount = mismatchedCount,
            failedCount = failedCount,
            candidateLimit = candidateLimit
        )
    }

    private fun preferredRepositoryCandidate(repoUrl: String): GitHubRepositoryCandidate? {
        val parsed = GitHubVersionUtils.parseOwnerRepo(repoUrl) ?: return null
        val owner = parsed.first.trim()
        val repo = parsed.second.trim().removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return null
        return GitHubRepositoryCandidate(
            owner = owner,
            repo = repo,
            repoUrl = "https://github.com/$owner/$repo",
            sourceType = GitHubRepositoryDiscoverySourceType.PreferredRepository,
            matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName
        )
    }

    private fun buildScanResult(
        packageName: String,
        appLabel: String,
        queryCount: Int,
        rawCandidates: List<GitHubRepositoryCandidate>,
        scannedRepoKeys: Set<String>,
        matchedCandidates: List<GitHubPackageRepositoryScanCandidate>,
        searchContext: PackageSearchContext,
        scoreCache: MutableMap<String, Pair<GitHubRepositoryCandidate, Int>>,
        mismatchedCount: Int,
        failedCount: Int,
        candidateLimit: Int
    ): GitHubPackageRepositoryScanResult {
        val rankedCandidates = rawCandidates.rankedPackageCandidates(
            searchContext = searchContext,
            candidateLimit = candidateLimit,
            scoreCache = scoreCache
        )
        val sortedMatchedCandidates = matchedCandidates.sortedWith(
            compareByDescending<GitHubPackageRepositoryScanCandidate> { it.score }
                .thenByDescending { it.repository.starCount }
                .thenBy { it.repository.fullName.lowercase() }
        )
        return GitHubPackageRepositoryScanResult(
            packageName = packageName,
            appLabel = appLabel,
            queryCount = queryCount,
            fetchedCandidateCount = rankedCandidates.size,
            scannedCandidateCount = scannedRepoKeys.size,
            matchedCandidates = sortedMatchedCandidates,
            mismatchedCandidateCount = mismatchedCount,
            failedCandidateCount = failedCount
        )
    }

    private fun GitHubRepositoryCandidate.withPackageSearchScore(
        searchContext: PackageSearchContext
    ): Pair<GitHubRepositoryCandidate, Int> {
        val searchable = listOf(fullName, repo, description)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val searchableTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(searchable).toSet()
        val compactRepo = GitHubRepositoryDiscoveryQueries.compactIdentifier(repo)
        val compactSearchable = GitHubRepositoryDiscoveryQueries.compactIdentifier(searchable)
        val hasPackageName = searchable.contains(searchContext.normalizedPackageName)
        val hasFullLabel =
            searchContext.appLabel.isNotBlank() && searchable.contains(searchContext.normalizedAppLabel)
        val tokenHits = searchContext.labelTokens.count { token -> searchable.contains(token) }
        val packageTailTokenHits = searchContext.packageTailTokens.count { token ->
            token in searchableTokens
        }
        val repoContainsPackageTail =
            searchContext.packageTail.length >= 3 &&
                    repo.lowercase(Locale.ROOT).contains(searchContext.packageTail)
        val repoContainsCompactPackageTail =
            searchContext.compactPackageTail.length >= 3 &&
                    (
                            compactRepo.contains(searchContext.compactPackageTail) ||
                                    compactSearchable.contains(searchContext.compactPackageTail)
                            )
        val repoContainsPackageTailTokens =
            searchContext.packageTailTokens.size >= 2 &&
                    packageTailTokenHits >= min(2, searchContext.packageTailTokens.size)
        val matchReason = when {
            hasPackageName -> GitHubRepositoryCandidateMatchReason.PackageName
            hasFullLabel || tokenHits >= 2 -> GitHubRepositoryCandidateMatchReason.AppLabel
            repoContainsPackageTail ||
                    repoContainsCompactPackageTail ||
                    repoContainsPackageTailTokens -> GitHubRepositoryCandidateMatchReason.RepositoryName
            else -> matchReason
        }
        val score = when (matchReason) {
            GitHubRepositoryCandidateMatchReason.PackageName -> 150
            GitHubRepositoryCandidateMatchReason.AppLabel -> 110
            GitHubRepositoryCandidateMatchReason.RepositoryName -> 80
            GitHubRepositoryCandidateMatchReason.Starred -> 40
        } +
                tokenHits * 10 +
                packageTailTokenHits * 8 +
                min(30, sqrt(starCount.toDouble()).toInt()) +
                if (archived) -35 else 0 +
                        if (fork) -12 else 0

        return copy(matchReason = matchReason) to score
    }

    private fun List<GitHubRepositoryCandidate>.dedupeByRepo(): List<GitHubRepositoryCandidate> {
        return distinctBy { "${it.owner.lowercase()}/${it.repo.lowercase()}" }
    }

    private fun List<GitHubRepositoryCandidate>.rankedPackageCandidates(
        searchContext: PackageSearchContext,
        candidateLimit: Int,
        scoreCache: MutableMap<String, Pair<GitHubRepositoryCandidate, Int>>
    ): List<Pair<GitHubRepositoryCandidate, Int>> {
        return dedupeByRepo()
            .map { candidate ->
                scoreCache.getOrPut(candidate.repoKey()) {
                    candidate.withPackageSearchScore(searchContext)
                }
            }
            .sortedWith(
                compareByDescending<Pair<GitHubRepositoryCandidate, Int>> { it.second }
                    .thenByDescending { it.first.starCount }
                    .thenBy { it.first.fullName.lowercase() }
            )
            .take(candidateLimit)
    }

    private fun GitHubRepositoryCandidate.repoKey(): String {
        return "${owner.lowercase()}/${repo.lowercase()}"
    }

    private fun scanCandidates(
        targets: List<Pair<GitHubRepositoryCandidate, Int>>,
        packageName: String,
        appLabel: String,
        lookupConfig: GitHubLookupConfig
    ): PackageRepositoryVerificationBatch {
        if (targets.isEmpty()) return PackageRepositoryVerificationBatch()
        if (targets.size == 1) {
            val (candidate, score) = targets.single()
            return PackageRepositoryVerificationBatch.fromOutcomes(
                listOf(
                    verifyCandidate(
                        candidate = candidate,
                        score = score,
                        packageName = packageName,
                        appLabel = appLabel,
                        lookupConfig = lookupConfig
                    )
                )
            )
        }
        val workerCount = min(MAX_PARALLEL_VERIFICATIONS, targets.size)
        val outcomes = GitHubBoundedRunner.mapOrdered(
            items = targets,
            maxConcurrency = workerCount,
            threadName = "github-package-repo-scan"
        ) { (candidate, score) ->
            verifyCandidate(
                candidate = candidate,
                score = score,
                packageName = packageName,
                appLabel = appLabel,
                lookupConfig = lookupConfig
            )
        }
        return PackageRepositoryVerificationBatch.fromOutcomes(outcomes)
    }

    private fun verifyCandidate(
        candidate: GitHubRepositoryCandidate,
        score: Int,
        packageName: String,
        appLabel: String,
        lookupConfig: GitHubLookupConfig
    ): PackageRepositoryVerification {
        val repoUrl = candidate.repoUrl.ifBlank {
            "https://github.com/${candidate.owner}/${candidate.repo}"
        }
        val scanResult = packageNameScanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = repoUrl,
                lookupConfig = lookupConfig
            )
        )
        return scanResult.fold(
            onSuccess = { scanned ->
                if (scanned.packageName.equals(packageName, ignoreCase = true)) {
                    PackageRepositoryVerification(
                        matchedCandidate = GitHubPackageRepositoryScanCandidate(
                            repository = candidate,
                            trackedApp = GitHubTrackedApp(
                                repoUrl = repoUrl,
                                owner = candidate.owner,
                                repo = candidate.repo,
                                packageName = packageName,
                                appLabel = appLabel.ifBlank { candidate.fullName }
                            ),
                            score = score + CONFIRMED_PACKAGE_SCORE,
                            releaseTag = scanned.releaseTag,
                            releaseUrl = scanned.releaseUrl,
                            assetName = scanned.assetName
                        )
                    )
                } else {
                    PackageRepositoryVerification(mismatched = true)
                }
            },
            onFailure = {
                PackageRepositoryVerification(failed = true)
            }
        )
    }

    private fun searchLimitForQuery(
        queryIndex: Int,
        candidateLimit: Int
    ): Int {
        if (candidateLimit > DEFAULT_CANDIDATE_LIMIT) return candidateLimit
        return when (queryIndex) {
            0 -> min(candidateLimit, EXACT_PACKAGE_SEARCH_LIMIT)
            else -> min(candidateLimit, FALLBACK_SEARCH_LIMIT)
        }
    }

    private fun verificationLimitForQuery(
        queryIndex: Int,
        queryCount: Int,
        verificationLimit: Int
    ): Int {
        val budget = when (queryIndex) {
            0 -> EXACT_PACKAGE_VERIFICATION_LIMIT
            queryCount - 1 -> verificationLimit
            else -> FALLBACK_VERIFICATION_LIMIT
        }
        return min(verificationLimit, budget)
    }

    companion object {
        private const val MAX_CANDIDATE_LIMIT = 50
        private const val DEFAULT_CANDIDATE_LIMIT = 16
        private const val CONFIRMED_PACKAGE_SCORE = 1_000
        private const val EXACT_PACKAGE_SEARCH_LIMIT = 10
        private const val FALLBACK_SEARCH_LIMIT = 8
        private const val EXACT_PACKAGE_VERIFICATION_LIMIT = 3
        private const val FALLBACK_VERIFICATION_LIMIT = 4
        private const val MAX_PARALLEL_VERIFICATIONS = 3
    }

    private data class PackageRepositoryVerification(
        val matchedCandidate: GitHubPackageRepositoryScanCandidate? = null,
        val mismatched: Boolean = false,
        val failed: Boolean = false
    )

    private data class PackageRepositoryVerificationBatch(
        val matchedCandidates: List<GitHubPackageRepositoryScanCandidate> = emptyList(),
        val mismatchedCount: Int = 0,
        val failedCount: Int = 0
    ) {
        companion object {
            fun fromOutcomes(
                outcomes: List<PackageRepositoryVerification>
            ): PackageRepositoryVerificationBatch {
                return PackageRepositoryVerificationBatch(
                    matchedCandidates = outcomes.mapNotNull { it.matchedCandidate },
                    mismatchedCount = outcomes.count { it.mismatched },
                    failedCount = outcomes.count { it.failed }
                )
            }
        }
    }

    private data class PackageSearchContext(
        val appLabel: String,
        val normalizedPackageName: String,
        val normalizedAppLabel: String,
        val packageTail: String,
        val packageTailTokens: List<String>,
        val compactPackageTail: String,
        val labelTokens: List<String>,
        val queries: List<String>
    ) {
        companion object {
            fun create(
                packageName: String,
                appLabel: String
            ): PackageSearchContext {
                val normalizedPackageName = packageName.trim()
                val normalizedAppLabel = appLabel.trim()
                val labelTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(
                    normalizedAppLabel
                )
                val packageTail = normalizedPackageName
                    .substringAfterLast('.')
                    .lowercase(Locale.ROOT)
                return PackageSearchContext(
                    appLabel = normalizedAppLabel,
                    normalizedPackageName = normalizedPackageName.lowercase(Locale.ROOT),
                    normalizedAppLabel = normalizedAppLabel.lowercase(Locale.ROOT),
                    packageTail = packageTail,
                    packageTailTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(
                        packageTail
                    ),
                    compactPackageTail = GitHubRepositoryDiscoveryQueries.compactIdentifier(
                        packageTail
                    ),
                    labelTokens = labelTokens,
                    queries = GitHubPackageRepositoryQueries.forPackage(
                        packageName = normalizedPackageName,
                        appLabel = normalizedAppLabel,
                        labelTokens = labelTokens
                    )
                )
            }
        }
    }
}

internal object GitHubPackageRepositoryQueries {
    fun forPackage(
        packageName: String,
        appLabel: String
    ): List<String> {
        return forPackage(
            packageName = packageName,
            appLabel = appLabel,
            labelTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(appLabel)
        )
    }

    fun forPackage(
        packageName: String,
        appLabel: String,
        labelTokens: List<String>
    ): List<String> {
        val normalizedPackageName = packageName.trim()
        val normalizedAppLabel = appLabel.trim()
        val packageTail = normalizedPackageName.substringAfterLast('.').trim()
        val packageTokens = GitHubRepositoryDiscoveryQueries
            .normalizedTokens(normalizedPackageName)
            .filter { it.length >= 3 }
            .takeLast(4)
        val packageTailTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(packageTail)
        return buildList {
            if (normalizedPackageName.isNotBlank()) {
                add("$normalizedPackageName android in:description,readme")
            }
            if (normalizedAppLabel.isNotBlank() && packageTail.length >= 3) {
                add("$normalizedAppLabel $packageTail android in:name,description,readme")
            }
            if (normalizedAppLabel.isNotBlank() && packageTailTokens.size >= 2) {
                add(
                    "$normalizedAppLabel ${
                        packageTailTokens.take(4).joinToString(" ")
                    } android in:name,description,readme"
                )
            }
            if (packageTailTokens.size >= 2) {
                add("${packageTailTokens.take(4).joinToString(" ")} in:name,description,readme")
            }
            if (packageTokens.size >= 2) {
                add("${packageTokens.joinToString(" ")} android in:name,description,readme")
            }
            if (normalizedAppLabel.isNotBlank()) {
                add("$normalizedAppLabel android in:name,description,readme")
            }
            if (labelTokens.isNotEmpty()) {
                add("${labelTokens.take(3).joinToString(" ")} android")
            }
            if (packageTailTokens.size >= 2) {
                add(
                    "${
                        packageTailTokens.take(4).joinToString(" ")
                    } android in:name,description,readme"
                )
            }
            if (packageTail.length >= 3) {
                add("$packageTail android in:name,description,readme")
            }
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun forInstalledApp(app: InstalledAppItem): List<String> {
        return forPackage(
            packageName = app.packageName,
            appLabel = app.label
        )
    }
}
