package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import os.kei.feature.github.model.GitHubRepositoryMetadata
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

internal class GitHubRepositoryMetadataRepository(
    apiToken: String = "",
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL
) {
    private val sanitizedToken = apiToken.trim()

    fun fetch(owner: String, repo: String): Result<GitHubRepositoryMetadata> = runCatching {
        val json = JSONObject(fetch(buildRepositoryUrl(owner, repo)))
        json.toRepositoryMetadata()
    }

    private fun fetch(url: String): String {
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
            return bodyText
        }
    }

    private fun JSONObject.toRepositoryMetadata(): GitHubRepositoryMetadata {
        val parent = optJSONObject("parent") ?: optJSONObject("source")
        return GitHubRepositoryMetadata(
            fullName = optString("full_name"),
            archived = optBoolean("archived", false),
            fork = optBoolean("fork", false),
            pushedAtMillis = optString("pushed_at").toEpochMillisOrDefault(),
            upstreamFullName = parent?.optString("full_name").orEmpty(),
            upstreamArchived = parent?.optBoolean("archived", false) ?: false,
            upstreamPushedAtMillis = parent?.optString("pushed_at").toEpochMillisOrDefault()
        )
    }

    private fun buildRepositoryUrl(owner: String, repo: String): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo"
    }

    private fun Response.buildErrorMessage(bodyText: String): String {
        val message = runCatching {
            JSONObject(bodyText).optString("message")
        }.getOrNull()
            .orEmpty()
            .ifBlank { bodyText.take(160) }
        return "GitHub repository metadata request failed: HTTP $code $message".trim()
    }

    private fun String?.toEpochMillisOrDefault(): Long {
        val raw = this?.takeIf { it.isNotBlank() } ?: return -1L
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(-1L)
    }

    companion object {
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"

        private val githubClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(12.seconds)
                .connectTimeout(6.seconds)
                .readTimeout(8.seconds)
                .writeTimeout(6.seconds)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
