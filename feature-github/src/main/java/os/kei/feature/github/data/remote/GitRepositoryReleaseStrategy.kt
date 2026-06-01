package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.core.io.SharedHttpClient
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.GitRepositoryTrackIdentity
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubVersionCandidateSource
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private data class GitRepositoryCachedSnapshot(
    val value: Result<GitHubRepositoryReleaseSnapshot>,
    val timestamp: Long
)

class GitRepositoryReleaseStrategy(
    private val identity: GitRepositoryTrackIdentity,
    private val client: OkHttpClient = defaultClient,
    private val gitLabApiBaseUrl: String = "https://${identity.host}/api/v4",
    private val giteeApiBaseUrl: String = "https://gitee.com/api/v5",
    private val genericRepositoryBaseUrl: String = buildGenericRepositoryBaseUrl(identity)
) : GitHubReleaseLookupStrategy {
    override val id: String = "git_repository_${identity.platform.storageId}"

    override fun loadSnapshot(owner: String, repo: String): Result<GitHubRepositoryReleaseSnapshot> {
        val key = "${identity.platform.storageId}|${identity.displayName}|$gitLabApiBaseUrl|$giteeApiBaseUrl|$genericRepositoryBaseUrl"
        val now = System.currentTimeMillis()
        snapshotCache[key]?.takeIf { now - it.timestamp < CACHE_TTL_MS }?.let { return it.value }

        val result = when (identity.platform) {
            GitRepositoryPlatform.GitHub -> {
                GitHubAtomReleaseStrategy.loadSnapshot(identity.namespace, identity.repo)
            }

            GitRepositoryPlatform.GitLab -> {
                loadGitLabSnapshot()
            }

            GitRepositoryPlatform.Gitee -> {
                loadGiteeSnapshot()
            }

            GitRepositoryPlatform.Generic -> {
                loadGenericGitSnapshot()
            }
        }
        if (result.isSuccess) {
            snapshotCache[key] = GitRepositoryCachedSnapshot(result, now)
        } else {
            snapshotCache.remove(key)
        }
        return result
    }

    override fun clearCaches() {
        snapshotCache.clear()
    }

    private fun loadGitLabSnapshot(): Result<GitHubRepositoryReleaseSnapshot> {
        val projectId = urlEncode("${identity.namespace}/${identity.repo}")
        val releasesUrl =
            "${gitLabApiBaseUrl.trimEnd('/')}/projects/$projectId/releases?per_page=30"
        val tagsUrl =
            "${gitLabApiBaseUrl.trimEnd('/')}/projects/$projectId/repository/tags?per_page=30"
        val releaseEntries = fetchJsonArray(releasesUrl)
            .map { releases ->
                parseReleaseEntries(
                    releases = releases,
                    releaseBodyKey = "description",
                    sourceBaseUrl = gitLabWebBaseUrl()
                )
            }
            .getOrElse { emptyList() }
        val tagEntries = fetchJsonArray(tagsUrl)
            .map { tags -> parseTagEntries(tags = tags, sourceBaseUrl = gitLabWebBaseUrl()) }
            .getOrDefault(emptyList())
        val entries = mergeReleaseAndTagEntries(
            releaseEntries = releaseEntries,
            tagEntries = tagEntries
        )
        return buildSnapshot(entries, feedUrl = releasesUrl)
    }

    private fun loadGiteeSnapshot(): Result<GitHubRepositoryReleaseSnapshot> {
        val ownerPath = identity.namespace
            .split('/')
            .joinToString("/") { it.urlEncodePathSegment() }
        val repoPath = identity.repo.urlEncodePathSegment()
        val releasesUrl =
            "${giteeApiBaseUrl.trimEnd('/')}/repos/$ownerPath/$repoPath/releases?per_page=30&direction=desc"
        val tagsUrl =
            "${giteeApiBaseUrl.trimEnd('/')}/repos/$ownerPath/$repoPath/tags?per_page=30&direction=desc"
        val releaseEntries = fetchJsonArray(releasesUrl)
            .map { releases ->
                parseReleaseEntries(
                    releases = releases,
                    releaseBodyKey = "body",
                    sourceBaseUrl = giteeWebBaseUrl()
                )
            }
            .getOrElse { emptyList() }
        val tagEntries = fetchJsonArray(tagsUrl)
            .map { tags -> parseTagEntries(tags = tags, sourceBaseUrl = giteeWebBaseUrl()) }
            .getOrDefault(emptyList())
        val entries = mergeReleaseAndTagEntries(
            releaseEntries = releaseEntries,
            tagEntries = tagEntries
        )
        return buildSnapshot(entries, feedUrl = releasesUrl)
    }

    private fun loadGenericGitSnapshot(): Result<GitHubRepositoryReleaseSnapshot> {
        val refsUrl = "${genericRepositoryBaseUrl.trimEnd('/')}/info/refs?service=git-upload-pack"
        return fetchText(refsUrl)
            .map { refs -> parseGitRefsTagEntries(refs, sourceBaseUrl = genericRepositoryBaseUrl) }
            .let { result ->
                if (result.getOrNull().orEmpty().isNotEmpty()) {
                    result.flatMapCompat { entries -> buildSnapshot(entries, feedUrl = refsUrl) }
                } else {
                    result.exceptionOrNull()?.let { Result.failure(it) }
                        ?: Result.failure(IllegalStateException("No Git tags found"))
                }
            }
    }

    private fun buildSnapshot(
        entries: List<GitHubAtomReleaseEntry>,
        feedUrl: String
    ): Result<GitHubRepositoryReleaseSnapshot> = runCatching {
        val sortedEntries = entries
            .distinctBy { it.tag.lowercase(Locale.ROOT) }
            .sortedWith(entryComparator.reversed())
        val latestStableEntry = pickLatestStableEntry(sortedEntries.filter { !it.isLikelyPreRelease })
        val latestPreEntry = pickLatestPreReleaseEntry(
            sortedEntries.filter { entry ->
                entry.isLikelyPreRelease &&
                    GitHubVersionUtils.hasMeaningfulPreReleaseVersionCandidates(
                        entry.versionCandidates,
                        GitHubVersionCandidateSource.Link.priority
                    )
            }
        )
        val hasStableRelease = latestStableEntry != null
        val effectiveStable = latestStableEntry ?: latestPreEntry ?: error("No Git release or tag entries found")
        val latestStableSignal = effectiveStable.toReleaseSignal()
        val latestPreSignal = latestPreEntry
            ?.takeUnless { pre ->
                hasStableRelease &&
                    GitHubVersionUtils.referToSameReleaseVersion(
                        pre.versionCandidates,
                        latestStableSignal.versionCandidates
                    )
            }
            ?.toReleaseSignal()
        GitHubRepositoryReleaseSnapshot(
            strategyId = id,
            feed = GitHubAtomFeed(
                title = "${identity.displayName} releases",
                feedUrl = feedUrl,
                updatedAtMillis = sortedEntries.firstOrNull()?.updatedAtMillis,
                entries = sortedEntries
            ),
            latestStable = latestStableSignal,
            hasStableRelease = hasStableRelease,
            latestPreRelease = latestPreSignal
        )
    }

    private fun parseReleaseEntries(
        releases: JSONArray,
        releaseBodyKey: String,
        sourceBaseUrl: String
    ): List<GitHubAtomReleaseEntry> {
        return buildList {
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", false)) continue
                val tag = release.optString("tag_name").trim()
                    .ifBlank { release.optString("tag").trim() }
                    .ifBlank { release.optString("name").trim() }
                if (tag.isBlank()) continue
                val title = release.optString("name").trim().ifBlank { tag }
                val body = release.optString(releaseBodyKey).trim()
                    .ifBlank { release.optString("description").trim() }
                    .ifBlank { release.optString("body").trim() }
                val contentPreview = GitHubAtomHeuristics.buildContentPreview(body)
                val channelFromText = GitHubAtomHeuristics.detectReleaseChannel(
                    tag = tag,
                    title = title,
                    contentPreview = contentPreview
                )
                val prerelease = release.optBoolean("prerelease", false) ||
                    release.optBoolean("pre_release", false) ||
                    channelFromText.isPreRelease
                val channel = when {
                    prerelease && !channelFromText.isPreRelease -> GitHubReleaseChannel.PREVIEW
                    prerelease -> channelFromText
                    else -> GitHubReleaseChannel.STABLE
                }
                val htmlUrl = release.optString("html_url").trim()
                    .ifBlank { release.optJSONObject("_links")?.optString("self").orEmpty().trim() }
                    .ifBlank { release.optString("url").trim() }
                    .ifBlank { buildReleaseUrl(sourceBaseUrl, tag) }
                val author = release.optJSONObject("author")
                val authorName = author?.optString("login").orEmpty().trim()
                    .ifBlank { author?.optString("username").orEmpty().trim() }
                    .ifBlank { author?.optString("name").orEmpty().trim() }
                val authorAvatarUrl = author?.optString("avatar_url").orEmpty().trim()
                    .ifBlank { author?.optString("avatar").orEmpty().trim() }
                add(
                    GitHubAtomReleaseEntry(
                        entryId = release.opt("id")?.toString().orEmpty().ifBlank { htmlUrl },
                        tag = tag,
                        title = title,
                        link = htmlUrl,
                        updatedAtMillis = release.optString("released_at").parseIsoInstantOrNull()
                            ?: release.optString("published_at").parseIsoInstantOrNull()
                            ?: release.optString("created_at").parseIsoInstantOrNull()
                            ?: release.optString("updated_at").parseIsoInstantOrNull(),
                        contentText = body,
                        authorName = authorName,
                        authorAvatarUrl = authorAvatarUrl,
                        versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                            GitHubVersionCandidateSource.Tag to tag,
                            GitHubVersionCandidateSource.Title to title,
                            GitHubVersionCandidateSource.Link to htmlUrl,
                            GitHubVersionCandidateSource.Id to release.opt("id")?.toString().orEmpty(),
                            GitHubVersionCandidateSource.Content to contentPreview
                        ),
                        channel = channel,
                        isLikelyPreRelease = prerelease
                    )
                )
            }
        }
    }

    private fun parseTagEntries(
        tags: JSONArray,
        sourceBaseUrl: String
    ): List<GitHubAtomReleaseEntry> {
        return buildList {
            for (index in 0 until tags.length()) {
                val tag = tags.optJSONObject(index) ?: continue
                val release = tag.optJSONObject("release")
                val tagName = tag.optString("name").trim()
                    .ifBlank { release?.optString("tag_name").orEmpty().trim() }
                if (tagName.isBlank()) continue
                val title = release?.optString("name").orEmpty().trim().ifBlank { tagName }
                val body = release?.optString("description").orEmpty().trim()
                    .ifBlank { release?.optString("body").orEmpty().trim() }
                    .ifBlank { tag.optString("message").trim() }
                val contentPreview = GitHubAtomHeuristics.buildContentPreview(body)
                val channel = GitHubAtomHeuristics.detectReleaseChannel(
                    tag = tagName,
                    title = title,
                    contentPreview = contentPreview
                )
                val htmlUrl = release?.optString("html_url").orEmpty().trim()
                    .ifBlank { release?.optJSONObject("_links")?.optString("self").orEmpty().trim() }
                    .ifBlank { buildTagUrl(sourceBaseUrl, tagName) }
                val commit = tag.optJSONObject("commit")
                val tagger = tag.optJSONObject("tagger")
                add(
                    GitHubAtomReleaseEntry(
                        entryId = tag.optString("target").trim()
                            .ifBlank { commit?.optString("id").orEmpty().trim() }
                            .ifBlank { commit?.optString("sha").orEmpty().trim() }
                            .ifBlank { htmlUrl },
                        tag = tagName,
                        title = title,
                        link = htmlUrl,
                        updatedAtMillis = tag.optString("created_at").parseIsoInstantOrNull()
                            ?: tagger?.optString("date").orEmpty().parseIsoInstantOrNull()
                            ?: commit?.optString("committed_date").orEmpty().parseIsoInstantOrNull()
                            ?: commit?.optString("created_at").orEmpty().parseIsoInstantOrNull()
                            ?: commit?.optString("date").orEmpty().parseIsoInstantOrNull(),
                        contentText = body,
                        versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                            GitHubVersionCandidateSource.Tag to tagName,
                            GitHubVersionCandidateSource.Title to title,
                            GitHubVersionCandidateSource.Link to htmlUrl,
                            GitHubVersionCandidateSource.Id to tag.optString("target").trim(),
                            GitHubVersionCandidateSource.Content to contentPreview
                        ),
                        channel = channel,
                        isLikelyPreRelease = channel.isPreRelease
                    )
                )
            }
        }
    }

    private fun parseGitRefsTagEntries(
        refs: String,
        sourceBaseUrl: String
    ): List<GitHubAtomReleaseEntry> {
        return gitTagRefRegex.findAll(refs)
            .map { match -> match.groupValues[1].removeSuffix("^{}").trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .map { tag ->
                val htmlUrl = buildTagUrl(sourceBaseUrl, tag)
                val channel = GitHubAtomHeuristics.detectReleaseChannel(
                    tag = tag,
                    title = tag,
                    contentPreview = ""
                )
                GitHubAtomReleaseEntry(
                    entryId = htmlUrl,
                    tag = tag,
                    title = tag,
                    link = htmlUrl,
                    versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                        GitHubVersionCandidateSource.Tag to tag,
                        GitHubVersionCandidateSource.Title to tag,
                        GitHubVersionCandidateSource.Link to htmlUrl
                    ),
                    channel = channel,
                    isLikelyPreRelease = channel.isPreRelease
                )
            }
            .toList()
    }

    private fun mergeReleaseAndTagEntries(
        releaseEntries: List<GitHubAtomReleaseEntry>,
        tagEntries: List<GitHubAtomReleaseEntry>
    ): List<GitHubAtomReleaseEntry> {
        val entriesByTag = linkedMapOf<String, GitHubAtomReleaseEntry>()
        releaseEntries.forEach { entry ->
            entriesByTag[entry.mergeKey] = entry
        }
        tagEntries.forEach { entry ->
            entriesByTag.putIfAbsent(entry.mergeKey, entry)
        }
        return entriesByTag.values.toList()
    }

    private fun fetchJsonArray(url: String): Result<JSONArray> {
        return fetchText(url).mapCatching { body -> JSONArray(body) }
    }

    private fun fetchText(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json,text/plain,text/html,*/*")
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
        return "Git repository request failed (HTTP ${response.code}${apiMessage.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""})"
    }

    private fun GitHubAtomReleaseEntry.toReleaseSignal(): GitHubReleaseVersionSignals {
        return GitHubReleaseVersionSignals(
            displayVersion = displayVersion,
            rawTag = tag,
            rawName = title,
            link = link,
            updatedAtMillis = updatedAtMillis,
            versionCandidates = versionCandidates,
            source = GitHubReleaseSignalSource.GitHubApi,
            channel = channel,
            authorName = authorName,
            authorAvatarUrl = authorAvatarUrl
        )
    }

    private fun gitLabWebBaseUrl(): String {
        return "https://${identity.host}/${identity.namespace}/${identity.repo}"
    }

    private fun giteeWebBaseUrl(): String {
        return "https://gitee.com/${identity.namespace}/${identity.repo}"
    }

    private fun buildReleaseUrl(sourceBaseUrl: String, tag: String): String {
        val base = sourceBaseUrl.trimEnd('/')
        val encodedTag = urlEncode(tag)
        return when (identity.platform) {
            GitRepositoryPlatform.GitLab -> "$base/-/releases/$encodedTag"
            else -> "$base/releases/tag/$encodedTag"
        }
    }

    private fun buildTagUrl(sourceBaseUrl: String, tag: String): String {
        val base = sourceBaseUrl.trimEnd('/')
        val encodedTag = urlEncode(tag)
        return when (identity.platform) {
            GitRepositoryPlatform.GitLab -> "$base/-/tags/$encodedTag"
            else -> "$base/releases/tag/$encodedTag"
        }
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching {
            if (isBlank()) null else Instant.parse(this).toEpochMilli()
        }.getOrNull()
    }

    private fun String.urlEncodePathSegment(): String {
        return urlEncode(this)
    }

    private fun urlEncode(raw: String): String {
        return URLEncoder.encode(raw.trim(), Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun <T, R> Result<T>.flatMapCompat(transform: (T) -> Result<R>): Result<R> {
        return fold(
            onSuccess = transform,
            onFailure = { Result.failure(it) }
        )
    }

    private fun pickLatestStableEntry(entries: List<GitHubAtomReleaseEntry>): GitHubAtomReleaseEntry? {
        return entries.maxWithOrNull(entryComparator)
    }

    private fun pickLatestPreReleaseEntry(entries: List<GitHubAtomReleaseEntry>): GitHubAtomReleaseEntry? {
        return entries.maxWithOrNull(entryComparator)
    }

    private val GitHubAtomReleaseEntry.mergeKey: String
        get() =
            tag.trim()
                .lowercase(Locale.ROOT)
                .replace(leadingVersionPrefixRegex, "")

    companion object {
        private const val CACHE_TTL_MS = 90_000L
        private const val GIT_USER_AGENT = "KeiOS-App/1.0 (Android Git Repository)"

        private val gitTagRefRegex = Regex("""refs/tags/([^\u0000\s^]+(?:\^\{\})?)""")
        private val leadingVersionPrefixRegex = Regex("""^v(?=\d)""", RegexOption.IGNORE_CASE)
        private val snapshotCache =
            ConcurrentHashMap<String, GitRepositoryCachedSnapshot>()
        private val entryComparator = Comparator<GitHubAtomReleaseEntry> { left, right ->
            val versionCompare =
                GitHubVersionUtils.compareStructuredCandidateSets(
                    left.versionCandidates,
                    right.versionCandidates
                )
            if (versionCompare != null && versionCompare != 0) {
                versionCompare
            } else {
                val updatedCompare =
                    compareValues(
                        left.updatedAtMillis ?: Long.MIN_VALUE,
                        right.updatedAtMillis ?: Long.MIN_VALUE
                    )
                if (updatedCompare != 0) updatedCompare else left.link.compareTo(right.link)
            }
        }

        private val defaultClient: OkHttpClient by lazy {
            SharedHttpClient.base.newBuilder()
                .callTimeout(18.seconds)
                .readTimeout(14.seconds)
                .build()
        }

        private fun buildGenericRepositoryBaseUrl(identity: GitRepositoryTrackIdentity): String {
            val raw = identity.url.trim()
            val base = when {
                raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) ->
                    raw.substringBefore('?').substringBefore('#').trimEnd('/')

                else -> "https://${identity.host}/${identity.namespace}/${identity.repo}"
            }
            return if (base.endsWith(".git", ignoreCase = true)) base else "$base.git"
        }
    }
}
