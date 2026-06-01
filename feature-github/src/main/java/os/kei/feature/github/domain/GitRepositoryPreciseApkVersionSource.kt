package os.kei.feature.github.domain

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.core.io.SharedHttpClient
import os.kei.feature.github.GitHubSingleFlight
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetJsonMapper
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.GitRepositoryTrackIdentity
import os.kei.feature.github.model.githubAssetSourceSignature
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

internal class GitRepositoryPreciseApkVersionSource(
    private val identity: GitRepositoryTrackIdentity,
    private val client: OkHttpClient = defaultClient,
    private val gitLabApiBaseUrl: String = "https://${identity.host}/api/v4",
    private val giteeApiBaseUrl: String = "https://gitee.com/api/v5",
    private val apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository()
) : GitHubPreciseApkVersionSource {
    override suspend fun loadReleaseAssetBundle(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle> {
        val normalizedTag = rawTag.trim()
        if (normalizedTag.isBlank()) {
            return Result.failure(IllegalArgumentException("missing Git release tag"))
        }
        val cacheKey = buildCacheKey(
            rawTag = normalizedTag,
            releaseUrl = releaseUrl,
            lookupConfig = lookupConfig
        )
        val now = System.currentTimeMillis()
        completedBundleCache[cacheKey]
            ?.takeIf { now - it.timestamp < CACHE_TTL_MS }
            ?.let { cached -> return Result.success(cached.bundle) }

        return inFlightBundleFetches.run(cacheKey) {
            completedBundleCache[cacheKey]
                ?.takeIf { now - it.timestamp < CACHE_TTL_MS }
                ?.let { cached -> return@run Result.success(cached.bundle) }

            val result = when (identity.platform) {
                GitRepositoryPlatform.Gitee -> {
                    loadGiteeReleaseBundle(
                        rawTag = normalizedTag,
                        releaseUrl = releaseUrl,
                        lookupConfig = lookupConfig
                    )
                }

                GitRepositoryPlatform.GitLab -> {
                    loadGitLabReleaseBundle(
                        rawTag = normalizedTag,
                        releaseUrl = releaseUrl,
                        lookupConfig = lookupConfig
                    )
                }

                GitRepositoryPlatform.GitHub,
                GitRepositoryPlatform.Generic -> {
                    Result.failure(IllegalStateException("Git release assets are unavailable"))
                }
            }
            result.onSuccess { bundle ->
                if (completedBundleCache.size >= MAX_COMPLETED_BUNDLE_CACHE_SIZE) {
                    completedBundleCache.clear()
                }
                completedBundleCache[cacheKey] =
                    GitRepositoryCachedAssetBundle(bundle = bundle, timestamp = System.currentTimeMillis())
            }
        }
    }

    override suspend fun inspectApk(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> {
        return apkInfoRepository.inspect(asset = asset, lookupConfig = lookupConfig)
    }

    private fun loadGiteeReleaseBundle(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle> {
        val detailUrl = buildGiteeReleaseDetailUrl(rawTag)
        val release = fetchJsonObject(detailUrl).recoverCatching {
            fetchGiteeReleaseList()
                .getOrThrow()
                .firstReleaseMatchingTag(rawTag)
                ?: error("Gitee release was not found for tag $rawTag")
        }.getOrElse { error ->
            return Result.failure(error)
        }
        return runCatching {
            GitHubReleaseAssetJsonMapper.parseReleaseBundle(release)
                .copy(
                    htmlUrl = release.optString("html_url").trim()
                        .ifBlank { releaseUrl.trim() }
                        .ifBlank { buildGiteeReleasePageUrl(rawTag) },
                    fetchSource = GITEE_FETCH_SOURCE,
                    sourceConfigSignature = lookupConfig.githubAssetSourceSignature()
                )
        }
    }

    private fun loadGitLabReleaseBundle(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle> {
        val release = fetchJsonObject(buildGitLabReleaseDetailUrl(rawTag)).getOrElse { error ->
            return Result.failure(error)
        }
        return runCatching {
            parseGitLabReleaseBundle(
                release = release,
                fallbackTag = rawTag,
                fallbackReleaseUrl = releaseUrl,
                sourceConfigSignature = lookupConfig.githubAssetSourceSignature()
            )
        }
    }

    private fun parseGitLabReleaseBundle(
        release: JSONObject,
        fallbackTag: String,
        fallbackReleaseUrl: String,
        sourceConfigSignature: String
    ): GitHubReleaseAssetBundle {
        val tagName = release.optString("tag_name").trim().ifBlank { fallbackTag }
        val releaseName = release.optString("name").trim().ifBlank { tagName }
        val htmlUrl = release.optJSONObject("_links")
            ?.optString("self")
            .orEmpty()
            .trim()
            .ifBlank { release.optString("url").trim() }
            .ifBlank { fallbackReleaseUrl.trim() }
            .ifBlank { buildGitLabReleasePageUrl(tagName) }
        val links = release.optJSONObject("assets")
            ?.optJSONArray("links")
            ?: JSONArray()
        val assets = buildList {
            for (index in 0 until links.length()) {
                val link = links.optJSONObject(index) ?: continue
                val downloadUrl = link.optString("direct_asset_url").trim()
                    .ifBlank { link.optString("url").trim() }
                    .let(::resolvePlatformUrl)
                if (downloadUrl.isBlank()) continue
                val name = link.optString("name").trim()
                    .ifBlank { fileNameFromUrl(downloadUrl) }
                if (name.isBlank()) continue
                add(
                    GitHubReleaseAssetFile(
                        name = name,
                        downloadUrl = downloadUrl,
                        apiAssetUrl = link.optString("url").trim().let(::resolvePlatformUrl),
                        sizeBytes = 0L,
                        downloadCount = 0,
                        contentType = link.optString("link_type").trim(),
                        updatedAtMillis = release.optString("released_at").parseIsoInstantOrNull()
                            ?: release.optString("created_at").parseIsoInstantOrNull(),
                        digest = link.optString("filepath").trim()
                    )
                )
            }
        }
        return GitHubReleaseAssetBundle(
            releaseName = releaseName,
            tagName = tagName,
            htmlUrl = htmlUrl,
            releaseUpdatedAtMillis = release.optString("released_at").parseIsoInstantOrNull()
                ?: release.optString("created_at").parseIsoInstantOrNull(),
            releaseNotesBody = release.optString("description").trim(),
            assets = assets,
            shortCommitSha = release.optJSONObject("commit")?.optString("short_id").orEmpty(),
            fetchSource = GITLAB_FETCH_SOURCE,
            sourceConfigSignature = sourceConfigSignature
        )
    }

    private fun fetchGiteeReleaseList(): Result<JSONArray> {
        val ownerPath = identity.namespace
            .split('/')
            .joinToString("/") { segment -> segment.urlEncodePathSegment() }
        val repoPath = identity.repo.urlEncodePathSegment()
        val url =
            "${giteeApiBaseUrl.trimEnd('/')}/repos/$ownerPath/$repoPath/releases?per_page=100&direction=desc"
        return fetchJsonArray(url)
    }

    private fun buildGiteeReleaseDetailUrl(rawTag: String): String {
        val ownerPath = identity.namespace
            .split('/')
            .joinToString("/") { segment -> segment.urlEncodePathSegment() }
        val repoPath = identity.repo.urlEncodePathSegment()
        return "${giteeApiBaseUrl.trimEnd('/')}/repos/$ownerPath/$repoPath/releases/tags/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGitLabReleaseDetailUrl(rawTag: String): String {
        val projectId = urlEncode("${identity.namespace}/${identity.repo}")
        return "${gitLabApiBaseUrl.trimEnd('/')}/projects/$projectId/releases/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGiteeReleasePageUrl(rawTag: String): String {
        return "https://gitee.com/${identity.namespace}/${identity.repo}/releases/tag/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGitLabReleasePageUrl(rawTag: String): String {
        return "https://${identity.host}/${identity.namespace}/${identity.repo}/-/releases/${rawTag.urlEncodePathSegment()}"
    }

    private fun fetchJsonObject(url: String): Result<JSONObject> {
        return fetchText(url).mapCatching { body -> JSONObject(body) }
    }

    private fun fetchJsonArray(url: String): Result<JSONArray> {
        return fetchText(url).mapCatching { body -> JSONArray(body) }
    }

    private fun fetchText(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json,*/*")
            .header("User-Agent", GIT_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error(buildErrorMessage(response, bodyText))
            }
            bodyText
        }
    }

    private fun buildErrorMessage(response: Response, bodyText: String): String {
        val apiMessage = runCatching {
            JSONObject(bodyText).optString("message").trim()
        }.getOrDefault("")
        return "Git release asset request failed (HTTP ${response.code}${apiMessage.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""})"
    }

    private fun JSONArray.firstReleaseMatchingTag(rawTag: String): JSONObject? {
        val normalized = rawTag.normalizedReleaseTag()
        for (index in 0 until length()) {
            val release = optJSONObject(index) ?: continue
            val tag = release.optString("tag_name").trim()
                .ifBlank { release.optString("tag").trim() }
            if (tag.equals(rawTag, ignoreCase = true) || tag.normalizedReleaseTag() == normalized) {
                return release
            }
        }
        return null
    }

    private fun resolvePlatformUrl(rawUrl: String): String {
        val value = rawUrl.trim()
        if (value.isBlank()) return ""
        if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) {
            return value
        }
        return if (value.startsWith('/')) {
            "https://${identity.host}$value"
        } else {
            value
        }
    }

    private fun fileNameFromUrl(rawUrl: String): String {
        val path = runCatching { URI(rawUrl).path }.getOrNull().orEmpty()
            .ifBlank { rawUrl.substringBefore('?').substringBefore('#') }
        return path.substringAfterLast('/').trim()
            .let { name -> URLDecoder.decode(name, Charsets.UTF_8.name()) }
    }

    private fun buildCacheKey(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): String {
        return listOf(
            "git-repository-assets-v1",
            identity.platform.storageId,
            identity.displayName,
            rawTag,
            releaseUrl.trim(),
            gitLabApiBaseUrl.trimEnd('/'),
            giteeApiBaseUrl.trimEnd('/'),
            lookupConfig.githubAssetSourceSignature()
        ).joinToString("|")
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching {
            if (isBlank()) null else Instant.parse(this).toEpochMilli()
        }.getOrNull()
    }

    private fun String.normalizedReleaseTag(): String {
        return trim()
            .lowercase(Locale.ROOT)
            .replace(leadingVersionPrefixRegex, "")
    }

    private fun String.urlEncodePathSegment(): String {
        return urlEncode(this)
    }

    private companion object {
        private const val CACHE_TTL_MS = 90_000L
        private const val MAX_COMPLETED_BUNDLE_CACHE_SIZE = 128
        private const val GIT_USER_AGENT = "KeiOS-App/1.0 (Android Git Repository Assets)"
        private const val GITEE_FETCH_SOURCE = "gitee-api"
        private const val GITLAB_FETCH_SOURCE = "gitlab-api"

        private val leadingVersionPrefixRegex = Regex("""^v(?=\d)""", RegexOption.IGNORE_CASE)
        private val completedBundleCache =
            ConcurrentHashMap<String, GitRepositoryCachedAssetBundle>()
        private val inFlightBundleFetches =
            GitHubSingleFlight<String, GitHubReleaseAssetBundle>()

        private val defaultClient: OkHttpClient by lazy {
            SharedHttpClient.base.newBuilder()
                .callTimeout(18.seconds)
                .readTimeout(14.seconds)
                .build()
        }

        private fun urlEncode(raw: String): String {
            return URLEncoder.encode(raw.trim(), Charsets.UTF_8.name()).replace("+", "%20")
        }
    }
}

private data class GitRepositoryCachedAssetBundle(
    val bundle: GitHubReleaseAssetBundle,
    val timestamp: Long
)
