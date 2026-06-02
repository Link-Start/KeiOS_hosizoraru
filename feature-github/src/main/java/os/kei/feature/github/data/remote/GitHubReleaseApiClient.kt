package os.kei.feature.github.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.GitHubExecution
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class GitHubReleaseApiClient(
    private val client: OkHttpClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork
) {
    suspend fun resolveApiAssetDownloadUrlAsync(
        apiAssetUrl: String,
        apiToken: String
    ): Result<String> = withContext(ioDispatcher) {
        cancellableResult {
            resolveApiAssetDownloadUrlInternal(apiAssetUrl, apiToken)
        }
    }

    private fun resolveApiAssetDownloadUrlInternal(
        apiAssetUrl: String,
        apiToken: String
    ): String {
        val request = Request.Builder()
            .url(apiAssetUrl)
            .get()
            .header("Accept", "application/octet-stream")
            .header("Authorization", "Bearer ${apiToken.trim()}")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", GITHUB_USER_AGENT)
            .header("Cache-Control", "no-store")
            .header("Pragma", "no-cache")
            .header("Connection", "close")
            .build()

        return client.newCall(request).execute().use { response ->
            val redirectedUrl = response.request.url.toString()
            when {
                response.isSuccessful && redirectedUrl.isNotBlank() -> redirectedUrl
                response.isRedirect -> response.header("Location").orEmpty().ifBlank {
                    error("GitHub API asset download returned no redirect URL")
                }

                else -> error(buildApiAssetErrorMessage(response))
            }
        }
    }

    suspend fun resolveShortCommitShaAsync(
        owner: String,
        repo: String,
        rawTag: String,
        apiToken: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            Result.success(resolveShortCommitShaInternal(owner, repo, rawTag, apiToken))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.success("")
        }
    }

    suspend fun fetchReleaseByTagAsync(
        owner: String,
        repo: String,
        rawTag: String,
        apiToken: String
    ): Result<JSONObject> = withContext(ioDispatcher) {
        cancellableResult {
            val encodedTag = urlEncode(rawTag)
            val url = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases/tags/$encodedTag"
            JSONObject(fetchJsonAsync(url, apiToken))
        }
    }

    suspend fun fetchReleaseListAsync(
        owner: String,
        repo: String,
        apiToken: String
    ): Result<JSONArray> = withContext(ioDispatcher) {
        cancellableResult {
            val url = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases?per_page=30"
            JSONArray(fetchJsonAsync(url, apiToken))
        }
    }

    suspend fun fetchLatestReleaseAsync(
        owner: String,
        repo: String,
        apiToken: String
    ): Result<JSONObject> = withContext(ioDispatcher) {
        cancellableResult {
            val url = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases/latest"
            JSONObject(fetchJsonAsync(url, apiToken, noStore = true))
        }
    }

    private suspend fun resolveShortCommitShaInternal(
        owner: String,
        repo: String,
        rawTag: String,
        apiToken: String
    ): String {
        val encodedTag = urlEncode(rawTag)
        val refUrl = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/git/ref/tags/$encodedTag"
        val refObject = JSONObject(fetchJsonAsync(refUrl, apiToken)).optJSONObject("object")
            ?: error("Git tag ref response is missing object")
        val refType = refObject.optString("type").trim()
        val refSha = refObject.optString("sha").trim()
        val commitSha = when {
            refType.equals("commit", ignoreCase = true) -> refSha
            refType.equals("tag", ignoreCase = true) && refSha.isNotBlank() -> {
                val tagUrl = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/git/tags/$refSha"
                val tagObject = JSONObject(fetchJsonAsync(tagUrl, apiToken)).optJSONObject("object")
                    ?: error("Annotated tag response is missing object")
                if (tagObject.optString("type").trim().equals("commit", ignoreCase = true)) {
                    tagObject.optString("sha").trim()
                } else {
                    ""
                }
            }

            else -> ""
        }
        return commitSha.take(7)
    }

    private suspend fun fetchJsonAsync(
        url: String,
        apiToken: String,
        noStore: Boolean = false
    ): String {
        val token = apiToken.trim()
        val result = GitHubExecution.retryOnce(
            shouldRetry = { error -> error is IOException }
        ) {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", GITHUB_USER_AGENT)
                .header("Connection", "close")
            if (noStore) {
                requestBuilder
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
            }
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body.string()
                if (!response.isSuccessful) {
                    val apiMessage = runCatching {
                        JSONObject(bodyText).optString("message").trim()
                    }.getOrDefault("")
                    error(buildJsonErrorMessage(response, token, apiMessage))
                }
                bodyText
            }
        }

        val lastError = result.exceptionOrNull()
        result.getOrNull()?.let { return it }
        val message = lastError?.message.orEmpty()
        if (message.contains("connection closed", ignoreCase = true)) {
            error("GitHub connection closed unexpectedly. Try again later.")
        }
        throw lastError ?: IllegalStateException("GitHub release request failed")
    }

    private suspend inline fun <T> cancellableResult(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private fun buildJsonErrorMessage(
        response: Response,
        token: String,
        apiMessage: String
    ): String {
        return when (response.code) {
            401 -> "GitHub API token is invalid or expired"
            403, 429 -> if (token.isBlank()) {
                "GitHub guest API is rate limited. Try again later or enter a token"
            } else {
                "GitHub API is rate limited"
            }

            404 -> "No release was found for this tag"
            else -> "GitHub release request failed (HTTP ${response.code}${
                apiMessage.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
            })"
        }
    }

    private fun buildApiAssetErrorMessage(response: Response): String {
        return when (response.code) {
            401 -> "GitHub API token is invalid or expired"
            403, 429 -> "GitHub API asset download is rate limited"
            404 -> "GitHub API asset URL has expired"
            else -> "GitHub API asset download failed (HTTP ${response.code})"
        }
    }

    private fun urlEncode(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
        const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
    }
}
