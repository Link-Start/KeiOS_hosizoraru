package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Test
import os.kei.core.io.BoundedContentTextReadTooLargeException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubAtomReleaseStrategyTest {
    @After
    fun tearDown() {
        GitHubAtomReleaseStrategy.clearCaches()
    }

    @Test
    fun `atom lookup rejects oversized chunked feed`() = runBlocking {
        MockWebServer().use { server ->
            // Routed rather than enqueued: the lookup goes out alongside the feed, so a single
            // queued response would land on whichever request happened to arrive first.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().endsWith("releases.atom")) {
                        MockResponse()
                            .setResponseCode(200)
                            .setChunkedBody("x".repeat(9 * 1024 * 1024), 64 * 1024)
                    } else {
                        MockResponse().setResponseCode(404)
                    }
            }

            val error = server.loadAtomSnapshotTrace().result.exceptionOrNull()

            assertTrue(error is BoundedContentTextReadTooLargeException)
        }
    }

    /** Each row is a feed plus the `releases/latest` answer, and the lanes the strategy must pick. */
    @Test
    fun `atom snapshot picks stable from the latest lookup and keeps the right prerelease`() = runBlocking {
        data class Case(
            val label: String,
            val feed: String,
            val latest: MockResponse,
            val stable: String,
            val preRelease: String?,
            val hasStable: Boolean = true,
            val stableName: String? = null,
        )
        val cases = listOf(
            Case(
                "stable redirect plus a newer beta entry",
                sampleAtomFeedXml(),
                latestRedirect("v1.1.0"),
                stable = "v1.1.0",
                preRelease = "v1.2.0-beta1",
            ),
            Case(
                "a forward prerelease that outruns stable stays visible",
                atomFeed(
                    AtomEntry("v1.4.7-prerelease3", updated = "2026-04-13T09:00:00Z", content = "Preview build"),
                    AtomEntry("v1.4.4-release", updated = "2026-04-12T09:00:00Z", content = "Stable build"),
                ),
                latestRedirect("v1.4.4-release"),
                stable = "v1.4.4-release",
                preRelease = "v1.4.7-prerelease3",
            ),
            Case(
                "the redirect matches the exact stable tag, not a newer alpha or its canary alias",
                atomFeed(
                    AtomEntry("Version.26.4.Alpha2_C384", updated = "2026-04-13T09:00:00Z", content = "Alpha preview build"),
                    AtomEntry(
                        "Canary.Version_C384",
                        updated = "2026-04-13T08:59:00Z",
                        title = "Canary Build Version.26.4.Canary_C384",
                        content = "Canary build",
                    ),
                    AtomEntry("Version.1.3.Fix2_C359", updated = "2026-04-12T09:00:00Z", content = "Stable build"),
                ),
                latestRedirect("Version.1.3.Fix2_C359"),
                stable = "Version.1.3.Fix2_C359",
                preRelease = "Version.26.4.Alpha2_C384",
            ),
            Case(
                "an rc stays visible when the redirect points at its same-base final release",
                atomFeed(
                    AtomEntry("3.8.0", updated = "2026-04-12T09:00:00Z", content = "Full Changelog 3.7.2-alpha02...3.8.0"),
                    AtomEntry("3.8.0-rc04", updated = "2026-04-13T09:00:00Z", content = "Full Changelog 3.8.0-rc03...3.8.0-rc04"),
                ),
                latestRedirect("3.8.0"),
                stable = "3.8.0",
                preRelease = "3.8.0-rc04",
            ),
            Case(
                "a prerelease-only repository stays explicit instead of faking a stable channel",
                atomFeed(
                    AtomEntry("0.0.8", updated = "2026-04-13T10:28:20Z", title = "v0.0.8", content = "Preview build"),
                    AtomEntry("0.0.7", updated = "2026-04-11T11:03:39Z", title = "v0.0.7", content = "Preview build"),
                ),
                MockResponse().setResponseCode(404),
                stable = "0.0.8",
                preRelease = "0.0.8",
                hasStable = false,
                stableName = "v0.0.8",
            ),
        )

        cases.forEach { case ->
            GitHubAtomReleaseStrategy.clearCaches()
            MockWebServer().use { server ->
                server.routeAtom(feed = case.feed, latest = case.latest)

                val snapshot = server.loadAtomSnapshotTrace().result.getOrThrow()

                assertEquals(case.hasStable, snapshot.hasStableRelease, case.label)
                assertEquals(case.stable, snapshot.latestStable.rawTag, case.label)
                assertEquals(case.preRelease, snapshot.latestPreRelease?.rawTag, case.label)
                case.stableName?.let { assertEquals(it, snapshot.latestStable.rawName, case.label) }
            }
        }
    }

    @Test
    fun `second atom snapshot hits both caches`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(feed = sampleAtomFeedXml(), latest = latestRedirect("v1.1.0"))

            val first = server.loadAtomSnapshotTrace()
            val second = server.loadAtomSnapshotTrace()

            assertTrue(first.result.isSuccess)
            assertTrue(second.result.isSuccess)
            assertFalse(first.fromCache)
            assertTrue(second.fromCache)
            assertEquals(2, server.requestCount)
        }
    }

    private fun sampleAtomFeedXml(): String = atomFeed(
        AtomEntry(
            "v1.2.0-beta1",
            updated = "2026-04-13T09:00:00Z",
            title = "Version 1.2.0 Beta 1",
            content = "Preview build",
        ),
        AtomEntry("v1.1.0", updated = "2026-04-12T09:00:00Z", title = "Version 1.1.0", content = "Stable build"),
    )
}
