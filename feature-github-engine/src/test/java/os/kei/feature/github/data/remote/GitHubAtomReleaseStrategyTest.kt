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
import kotlin.test.assertNull
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

    @Test
    fun `atom snapshot keeps stable redirect and prerelease entry`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(feed = sampleAtomFeedXml(), latest = latestRedirect("v1.1.0"))

            val trace = server.loadAtomSnapshotTrace()
            val snapshot = trace.result.getOrThrow()

            assertFalse(trace.fromCache)
            assertEquals("v1.1.0", snapshot.latestStable.rawTag)
            assertEquals("v1.2.0-beta1", snapshot.latestPreRelease?.rawTag)
            assertEquals(2, snapshot.feed.entries.size)
        }
    }

    @Test
    fun `atom snapshot keeps forward prerelease when it outruns stable`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(
                feed = atomFeed(
                    AtomEntry("v1.4.7-prerelease3", updated = "2026-04-13T09:00:00Z", content = "Preview build"),
                    AtomEntry("v1.4.4-release", updated = "2026-04-12T09:00:00Z", content = "Stable build"),
                ),
                latest = latestRedirect("v1.4.4-release"),
            )

            val trace = server.loadAtomSnapshotTrace()
            val snapshot = trace.result.getOrThrow()

            assertFalse(trace.fromCache)
            assertEquals("v1.4.4-release", snapshot.latestStable.rawTag)
            assertEquals("v1.4.7-prerelease3", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `atom latest redirect matches exact stable tag instead of newer alpha entry`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(
                feed = atomFeed(
                    AtomEntry(
                        "Version.26.4.Alpha2_C384",
                        updated = "2026-04-13T09:00:00Z",
                        content = "Alpha preview build",
                    ),
                    AtomEntry(
                        "Canary.Version_C384",
                        updated = "2026-04-13T08:59:00Z",
                        title = "Canary Build Version.26.4.Canary_C384",
                        content = "Canary build",
                    ),
                    AtomEntry("Version.1.3.Fix2_C359", updated = "2026-04-12T09:00:00Z", content = "Stable build"),
                ),
                latest = latestRedirect("Version.1.3.Fix2_C359"),
            )

            val snapshot = server.loadAtomSnapshotTrace().result.getOrThrow()

            assertTrue(snapshot.hasStableRelease)
            assertEquals("Version.1.3.Fix2_C359", snapshot.latestStable.rawTag)
            assertEquals("Version.26.4.Alpha2_C384", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `atom keeps rc prerelease visible when stable redirect points to same base final release`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(
                feed = atomFeed(
                    AtomEntry(
                        "3.8.0",
                        updated = "2026-04-12T09:00:00Z",
                        content = "Full Changelog 3.7.2-alpha02...3.8.0",
                    ),
                    AtomEntry(
                        "3.8.0-rc04",
                        updated = "2026-04-13T09:00:00Z",
                        content = "Full Changelog 3.8.0-rc03...3.8.0-rc04",
                    ),
                ),
                latest = latestRedirect("3.8.0"),
            )

            val snapshot = server.loadAtomSnapshotTrace().result.getOrThrow()

            assertTrue(snapshot.hasStableRelease)
            assertEquals("3.8.0", snapshot.latestStable.rawTag)
            assertEquals("3.8.0-rc04", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `atom snapshot keeps prerelease only repos explicit instead of faking stable channel`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(
                feed = atomFeed(
                    AtomEntry("0.0.8", updated = "2026-04-13T10:28:20Z", title = "v0.0.8", content = "Preview build"),
                    AtomEntry("0.0.7", updated = "2026-04-11T11:03:39Z", title = "v0.0.7", content = "Preview build"),
                ),
                latest = MockResponse().setResponseCode(404),
            )

            val trace = server.loadAtomSnapshotTrace()
            val snapshot = trace.result.getOrThrow()

            assertFalse(trace.fromCache)
            assertFalse(snapshot.hasStableRelease)
            assertEquals("0.0.8", snapshot.latestStable.rawTag)
            assertEquals("v0.0.8", snapshot.latestStable.rawName)
            assertEquals("0.0.8", snapshot.latestPreRelease?.rawTag)
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
