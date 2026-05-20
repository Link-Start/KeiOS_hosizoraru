package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import os.kei.feature.github.model.GitHubApiAuthMode
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class GitHubActionsApiClient(
    private val apiToken: String,
    private val client: OkHttpClient,
    private val noRedirectClient: OkHttpClient,
    private val apiBaseUrl: String
) {
    private val sanitizedToken = apiToken.trim()

    val authMode: GitHubApiAuthMode
        get() = if (sanitizedToken.isBlank()) GitHubApiAuthMode.Guest else GitHubApiAuthMode.Token

    fun fetchJson(
        url: String,
        cacheTtlMillis: Long = 0L
    ): Result<String> = runCatching {
        val cacheKey = jsonResponseCacheKey(url)
        if (cacheTtlMillis > 0L) {
            cachedValue(jsonResponseCache[cacheKey], cacheTtlMillis)?.let { cached ->
                return@runCatching cached
            }
        }
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
                error(buildErrorMessage(response, bodyText))
            }
            if (cacheTtlMillis > 0L) {
                putCachedValue(jsonResponseCache, cacheKey, bodyText)
            }
            bodyText
        }
    }

    fun resolveRedirectDownloadUrl(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $sanitizedToken")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", GITHUB_USER_AGENT)
            .build()
        noRedirectClient.newCall(request).execute().use { response ->
            when {
                response.isRedirect -> response.header("Location").orEmpty().ifBlank {
                    error("GitHub Actions artifact download returned no redirect URL")
                }

                response.isSuccessful -> response.request.url.toString()
                else -> error(buildErrorMessage(response, response.body.string()))
            }
        }
    }

    private fun jsonResponseCacheKey(url: String): String {
        return listOf(
            authCachePartition(),
            apiBaseUrl.trimEnd('/'),
            url
        ).joinToString("|")
    }

    private fun authCachePartition(): String {
        return sanitizedToken
            .takeIf { it.isNotBlank() }
            ?.let { token -> "token:${stableHash(token)}" }
            ?: "guest"
    }

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun buildErrorMessage(response: Response, bodyText: String): String {
        val apiMessage = runCatching {
            JSONObject(bodyText).optString("message").trim()
        }.getOrDefault("")
        val rateRemaining = response.header("X-RateLimit-Remaining").orEmpty()
        val looksRateLimited = response.code == 429 ||
                rateRemaining == "0" ||
                apiMessage.contains("rate limit", ignoreCase = true)
        return when (response.code) {
            401 -> "GitHub Actions token is invalid or expired"
            403, 429 -> when {
                looksRateLimited && authMode == GitHubApiAuthMode.Guest ->
                    "GitHub Actions guest API is rate limited. Try again later or enter a token."

                looksRateLimited -> "GitHub Actions API is rate limited"
                else -> "GitHub Actions API access was denied${apiMessage.toErrorSuffix()}"
            }

            404 -> "The repository or GitHub Actions resource does not exist, or the current token lacks access"
            410 -> "The GitHub Actions artifact has expired"
            else -> "GitHub Actions request failed (HTTP ${response.code}${
                apiMessage.toErrorSuffix(
                    ", "
                )
            })"
        }
    }

    private fun String.toErrorSuffix(prefix: String = ": "): String {
        return takeIf { it.isNotBlank() }?.let { "$prefix$it" }.orEmpty()
    }

    private fun <T> cachedValue(entry: CachedValue<T>?, ttlMillis: Long): T? {
        if (entry == null) return null
        val ageMillis = System.currentTimeMillis() - entry.fetchedAtMillis
        return entry.value.takeIf { ageMillis in 0 until ttlMillis }
    }

    private fun <T> putCachedValue(
        cache: ConcurrentHashMap<String, CachedValue<T>>,
        key: String,
        value: T
    ) {
        if (cache.size >= ACTIONS_CACHE_MAX_ENTRIES) {
            cache.clear()
        }
        cache[key] = CachedValue(value, System.currentTimeMillis())
    }

    private data class CachedValue<T>(
        val value: T,
        val fetchedAtMillis: Long
    )

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
        const val ACTIONS_CACHE_MAX_ENTRIES = 160
        val jsonResponseCache = ConcurrentHashMap<String, CachedValue<String>>()
    }
}
