package os.kei.feature.github.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import os.kei.core.io.SharedHttpClient
import os.kei.core.io.cancellableResult
import os.kei.core.io.executeCancellable
import os.kei.core.io.stringLimitedBlocking
import os.kei.feature.github.engine.release.GitHubReleaseSelector
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.toReleaseVersionSignals
import java.net.URLDecoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private data class CachedValue<T>(
    val value: T,
    val timestamp: Long
)

/**
 * What `github.com/<owner>/<repo>/releases/latest` was able to tell us.
 *
 * Three answers, because the old code had two and the missing one was doing real damage: anything
 * that was not a redirect to a tag — a 404, a rate limit, a 5xx, an unreachable host, an HTML 200 —
 * was recorded as *this repository has no stable release*, which is a claim, not an absence of one.
 *
 * Only [NoStableRelease] is GitHub stating something. `/releases/latest` excludes pre-releases, so
 * its 404 means there is no non-pre-release release to point at. Everything else is [Unknown], and
 * an unknown falls back to the feed rather than overriding it.
 */
internal enum class GitHubAtomLatestOutcome {
    Resolved,
    NoStableRelease,
    Unknown,
}

internal data class GitHubAtomLatestLookup(
    val outcome: GitHubAtomLatestOutcome,
    val tag: String = "",
    val link: String = "",
)

object GitHubAtomReleaseStrategy : GitHubReleaseLookupStrategy {
    override val id: String = "atom_feed"

    private const val CACHE_TTL_MS = 90_000L
    private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
    private const val MAX_ATOM_RESPONSE_BYTES = 8L * 1024L * 1024L
    private const val HTTP_NOT_FOUND = 404

    /**
     * What `releases.atom` returns, always, with no way to ask for more.
     *
     * A third of what API mode reads, and not a setting. For a repository that publishes CI builds as
     * releases the whole window can be rolling builds -- `iebb/mithka` currently has ten entries and
     * no stable release among them -- which is why the redirect above is not a nicety here.
     */
    private const val ATOM_FEED_PAGE_SIZE = 10

    private val feedCache = ConcurrentHashMap<String, CachedValue<Result<GitHubAtomFeed>>>()
    private val stableCache = ConcurrentHashMap<String, CachedValue<Result<GitHubAtomLatestLookup>>>()

    private val githubClient: OkHttpClient by lazy {
        SharedHttpClient.base.newBuilder()
            .callTimeout(18.seconds)
            .readTimeout(14.seconds)
            .build()
    }

    private val githubNoRedirectClient: OkHttpClient by lazy {
        githubClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override suspend fun loadSnapshot(owner: String, repo: String): Result<GitHubRepositoryReleaseSnapshot> {
        return loadSnapshotTrace(owner, repo).result
    }

    suspend fun loadSnapshotTrace(
        owner: String,
        repo: String,
        atomFeedUrl: String = buildFeedUrl(owner, repo),
        latestReleaseUrl: String = buildLatestReleaseUrl(owner, repo),
        requestClient: OkHttpClient = githubClient,
        noRedirectRequestClient: OkHttpClient = githubNoRedirectClient
    ): GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot> {
        val startedAt = System.currentTimeMillis()
        // Both requests at once. They answer different questions -- what this repository has
        // published, and which release it calls current -- and neither needs the other's answer, so
        // running them in sequence spent two round trips on one repository's worth of information.
        // That is Atom mode's whole latency disadvantage: it always makes two requests where the API
        // usually makes one.
        val (feedTrace, latestTrace) = coroutineScope {
            val feed = async {
                fetchAtomFeedTrace(
                    owner = owner,
                    repo = repo,
                    atomFeedUrl = atomFeedUrl,
                    requestClient = requestClient
                )
            }
            val latest = async {
                fetchLatestStableLookupTrace(
                    owner = owner,
                    repo = repo,
                    latestReleaseUrl = latestReleaseUrl,
                    noRedirectRequestClient = noRedirectRequestClient
                )
            }
            feed.await() to latest.await()
        }
        val feed = feedTrace.result.getOrElse { error ->
            return GitHubStrategyLoadTrace(
                result = Result.failure(error),
                fromCache = feedTrace.fromCache,
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        }
        // The confirmation is a second request and it is allowed to fail. It used to take the whole
        // snapshot down with it, so a repository whose feed loaded perfectly reported a failed check
        // because an optional request timed out.
        val lookup = latestTrace.result.getOrElse {
            GitHubAtomLatestLookup(outcome = GitHubAtomLatestOutcome.Unknown)
        }

        val result = runCatching {
            val entries = feed.entries.withLatestKnowledge(lookup.outcome)
            val plan = GitHubReleaseSelector.plan(
                entries = entries,
                windowWasFull = entries.size >= ATOM_FEED_PAGE_SIZE,
                source = GitHubReleaseSignalSource.AtomFallback
            )
            val selection = plan.resolve(
                authoritativeStable = lookup
                    .takeIf { it.outcome == GitHubAtomLatestOutcome.Resolved }
                    ?.toAuthoritativeSignal(feed)
            )
            val latestStable = selection.stable
                ?: selection.preRelease
                ?: error("no release entries")

            GitHubRepositoryReleaseSnapshot(
                strategyId = id,
                feed = feed.copy(entries = entries),
                latestStable = latestStable,
                hasStableRelease = selection.hasStableRelease,
                latestPreRelease = selection.preRelease,
                selection = selection
            )
        }

        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = feedTrace.fromCache && latestTrace.fromCache,
            elapsedMs = System.currentTimeMillis() - startedAt
        )
    }

    /**
     * Fold what `/releases/latest` said back into the entries it describes.
     *
     * The feed carries no `prerelease` flag, so every entry's lane is a guess made from its tag,
     * title and body. [GitHubAtomLatestOutcome.NoStableRelease] is GitHub contradicting that guess
     * for the whole repository: it has no non-pre-release release, so an entry the text made look
     * stable is one this parser misread.
     *
     * Only the lane is corrected, not the channel. The channel is what the release's own text claims
     * to be, and that is still true — a release can call itself `1.2.0` and be published as a
     * pre-release. What changes is which row it belongs in.
     */
    private fun List<GitHubAtomReleaseEntry>.withLatestKnowledge(
        outcome: GitHubAtomLatestOutcome
    ): List<GitHubAtomReleaseEntry> {
        if (outcome != GitHubAtomLatestOutcome.NoStableRelease) return this
        return map { entry ->
            if (entry.isLikelyPreRelease) entry else entry.copy(isLikelyPreRelease = true)
        }
    }

    /** The redirect's tag, matched back to the entry that describes it where the feed has one. */
    private fun GitHubAtomLatestLookup.toAuthoritativeSignal(
        feed: GitHubAtomFeed
    ): GitHubReleaseVersionSignals {
        val matchedEntry = feed.entries.firstOrNull { entry ->
            entry.tag.equals(tag, ignoreCase = true) ||
                GitHubVersionUtils.referToSameReleaseVersion(
                    GitHubVersionUtils.buildVersionCandidates(
                        GitHubVersionCandidateSource.Tag to tag
                    ),
                    entry.versionCandidates,
                    leftChannel = GitHubReleaseChannel.STABLE,
                    rightChannel = entry.channel,
                )
        }
        return matchedEntry
            ?.toReleaseVersionSignals(GitHubReleaseSignalSource.LatestRedirect)
            ?.copy(link = link)
            ?: GitHubReleaseVersionSignals(
                displayVersion = tag,
                rawTag = tag,
                rawName = tag,
                link = link,
                // The release is older than the ten entries the feed carries, so its own timestamp is
                // not available at any price. The feed's is the closest bound there is.
                updatedAtMillis = feed.updatedAtMillis,
                versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                    GitHubVersionCandidateSource.Tag to tag
                ),
                source = GitHubReleaseSignalSource.LatestRedirect,
                channel = GitHubAtomHeuristics.detectReleaseChannel(tag, tag, ""),
                authorName = ""
            )
    }

    suspend fun fetchAtomFeed(owner: String, repo: String): Result<GitHubAtomFeed> {
        return fetchAtomFeedTrace(owner, repo).result
    }

    internal suspend fun fetchAtomFeedTrace(
        owner: String,
        repo: String,
        atomFeedUrl: String = buildFeedUrl(owner, repo),
        requestClient: OkHttpClient = githubClient
    ): GitHubStrategyLoadTrace<GitHubAtomFeed> {
        val startedAt = System.currentTimeMillis()
        val key = "$owner/$repo|$atomFeedUrl"
        val now = System.currentTimeMillis()
        feedCache[key]?.takeIf { now - it.timestamp < CACHE_TTL_MS }?.let {
            return GitHubStrategyLoadTrace(
                result = it.value,
                fromCache = true,
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        }

        // mapCatching, not map: a feed this parser cannot read is a failed load, not an exception
        // thrown past the Result, the retry loop and the diagnostics that exist to describe it.
        val result = fetch(atomFeedUrl, requestClient).mapCatching { body ->
            parseAtomFeed(xml = body, feedUrl = atomFeedUrl)
        }
        if (result.isSuccess) {
            feedCache[key] = CachedValue(result, now)
        } else {
            feedCache.remove(key)
        }
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt
        )
    }

    suspend fun fetchReleaseEntries(owner: String, repo: String, limit: Int = 30): Result<List<GitHubAtomReleaseEntry>> {
        return fetchAtomFeed(owner, repo).map { it.entries.take(limit) }
    }

    private suspend fun fetchLatestStableLookupTrace(
        owner: String,
        repo: String,
        latestReleaseUrl: String = buildLatestReleaseUrl(owner, repo),
        noRedirectRequestClient: OkHttpClient = githubNoRedirectClient
    ): GitHubStrategyLoadTrace<GitHubAtomLatestLookup> {
        val startedAt = System.currentTimeMillis()
        val key = "$owner/$repo|$latestReleaseUrl"
        val now = System.currentTimeMillis()
        stableCache[key]?.takeIf { now - it.timestamp < CACHE_TTL_MS }?.let {
            return GitHubStrategyLoadTrace(
                result = it.value,
                fromCache = true,
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        }

        val request = Request.Builder()
            .url(latestReleaseUrl)
            .get()
            .header("User-Agent", GITHUB_USER_AGENT)
            .build()

        val result = cancellableResult {
            noRedirectRequestClient.executeCancellable(request) { response ->
                val location = response.header("Location").orEmpty()
                val finalUrl = when {
                    location.isNotBlank() -> location
                    response.request.url.toString().contains("/releases/tag/") ->
                        response.request.url.toString()

                    else -> ""
                }
                when {
                    finalUrl.contains("/releases/tag/") -> GitHubAtomLatestLookup(
                        outcome = GitHubAtomLatestOutcome.Resolved,
                        tag = URLDecoder.decode(
                            finalUrl.substringAfterLast("/releases/tag/").trim('/'),
                            Charsets.UTF_8.name()
                        ),
                        link = finalUrl
                    )

                    // The one answer that is a statement. `/releases/latest` skips pre-releases, so a
                    // 404 is GitHub saying there is no non-pre-release release to point at.
                    response.code == HTTP_NOT_FOUND ->
                        GitHubAtomLatestLookup(outcome = GitHubAtomLatestOutcome.NoStableRelease)

                    // Rate limited, a 5xx, a redirect somewhere else, an interstitial. None of these
                    // is evidence about the repository, and reading them as one is what produced a
                    // card saying "may only have pre-releases" above the stable release it had found.
                    else -> GitHubAtomLatestLookup(outcome = GitHubAtomLatestOutcome.Unknown)
                }
            }
        }

        // An Unknown is not worth remembering for ninety seconds: the next refresh is the user asking
        // again, and the reason it failed is usually gone by then.
        if (result.getOrNull()?.outcome?.let { it != GitHubAtomLatestOutcome.Unknown } == true) {
            stableCache[key] = CachedValue(result, now)
        } else {
            stableCache.remove(key)
        }
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt
        )
    }

    override fun clearCaches() {
        feedCache.clear()
        stableCache.clear()
    }

    private suspend fun fetch(url: String, requestClient: OkHttpClient = githubClient): Result<String> = cancellableResult {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", GITHUB_USER_AGENT)
            .build()
        requestClient.executeCancellable(request) { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body.stringLimitedBlocking(MAX_ATOM_RESPONSE_BYTES)
        }
    }

    private fun buildFeedUrl(owner: String, repo: String): String {
        return "https://github.com/$owner/$repo/releases.atom"
    }

    private fun buildLatestReleaseUrl(owner: String, repo: String): String {
        return "https://github.com/$owner/$repo/releases/latest"
    }

    private fun parseAtomFeed(
        xml: String,
        feedUrl: String
    ): GitHubAtomFeed {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(xml.reader())
        }

        var feedTitle = ""
        var feedUpdatedAt: Long? = null
        val entries = mutableListOf<GitHubAtomReleaseEntry>()

        var eventType = parser.eventType
        var inEntry = false
        var inAuthor = false

        var entryId = ""
        var entryUpdatedText = ""
        var entryLink = ""
        var entryTitle = ""
        var entryContentHtml = ""
        var entryAuthorName = ""
        var entryAuthorAvatarUrl = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.orEmpty()
                    when {
                        name == "entry" -> {
                            inEntry = true
                            inAuthor = false
                            entryId = ""
                            entryUpdatedText = ""
                            entryLink = ""
                            entryTitle = ""
                            entryContentHtml = ""
                            entryAuthorName = ""
                            entryAuthorAvatarUrl = ""
                        }

                        name == "author" && inEntry -> inAuthor = true

                        name == "title" && !inEntry -> {
                            feedTitle = GitHubAtomHeuristics.decodeXmlEscapes(parser.nextText()).trim()
                        }

                        name == "updated" && !inEntry -> {
                            feedUpdatedAt = parser.nextText().trim().parseIsoInstantOrNull()
                        }

                        name == "id" && inEntry -> {
                            entryId = GitHubAtomHeuristics.decodeXmlEscapes(parser.nextText()).trim()
                        }

                        name == "updated" && inEntry -> {
                            entryUpdatedText = parser.nextText().trim()
                        }

                        name == "title" && inEntry -> {
                            entryTitle = GitHubAtomHeuristics.decodeXmlEscapes(parser.nextText()).trim()
                        }

                        name == "content" && inEntry -> {
                            entryContentHtml = GitHubAtomHeuristics.decodeXmlEscapes(parser.nextText()).trim()
                        }

                        name == "name" && inEntry && inAuthor -> {
                            entryAuthorName = GitHubAtomHeuristics.decodeXmlEscapes(parser.nextText()).trim()
                        }

                        name == "link" && inEntry -> {
                            val rel = parser.getAttributeValue(null, "rel").orEmpty()
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            if (rel == "alternate" && href.isNotBlank()) {
                                entryLink = href
                            }
                        }

                        name.endsWith("thumbnail") && inEntry -> {
                            entryAuthorAvatarUrl = parser.getAttributeValue(null, "url").orEmpty()
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name.orEmpty()
                    when {
                        name == "author" && inEntry -> inAuthor = false
                        name == "entry" && inEntry -> {
                            val effectiveLink = entryLink.ifBlank { entryId }
                            val derivedTag = extractTag(effectiveLink, entryTitle, entryId)
                            if (derivedTag.isNotBlank()) {
                                val contentText = GitHubAtomHeuristics.htmlToPlainText(entryContentHtml)
                                val contentPreview = GitHubAtomHeuristics.buildContentPreview(contentText)
                                val channel = GitHubAtomHeuristics.detectReleaseChannel(
                                    tag = derivedTag,
                                    title = entryTitle,
                                    contentPreview = contentPreview
                                )
                                val releaseEntry = GitHubAtomReleaseEntry(
                                    entryId = entryId,
                                    tag = derivedTag,
                                    title = entryTitle.ifBlank { derivedTag },
                                    link = effectiveLink,
                                    updatedAtMillis = entryUpdatedText.parseIsoInstantOrNull(),
                                    contentHtml = entryContentHtml,
                                    contentText = contentText,
                                    authorName = entryAuthorName,
                                    authorAvatarUrl = entryAuthorAvatarUrl,
                                    versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                                        GitHubVersionCandidateSource.Tag to derivedTag,
                                        GitHubVersionCandidateSource.Title to entryTitle,
                                        GitHubVersionCandidateSource.Link to effectiveLink,
                                        GitHubVersionCandidateSource.Id to entryId.substringAfterLast('/'),
                                        GitHubVersionCandidateSource.Content to contentPreview
                                    ),
                                    channel = channel,
                                    isLikelyPreRelease = channel.isPreRelease
                                )
                                if (!releaseEntry.isLikelyPreRelease ||
                                    GitHubVersionUtils.hasComparableVersionCandidates(
                                        releaseEntry.versionCandidates,
                                        GitHubVersionCandidateSource.Link.priority
                                    )
                                ) {
                                    entries += releaseEntry
                                }
                            }
                            inEntry = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val sortedEntries = entries.sortedByDescending { entry ->
            entry.updatedAtMillis ?: Long.MIN_VALUE
        }

        return GitHubAtomFeed(
            title = feedTitle,
            feedUrl = feedUrl,
            updatedAtMillis = feedUpdatedAt,
            entries = sortedEntries
        )
    }

    private fun extractTag(
        link: String,
        title: String,
        entryId: String
    ): String {
        return when {
            link.contains("/releases/tag/") -> URLDecoder.decode(
                link.substringAfter("/releases/tag/").trim('/'),
                Charsets.UTF_8.name()
            )

            entryId.isNotBlank() -> entryId.substringAfterLast('/').trim()
            else -> title.trim()
        }
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
    }
}
