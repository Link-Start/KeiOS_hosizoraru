package os.kei.ui.page.main.github.importer

import androidx.annotation.StringRes
import os.kei.R
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import java.net.URI

internal enum class StarImportViewFilter(@param:StringRes val labelRes: Int) {
    All(R.string.github_star_import_filter_all),
    Importable(R.string.github_star_import_filter_importable),
    Selected(R.string.github_star_import_filter_selected),
    Tracked(R.string.github_star_import_filter_tracked)
}

internal enum class StarImportQualityFilter(@param:StringRes val labelRes: Int) {
    LikelyAndroid(R.string.github_star_import_quality_likely_android),
    NeedsReview(R.string.github_star_import_quality_needs_review),
    OtherPlatform(R.string.github_star_import_quality_other_platform),
    ArchivedOrFork(R.string.github_star_import_quality_archived_or_fork);

    companion object {
        fun defaultVisible(): Set<StarImportQualityFilter> = setOf(LikelyAndroid, NeedsReview)
    }
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

internal fun GitHubRepositoryImportCandidate.matchesStarImportQualityFilter(
    filter: StarImportQualityFilter
): Boolean {
    return starImportQualityFilter() == filter
}

internal fun GitHubRepositoryImportCandidate.starImportQualityFilter(): StarImportQualityFilter {
    val repo = this.repository
    if (repo.archived || repo.fork) return StarImportQualityFilter.ArchivedOrFork
    val searchable = listOf(
        repo.fullName,
        repo.description,
        repo.language
    ).joinToString(" ").lowercase()
    val androidScore = androidStarImportSignals.sumOf { signal ->
        if (searchable.contains(signal)) 2 else 0
    } + androidLanguageScore(repo.language)
    val otherPlatformScore = otherPlatformStarImportSignals.sumOf { signal ->
        if (searchable.contains(signal)) 1 else 0
    }
    return when {
        androidScore >= 3 && otherPlatformScore <= 2 -> StarImportQualityFilter.LikelyAndroid
        androidScore >= 2 -> StarImportQualityFilter.NeedsReview
        otherPlatformScore >= 2 -> StarImportQualityFilter.OtherPlatform
        else -> StarImportQualityFilter.NeedsReview
    }
}

internal fun GitHubRepositoryImportCandidate.isDefaultSelectedStarImportCandidate(): Boolean {
    return !alreadyTracked && starImportQualityFilter() == StarImportQualityFilter.LikelyAndroid
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

private fun androidLanguageScore(language: String): Int {
    return when (language.trim().lowercase()) {
        "kotlin" -> 2
        "java" -> 1
        else -> 0
    }
}

private val androidStarImportSignals = listOf(
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

private val otherPlatformStarImportSignals = listOf(
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
