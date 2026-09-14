package os.kei.feature.github.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import os.kei.core.io.SharedHttpClient
import os.kei.core.io.cancellableResult
import os.kei.core.io.executeCancellable
import os.kei.core.io.stringLimitedBlocking
import os.kei.core.json.jsonPrimitiveOrNull
import os.kei.core.json.optBoolean
import os.kei.core.json.optInt
import os.kei.core.json.optLong
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.engine.release.GitHubReleaseSelector
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubReleaseWindow
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.toReleaseVersionSignals
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private data class GitHubApiCachedValue<T>(
    val value: T,
    val timestamp: Long
)

class GitHubApiTokenReleaseStrategy(
    private val apiToken: String = "",
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL
) : GitHubReleaseLookupStrategy {
    override val id: String = GitHubLookupStrategyOption.GitHubApiToken.storageId

    private val sanitizedToken: String = apiToken.trim()

    val authMode: GitHubApiAuthMode
        get() = if (sanitizedToken.isBlank()) GitHubApiAuthMode.Guest else GitHubApiAuthMode.Token

    override suspend fun loadSnapshot(owner: String, repo: String): Result<GitHubRepositoryReleaseSnapshot> {
        return loadSnapshotTrace(owner, repo).result
    }

    suspend fun loadSnapshotTrace(owner: String, repo: String): GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot> {
        val startedAt = System.currentTimeMillis()
        val entriesTrace = fetchReleaseEntriesTrace(owner, repo)
        val window = entriesTrace.result.getOrElse { error ->
            return GitHubStrategyLoadTrace(
                result = Result.failure(error),
                fromCache = entriesTrace.fromCache,
                elapsedMs = System.currentTimeMillis() - startedAt,
                authMode = authMode
            )
        }
        val entries = window.entries
        // Every rule that turns this list into "the stable" and "the pre-release" lives in the
        // selector, including the one deciding whether the second request below is worth making.
        // This method's remaining job is to make it, or not, and to hand back the answer.
        val plan = GitHubReleaseSelector.plan(entries, windowWasFull = window.windowWasFull)
        val latestStableTrace = when {
            plan.shouldConsultForgeLatest -> fetchLatestStableSignalTrace(owner, repo)
            else -> null
        }
        val result = runCatching {
            val selection = plan.resolve(
                authoritativeStable = latestStableTrace?.result?.getOrNull(),
            )
            val latestStableSignal = selection.stable
                ?: selection.preRelease
                ?: error("no release entries")
            val updatedAt = entries.maxOfOrNull { it.updatedAtMillis ?: Long.MIN_VALUE }
                ?.takeIf { it > Long.MIN_VALUE }

            GitHubRepositoryReleaseSnapshot(
                strategyId = id,
                feed = GitHubAtomFeed(
                    title = "$owner/$repo releases",
                    feedUrl = buildApiUrl(owner, repo),
                    updatedAtMillis = updatedAt,
                    entries = entries
                ),
                latestStable = latestStableSignal,
                hasStableRelease = selection.hasStableRelease,
                latestPreRelease = selection.preRelease,
                selection = selection
            )
        }
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = entriesTrace.fromCache && latestStableTrace?.fromCache != false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            authMode = authMode
        )
    }

    suspend fun fetchReleaseEntries(owner: String, repo: String, limit: Int = 30): Result<List<GitHubAtomReleaseEntry>> {
        return fetchReleaseEntriesTrace(owner, repo, limit).result.map { it.entries }
    }

    internal suspend fun fetchReleaseEntriesTrace(
        owner: String,
        repo: String,
        limit: Int = 30
    ): GitHubStrategyLoadTrace<GitHubReleaseWindow> {
        val startedAt = System.currentTimeMillis()
        val key = cacheKey(owner, repo)
        val now = System.currentTimeMillis()
        releaseCache[key]?.takeIf { now - it.timestamp < CACHE_TTL_MS }?.let {
            return GitHubStrategyLoadTrace(
                result = it.value,
                fromCache = true,
                elapsedMs = System.currentTimeMillis() - startedAt,
                authMode = authMode
            )
        }

        val result = fetch(buildApiUrl(owner, repo, limit)).map { body ->
            parseReleaseWindow(
                json = body,
                owner = owner,
                repo = repo,
                limit = limit
            )
        }
        if (result.isSuccess) {
            releaseCache[key] = GitHubApiCachedValue(result, now)
        } else {
            releaseCache.remove(key)
        }
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            authMode = authMode
        )
    }

    override fun clearCaches() {
        clearSharedCaches()
    }

    private suspend fun fetchLatestStableSignalTrace(
        owner: String,
        repo: String
    ): GitHubStrategyLoadTrace<GitHubReleaseVersionSignals> {
        val startedAt = System.currentTimeMillis()
        val key = cacheKey(owner, repo) + "|latest"
        val now = System.currentTimeMillis()
        stableCache[key]?.takeIf { now - it.timestamp < CACHE_TTL_MS }?.let {
            return GitHubStrategyLoadTrace(
                result = it.value,
                fromCache = true,
                elapsedMs = System.currentTimeMillis() - startedAt,
                authMode = authMode
            )
        }

        val result = fetch(buildLatestApiUrl(owner, repo)).mapCatching { body ->
            val release = body.parseJsonObjectOrNull() ?: error("latest release response is not a JSON object")
            val entry = parseReleaseEntry(
                release = release,
                owner = owner,
                repo = repo
            ) ?: error("latest release missing")
            check(!entry.isLikelyPreRelease) { "latest release is not stable" }
            entry.toReleaseVersionSignals()
        }
        if (result.isSuccess) {
            stableCache[key] = GitHubApiCachedValue(result, now)
        } else {
            stableCache.remove(key)
        }
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            authMode = authMode
        )
    }

    suspend fun checkCredential(): Result<GitHubApiCredentialStatus> {
        return checkCredentialTrace().result
    }

    suspend fun checkCredentialTrace(): GitHubStrategyLoadTrace<GitHubApiCredentialStatus> {
        val startedAt = System.currentTimeMillis()
        val key = "credential|${cacheKey(owner = "_", repo = "_")}"
        val now = System.currentTimeMillis()
        credentialCache[key]?.takeIf { now - it.timestamp < CACHE_TTL_MS }?.let {
            return GitHubStrategyLoadTrace(
                result = it.value,
                fromCache = true,
                elapsedMs = System.currentTimeMillis() - startedAt,
                authMode = authMode
            )
        }

        val result = fetch(buildRateLimitUrl()).map { body ->
            parseCredentialStatus(body)
        }
        if (result.isSuccess) {
            credentialCache[key] = GitHubApiCachedValue(result, now)
        } else {
            credentialCache.remove(key)
        }
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            authMode = authMode
        )
    }

    private suspend fun fetch(url: String): Result<String> = cancellableResult {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", GITHUB_USER_AGENT)
        if (sanitizedToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $sanitizedToken")
        }
        val request = requestBuilder.build()

        client.executeCancellable(request) { response ->
            val bodyText = response.body.stringLimitedBlocking(MAX_RELEASE_API_RESPONSE_BYTES)
            if (!response.isSuccessful) {
                error(buildErrorMessage(response, bodyText))
            }
            bodyText
        }
    }

    internal fun parseReleaseEntries(
        json: String,
        owner: String,
        repo: String
    ): List<GitHubAtomReleaseEntry> {
        val array = json.parseJsonArrayOrNull() ?: throw IllegalArgumentException("release entries payload is not a JSON array")
        val entries = buildList {
            for (element in array) {
                val release = element as? JsonObject ?: continue
                val entry = parseReleaseEntry(release, owner, repo) ?: continue
                add(entry)
            }
        }

        return entries.sortedByDescending { entry ->
            entry.updatedAtMillis ?: Long.MIN_VALUE
        }
    }

    /**
     * One page of releases, as far as the parser got.
     *
     * [GitHubReleaseWindow.windowWasFull] is counted from what GitHub returned, not from what
     * survived parsing: drafts and unversioned pre-releases are dropped on the way through, so an
     * entry count of 27 out of a 30-release page still means the history was cut off.
     */
    internal fun parseReleaseWindow(
        json: String,
        owner: String,
        repo: String,
        limit: Int = DEFAULT_RELEASE_PAGE_SIZE
    ): GitHubReleaseWindow {
        val array = json.parseJsonArrayOrNull()
            ?: throw IllegalArgumentException("release entries payload is not a JSON array")
        return GitHubReleaseWindow(
            entries = parseReleaseEntries(json = json, owner = owner, repo = repo).take(limit),
            windowWasFull = array.size >= limit
        )
    }

    private fun buildApiUrl(
        owner: String,
        repo: String,
        limit: Int = DEFAULT_RELEASE_PAGE_SIZE
    ): String {
        // Asked for exactly as many as will be read. The page size used to be pinned at 30 while the
        // caller's limit was applied afterwards, so a caller asking for more silently got 30.
        val perPage = limit.coerceIn(1, MAX_RELEASE_PAGE_SIZE)
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases?per_page=$perPage"
    }

    private fun buildLatestApiUrl(owner: String, repo: String): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases/latest"
    }

    private fun buildRateLimitUrl(): String {
        return "${apiBaseUrl.trimEnd('/')}/rate_limit"
    }

    private fun parseReleaseEntry(
        release: JsonObject,
        owner: String,
        repo: String
    ): GitHubAtomReleaseEntry? {
        if (release.optBoolean("draft", false)) return null

        val rawTag = release.optString("tag_name").trim()
        val name = release.optString("name").trim().ifBlank { rawTag }
        if (rawTag.isBlank() && name.isBlank()) return null

        val htmlUrl = release.optString("html_url").trim().ifBlank {
            GitHubVersionUtils.buildReleaseUrl(owner, repo)
        }
        val releaseId = release.optElementString("id")
        val body = release.optString("body")
        val contentPreview = GitHubAtomHeuristics.buildContentPreview(body)
        val prereleaseFlag = release.optBoolean("prerelease", false)
        val heuristicsChannel = GitHubAtomHeuristics.detectReleaseChannel(
            tag = rawTag,
            title = name,
            contentPreview = if (prereleaseFlag) contentPreview else ""
        )
        val channel = when {
            prereleaseFlag && !heuristicsChannel.isPreRelease -> GitHubReleaseChannel.PREVIEW
            prereleaseFlag -> heuristicsChannel
            else -> GitHubReleaseChannel.STABLE
        }
        val author = release.optObject("author")
        val authorName = author?.optString("login").orEmpty().trim()
        val authorAvatarUrl = author?.optString("avatar_url").orEmpty().trim()
        val publishedAtMillis = release.optString("published_at").parseIsoInstantOrNull()
            ?: release.optString("created_at").parseIsoInstantOrNull()
        // Counted rather than classified. "No assets at all" is a fact about the release; "no asset
        // that looks installable" is a guess about packaging, and guessing wrong here hides a real
        // update. GitHub's source tarballs are not assets, so an empty list means the maintainer
        // attached nothing.
        val assets = release["assets"] as? JsonArray
        val assetCount = assets?.size ?: 0
        // The newest asset, not the release. A rolling CI tag keeps its publish date and replaces
        // what is inside it, so this is the only field that moves when such a line is still alive.
        val assetsUpdatedAtMillis = assets
            ?.mapNotNull { asset ->
                (asset as? JsonObject)?.optString("updated_at")?.parseIsoInstantOrNull()
            }
            ?.maxOrNull()
        val versionCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to rawTag,
            GitHubVersionCandidateSource.Title to name,
            GitHubVersionCandidateSource.Link to htmlUrl,
            GitHubVersionCandidateSource.Id to releaseId,
            GitHubVersionCandidateSource.Content to contentPreview
        )
        if (prereleaseFlag && !GitHubVersionUtils.hasMeaningfulPreReleaseVersionCandidates(versionCandidates, GitHubVersionCandidateSource.Link.priority)) {
            return null
        }

        return GitHubAtomReleaseEntry(
            entryId = release.optString("node_id").ifBlank { releaseId },
            tag = rawTag.ifBlank { name },
            title = name,
            link = htmlUrl,
            updatedAtMillis = publishedAtMillis,
            contentHtml = "",
            contentText = body,
            authorName = authorName,
            authorAvatarUrl = authorAvatarUrl,
            versionCandidates = versionCandidates,
            channel = channel,
            isLikelyPreRelease = prereleaseFlag,
            hasDownloadableAsset = assetCount > 0,
            assetsUpdatedAtMillis = assetsUpdatedAtMillis
        )
    }

    private fun cacheKey(owner: String, repo: String): String {
        val authKey = when (authMode) {
            GitHubApiAuthMode.Guest -> "guest"
            GitHubApiAuthMode.Token -> sanitizedToken.hashCode().toString()
        }
        return "$authKey|$owner/$repo"
    }

    private fun buildErrorMessage(response: Response, bodyText: String): String {
        val code = response.code
        val apiMessage = runCatching {
            bodyText.parseJsonObjectOrNull()?.optString("message")?.trim().orEmpty()
        }.getOrDefault("")
        val rateRemaining = response.header("X-RateLimit-Remaining").orEmpty()
        val rateResetEpochSeconds = response.header("X-RateLimit-Reset").orEmpty().toLongOrNull()
        val rateResetSuffix = rateResetEpochSeconds
            ?.let { resetEpochSeconds ->
                val waitMinutes = ((resetEpochSeconds * 1000L) - System.currentTimeMillis())
                    .coerceAtLeast(0L) / 60_000L
                if (waitMinutes > 0) ", resets in about $waitMinutes min" else ""
            }
            .orEmpty()
        val looksRateLimited = code == 429 ||
            rateRemaining == "0" ||
            apiMessage.contains("rate limit", ignoreCase = true)
        return when (code) {
            401 -> "GitHub API token is invalid or expired"
            403, 429 -> when {
                looksRateLimited && authMode == GitHubApiAuthMode.Guest ->
                    "GitHub guest API is rate limited. Try again later or enter a token$rateResetSuffix"
                looksRateLimited ->
                    "GitHub API is rate limited$rateResetSuffix"
                else ->
                    "GitHub API access was denied${apiMessage.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
            }
            404 -> "Repository does not exist, or the current token lacks repository access"
            else -> "GitHub API request failed (HTTP $code${apiMessage.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""})"
        }
    }

    private fun JsonObject.optElementString(key: String): String {
        val value = this[key] ?: return ""
        return value.jsonPrimitiveOrNull()?.contentOrNull?.trim()
            ?: value.toString().trim()
    }

    internal fun parseCredentialStatus(json: String): GitHubApiCredentialStatus {
        val root = json.parseJsonObjectOrNull() ?: throw IllegalArgumentException("rate limit payload is not a JSON object")
        val core = root.optObject("resources")
            ?.optObject("core")
        val limit = core?.optInt("limit", 0) ?: 0
        val remaining = core?.optInt("remaining", 0) ?: 0
        val used = core?.optInt("used", 0) ?: 0
        val resetAtMillis = core?.optLong("reset", 0L)
            ?.takeIf { it > 0L }
            ?.times(1000L)
        return GitHubApiCredentialStatus(
            authMode = authMode,
            coreLimit = limit,
            coreRemaining = remaining,
            coreUsed = used,
            resetAtMillis = resetAtMillis
        )
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching {
            if (isBlank()) null else Instant.parse(this).toEpochMilli()
        }.getOrNull()
    }

    companion object {
        private const val CACHE_TTL_MS = 90_000L
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
        private const val MAX_RELEASE_API_RESPONSE_BYTES = 12L * 1024L * 1024L
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_RELEASE_PAGE_SIZE = 30
        /** GitHub's own ceiling for `per_page`. */
        private const val MAX_RELEASE_PAGE_SIZE = 100

        private val releaseCache =
            ConcurrentHashMap<String, GitHubApiCachedValue<Result<GitHubReleaseWindow>>>()
        private val stableCache =
            ConcurrentHashMap<String, GitHubApiCachedValue<Result<GitHubReleaseVersionSignals>>>()
        private val credentialCache =
            ConcurrentHashMap<String, GitHubApiCachedValue<Result<GitHubApiCredentialStatus>>>()

        private val githubClient: OkHttpClient by lazy {
            SharedHttpClient.base.newBuilder()
                .callTimeout(18.seconds)
                .readTimeout(14.seconds)
                .build()
        }

        fun clearSharedCaches() {
            releaseCache.clear()
            stableCache.clear()
            credentialCache.clear()
        }
    }
}
