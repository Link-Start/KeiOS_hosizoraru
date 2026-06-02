package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import os.kei.core.io.SharedHttpClient
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import kotlin.time.Duration.Companion.seconds

class GitHubRepositoryProfileHttpClient(
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val htmlBaseUrl: String = DEFAULT_GITHUB_HTML_BASE_URL
) {
    fun fetchJson(url: String, apiToken: String): Result<String> = runCatching {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", GITHUB_USER_AGENT)
        val token = apiToken.trim()
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error(response.buildErrorMessage(bodyText))
            }
            bodyText
        }
    }

    fun fetchHtml(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", GITHUB_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error("GitHub repository page request failed (HTTP ${response.code})")
            }
            bodyText
        }
    }

    fun repositoryApiUrl(owner: String, repo: String): String {
        return apiUrl("/repos/$owner/$repo")
    }

    fun communityProfileUrl(owner: String, repo: String): String {
        return apiUrl("/repos/$owner/$repo/community/profile")
    }

    fun trafficViewsUrl(owner: String, repo: String): String {
        return apiUrl("/repos/$owner/$repo/traffic/views")
    }

    fun trafficClonesUrl(owner: String, repo: String): String {
        return apiUrl("/repos/$owner/$repo/traffic/clones")
    }

    fun compareUrl(
        upstreamOwner: String,
        upstreamRepo: String,
        baseBranch: String,
        headOwner: String,
        headBranch: String
    ): String {
        val base = baseBranch.encodeGitHubPathSegment()
        val head = "${headOwner.encodeGitHubPathSegment()}:${headBranch.encodeGitHubPathSegment()}"
        return apiUrl("/repos/$upstreamOwner/$upstreamRepo/compare/$base...$head")
    }

    fun dependabotAlertsUrl(owner: String, repo: String): String {
        return apiUrl("/repos/$owner/$repo/dependabot/alerts?state=open&per_page=100")
    }

    fun codeScanningAlertsUrl(owner: String, repo: String): String {
        return apiUrl("/repos/$owner/$repo/code-scanning/alerts?state=open&per_page=100")
    }

    fun htmlRepositoryUrl(owner: String, repo: String): String {
        return "${htmlBaseUrl.trimEnd('/')}/$owner/$repo"
    }

    fun ownerAvatarUrl(owner: String): String {
        return "${htmlBaseUrl.trimEnd('/')}/$owner.png?size=96"
    }

    private fun apiUrl(path: String): String {
        return "${apiBaseUrl.trimEnd('/')}${path.ensureLeadingSlash()}"
    }

    private fun Response.buildErrorMessage(bodyText: String): String {
        val apiMessage = runCatching {
            bodyText.parseJsonObjectOrNull()?.optString("message")?.trim().orEmpty()
        }.getOrDefault("")
        return when (code) {
            401 -> "GitHub API token is invalid or expired"
            403, 429 -> "GitHub API is rate limited"
            404 -> "GitHub repository profile resource was not found"
            else -> "GitHub profile request failed (HTTP $code${
                apiMessage.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
            })"
        }
    }

    private fun String.ensureLeadingSlash(): String {
        return if (startsWith('/')) this else "/$this"
    }

    internal companion object {
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_GITHUB_HTML_BASE_URL = "https://github.com"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"

        val githubClient: OkHttpClient by lazy {
            SharedHttpClient.base.newBuilder()
                .callTimeout(18.seconds)
                .connectTimeout(8.seconds)
                .readTimeout(14.seconds)
                .writeTimeout(8.seconds)
                .build()
        }
    }
}
