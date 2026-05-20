package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.core.io.SharedHttpClient
import os.kei.feature.github.domain.GitHubRepositoryDiscoverySource
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubStarListSummary
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class GitHubRepositoryDiscoveryRepository(
    apiToken: String = "",
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val webBaseUrl: String = DEFAULT_GITHUB_WEB_BASE_URL
) : GitHubRepositoryDiscoverySource {
    private val sanitizedToken = apiToken.trim()

    override val supportsParallelSearch: Boolean = true

    override fun fetchAuthenticatedStarredRepositories(
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>> = runCatching {
        check(sanitizedToken.isNotBlank()) { "GitHub token is required for /user/starred." }
        fetchStarredRepositories(
            firstPagePath = "/user/starred",
            sourceType = GitHubRepositoryDiscoverySourceType.AuthenticatedStars,
            limit = limit
        )
    }

    override fun fetchUserStarredRepositories(
        username: String,
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>> = runCatching {
        val normalizedUsername = username.trim()
        check(normalizedUsername.isNotBlank()) { "GitHub username is required." }
        fetchStarredRepositories(
            firstPagePath = "/users/${normalizedUsername.urlEncode()}/starred",
            sourceType = GitHubRepositoryDiscoverySourceType.PublicUserStars,
            limit = limit
        )
    }

    override fun searchRepositories(
        query: String,
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>> = runCatching {
        val normalizedQuery = query.trim()
        check(normalizedQuery.isNotBlank()) { "GitHub repository search query is required." }
        val perPage = limit.coerceIn(1, SEARCH_PAGE_SIZE)
        val url = buildUrl(
            "/search/repositories?q=${normalizedQuery.urlEncode()}&per_page=$perPage"
        )
        val response = fetch(url)
        val json = JSONObject(response.bodyText)
        json.optJSONArray("items")
            .mapNotNullRepositories()
            .mapNotNull { item ->
                item.toRepositoryCandidate(
                    sourceType = GitHubRepositoryDiscoverySourceType.RepositorySearch,
                    matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName
                )
            }
    }

    override fun fetchStarListRepositories(
        starListUrl: String,
        limit: Int
    ): Result<List<GitHubRepositoryCandidate>> = runCatching {
        val firstPath = normalizeStarListPath(starListUrl)
        val cappedLimit = limit.coerceIn(1, MAX_STAR_LIMIT)
        val candidates = mutableListOf<GitHubRepositoryCandidate>()
        var page = 1
        var hasNextPage = true
        while (candidates.size < cappedLimit && hasNextPage) {
            val separator = if ('?' in firstPath) '&' else '?'
            val response = fetchWeb(buildWebUrl("$firstPath${separator}page=$page"))
            val pageItems = parseStarListRepositoryCandidates(response.bodyText)
            candidates += pageItems
                .filterNot { candidate ->
                    candidates.any { existing ->
                        existing.owner.equals(candidate.owner, ignoreCase = true) &&
                                existing.repo.equals(candidate.repo, ignoreCase = true)
                    }
                }
                .take(cappedLimit - candidates.size)
            hasNextPage = response.hasNextPage && pageItems.isNotEmpty()
            page += 1
        }
        candidates
    }

    override fun fetchStarLists(starListsUrl: String): Result<List<GitHubStarListSummary>> =
        runCatching {
            val pathAndQuery = normalizeStarListsOverviewPath(starListsUrl)
            val response = fetchWeb(buildWebUrl(pathAndQuery))
            parseStarListSummaries(response.bodyText)
        }

    private fun fetchStarredRepositories(
        firstPagePath: String,
        sourceType: GitHubRepositoryDiscoverySourceType,
        limit: Int
    ): List<GitHubRepositoryCandidate> {
        val cappedLimit = limit.coerceIn(1, MAX_STAR_LIMIT)
        val perPage = minOf(STAR_PAGE_SIZE, cappedLimit)
        val candidates = mutableListOf<GitHubRepositoryCandidate>()
        var page = 1
        var hasNextPage = true
        while (candidates.size < cappedLimit && hasNextPage) {
            val url = buildUrl("$firstPagePath?per_page=$perPage&page=$page")
            val response = fetch(url)
            val pageItems = JSONArray(response.bodyText)
                .mapNotNullRepositories()
                .mapNotNull { item ->
                    item.toRepositoryCandidate(
                        sourceType = sourceType,
                        matchReason = GitHubRepositoryCandidateMatchReason.Starred
                    )
                }
            candidates += pageItems.take(cappedLimit - candidates.size)
            hasNextPage = response.hasNextPage && pageItems.isNotEmpty()
            page += 1
        }
        return candidates
    }

    private fun fetch(url: String): GitHubDiscoveryResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", GITHUB_USER_AGENT)
        if (sanitizedToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $sanitizedToken")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error(response.buildErrorMessage(bodyText))
            }
            return GitHubDiscoveryResponse(
                bodyText = bodyText,
                hasNextPage = response.header("Link").hasGitHubNextPage()
            )
        }
    }

    private fun fetchWeb(url: String): GitHubDiscoveryResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", GITHUB_USER_AGENT)
        if (sanitizedToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $sanitizedToken")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error(response.buildErrorMessage(bodyText))
            }
            return GitHubDiscoveryResponse(
                bodyText = bodyText,
                hasNextPage = response.header("Link").hasGitHubNextPage() ||
                        htmlNextPageRegex.containsMatchIn(bodyText)
            )
        }
    }

    private fun buildUrl(pathAndQuery: String): String {
        return "${apiBaseUrl.trimEnd('/')}/${pathAndQuery.trimStart('/')}"
    }

    private fun buildWebUrl(pathAndQuery: String): String {
        return "${webBaseUrl.trimEnd('/')}/${pathAndQuery.trimStart('/')}"
    }

    private fun normalizeStarListPath(starListUrl: String): String {
        val raw = starListUrl.trim().trimEnd('/')
        check(raw.isNotBlank()) { "GitHub star list URL is required." }
        val parsed = runCatching { URI(raw) }.getOrNull()
        val rawPath = when {
            parsed != null && parsed.host?.contains("github.com", ignoreCase = true) == true -> {
                parsed.rawPath.orEmpty()
            }

            raw.startsWith("/stars/") -> raw.substringBefore('?')
            raw.startsWith("stars/") -> "/$raw".substringBefore('?')
            else -> ""
        }.trim()
        val segments = rawPath.trim('/').split('/').filter { it.isNotBlank() }
        check(segments.size >= 2 && segments.first().equals("stars", ignoreCase = true)) {
            "Invalid GitHub star list URL."
        }
        check(
            segments.size == 2 || (segments.size >= 4 && segments[2].equals(
                "lists",
                ignoreCase = true
            ))
        ) {
            "Invalid GitHub star list URL."
        }
        return "/" + segments.take(if (segments.size >= 4) 4 else 2).joinToString("/")
    }

    private fun normalizeStarListsOverviewPath(starListsUrl: String): String {
        val raw = starListsUrl.trim().trimEnd('/')
        check(raw.isNotBlank()) { "GitHub stars URL is required." }
        val parsed = runCatching { URI(raw) }.getOrNull()
        if (parsed != null && parsed.host?.contains("github.com", ignoreCase = true) == true) {
            val path = parsed.rawPath.orEmpty().ifBlank { "/" }
            val query = parsed.rawQuery.orEmpty()
            val segments = path.trim('/').split('/').filter { it.isNotBlank() }
            if (segments.size >= 2 && segments.first().equals("stars", ignoreCase = true)) {
                return "/" + segments.take(2).joinToString("/")
            }
            if (segments.size == 1 && query.split('&').any {
                    it.equals("tab=stars", ignoreCase = true)
                }
            ) {
                return "/${segments.single()}?tab=stars"
            }
        }
        val rawPath = when {
            raw.startsWith("/stars/") -> raw.substringBefore('?')
            raw.startsWith("stars/") -> "/$raw".substringBefore('?')
            raw.matches(githubUsernameRegex) -> "/$raw?tab=stars"
            else -> ""
        }
        val segments = rawPath.trim('/').split('/').filter { it.isNotBlank() }
        check(
            segments.size >= 2 && segments.first().equals("stars", ignoreCase = true) ||
                    segments.size == 1 && segments.single().matches(githubUsernameRegex)
        ) {
            "Invalid GitHub stars URL."
        }
        return if (segments.size == 1) {
            "/${segments.single()}?tab=stars"
        } else {
            "/" + segments.take(2).joinToString("/")
        }
    }

    private fun parseStarListSummaries(html: String): List<GitHubStarListSummary> {
        return htmlStarListLinkRegex.findAll(html)
            .mapNotNull { match ->
                val href = match.groupValues.getOrNull(1).orEmpty().htmlUnescape()
                val body = match.groupValues.getOrNull(2).orEmpty()
                val path = normalizeStarListHrefPath(href) ?: return@mapNotNull null
                val text = body
                    .replace(htmlTagRegex, " ")
                    .htmlUnescape()
                    .replace(whitespaceRegex, " ")
                    .trim()
                val count = repositoryCountRegex.find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace(",", "")
                    ?.toIntOrNull()
                    ?: -1
                val name = text
                    .replace(repositoryCountRegex, "")
                    .trim()
                    .ifBlank { path.substringAfterLast('/').replace('-', ' ') }
                GitHubStarListSummary(
                    name = name,
                    repositoryCount = count,
                    url = "https://github.com$path"
                )
            }
            .distinctBy { it.url.lowercase() }
            .toList()
    }

    private fun normalizeStarListHrefPath(href: String): String? {
        val uri = runCatching { URI(href) }.getOrNull()
        val path = when {
            uri != null && uri.host?.contains("github.com", ignoreCase = true) == true -> uri.path
            href.startsWith("/") -> href
            else -> return null
        }.trim('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 4) return null
        if (!parts[0].equals("stars", ignoreCase = true)) return null
        if (!parts[2].equals("lists", ignoreCase = true)) return null
        val user = parts[1]
        val slug = parts[3]
        if (!githubUsernameRegex.matches(user) || !repositoryPathSegmentRegex.matches(slug)) {
            return null
        }
        return "/stars/$user/lists/$slug"
    }

    private fun parseStarListRepositoryCandidates(html: String): List<GitHubRepositoryCandidate> {
        return htmlRepositoryLinkRegex.findAll(html)
            .mapNotNull { match ->
                val href = match.groupValues.getOrNull(1).orEmpty().htmlUnescape()
                val segments = normalizeRepositoryHrefSegments(href) ?: return@mapNotNull null
                val owner = segments.first
                val repo = segments.second
                GitHubRepositoryCandidate(
                    owner = owner,
                    repo = repo,
                    repoUrl = "https://github.com/$owner/$repo",
                    sourceType = GitHubRepositoryDiscoverySourceType.StarList,
                    matchReason = GitHubRepositoryCandidateMatchReason.Starred
                )
            }
            .distinctBy { candidate -> "${candidate.owner.lowercase()}/${candidate.repo.lowercase()}" }
            .toList()
    }

    private fun normalizeRepositoryHrefSegments(href: String): Pair<String, String>? {
        val uri = runCatching { URI(href) }.getOrNull()
        val path = when {
            uri != null && uri.host?.contains("github.com", ignoreCase = true) == true -> uri.path
            href.startsWith("/") -> href
            else -> return null
        }.trim('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        if (!repositoryPathSegmentRegex.matches(owner) || !repositoryPathSegmentRegex.matches(repo)) {
            return null
        }
        if (owner.lowercase() in reservedRepositoryOwners) return null
        return owner to repo.removeSuffix(".git")
    }

    private fun Any?.toRepositoryCandidate(
        sourceType: GitHubRepositoryDiscoverySourceType,
        matchReason: GitHubRepositoryCandidateMatchReason
    ): GitHubRepositoryCandidate? {
        val json = this as? JSONObject ?: return null
        val fullName = json.optString("full_name").trim()
        val ownerFromFullName = fullName.substringBefore('/', missingDelimiterValue = "")
        val repoFromFullName = fullName.substringAfter('/', missingDelimiterValue = "")
        val owner = json.optJSONObject("owner")
            ?.optString("login")
            ?.trim()
            .orEmpty()
            .ifBlank { ownerFromFullName }
        val repo = json.optString("name").trim().ifBlank { repoFromFullName }
        if (owner.isBlank() || repo.isBlank()) return null
        return GitHubRepositoryCandidate(
            owner = owner,
            repo = repo,
            repoUrl = json.optString("html_url").trim()
                .ifBlank { "https://github.com/$owner/$repo" },
            description = json.optString("description").trim(),
            language = json.optString("language").trim(),
            starCount = json.optInt("stargazers_count", 0),
            forkCount = json.optInt("forks_count", 0),
            archived = json.optBoolean("archived", false),
            fork = json.optBoolean("fork", false),
            updatedAtMillis = json.optString("updated_at").toEpochMillisOrFallback(),
            sourceType = sourceType,
            matchReason = matchReason
        )
    }

    private fun String?.hasGitHubNextPage(): Boolean {
        val value = this.orEmpty()
        if (value.isBlank()) return false
        return value
            .split(',')
            .any { linkPart -> linkPart.contains("""rel="next"""", ignoreCase = true) }
    }

    private fun Response.buildErrorMessage(bodyText: String): String {
        val message = runCatching {
            JSONObject(bodyText).optString("message")
        }.getOrNull()
            .orEmpty()
            .ifBlank { bodyText.take(160) }
        return "GitHub discovery request failed: HTTP $code $message".trim()
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun String.toEpochMillisOrFallback(): Long {
        val value = trim()
        if (value.isBlank()) return -1L
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(-1L)
    }

    private fun JSONArray?.mapNotNullRepositories(): List<Any?> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.opt(index))
            }
        }
    }

    private data class GitHubDiscoveryResponse(
        val bodyText: String,
        val hasNextPage: Boolean
    )

    companion object {
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_GITHUB_WEB_BASE_URL = "https://github.com"
        private const val STAR_PAGE_SIZE = 100
        private const val SEARCH_PAGE_SIZE = 50
        private const val MAX_STAR_LIMIT = 1_000
        private val htmlRepositoryLinkRegex = Regex(
            """href=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val htmlNextPageRegex = Regex(
            """rel=["']next["']""",
            RegexOption.IGNORE_CASE
        )
        private val htmlStarListLinkRegex = Regex(
            """<a\b[^>]*href=["']([^"']*/stars/[^"']+/lists/[^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val htmlTagRegex = Regex("""<[^>]+>""")
        private val whitespaceRegex = Regex("""\s+""")
        private val repositoryCountRegex = Regex(
            """(\d[\d,]*)\s+repositories?""",
            RegexOption.IGNORE_CASE
        )
        private val repositoryPathSegmentRegex = Regex("""[A-Za-z0-9_.-]+""")
        private val githubUsernameRegex = Regex("""[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?""")
        private val reservedRepositoryOwners = setOf(
            "about",
            "apps",
            "collections",
            "contact",
            "events",
            "explore",
            "features",
            "github",
            "marketplace",
            "new",
            "notifications",
            "organizations",
            "pricing",
            "settings",
            "stars",
            "topics"
        )

        private val githubClient: OkHttpClient by lazy {
            SharedHttpClient.base.newBuilder()
                .callTimeout(18.seconds)
                .readTimeout(14.seconds)
                .build()
        }
    }
}

private fun String.htmlUnescape(): String {
    return replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
