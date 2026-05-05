package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanResult
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
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
        val queries = GitHubPackageRepositoryQueries.forPackage(
            packageName = packageName,
            appLabel = appLabel
        )
        val candidateLimit = request.candidateLimit.coerceIn(1, MAX_CANDIDATE_LIMIT)
        val verificationLimit = request.verificationLimit.coerceIn(1, candidateLimit)
        val rawCandidates = queries.flatMap { query ->
            discoverySource.searchRepositories(query, candidateLimit).getOrThrow()
        }
        val rankedCandidates = rawCandidates
            .dedupeByRepo()
            .map { candidate ->
                val scored = candidate.withPackageSearchScore(
                    packageName = packageName,
                    appLabel = appLabel
                )
                scored.first to scored.second
            }
            .sortedWith(
                compareByDescending<Pair<GitHubRepositoryCandidate, Int>> { it.second }
                    .thenByDescending { it.first.starCount }
                    .thenBy { it.first.fullName.lowercase() }
            )
            .take(candidateLimit)

        var mismatchedCount = 0
        var failedCount = 0
        val matchedCandidates = buildList {
            rankedCandidates.take(verificationLimit).forEach { (candidate, score) ->
                val scanResult = packageNameScanner.scan(
                    GitHubApkPackageNameScanRequest(
                        repoUrl = candidate.repoUrl.ifBlank {
                            "https://github.com/${candidate.owner}/${candidate.repo}"
                        },
                        lookupConfig = request.lookupConfig
                    )
                )
                scanResult.fold(
                    onSuccess = { scanned ->
                        if (scanned.packageName.equals(packageName, ignoreCase = true)) {
                            add(
                                GitHubPackageRepositoryScanCandidate(
                                    repository = candidate,
                                    trackedApp = GitHubTrackedApp(
                                        repoUrl = candidate.repoUrl.ifBlank {
                                            "https://github.com/${candidate.owner}/${candidate.repo}"
                                        },
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
                            mismatchedCount += 1
                        }
                    },
                    onFailure = {
                        failedCount += 1
                    }
                )
            }
        }.sortedWith(
            compareByDescending<GitHubPackageRepositoryScanCandidate> { it.score }
                .thenByDescending { it.repository.starCount }
                .thenBy { it.repository.fullName.lowercase() }
        )

        GitHubPackageRepositoryScanResult(
            packageName = packageName,
            appLabel = appLabel,
            queryCount = queries.size,
            fetchedCandidateCount = rankedCandidates.size,
            scannedCandidateCount = rankedCandidates.take(verificationLimit).size,
            matchedCandidates = matchedCandidates,
            mismatchedCandidateCount = mismatchedCount,
            failedCandidateCount = failedCount
        )
    }

    private fun GitHubRepositoryCandidate.withPackageSearchScore(
        packageName: String,
        appLabel: String
    ): Pair<GitHubRepositoryCandidate, Int> {
        val labelTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(appLabel)
        val packageTail = packageName.substringAfterLast('.').lowercase()
        val searchable = listOf(fullName, repo, description).joinToString(" ").lowercase()
        val normalizedPackageName = packageName.lowercase()
        val hasPackageName = searchable.contains(normalizedPackageName)
        val hasFullLabel = appLabel.isNotBlank() && searchable.contains(appLabel.lowercase())
        val tokenHits = labelTokens.count { token -> searchable.contains(token) }
        val repoContainsPackageTail =
            packageTail.length >= 3 && repo.lowercase().contains(packageTail)
        val matchReason = when {
            hasPackageName -> GitHubRepositoryCandidateMatchReason.PackageName
            hasFullLabel || tokenHits >= 2 -> GitHubRepositoryCandidateMatchReason.AppLabel
            repoContainsPackageTail -> GitHubRepositoryCandidateMatchReason.RepositoryName
            else -> matchReason
        }
        val score = when (matchReason) {
            GitHubRepositoryCandidateMatchReason.PackageName -> 150
            GitHubRepositoryCandidateMatchReason.AppLabel -> 110
            GitHubRepositoryCandidateMatchReason.RepositoryName -> 80
            GitHubRepositoryCandidateMatchReason.Starred -> 40
        } +
                tokenHits * 10 +
                min(30, sqrt(starCount.toDouble()).toInt()) +
                if (archived) -35 else 0 +
                        if (fork) -12 else 0

        return copy(matchReason = matchReason) to score
    }

    private fun List<GitHubRepositoryCandidate>.dedupeByRepo(): List<GitHubRepositoryCandidate> {
        return distinctBy { "${it.owner.lowercase()}/${it.repo.lowercase()}" }
    }

    companion object {
        private const val MAX_CANDIDATE_LIMIT = 50
        private const val CONFIRMED_PACKAGE_SCORE = 1_000
    }
}

internal object GitHubPackageRepositoryQueries {
    fun forPackage(
        packageName: String,
        appLabel: String
    ): List<String> {
        val normalizedPackageName = packageName.trim()
        val packageTail = normalizedPackageName.substringAfterLast('.').trim()
        val labelTokens = GitHubRepositoryDiscoveryQueries.normalizedTokens(appLabel)
        return buildList {
            if (normalizedPackageName.isNotBlank()) {
                add("$normalizedPackageName android in:description,readme")
            }
            if (appLabel.isNotBlank()) {
                add("${appLabel.trim()} android in:name,description,readme")
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

    fun forInstalledApp(app: InstalledAppItem): List<String> {
        return forPackage(
            packageName = app.packageName,
            appLabel = app.label
        )
    }
}
