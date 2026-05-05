package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.domain.GitHubRepositoryDiscoverySource
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class GitHubRepositoryDiscoveryRepository(
    apiToken: String = "",
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val webBaseUrl: String = DEFAULT_GITHUB_WEB_BASE_URL
) : GitHubRepositoryDiscoverySource {
    private val sanitizedToken = apiToken.trim()

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
        private val repositoryPathSegmentRegex = Regex("""[A-Za-z0-9_.-]+""")
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
            OkHttpClient.Builder()
                .callTimeout(18, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(14, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .fastFallback(true)
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
