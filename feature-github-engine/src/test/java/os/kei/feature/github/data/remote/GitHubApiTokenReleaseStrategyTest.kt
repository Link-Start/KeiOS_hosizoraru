package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubReleaseChannel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Test
import os.kei.core.io.BoundedContentTextReadTooLargeException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubApiTokenReleaseStrategyTest {
    private val strategy = GitHubApiTokenReleaseStrategy("test-token")

    @After
    fun tearDown() {
        GitHubApiTokenReleaseStrategy.clearSharedCaches()
    }

    @Test
    fun `release api rejects oversized chunked response`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setChunkedBody("x".repeat(13 * 1024 * 1024), 64 * 1024),
            )
            val boundedStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "",
                apiBaseUrl = server.url("/").toString(),
            )

            val error = boundedStrategy.loadSnapshotTrace("demo", "app")
                .result
                .exceptionOrNull()

            assertTrue(error is BoundedContentTextReadTooLargeException)
        }
    }

    @Test
    fun `parser filters drafts and keeps newest release first`() = runBlocking {
        val entries = strategy.parseReleaseEntries(
            json = releases(
                release(1, "v1.1.0", false, "2026-04-12T08:00:00Z"),
                release(2, "v1.2.0-beta1", true, "2026-04-13T08:00:00Z"),
                release(3, "v1.3.0", false, "2026-04-14T08:00:00Z", draft = true),
            ),
            owner = "demo",
            repo = "app"
        )

        assertEquals(2, entries.size)
        assertEquals("v1.2.0-beta1", entries.first().tag)
        assertTrue(entries.first().isLikelyPreRelease)
        assertFalse(entries.last().isLikelyPreRelease)
    }

    @Test
    fun `prerelease flag upgrades stable looking tag to preview channel`() = runBlocking {
        val entry = strategy.parseReleaseEntries(
            json = releases(release(9, "v2.0.0", true, "2026-04-13T12:00:00Z", name = "Version 2.0.0")),
            owner = "demo",
            repo = "app"
        ).single()

        assertTrue(entry.isLikelyPreRelease)
        assertEquals(GitHubReleaseChannel.PREVIEW, entry.channel)
    }

    @Test
    fun `blank token uses guest api without authorization header`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(successReleaseListResponse())
            val guestStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "",
                apiBaseUrl = server.url("/").toString()
            )

            val trace = guestStrategy.loadSnapshotTrace(owner = "demo", repo = "app")

            assertTrue(trace.result.isSuccess)
            assertEquals(GitHubApiAuthMode.Guest, trace.authMode)
            assertFalse(trace.fromCache)
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `token api sends bearer authorization header`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(successReleaseListResponse())
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val trace = tokenStrategy.loadSnapshotTrace(owner = "demo", repo = "app")

            assertTrue(trace.result.isSuccess)
            assertEquals(GitHubApiAuthMode.Token, trace.authMode)
            assertEquals("Bearer ghp_testtoken123", server.takeRequest().getHeader("Authorization"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `guest api rate limit error is actionable`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .addHeader("X-RateLimit-Remaining", "0")
                    .setBody("""{"message":"API rate limit exceeded for 127.0.0.1."}""")
            )
            val guestStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "",
                apiBaseUrl = server.url("/").toString()
            )

            val errorMessage = guestStrategy
                .loadSnapshot(owner = "demo", repo = "app")
                .exceptionOrNull()
                ?.message
                .orEmpty()

            assertTrue(errorMessage.contains("GitHub guest API is rate limited"))
            assertTrue(errorMessage.contains("enter a token"))
        }
    }

    @Test
    fun `second api load hits cache and avoids extra network request`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(successReleaseListResponse())
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val first = tokenStrategy.loadSnapshotTrace(owner = "demo", repo = "app")
            val second = tokenStrategy.loadSnapshotTrace(owner = "demo", repo = "app")

            assertTrue(first.result.isSuccess)
            assertTrue(second.result.isSuccess)
            assertFalse(first.fromCache)
            assertTrue(second.fromCache)
            assertEquals(1, server.requestCount)
        }
    }

    /**
     * Histories that all carry an ordinary stable release, so the strategy must decide from the list
     * alone and never ask `releases/latest`. Each row is a repository shape that once picked the
     * wrong stable or pre-release line.
     */
    @Test
    fun `ordinary histories pick stable and prerelease from the list alone`() = runBlocking {
        data class History(val label: String, val releases: String, val stable: String, val preRelease: String?)
        val histories = listOf(
            History(
                "a same-base rc stays visible beside its final release",
                releases(
                    release(1, "3.8.0", false, "2026-04-09T19:28:15Z", body = "Stable build for everyone, thanks rc testers"),
                    release(2, "3.8.0-rc04", true, "2026-04-10T19:28:15Z"),
                ),
                stable = "3.8.0",
                preRelease = "3.8.0-rc04",
            ),
            History(
                "a stable release is not downgraded by rc words in its changelog",
                releases(
                    release(1, "1.4.3", false, "2026-04-13T10:28:20Z", body = "Fix pre-translation flow and rc migration leftovers"),
                    release(2, "1.4.2", false, "2026-04-11T11:03:39Z"),
                ),
                stable = "1.4.3",
                preRelease = null,
            ),
            History(
                "a placeholder prerelease without a version candidate is ignored",
                releases(
                    release(1, "v0.5.1", false, "2026-04-09T19:28:15Z", name = "Release"),
                    release(2, "Pre-release", true, "2026-04-10T19:28:15Z"),
                ),
                stable = "v0.5.1",
                preRelease = null,
            ),
            History(
                "a newer prerelease ahead of stable is kept visible",
                releases(
                    release(1, "v1.4.7-prerelease3", true, "2026-04-10T19:28:15Z"),
                    release(2, "v1.4.4-release", false, "2026-04-09T19:28:15Z"),
                ),
                stable = "v1.4.4-release",
                preRelease = "v1.4.7-prerelease3",
            ),
            History(
                "a branch-like historical prerelease does not surface",
                releases(
                    release(1, "v1.8.7", false, "2026-04-12T02:42:30Z"),
                    release(2, "dev-fix-access-denied-error-1", true, "2025-09-13T07:25:53Z"),
                ),
                stable = "v1.8.7",
                preRelease = null,
            ),
            History(
                "a newer beta wins over an older major branch beta (animeko)",
                releases(
                    release(1, "v5.4.3", false, "2026-04-12T02:42:30Z", name = "5.4.3"),
                    release(2, "v5.4.0-beta05", true, "2026-03-25T07:25:53Z", name = "5.4.0-beta05"),
                    release(3, "v4.11.0-beta01", true, "2026-02-01T07:25:53Z", name = "4.11.0-beta01"),
                ),
                stable = "v5.4.3",
                preRelease = "v5.4.0-beta05",
            ),
        )

        histories.forEach { history ->
            GitHubApiTokenReleaseStrategy.clearSharedCaches()
            MockWebServer().use { server ->
                server.failFastOnUnqueuedRequest()
                server.enqueue(MockResponse().setResponseCode(200).setBody(history.releases))
                val tokenStrategy = GitHubApiTokenReleaseStrategy(
                    apiToken = "ghp_testtoken123",
                    apiBaseUrl = server.url("/").toString()
                )

                val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

                assertEquals(1, server.requestCount, "${history.label}: must not ask releases/latest")
                assertTrue(snapshot.hasStableRelease, history.label)
                assertEquals(history.stable, snapshot.latestStable.rawTag, history.label)
                assertEquals(history.preRelease, snapshot.latestPreRelease?.rawTag, history.label)
            }
        }
    }

    @Test
    fun `prerelease only repository exposes newest build as prerelease without faking a stable channel`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        releases(
                            release(1, "0.0.8", true, "2026-04-13T10:28:20Z", name = "v0.0.8"),
                            release(2, "0.0.7", true, "2026-04-11T11:03:39Z", name = "v0.0.7"),
                        )
                    )
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("""{"message":"Not Found"}""")
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertFalse(snapshot.hasStableRelease)
            assertEquals("0.0.8", snapshot.latestStable.rawTag)
            assertEquals("v0.0.8", snapshot.latestStable.rawName)
            assertEquals("0.0.8", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `credential check reports guest quota without authorization header`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(rateLimitResponse(limit = 60, remaining = 57, used = 3, reset = 1_901_000_000L))
            val guestStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "",
                apiBaseUrl = server.url("/").toString()
            )

            val trace = guestStrategy.checkCredentialTrace()
            val status = trace.result.getOrThrow()

            assertEquals(GitHubApiAuthMode.Guest, status.authMode)
            assertEquals(60, status.coreLimit)
            assertEquals(57, status.coreRemaining)
            assertFalse(trace.fromCache)
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun `credential check reports token quota with authorization header`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(rateLimitResponse(limit = 5000, remaining = 4988, used = 12, reset = 1_901_000_000L))
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val trace = tokenStrategy.checkCredentialTrace()
            val status = trace.result.getOrThrow()

            assertEquals(GitHubApiAuthMode.Token, status.authMode)
            assertEquals(5000, status.coreLimit)
            assertEquals(4988, status.coreRemaining)
            assertEquals("Bearer ghp_testtoken123", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun `credential check surfaces invalid token`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"message":"Bad credentials"}""")
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_badtoken",
                apiBaseUrl = server.url("/").toString()
            )

            val error = tokenStrategy.checkCredentialTrace().result.exceptionOrNull()

            assertNotNull(error)
            assertTrue(error.message.orEmpty().contains("token is invalid"))
        }
    }

    @Test
    fun `second credential check hits cache`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(rateLimitResponse(limit = 5000, remaining = 4999, used = 1, reset = 1_901_000_000L))
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val first = tokenStrategy.checkCredentialTrace()
            val second = tokenStrategy.checkCredentialTrace()

            assertTrue(first.result.isSuccess)
            assertTrue(second.result.isSuccess)
            assertFalse(first.fromCache)
            assertTrue(second.fromCache)
            assertEquals(1, server.requestCount)
        }
    }

    /**
     * Answer an unqueued request at once instead of blocking until the read timeout, so a regression
     * that starts asking `releases/latest` fails on the request count straight away.
     */
    private fun MockWebServer.failFastOnUnqueuedRequest() {
        dispatcher = QueueDispatcher().apply { setFailFast(true) }
    }

    private fun successReleaseListResponse(): MockResponse =
        MockResponse().setResponseCode(200).setBody(releases(release(1, "v1.1.0", false, "2026-04-12T08:00:00Z")))

    private fun releases(vararg releases: String): String = releases.joinToString(",", "[", "]")

    private fun release(
        id: Int,
        tag: String,
        prerelease: Boolean,
        publishedAt: String,
        name: String = tag,
        body: String = "",
        draft: Boolean = false,
    ): String =
        """{"id":$id,"tag_name":"$tag","name":"$name","html_url":"https://github.com/demo/app/releases/tag/$tag",""" +
            """"body":"$body","draft":$draft,"prerelease":$prerelease,"published_at":"$publishedAt"}"""

    private fun rateLimitResponse(
        limit: Int,
        remaining: Int,
        used: Int,
        reset: Long
    ): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                    {
                      "resources": {
                        "core": {
                          "limit": $limit,
                          "remaining": $remaining,
                          "used": $used,
                          "reset": $reset
                        }
                      }
                    }
                """.trimIndent()
            )
    }
}
