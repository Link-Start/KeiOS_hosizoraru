package os.kei.feature.github.domain

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.core.io.SharedHttpClient
import os.kei.feature.github.GitHubSingleFlight
import os.kei.feature.github.data.remote.GitHubAtomHeuristics
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetJsonMapper
import os.kei.feature.github.data.remote.GitHubReleaseAssetSelector
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.GitRepositoryTrackIdentity
import os.kei.feature.github.model.githubAssetSourceSignature
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

internal class GitRepositoryReleaseAssetSource(
    private val identity: GitRepositoryTrackIdentity,
    private val client: OkHttpClient = defaultClient,
    private val gitLabApiBaseUrl: String = "https://${identity.host}/api/v4",
    private val giteeApiBaseUrl: String = "https://gitee.com/api/v5",
    private val giteaApiBaseUrl: String = "https://${identity.host}/api/v1",
) {
    fun buildAssetCacheKey(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): String {
        return listOf(
            "git-repository-assets-v2",
            identity.platform.storageId,
            identity.displayName,
            rawTag.trim(),
            releaseUrl.trim(),
            includeAllAssets.toString(),
            gitLabApiBaseUrl.trimEnd('/'),
            giteeApiBaseUrl.trimEnd('/'),
            giteaApiBaseUrl.trimEnd('/'),
            lookupConfig.githubAssetSourceSignature()
        ).joinToString("|")
    }

    fun fetchReleaseNotesTargets(
        stableLimit: Int = 2,
        prereleaseLimit: Int = 2,
    ): Result<List<GitHubReleaseNotesTarget>> {
        val releases = when (identity.platform) {
            GitRepositoryPlatform.Gitee -> fetchGiteeReleaseList(limit = TARGET_RELEASE_FETCH_LIMIT)
            GitRepositoryPlatform.GitLab -> fetchGitLabReleaseList(limit = TARGET_RELEASE_FETCH_LIMIT)
            GitRepositoryPlatform.Gitea -> fetchGiteaReleaseList(limit = TARGET_RELEASE_FETCH_LIMIT)
            GitRepositoryPlatform.GitHub,
            GitRepositoryPlatform.Generic -> {
                Result.failure(IllegalStateException("Git release notes are unavailable"))
            }
        }.getOrElse { error ->
            return Result.failure(error)
        }
        return runCatching {
            buildReleaseNotesTargets(
                releases = releases,
                stableLimit = stableLimit,
                prereleaseLimit = prereleaseLimit
            )
        }
    }

    suspend fun loadReleaseAssetBundle(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): Result<GitHubReleaseAssetBundle> {
        val normalizedTag = rawTag.trim()
        if (normalizedTag.isBlank()) {
            return Result.failure(IllegalArgumentException("missing Git release tag"))
        }
        val cacheKey = buildAssetCacheKey(
            rawTag = normalizedTag,
            releaseUrl = releaseUrl,
            lookupConfig = lookupConfig,
            includeAllAssets = includeAllAssets
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
                        lookupConfig = lookupConfig,
                        includeAllAssets = includeAllAssets
                    )
                }

                GitRepositoryPlatform.GitLab -> {
                    loadGitLabReleaseBundle(
                        rawTag = normalizedTag,
                        releaseUrl = releaseUrl,
                        lookupConfig = lookupConfig,
                        includeAllAssets = includeAllAssets
                    )
                }

                GitRepositoryPlatform.Gitea -> {
                    loadGiteaReleaseBundle(
                        rawTag = normalizedTag,
                        releaseUrl = releaseUrl,
                        lookupConfig = lookupConfig,
                        includeAllAssets = includeAllAssets
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

    private fun loadGiteeReleaseBundle(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): Result<GitHubReleaseAssetBundle> {
        val release = fetchJsonObject(buildGiteeReleaseDetailUrl(rawTag)).recoverCatching {
            fetchGiteeReleaseList(limit = FALLBACK_RELEASE_FETCH_LIMIT)
                .getOrThrow()
                .firstReleaseMatchingTag(rawTag)
                ?: error("Gitee release was not found for tag $rawTag")
        }.getOrElse { error ->
            return Result.failure(error)
        }
        return runCatching {
            parsePlatformReleaseBundle(
                release = release,
                fallbackTag = rawTag,
                fallbackReleaseUrl = releaseUrl,
                fetchSource = GITEE_FETCH_SOURCE,
                sourceConfigSignature = lookupConfig.githubAssetSourceSignature(),
                includeAllAssets = includeAllAssets,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering
            )
        }
    }

    private fun loadGiteaReleaseBundle(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): Result<GitHubReleaseAssetBundle> {
        val release = fetchJsonObject(buildGiteaReleaseDetailUrl(rawTag)).recoverCatching {
            fetchGiteaReleaseList(limit = FALLBACK_RELEASE_FETCH_LIMIT)
                .getOrThrow()
                .firstReleaseMatchingTag(rawTag)
                ?: error("Gitea release was not found for tag $rawTag")
        }.getOrElse { error ->
            return Result.failure(error)
        }
        return runCatching {
            parsePlatformReleaseBundle(
                release = release,
                fallbackTag = rawTag,
                fallbackReleaseUrl = releaseUrl,
                fetchSource = GITEA_FETCH_SOURCE,
                sourceConfigSignature = lookupConfig.githubAssetSourceSignature(),
                includeAllAssets = includeAllAssets,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering
            )
        }
    }

    private fun loadGitLabReleaseBundle(
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): Result<GitHubReleaseAssetBundle> {
        val release = fetchJsonObject(buildGitLabReleaseDetailUrl(rawTag)).getOrElse { error ->
            return Result.failure(error)
        }
        return runCatching {
            parseGitLabReleaseBundle(
                release = release,
                fallbackTag = rawTag,
                fallbackReleaseUrl = releaseUrl,
                sourceConfigSignature = lookupConfig.githubAssetSourceSignature(),
                includeAllAssets = includeAllAssets,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering
            )
        }
    }

    private fun parsePlatformReleaseBundle(
        release: JSONObject,
        fallbackTag: String,
        fallbackReleaseUrl: String,
        fetchSource: String,
        sourceConfigSignature: String,
        includeAllAssets: Boolean,
        aggressiveFiltering: Boolean,
    ): GitHubReleaseAssetBundle {
        val parsed = GitHubReleaseAssetJsonMapper.parseReleaseBundle(release)
        val tagName = parsed.tagName.ifBlank { releaseTagName(release).ifBlank { fallbackTag } }
        return parsed.copy(
            releaseName = parsed.releaseName.ifBlank { tagName },
            tagName = tagName,
            htmlUrl = releaseWebUrl(release, tagName)
                .ifBlank { fallbackReleaseUrl.trim() }
                .ifBlank { buildPlatformReleasePageUrl(tagName) },
            releaseUpdatedAtMillis = parsed.releaseUpdatedAtMillis ?: releaseUpdatedAtMillis(release),
            releaseNotesBody = releaseBody(release).ifBlank { parsed.releaseNotesBody },
            fetchSource = fetchSource,
            sourceConfigSignature = sourceConfigSignature
        ).selectDisplayAssets(
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets
        )
    }

    private fun parseGitLabReleaseBundle(
        release: JSONObject,
        fallbackTag: String,
        fallbackReleaseUrl: String,
        sourceConfigSignature: String,
        includeAllAssets: Boolean,
        aggressiveFiltering: Boolean,
    ): GitHubReleaseAssetBundle {
        val tagName = releaseTagName(release).ifBlank { fallbackTag }
        val releaseName = release.optString("name").trim().ifBlank { tagName }
        val htmlUrl = releaseWebUrl(release, tagName)
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
                        updatedAtMillis = releaseUpdatedAtMillis(release),
                        digest = link.optString("filepath").trim()
                    )
                )
            }
        }
        return GitHubReleaseAssetBundle(
            releaseName = releaseName,
            tagName = tagName,
            htmlUrl = htmlUrl,
            releaseUpdatedAtMillis = releaseUpdatedAtMillis(release),
            releaseNotesBody = releaseBody(release),
            assets = assets,
            shortCommitSha = release.optJSONObject("commit")?.optString("short_id").orEmpty(),
            fetchSource = GITLAB_FETCH_SOURCE,
            sourceConfigSignature = sourceConfigSignature
        ).selectDisplayAssets(
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets
        )
    }

    private fun buildReleaseNotesTargets(
        releases: JSONArray,
        stableLimit: Int,
        prereleaseLimit: Int,
    ): List<GitHubReleaseNotesTarget> {
        val targets = buildList {
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", false)) continue
                val tagName = releaseTagName(release)
                if (tagName.isBlank()) continue
                val releaseName = release.optString("name").trim().ifBlank { tagName }
                val bodyPreview = GitHubAtomHeuristics.buildContentPreview(releaseBody(release))
                val channel = GitHubAtomHeuristics.detectReleaseChannel(
                    tag = tagName,
                    title = releaseName,
                    contentPreview = bodyPreview
                )
                val prerelease = release.optBoolean("prerelease", false) ||
                    release.optBoolean("pre_release", false) ||
                    channel.isPreRelease
                val htmlUrl = releaseWebUrl(release, tagName).ifBlank { buildPlatformReleasePageUrl(tagName) }
                add(
                    GitHubReleaseNotesTarget(
                        releaseName = releaseName,
                        tagName = tagName,
                        htmlUrl = htmlUrl,
                        prerelease = prerelease,
                        latestInChannel = false,
                        updatedAtMillis = releaseUpdatedAtMillis(release)
                    )
                )
            }
        }
        val stable = targets
            .filter { !it.prerelease }
            .take(stableLimit.coerceAtLeast(0))
            .mapIndexed { index, target -> target.copy(latestInChannel = index == 0) }
        val prerelease = targets
            .filter { it.prerelease }
            .take(prereleaseLimit.coerceAtLeast(0))
            .mapIndexed { index, target -> target.copy(latestInChannel = index == 0) }
        return (stable + prerelease)
            .distinctBy { it.id.lowercase(Locale.ROOT) }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
    }

    private fun fetchGiteeReleaseList(limit: Int): Result<JSONArray> {
        val url =
            "${giteeApiBaseUrl.trimEnd('/')}/repos/${repositoryApiPath()}/releases?per_page=${limit.coerceIn(1, 100)}&direction=desc"
        return fetchJsonArray(url)
    }

    private fun fetchGitLabReleaseList(limit: Int): Result<JSONArray> {
        val projectId = urlEncode("${identity.namespace}/${identity.repo}")
        val url =
            "${gitLabApiBaseUrl.trimEnd('/')}/projects/$projectId/releases?per_page=${limit.coerceIn(1, 100)}"
        return fetchJsonArray(url)
    }

    private fun fetchGiteaReleaseList(limit: Int): Result<JSONArray> {
        val url =
            "${giteaApiBaseUrl.trimEnd('/')}/repos/${repositoryApiPath()}/releases?limit=${limit.coerceIn(1, 100)}"
        return fetchJsonArray(url)
    }

    private fun buildGiteeReleaseDetailUrl(rawTag: String): String {
        return "${giteeApiBaseUrl.trimEnd('/')}/repos/${repositoryApiPath()}/releases/tags/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGitLabReleaseDetailUrl(rawTag: String): String {
        val projectId = urlEncode("${identity.namespace}/${identity.repo}")
        return "${gitLabApiBaseUrl.trimEnd('/')}/projects/$projectId/releases/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGiteaReleaseDetailUrl(rawTag: String): String {
        return "${giteaApiBaseUrl.trimEnd('/')}/repos/${repositoryApiPath()}/releases/tags/${rawTag.urlEncodePathSegment()}"
    }

    private fun repositoryApiPath(): String {
        val ownerPath = identity.namespace
            .split('/')
            .joinToString("/") { segment -> segment.urlEncodePathSegment() }
        val repoPath = identity.repo.urlEncodePathSegment()
        return "$ownerPath/$repoPath"
    }

    private fun buildPlatformReleasePageUrl(rawTag: String): String {
        return when (identity.platform) {
            GitRepositoryPlatform.GitLab -> buildGitLabReleasePageUrl(rawTag)
            GitRepositoryPlatform.Gitee -> buildGiteeReleasePageUrl(rawTag)
            GitRepositoryPlatform.Gitea -> buildGiteaReleasePageUrl(rawTag)
            GitRepositoryPlatform.GitHub -> "https://github.com/${identity.namespace}/${identity.repo}/releases/tag/${rawTag.urlEncodePathSegment()}"
            GitRepositoryPlatform.Generic -> "${identity.url.trimEnd('/')}/releases/tag/${rawTag.urlEncodePathSegment()}"
        }
    }

    private fun buildGiteeReleasePageUrl(rawTag: String): String {
        return "https://gitee.com/${identity.namespace}/${identity.repo}/releases/tag/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGitLabReleasePageUrl(rawTag: String): String {
        return "https://${identity.host}/${identity.namespace}/${identity.repo}/-/releases/${rawTag.urlEncodePathSegment()}"
    }

    private fun buildGiteaReleasePageUrl(rawTag: String): String {
        return "https://${identity.host}/${identity.namespace}/${identity.repo}/releases/tag/${rawTag.urlEncodePathSegment()}"
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
            val tag = releaseTagName(release)
            if (tag.equals(rawTag, ignoreCase = true) || tag.normalizedReleaseTag() == normalized) {
                return release
            }
        }
        return null
    }

    private fun releaseTagName(release: JSONObject): String {
        return release.optString("tag_name").trim()
            .ifBlank { release.optString("tag").trim() }
            .ifBlank { release.optString("name").trim() }
    }

    private fun releaseBody(release: JSONObject): String {
        return release.optString("body").trim()
            .ifBlank { release.optString("description").trim() }
    }

    private fun releaseWebUrl(release: JSONObject, tagName: String): String {
        val direct = release.optString("html_url").trim()
            .ifBlank { release.optString("web_url").trim() }
        if (direct.isNotBlank()) return direct
        return when (identity.platform) {
            GitRepositoryPlatform.Gitee -> buildGiteeReleasePageUrl(tagName)
            GitRepositoryPlatform.GitLab -> buildGitLabReleasePageUrl(tagName)
            GitRepositoryPlatform.Gitea -> buildGiteaReleasePageUrl(tagName)
            GitRepositoryPlatform.GitHub,
            GitRepositoryPlatform.Generic -> {
                release.optJSONObject("_links")?.optString("self").orEmpty().trim()
                    .ifBlank { release.optString("url").trim() }
            }
        }
    }

    private fun releaseUpdatedAtMillis(release: JSONObject): Long? {
        return release.optString("released_at").parseIsoInstantOrNull()
            ?: release.optString("published_at").parseIsoInstantOrNull()
            ?: release.optString("created_at").parseIsoInstantOrNull()
            ?: release.optString("updated_at").parseIsoInstantOrNull()
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

    private fun GitHubReleaseAssetBundle.selectDisplayAssets(
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
    ): GitHubReleaseAssetBundle {
        return GitHubReleaseAssetSelector.selectDisplayAssets(
            bundle = this,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets
        )
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        val value = trim()
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
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
        private const val TARGET_RELEASE_FETCH_LIMIT = 30
        private const val FALLBACK_RELEASE_FETCH_LIMIT = 100
        private const val GIT_USER_AGENT = "KeiOS-App/1.0 (Android Git Repository Assets)"
        private const val GITEE_FETCH_SOURCE = "gitee-api"
        private const val GITLAB_FETCH_SOURCE = "gitlab-api"
        private const val GITEA_FETCH_SOURCE = "gitea-api"

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
