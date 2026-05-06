package os.kei.ui.page.main.github.importer

import androidx.annotation.StringRes
import os.kei.R
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import java.net.URI

internal enum class StarImportViewFilter(@param:StringRes val labelRes: Int) {
    All(R.string.github_star_import_filter_all),
    Importable(R.string.github_star_import_filter_importable),
    VerifiedApk(R.string.github_star_import_filter_verified_apk),
    Selected(R.string.github_star_import_filter_selected),
    Tracked(R.string.github_star_import_filter_tracked)
}

internal enum class StarImportConflictStrategy(@param:StringRes val labelRes: Int) {
    NewOnly(R.string.github_star_import_conflict_new_only),
    IncludeTracked(R.string.github_star_import_conflict_include_tracked)
}

internal enum class StarImportUiSource(
    @param:StringRes val labelRes: Int,
    @param:StringRes val requirementMessageRes: Int,
    @param:StringRes val sampleRes: Int
) {
    MyStars(
        labelRes = R.string.github_star_import_source_my_stars,
        requirementMessageRes = R.string.github_star_import_requirement_token,
        sampleRes = R.string.github_star_import_sample_token
    ),
    PublicUser(
        labelRes = R.string.github_star_import_source_user,
        requirementMessageRes = R.string.github_star_import_requirement_username,
        sampleRes = R.string.github_star_import_sample_username
    ),
    ListUrl(
        labelRes = R.string.github_star_import_source_list_url,
        requirementMessageRes = R.string.github_star_import_requirement_list_url,
        sampleRes = R.string.github_star_import_sample_list_url
    );

    fun isReady(
        token: String,
        username: String,
        listUrl: String
    ): Boolean {
        return when (this) {
            MyStars -> token.trim().isNotBlank()
            PublicUser -> username.isValidGitHubUsernameInput()
            ListUrl -> listUrl.trim().isValidGitHubStarListInput()
        }
    }

    fun toRequestSource(): GitHubStarredRepositoryImportSource {
        return when (this) {
            MyStars -> GitHubStarredRepositoryImportSource.AuthenticatedUser
            PublicUser -> GitHubStarredRepositoryImportSource.PublicUser
            ListUrl -> GitHubStarredRepositoryImportSource.StarListUrl
        }
    }
}

internal fun GitHubStarImportQuality.labelRes(): Int {
    return when (this) {
        GitHubStarImportQuality.LikelyAndroid -> R.string.github_star_import_quality_likely_android
        GitHubStarImportQuality.NeedsReview -> R.string.github_star_import_quality_needs_review
        GitHubStarImportQuality.OtherPlatform -> R.string.github_star_import_quality_other_platform
        GitHubStarImportQuality.ArchivedOrFork -> R.string.github_star_import_quality_archived_or_fork
    }
}

internal fun defaultVisibleStarImportQualities(): Set<GitHubStarImportQuality> {
    return setOf(GitHubStarImportQuality.LikelyAndroid, GitHubStarImportQuality.NeedsReview)
}

internal fun GitHubStarImportQuality.summaryRes(): Int {
    return when {
        this == GitHubStarImportQuality.LikelyAndroid ->
            R.string.github_star_import_quality_summary_likely_android

        this == GitHubStarImportQuality.NeedsReview ->
            R.string.github_star_import_quality_summary_needs_review

        this == GitHubStarImportQuality.OtherPlatform ->
            R.string.github_star_import_quality_summary_other_platform

        else ->
            R.string.github_star_import_quality_summary_archived_or_fork
    }
}

internal fun os.kei.feature.github.model.GitHubRepositoryImportCandidate.matchesStarImportQuality(
    quality: GitHubStarImportQuality
): Boolean {
    return GitHubStarImportClassifier.classify(this) == quality
}

internal data class StarImportApkVerificationUiState(
    val checking: Boolean = false,
    val verification: GitHubStarImportApkVerification? = null
)

internal data class StarImportCandidateListUiState(
    val searchedCandidates: List<GitHubRepositoryImportCandidate>,
    val filteredCandidates: List<GitHubRepositoryImportCandidate>,
    val qualityFilterCounts: Map<GitHubStarImportQuality, Int>,
    val selectedImportableCount: Int,
    val visibleImportableIds: Set<String>,
    val visibleRecommendedIds: Set<String>,
    val visibleVerifiedApkIds: Set<String>,
    val selectedCandidates: List<GitHubRepositoryImportCandidate>,
    val selectedVerificationTargets: List<GitHubRepositoryImportCandidate>,
    val visibleVerificationTargets: List<GitHubRepositoryImportCandidate>,
    val verifiedApkCount: Int,
    val checkingCount: Int
)

internal fun buildStarImportCandidateListUiState(
    candidates: List<GitHubRepositoryImportCandidate>,
    filterInput: String,
    viewFilter: StarImportViewFilter,
    qualityFilters: Set<GitHubStarImportQuality>,
    conflictStrategy: StarImportConflictStrategy,
    selectedIds: Set<String>,
    verificationStates: Map<String, StarImportApkVerificationUiState>
): StarImportCandidateListUiState {
    val query = filterInput.trim()
    val activeQualityFilters = qualityFilters.ifEmpty { GitHubStarImportQuality.entries.toSet() }
    val searchedCandidates = if (query.isBlank()) {
        candidates
    } else {
        candidates.filter { candidate -> candidate.matchesStarImportQuery(query) }
    }
    val searchedClassifiedCandidates = searchedCandidates.map { candidate ->
        StarImportClassifiedCandidate(
            candidate = candidate,
            quality = GitHubStarImportClassifier.classify(candidate)
        )
    }
    val qualityFilterCounts = searchedClassifiedCandidates
        .groupingBy { classified -> classified.quality }
        .eachCount()
        .let { counts ->
            GitHubStarImportQuality.entries.associateWith { quality -> counts[quality] ?: 0 }
        }
    val filteredClassifiedCandidates = searchedClassifiedCandidates.filter { classified ->
        val candidate = classified.candidate
        val quality = classified.quality
        val verification = verificationStates[candidate.trackedApp.id]?.verification
        val hasVerifiedApk =
            verification?.status == GitHubStarImportApkVerificationStatus.HasApk
        val statusMatches = when (viewFilter) {
            StarImportViewFilter.All -> true
            StarImportViewFilter.Importable -> !candidate.alreadyTracked
            StarImportViewFilter.VerifiedApk -> hasVerifiedApk
            StarImportViewFilter.Selected -> candidate.trackedApp.id in selectedIds
            StarImportViewFilter.Tracked -> candidate.alreadyTracked
        }
        statusMatches && quality in activeQualityFilters
    }
    val filteredCandidates = filteredClassifiedCandidates.map { classified ->
        classified.candidate
    }
    val trackedSelectable = conflictStrategy == StarImportConflictStrategy.IncludeTracked
    val selectedCandidates = candidates.filter { candidate ->
        (!candidate.alreadyTracked || trackedSelectable) && candidate.trackedApp.id in selectedIds
    }
    val selectedVerificationTargets = selectedCandidates.filter { candidate ->
        val state = verificationStates[candidate.trackedApp.id]
        state.needsStarImportApkVerification()
    }
    val visibleVerificationTargets = filteredCandidates
        .filter { candidate -> (!candidate.alreadyTracked || trackedSelectable) }
        .filter { candidate -> verificationStates[candidate.trackedApp.id].needsStarImportApkVerification() }
    val visibleVerifiedApkIds = filteredCandidates
        .asSequence()
        .filter { candidate -> !candidate.alreadyTracked || trackedSelectable }
        .filter { candidate ->
            verificationStates[candidate.trackedApp.id]
                ?.verification
                ?.status == GitHubStarImportApkVerificationStatus.HasApk
        }
        .map { candidate -> candidate.trackedApp.id }
        .toSet()
    val verifiedApkCount = candidates.count { candidate ->
        verificationStates[candidate.trackedApp.id]
            ?.verification
            ?.status == GitHubStarImportApkVerificationStatus.HasApk
    }
    val checkingCount = verificationStates.values.count { it.checking }
    return StarImportCandidateListUiState(
        searchedCandidates = searchedCandidates,
        filteredCandidates = filteredCandidates,
        qualityFilterCounts = qualityFilterCounts,
        selectedImportableCount = selectedCandidates.size,
        visibleImportableIds = filteredCandidates
            .asSequence()
            .filter { !it.alreadyTracked || trackedSelectable }
            .map { it.trackedApp.id }
            .toSet(),
        visibleRecommendedIds = filteredClassifiedCandidates
            .asSequence()
            .filter { classified ->
                (!classified.candidate.alreadyTracked || trackedSelectable) &&
                        classified.quality == GitHubStarImportQuality.LikelyAndroid
            }
            .map { classified -> classified.candidate.trackedApp.id }
            .toSet(),
        visibleVerifiedApkIds = visibleVerifiedApkIds,
        selectedCandidates = selectedCandidates,
        selectedVerificationTargets = selectedVerificationTargets,
        visibleVerificationTargets = visibleVerificationTargets,
        verifiedApkCount = verifiedApkCount,
        checkingCount = checkingCount
    )
}

private fun StarImportApkVerificationUiState?.needsStarImportApkVerification(): Boolean {
    return this?.checking != true &&
            (
                    this?.verification == null ||
                            this.verification.status == GitHubStarImportApkVerificationStatus.Failed
                    )
}

private data class StarImportClassifiedCandidate(
    val candidate: GitHubRepositoryImportCandidate,
    val quality: GitHubStarImportQuality
)

private fun GitHubRepositoryImportCandidate.matchesStarImportQuery(query: String): Boolean {
    return repository.fullName.contains(query, ignoreCase = true) ||
            repository.description.contains(query, ignoreCase = true) ||
            repository.language.contains(query, ignoreCase = true) ||
            repository.repo.contains(query, ignoreCase = true) ||
            repository.owner.contains(query, ignoreCase = true)
}

internal fun String.isValidGitHubUsernameInput(): Boolean {
    return toGitHubUsernameInput().isNotBlank()
}

internal fun String.toGitHubUsernameInput(): String {
    val raw = trim()
    if (raw.isBlank()) return ""
    val parsed = raw.parseGitHubUri()
    if (parsed != null) {
        val parts = parsed.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        return when {
            parts.size >= 2 && parts.first().equals("stars", ignoreCase = true) ->
                parts[1].validGitHubUsernameOrBlank()

            parts.size == 1 ->
                parts.single().validGitHubProfileUsernameOrBlank()

            else -> ""
        }
    }
    val pathParts = raw.substringBefore('?')
        .substringBefore('#')
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
    return when {
        pathParts.size >= 2 && pathParts.first().equals("stars", ignoreCase = true) ->
            pathParts[1].validGitHubUsernameOrBlank()

        pathParts.size == 1 && '/' !in raw ->
            pathParts.single().removePrefix("@").validGitHubProfileUsernameOrBlank()

        else -> ""
    }
}

internal fun String.isValidGitHubStarListInput(): Boolean {
    val value = trim().trimEnd('/')
    return value.startsWith("/stars/", ignoreCase = true) ||
            value.startsWith("stars/", ignoreCase = true) ||
            value.startsWith("https://github.com/stars/", ignoreCase = true) ||
            value.startsWith("http://github.com/stars/", ignoreCase = true) ||
            value.isGitHubProfileStarsUrl()
}

internal fun String.isDirectGitHubStarListInput(): Boolean {
    val path = gitHubStarsInputPath() ?: return false
    val parts = path.trim('/').split('/').filter { it.isNotBlank() }
    return parts.size >= 4 &&
            parts[0].equals("stars", ignoreCase = true) &&
            parts[2].equals("lists", ignoreCase = true)
}

internal fun String.isGitHubStarsOverviewInput(): Boolean {
    if (isDirectGitHubStarListInput()) return false
    val path = gitHubStarsInputPath() ?: return false
    val parts = path.trim('/').split('/').filter { it.isNotBlank() }
    return parts.size == 2 && parts[0].equals("stars", ignoreCase = true) ||
            isGitHubProfileStarsUrl()
}

internal fun String.toGitHubStarsRepositoryUrlInput(): String {
    val parsed = runCatching { URI(trim()) }.getOrNull() ?: return trim()
    if (parsed.host?.contains("github.com", ignoreCase = true) != true) return trim()
    val parts = parsed.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
    return if (parts.size == 1 && parsed.query.orEmpty().split('&').any {
            it.equals("tab=stars", ignoreCase = true)
        }
    ) {
        "/stars/${parts.single()}"
    } else {
        trim()
    }
}

private fun String.gitHubStarsInputPath(): String? {
    val raw = trim().trimEnd('/')
    val parsed = raw.parseGitHubUri()
    return when {
        parsed != null ->
            parsed.path.orEmpty()

        raw.startsWith("/stars/", ignoreCase = true) -> raw.substringBefore('?')
        raw.startsWith("stars/", ignoreCase = true) -> "/$raw".substringBefore('?')
        else -> null
    }
}

private fun String.isGitHubProfileStarsUrl(): Boolean {
    val parsed = trim().parseGitHubUri() ?: return false
    val parts = parsed.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
    return parts.size == 1 && parsed.query.orEmpty().split('&').any {
        it.equals("tab=stars", ignoreCase = true)
    }
}

private fun String.parseGitHubUri(): URI? {
    val raw = trim()
    val candidate = when {
        raw.startsWith("https://", ignoreCase = true) ||
                raw.startsWith("http://", ignoreCase = true) -> raw

        raw.startsWith("github.com/", ignoreCase = true) ||
                raw.startsWith("www.github.com/", ignoreCase = true) -> "https://$raw"

        else -> return null
    }
    val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
    val host = parsed.host.orEmpty().removePrefix("www.")
    return if (host.equals("github.com", ignoreCase = true)) parsed else null
}

private fun String.validGitHubProfileUsernameOrBlank(): String {
    val value = validGitHubUsernameOrBlank()
    return if (value.isNotBlank() && value.lowercase() !in reservedGitHubProfileSegments) {
        value
    } else {
        ""
    }
}

private fun String.validGitHubUsernameOrBlank(): String {
    val value = trim()
    return if (githubUsernameInputRegex.matches(value)) value else ""
}

internal fun Int.formatStarCount(): String {
    return when {
        this >= 1_000_000 -> String.format("%.1fm", this / 1_000_000.0).trimTrailingZeroDecimal()
        this >= 1_000 -> String.format("%.1fk", this / 1_000.0).trimTrailingZeroDecimal()
        else -> toString()
    }
}

private fun String.trimTrailingZeroDecimal(): String {
    return replace(".0", "")
}

private val githubUsernameInputRegex = Regex("""[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?""")

private val reservedGitHubProfileSegments = setOf(
    "about",
    "apps",
    "blog",
    "collections",
    "contact",
    "dashboard",
    "enterprise",
    "events",
    "explore",
    "features",
    "issues",
    "join",
    "login",
    "marketplace",
    "new",
    "notifications",
    "organizations",
    "orgs",
    "pricing",
    "pulls",
    "repositories",
    "search",
    "settings",
    "stars",
    "topics",
    "trending"
)
