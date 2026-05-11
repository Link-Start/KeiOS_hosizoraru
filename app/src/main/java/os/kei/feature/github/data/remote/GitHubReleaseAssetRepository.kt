package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.GitHubSingleFlight
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

data class GitHubReleaseAssetFile(
    val name: String,
    val downloadUrl: String,
    val apiAssetUrl: String = "",
    val sizeBytes: Long,
    val downloadCount: Int,
    val contentType: String = "",
    val updatedAtMillis: Long? = null,
    val digest: String = ""
)

data class GitHubReleaseAssetBundle(
    val releaseName: String,
    val tagName: String,
    val htmlUrl: String,
    val releaseUpdatedAtMillis: Long? = null,
    val releaseNotesBody: String = "",
    val assets: List<GitHubReleaseAssetFile>,
    val showingAllAssets: Boolean = false,
    val shortCommitSha: String = "",
    val fetchSource: String = "",
    val sourceConfigSignature: String = ""
)

data class GitHubReleaseNotesTarget(
    val releaseName: String,
    val tagName: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val latestInChannel: Boolean,
    val updatedAtMillis: Long? = null
) {
    val id: String
        get() = "${tagName.trim()}|${htmlUrl.trim()}"
}

object GitHubReleaseAssetFetchSources {
    const val HTML = "html"
    const val API = "api"
}

private data class HtmlReleaseMetadata(
    val releaseName: String,
    val tagName: String,
    val htmlUrl: String,
    val releaseUpdatedAtMillis: Long?,
    val releaseNotesBody: String
)

object GitHubReleaseAssetRepository {
    private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
    private val htmlBlockOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val htmlHrefRegex = Regex("""href="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val releaseTitleRegex =
        Regex("""<h1[^>]*class="[^"]*d-inline[^"]*"[^>]*>(.*?)</h1>""", htmlBlockOptions)
    private val openGraphTitleRegex =
        Regex("""<meta[^>]*property="og:title"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val releaseUpdatedAtRegex = Regex(
        """(?:released|published)\s+this\s*<relative-time[^>]*datetime="([^"]+)"""",
        htmlBlockOptions
    )
    private val expandedAssetsSrcRegex =
        Regex(
            """<include-fragment[^>]*src="([^"]*?/releases/expanded_assets/[^"]+)"""",
            RegexOption.IGNORE_CASE
        )
    private val expandedAssetRowRegex =
        Regex("""<li\b[^>]*class="[^"]*Box-row[^"]*"[^>]*>(.*?)</li>""", htmlBlockOptions)
    private val expandedAssetLinkRegex =
        Regex(
            """<a[^>]*href="([^"]+)"[^>]*class="[^"]*Truncate[^"]*"[^>]*>(.*?)</a>""",
            htmlBlockOptions
        )
    private val expandedAssetSizeRegex =
        Regex(""">\s*(\d+(?:\.\d+)?)\s*([KMGT]?B)\s*<""", RegexOption.IGNORE_CASE)
    private val relativeTimeRegex =
        Regex("""<relative-time[^>]*datetime="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val sourceCodeArchiveLabelRegex =
        Regex("""^Source code\s*\(([^)]+)\)$""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val whitespaceRegex = Regex("""\s+""")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(16.seconds)
            .connectTimeout(8.seconds)
            .readTimeout(12.seconds)
            .writeTimeout(8.seconds)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .fastFallback(true)
            .build()
    }
    private val apiClient: GitHubReleaseApiClient by lazy {
        GitHubReleaseApiClient(client)
    }
    private val inFlightAssetFetches = GitHubSingleFlight<String, GitHubReleaseAssetBundle>()

    fun fetchApkAssets(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String = "",
        preferHtml: Boolean = false,
        aggressiveFiltering: Boolean = false,
        includeAllAssets: Boolean = false,
        apiToken: String = ""
    ): Result<GitHubReleaseAssetBundle> {
        return GitHubExecution.runBlockingIo {
            fetchApkAssetsAsync(
                owner = owner,
                repo = repo,
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = aggressiveFiltering,
                includeAllAssets = includeAllAssets,
                apiToken = apiToken
            )
        }
    }

    suspend fun fetchApkAssetsAsync(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String = "",
        preferHtml: Boolean = false,
        aggressiveFiltering: Boolean = false,
        includeAllAssets: Boolean = false,
        apiToken: String = ""
    ): Result<GitHubReleaseAssetBundle> {
        val inFlightKey = buildAssetFetchKey(
            requestKind = ASSET_FETCH_KIND_TAG,
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets,
            apiToken = apiToken
        )
        return runInFlightAssetFetch(inFlightKey) {
            fetchApkAssetsUnshared(
                owner = owner,
                repo = repo,
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = aggressiveFiltering,
                includeAllAssets = includeAllAssets,
                apiToken = apiToken
            )
        }
    }

    private fun fetchApkAssetsUnshared(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        apiToken: String
    ): Result<GitHubReleaseAssetBundle> {
        val normalizedTag = rawTag.trim()
        if (normalizedTag.isBlank()) {
            return Result.failure(IllegalArgumentException("missing release tag"))
        }

        val normalizedReleaseUrl = releaseUrl.trim()
        val resolvedHtmlReleaseUrl = normalizedReleaseUrl.ifBlank {
            GitHubVersionUtils.buildReleaseTagUrl(owner, repo, normalizedTag)
        }
        val primarySource = if (preferHtml) {
            GitHubReleaseAssetFetchSources.HTML
        } else {
            GitHubReleaseAssetFetchSources.API
        }
        val primary = if (preferHtml) {
            fetchReleaseFromHtml(owner, repo, normalizedTag, resolvedHtmlReleaseUrl, apiToken)
        } else {
            fetchReleaseByTagWithFallback(owner, repo, normalizedTag, normalizedReleaseUrl, apiToken)
        }
        val fallbackSource = when {
            preferHtml -> GitHubReleaseAssetFetchSources.API
            resolvedHtmlReleaseUrl.isNotBlank() -> GitHubReleaseAssetFetchSources.HTML
            else -> primarySource
        }
        val resolvedSource = if (primary.isSuccess) primarySource else fallbackSource
        val resolvedRelease = primary.takeIf { it.isSuccess } ?: when {
            preferHtml -> {
                fetchReleaseByTagWithFallback(
                    owner = owner,
                    repo = repo,
                    rawTag = normalizedTag,
                    releaseUrl = normalizedReleaseUrl,
                    apiToken = apiToken
                )
            }
            resolvedHtmlReleaseUrl.isNotBlank() -> {
                fetchReleaseFromHtml(owner, repo, normalizedTag, resolvedHtmlReleaseUrl, apiToken)
            }
            else -> Result.failure(primary.exceptionOrNull() ?: IllegalStateException("release fetch failed"))
        }

        return resolvedRelease.mapCatching { release ->
            parseReleaseBundle(release)
                .copy(fetchSource = resolvedSource)
                .withResolvedShortCommitSha(owner, repo, normalizedTag, apiToken)
                .selectDisplayAssets(
                    aggressiveFiltering = aggressiveFiltering,
                    includeAllAssets = includeAllAssets
                )
        }
    }

    fun fetchLatestStableApkAssets(
        owner: String,
        repo: String,
        aggressiveFiltering: Boolean = false,
        apiToken: String = ""
    ): Result<GitHubReleaseAssetBundle> {
        return GitHubExecution.runBlockingIo {
            fetchLatestStableApkAssetsAsync(
                owner = owner,
                repo = repo,
                aggressiveFiltering = aggressiveFiltering,
                apiToken = apiToken
            )
        }
    }

    suspend fun fetchLatestStableApkAssetsAsync(
        owner: String,
        repo: String,
        aggressiveFiltering: Boolean = false,
        apiToken: String = ""
    ): Result<GitHubReleaseAssetBundle> {
        val inFlightKey = buildAssetFetchKey(
            requestKind = ASSET_FETCH_KIND_LATEST,
            owner = owner,
            repo = repo,
            rawTag = "latest",
            releaseUrl = "",
            preferHtml = false,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = false,
            apiToken = apiToken
        )
        return runInFlightAssetFetch(inFlightKey) {
            runCatching {
                val release = apiClient.fetchLatestRelease(owner, repo, apiToken).getOrThrow()
                parseReleaseBundle(release)
                    .copy(fetchSource = GitHubReleaseAssetFetchSources.API)
                    .selectDisplayAssets(
                        aggressiveFiltering = aggressiveFiltering,
                        includeAllAssets = false
                    )
            }
        }
    }

    suspend fun fetchReleaseNotesTargetsAsync(
        owner: String,
        repo: String,
        apiToken: String = "",
        stableLimit: Int = 2,
        prereleaseLimit: Int = 2
    ): Result<List<GitHubReleaseNotesTarget>> {
        return withReleaseList(owner, repo, apiToken) { releases ->
            buildReleaseNotesTargets(
                releases = releases,
                stableLimit = stableLimit,
                prereleaseLimit = prereleaseLimit
            )
        }
    }

    fun resolvePreferredDownloadUrl(
        asset: GitHubReleaseAssetFile,
        useApiAssetUrl: Boolean,
        apiToken: String = ""
    ): Result<String> {
        val token = apiToken.trim()
        val apiAssetUrl = asset.apiAssetUrl.trim()
        if (!useApiAssetUrl || token.isBlank() || apiAssetUrl.isBlank()) {
            return Result.success(asset.downloadUrl)
        }
        return apiClient.resolveApiAssetDownloadUrl(apiAssetUrl, token).recoverCatching {
            asset.downloadUrl
        }
    }

    fun parseReleaseTagFromUrl(url: String): String {
        val raw = url.trim()
        if (raw.isBlank()) return ""
        val marker = "/releases/tag/"
        val fromPath = runCatching {
            val uri = URI(raw)
            val path = uri.rawPath.orEmpty()
            if (!path.contains(marker)) return@runCatching ""
            val encoded = path.substringAfter(marker).trim('/').trim()
            URLDecoder.decode(encoded, Charsets.UTF_8.name())
        }.getOrDefault("")
        if (fromPath.isNotBlank()) return fromPath
        return raw.substringAfter(marker, "").trim('/')
    }

    internal fun List<GitHubReleaseAssetFile>.filterRelevantApks(aggressiveFiltering: Boolean): List<GitHubReleaseAssetFile> {
        return GitHubReleaseAssetSelector.filterRelevantApks(this, aggressiveFiltering)
    }

    private fun List<GitHubReleaseAssetFile>.filterNonSourceAssets(): List<GitHubReleaseAssetFile> {
        return GitHubReleaseAssetSelector.run { filterNonSourceAssets() }
    }

    private fun GitHubReleaseAssetBundle.withResolvedShortCommitSha(
        owner: String,
        repo: String,
        rawTag: String,
        apiToken: String
    ): GitHubReleaseAssetBundle {
        val token = apiToken.trim()
        if (token.isBlank()) return copy(shortCommitSha = "")
        val commitSha = apiClient.resolveShortCommitSha(owner, repo, rawTag, token).getOrDefault("")
        return copy(shortCommitSha = commitSha)
    }

    private fun GitHubReleaseAssetBundle.selectDisplayAssets(
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean
    ): GitHubReleaseAssetBundle {
        return GitHubReleaseAssetSelector.selectDisplayAssets(
            bundle = this,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets
        )
    }

    private suspend fun runInFlightAssetFetch(
        key: String,
        block: () -> Result<GitHubReleaseAssetBundle>
    ): Result<GitHubReleaseAssetBundle> {
        return inFlightAssetFetches.run(key) {
            try {
                block()
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }

    private fun buildAssetFetchKey(
        requestKind: String,
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        apiToken: String
    ): String {
        return listOf(
            requestKind,
            owner.trim().lowercase(Locale.ROOT),
            repo.trim().lowercase(Locale.ROOT),
            rawTag.trim(),
            releaseUrl.trim(),
            if (preferHtml) GitHubReleaseAssetFetchSources.HTML else GitHubReleaseAssetFetchSources.API,
            aggressiveFiltering.toString(),
            includeAllAssets.toString(),
            apiToken.trim().hashCode().toString()
        ).joinToString("|")
    }

    private const val ASSET_FETCH_KIND_TAG = "tag"
    private const val ASSET_FETCH_KIND_LATEST = "latest"

    private fun List<GitHubReleaseAssetFile>.sortForDisplay(): List<GitHubReleaseAssetFile> {
        return GitHubReleaseAssetSelector.run { sortForDisplay() }
    }

    private fun fetchReleaseByTagWithFallback(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        apiToken: String
    ): Result<JSONObject> {
        val byTag = apiClient.fetchReleaseByTag(owner, repo, rawTag, apiToken)
        if (byTag.isSuccess) return byTag

        val fallback = apiClient.fetchReleaseList(owner, repo, apiToken).mapCatching { releases ->
            findMatchingRelease(releases, rawTag)
                ?: buildReleaseStub(
                    releaseName = rawTag,
                    rawTag = rawTag,
                    releaseUrl = releaseUrl
                )
        }
        return fallback.takeIf { it.isSuccess } ?: byTag
    }

    private fun <T> withReleaseList(
        owner: String,
        repo: String,
        apiToken: String,
        block: (JSONArray) -> T
    ): Result<T> = runCatching {
        val releases = apiClient.fetchReleaseList(owner, repo, apiToken).getOrThrow()
        block(releases)
    }

    private fun buildReleaseNotesTargets(
        releases: JSONArray,
        stableLimit: Int,
        prereleaseLimit: Int
    ): List<GitHubReleaseNotesTarget> {
        val targets = buildList {
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", false)) continue
                val tagName = release.optString("tag_name").trim()
                val htmlUrl = release.optString("html_url").trim()
                if (tagName.isBlank() || htmlUrl.isBlank()) continue
                add(
                    GitHubReleaseNotesTarget(
                        releaseName = release.optString("name").trim().ifBlank { tagName },
                        tagName = tagName,
                        htmlUrl = htmlUrl,
                        prerelease = release.optBoolean("prerelease", false),
                        latestInChannel = false,
                        updatedAtMillis = release.optString("published_at").trim()
                            .parseIsoInstantOrNull()
                            ?: release.optString("created_at").trim().parseIsoInstantOrNull()
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

    private fun fetchReleaseFromHtml(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        apiToken: String
    ): Result<JSONObject> = runCatching {
        val normalizedReleaseUrl = releaseUrl.trim().ifBlank {
            GitHubVersionUtils.buildReleaseTagUrl(owner, repo, rawTag)
        }
        val releaseHtml = fetchHtml(normalizedReleaseUrl, apiToken)
        val metadata = parseReleaseMetadataFromHtml(releaseHtml, rawTag, normalizedReleaseUrl)
        val expandedAssetsUrl = parseExpandedAssetsUrl(releaseHtml).orEmpty()
            .ifBlank { buildExpandedAssetsUrl(owner, repo, rawTag) }
        val assets = runCatching {
            val expandedAssetsHtml = fetchHtml(expandedAssetsUrl, apiToken)
            parseAssetsFromExpandedHtml(expandedAssetsHtml, owner, repo, rawTag)
        }.recoverCatching {
            parseAssetsFromReleaseHtml(releaseHtml, owner, repo, rawTag)
        }.getOrThrow()
        buildReleaseStub(
            releaseName = metadata.releaseName,
            rawTag = metadata.tagName,
            releaseUrl = metadata.htmlUrl,
            releaseUpdatedAtMillis = metadata.releaseUpdatedAtMillis,
            releaseNotesBody = metadata.releaseNotesBody,
            assets = assets
        )
    }

    private fun fetchHtml(url: String, apiToken: String): String {
        val token = apiToken.trim()
        val result = GitHubExecution.retryOnceBlocking(
            shouldRetry = { error -> error is IOException }
        ) {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", GITHUB_USER_AGENT)
                .header("Connection", "close")
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body.string()
                if (!response.isSuccessful) {
                    error("GitHub release page request failed (HTTP ${response.code})")
                }
                bodyText
            }
        }
        val lastError = result.exceptionOrNull()
        result.getOrNull()?.let { return it }
        val message = lastError?.message.orEmpty()
        if (message.contains("connection closed", ignoreCase = true)) {
            error("GitHub page connection closed unexpectedly. Try again later.")
        }
        throw lastError ?: IllegalStateException("GitHub release page request failed")
    }

    private fun parseAssetsFromReleaseHtml(
        html: String,
        owner: String,
        repo: String,
        rawTag: String
    ): List<GitHubReleaseAssetFile> {
        val unique = linkedMapOf<String, GitHubReleaseAssetFile>()
        htmlHrefRegex.findAll(html).forEach { match ->
            val rawHref = match.groupValues.getOrNull(1).orEmpty()
            val href = rawHref.replace("&amp;", "&")
            val normalizedUrl = when {
                href.startsWith("https://") || href.startsWith("http://") -> href
                href.startsWith("/") -> "https://github.com$href"
                else -> ""
            }
            if (normalizedUrl.isBlank()) return@forEach
            if (!normalizedUrl.contains("/$owner/$repo/releases/download/")) return@forEach
            val fileName = normalizedUrl.substringAfterLast('/').substringBefore('?')
            val decodedName = runCatching { URLDecoder.decode(fileName, Charsets.UTF_8.name()) }.getOrDefault(fileName)
            unique.putIfAbsent(
                decodedName,
                GitHubReleaseAssetFile(
                    name = decodedName,
                    downloadUrl = normalizedUrl,
                    sizeBytes = 0L,
                    downloadCount = 0,
                    contentType = "application/vnd.android.package-archive",
                    updatedAtMillis = null
                )
            )
        }
        return unique.values.toList().ifEmpty {
            error("No downloadable assets found on the release page: $rawTag")
        }
    }

    private fun parseReleaseMetadataFromHtml(
        html: String,
        rawTag: String,
        releaseUrl: String
    ): HtmlReleaseMetadata {
        val releaseName = releaseTitleRegex.find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .stripHtml()
            .decodeHtmlEntities()
            .trim()
            .ifBlank {
                openGraphTitleRegex.find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                    .decodeHtmlEntities()
                    .substringBefore(" · ")
                    .trim()
            }
            .ifBlank { rawTag }

        val releaseUpdatedAtMillis = releaseUpdatedAtRegex.find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .parseIsoInstantOrNull()

        return HtmlReleaseMetadata(
            releaseName = releaseName,
            tagName = rawTag.trim(),
            htmlUrl = releaseUrl.trim(),
            releaseUpdatedAtMillis = releaseUpdatedAtMillis,
            releaseNotesBody = GitHubReleaseNotesHtmlMarkdownConverter.parse(html)
        )
    }

    private fun parseExpandedAssetsUrl(html: String): String? {
        val src = expandedAssetsSrcRegex.find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .replace("&amp;", "&")
            .trim()
        return normalizeGitHubUrl(src)
    }

    private fun buildExpandedAssetsUrl(
        owner: String,
        repo: String,
        rawTag: String
    ): String {
        val encodedTag = URLEncoder.encode(rawTag, Charsets.UTF_8.name()).replace("+", "%20")
        return "https://github.com/$owner/$repo/releases/expanded_assets/$encodedTag"
    }

    private fun parseAssetsFromExpandedHtml(
        html: String,
        owner: String,
        repo: String,
        rawTag: String
    ): List<GitHubReleaseAssetFile> {
        val unique = linkedMapOf<String, GitHubReleaseAssetFile>()
        expandedAssetRowRegex.findAll(html).forEach { rowMatch ->
            val rowHtml = rowMatch.groupValues.getOrNull(1).orEmpty()
            val linkMatch = expandedAssetLinkRegex.find(rowHtml) ?: return@forEach
            val normalizedUrl = normalizeGitHubUrl(linkMatch.groupValues.getOrNull(1).orEmpty())
            if (normalizedUrl.isNullOrBlank()) return@forEach
            val fileName = inferHtmlAssetFileName(normalizedUrl, linkMatch.groupValues.getOrNull(2).orEmpty())
            if (fileName.isBlank()) return@forEach
            if (!normalizedUrl.contains("/$owner/$repo/")) return@forEach
            val sizeBytes = expandedAssetSizeRegex.find(rowHtml)?.let { sizeMatch ->
                parseSizeBytes(
                    sizeValue = sizeMatch.groupValues.getOrNull(1).orEmpty(),
                    sizeUnit = sizeMatch.groupValues.getOrNull(2).orEmpty()
                )
            } ?: 0L
            val updatedAtMillis = relativeTimeRegex.find(rowHtml)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .parseIsoInstantOrNull()
            unique.putIfAbsent(
                fileName,
                GitHubReleaseAssetFile(
                    name = fileName,
                    downloadUrl = normalizedUrl,
                    sizeBytes = sizeBytes,
                    downloadCount = 0,
                    contentType = inferAssetContentType(fileName),
                    updatedAtMillis = updatedAtMillis
                )
            )
        }
        return unique.values.toList().ifEmpty {
            error("No downloadable assets found on the expanded_assets page: $rawTag")
        }
    }

    private fun inferHtmlAssetFileName(
        normalizedUrl: String,
        linkInnerHtml: String
    ): String {
        val urlFileName = when {
            normalizedUrl.contains("/releases/download/") -> {
                normalizedUrl.substringAfterLast('/').substringBefore('?')
            }
            normalizedUrl.contains("/archive/refs/tags/") -> when {
                normalizedUrl.endsWith(".tar.gz", ignoreCase = true) -> "Source code.tar.gz"
                normalizedUrl.endsWith(".zip", ignoreCase = true) -> "Source code.zip"
                else -> normalizedUrl.substringAfterLast('/').substringBefore('?')
            }
            else -> ""
        }
        val decodedUrlName = runCatching {
            URLDecoder.decode(urlFileName, Charsets.UTF_8.name())
        }.getOrDefault(urlFileName).trim()
        if (decodedUrlName.isNotBlank()) return decodedUrlName

        val linkText = linkInnerHtml.stripHtml().decodeHtmlEntities().trim()
        val sourceCodeMatch = sourceCodeArchiveLabelRegex.find(linkText)
        if (sourceCodeMatch != null) {
            return "Source code.${sourceCodeMatch.groupValues[1].trim()}"
        }
        return linkText
    }

    private fun normalizeGitHubUrl(rawUrl: String): String? {
        val cleaned = rawUrl.replace("&amp;", "&").trim()
        if (cleaned.isBlank()) return null
        return when {
            cleaned.startsWith("https://", ignoreCase = true) ||
                cleaned.startsWith("http://", ignoreCase = true) -> cleaned
            cleaned.startsWith("/") -> "https://github.com$cleaned"
            else -> null
        }
    }

    private fun parseSizeBytes(
        sizeValue: String,
        sizeUnit: String
    ): Long {
        val number = sizeValue.trim().toDoubleOrNull() ?: return 0L
        val multiplier = when (sizeUnit.trim().uppercase(Locale.ROOT)) {
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024.0
            "GB" -> 1024.0 * 1024.0 * 1024.0
            "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (number * multiplier).roundToLong()
    }

    private fun inferAssetContentType(fileName: String): String {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".apkm") -> "application/octet-stream"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> "application/gzip"
            else -> ""
        }
    }

    private fun findMatchingRelease(releases: JSONArray, rawTag: String): JSONObject? {
        val normalizedTag = rawTag.trim()
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            val candidateTag = release.optString("tag_name").trim()
            if (candidateTag.equals(normalizedTag, ignoreCase = true)) {
                return release
            }
            val htmlUrl = release.optString("html_url").trim()
            if (parseReleaseTagFromUrl(htmlUrl).equals(normalizedTag, ignoreCase = true)) {
                return release
            }
        }
        return null
    }

    private fun buildReleaseStub(
        releaseName: String,
        rawTag: String,
        releaseUrl: String,
        releaseUpdatedAtMillis: Long? = null,
        releaseNotesBody: String = "",
        assets: List<GitHubReleaseAssetFile> = emptyList()
    ): JSONObject = GitHubReleaseAssetJsonMapper.buildReleaseStub(
        releaseName = releaseName,
        rawTag = rawTag,
        releaseUrl = releaseUrl,
        releaseUpdatedAtMillis = releaseUpdatedAtMillis,
        releaseNotesBody = releaseNotesBody,
        assets = assets
    )

    private fun parseReleaseBundle(release: JSONObject): GitHubReleaseAssetBundle =
        GitHubReleaseAssetJsonMapper.parseReleaseBundle(release)

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching { if (isBlank()) null else Instant.parse(this).toEpochMilli() }.getOrNull()
    }

    private fun String.stripHtml(): String {
        return replace(htmlTagRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun String.decodeHtmlEntities(): String {
        return replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

}
