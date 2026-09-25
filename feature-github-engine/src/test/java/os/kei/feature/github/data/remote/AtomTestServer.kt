package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStrategyLoadTrace

/** One `<entry>` of a `demo/app` releases feed, as GitHub writes it. */
internal data class AtomEntry(
    val tag: String,
    val updated: String,
    val title: String = tag,
    val content: String = "",
)

internal fun atomFeed(vararg entries: AtomEntry): String = buildString {
    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    appendLine("""<feed xmlns="http://www.w3.org/2005/Atom">""")
    appendLine("  <title>demo/app releases</title>")
    appendLine("  <updated>2026-04-13T10:00:00Z</updated>")
    entries.forEach { entry ->
        appendLine("  <entry>")
        appendLine("    <id>tag:github.com,2008:Repository/1/${entry.tag}</id>")
        appendLine("    <updated>${entry.updated}</updated>")
        appendLine("    <title>${entry.title}</title>")
        appendLine("""    <link rel="alternate" href="https://github.com/demo/app/releases/tag/${entry.tag}" />""")
        appendLine("""    <content type="html">${entry.content}</content>""")
        appendLine("    <author><name>demo</name></author>")
        appendLine("  </entry>")
    }
    append("</feed>")
}

/** What `releases/latest` answers when the maintainer's latest release is [tag]. */
internal fun latestRedirect(tag: String, owner: String = "demo", repo: String = "app"): MockResponse =
    MockResponse()
        .setResponseCode(302)
        .addHeader("Location", "https://github.com/$owner/$repo/releases/tag/$tag")

/**
 * Serve by path rather than by arrival order.
 *
 * The feed and the `releases/latest` lookup are issued together now, so `enqueue` — which hands out
 * responses first-come — would give one request the other's answer, at random. Routing on the path
 * says what these tests actually mean.
 */
internal fun MockWebServer.routeAtom(feed: String, latest: MockResponse) {
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            if (request.path.orEmpty().endsWith("releases.atom")) {
                MockResponse().setResponseCode(200).setBody(feed)
            } else {
                latest
            }
    }
}

/** Load [owner]/[repo] in Atom mode against this server's feed and `releases/latest` paths. */
internal suspend fun MockWebServer.loadAtomSnapshotTrace(
    owner: String = "demo",
    repo: String = "app",
): GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot> =
    GitHubAtomReleaseStrategy.loadSnapshotTrace(
        owner = owner,
        repo = repo,
        atomFeedUrl = url("/$owner/$repo/releases.atom").toString(),
        latestReleaseUrl = url("/$owner/$repo/releases/latest").toString(),
    )
