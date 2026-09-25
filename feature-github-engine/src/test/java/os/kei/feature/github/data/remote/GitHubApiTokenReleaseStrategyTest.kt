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
            json = """
                [
                  {
                    "id": 1,
                    "node_id": "R_1",
                    "tag_name": "v1.1.0",
                    "name": "Version 1.1.0",
                    "html_url": "https://github.com/demo/app/releases/tag/v1.1.0",
                    "body": "Stable build",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-12T08:00:00Z",
                    "author": {
                      "login": "demo",
                      "avatar_url": "https://avatars.githubusercontent.com/u/1"
                    }
                  },
                  {
                    "id": 2,
                    "node_id": "R_2",
                    "tag_name": "v1.2.0-beta1",
                    "name": "Version 1.2.0 Beta 1",
                    "html_url": "https://github.com/demo/app/releases/tag/v1.2.0-beta1",
                    "body": "Preview build",
                    "draft": false,
                    "prerelease": true,
                    "published_at": "2026-04-13T08:00:00Z",
                    "author": {
                      "login": "demo",
                      "avatar_url": "https://avatars.githubusercontent.com/u/1"
                    }
                  },
                  {
                    "id": 3,
                    "node_id": "R_3",
                    "tag_name": "v1.3.0",
                    "name": "Version 1.3.0",
                    "html_url": "https://github.com/demo/app/releases/tag/v1.3.0",
                    "body": "Draft build",
                    "draft": true,
                    "prerelease": false,
                    "published_at": "2026-04-14T08:00:00Z",
                    "author": {
                      "login": "demo",
                      "avatar_url": "https://avatars.githubusercontent.com/u/1"
                    }
                  }
                ]
            """.trimIndent(),
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
            json = """
                [
                  {
                    "id": 9,
                    "node_id": "R_9",
                    "tag_name": "v2.0.0",
                    "name": "Version 2.0.0",
                    "html_url": "https://github.com/demo/app/releases/tag/v2.0.0",
                    "body": "Candidate build",
                    "draft": false,
                    "prerelease": true,
                    "published_at": "2026-04-13T12:00:00Z",
                    "author": {
                      "login": "demo",
                      "avatar_url": "https://avatars.githubusercontent.com/u/1"
                    }
                  }
                ]
            """.trimIndent(),
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

    @Test
    fun `a same-base rc stays visible beside its final release`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "3.8.0",
                                "name": "3.8.0",
                                "html_url": "https://github.com/demo/app/releases/tag/3.8.0",
                                "body": "Stable build for everyone, thanks rc testers",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-09T19:28:15Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "3.8.0-rc04",
                                "name": "3.8.0-rc04",
                                "html_url": "https://github.com/demo/app/releases/tag/3.8.0-rc04",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-04-10T19:28:15Z"
                              }
                            ]
                        """.trimIndent()
                    )
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertEquals(1, server.requestCount, "an ordinary history must not ask releases/latest")
            assertEquals("3.8.0", snapshot.latestStable.rawTag)
            assertEquals("3.8.0-rc04", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `stable latest release is not downgraded by rc words in changelog`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "1.4.3",
                                "name": "1.4.3",
                                "html_url": "https://github.com/demo/app/releases/tag/1.4.3",
                                "body": "Fix pre-translation flow and rc migration leftovers",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-13T10:28:20Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "1.4.2",
                                "name": "1.4.2",
                                "html_url": "https://github.com/demo/app/releases/tag/1.4.2",
                                "body": "Stable build",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-11T11:03:39Z"
                              }
                            ]
                        """.trimIndent()
                    )
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertEquals(1, server.requestCount, "an ordinary history must not ask releases/latest")
            assertTrue(snapshot.hasStableRelease)
            assertEquals("1.4.3", snapshot.latestStable.rawTag)
            assertNull(snapshot.latestPreRelease)
        }
    }

    @Test
    fun `placeholder prerelease without version candidate is ignored`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "v0.5.1",
                                "name": "Release",
                                "html_url": "https://github.com/demo/app/releases/tag/v0.5.1",
                                "body": "stable",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-09T19:28:15Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "Pre-release",
                                "name": "Pre-release",
                                "html_url": "https://github.com/demo/app/releases/tag/Pre-release",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-04-10T19:28:15Z"
                              }
                            ]
                        """.trimIndent()
                    )
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertEquals(1, server.requestCount, "an ordinary history must not ask releases/latest")
            assertEquals("v0.5.1", snapshot.latestStable.rawTag)
            assertNull(snapshot.latestPreRelease)
        }
    }

    @Test
    fun `newer prerelease ahead of stable is kept visible`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "v1.4.7-prerelease3",
                                "name": "v1.4.7-prerelease3",
                                "html_url": "https://github.com/demo/app/releases/tag/v1.4.7-prerelease3",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-04-10T19:28:15Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "v1.4.4-release",
                                "name": "v1.4.4-release",
                                "html_url": "https://github.com/demo/app/releases/tag/v1.4.4-release",
                                "body": "stable",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-09T19:28:15Z"
                              }
                            ]
                        """.trimIndent()
                    )
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertEquals(1, server.requestCount, "an ordinary history must not ask releases/latest")
            assertEquals("v1.4.4-release", snapshot.latestStable.rawTag)
            assertEquals("v1.4.7-prerelease3", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `branch like historical prerelease does not surface as latest prerelease`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "v1.8.7",
                                "name": "v1.8.7",
                                "html_url": "https://github.com/demo/app/releases/tag/v1.8.7",
                                "body": "stable",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-12T02:42:30Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "dev-fix-access-denied-error-1",
                                "name": "dev-fix-access-denied-error-1",
                                "html_url": "https://github.com/demo/app/releases/tag/dev-fix-access-denied-error-1",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2025-09-13T07:25:53Z"
                              }
                            ]
                        """.trimIndent()
                    )
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertEquals(1, server.requestCount, "an ordinary history must not ask releases/latest")
            assertEquals("v1.8.7", snapshot.latestStable.rawTag)
            assertNull(snapshot.latestPreRelease)
        }
    }

    @Test
    fun `latest prerelease selection prefers newer animeko beta over older major branch beta`() = runBlocking {
        MockWebServer().use { server ->
            server.failFastOnUnqueuedRequest()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "v5.4.3",
                                "name": "5.4.3",
                                "html_url": "https://github.com/demo/app/releases/tag/v5.4.3",
                                "body": "stable",
                                "draft": false,
                                "prerelease": false,
                                "published_at": "2026-04-12T02:42:30Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "v5.4.0-beta05",
                                "name": "5.4.0-beta05",
                                "html_url": "https://github.com/demo/app/releases/tag/v5.4.0-beta05",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-03-25T07:25:53Z"
                              },
                              {
                                "id": 3,
                                "node_id": "R_3",
                                "tag_name": "v4.11.0-beta01",
                                "name": "4.11.0-beta01",
                                "html_url": "https://github.com/demo/app/releases/tag/v4.11.0-beta01",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-02-01T07:25:53Z"
                              }
                            ]
                        """.trimIndent()
                    )
            )
            val tokenStrategy = GitHubApiTokenReleaseStrategy(
                apiToken = "ghp_testtoken123",
                apiBaseUrl = server.url("/").toString()
            )

            val snapshot = tokenStrategy.loadSnapshot(owner = "demo", repo = "app").getOrThrow()

            assertEquals(1, server.requestCount, "an ordinary history must not ask releases/latest")
            assertEquals("v5.4.3", snapshot.latestStable.rawTag)
            assertEquals("v5.4.0-beta05", snapshot.latestPreRelease?.rawTag)
        }
    }

    @Test
    fun `prerelease only repository exposes newest build as prerelease without faking a stable channel`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            [
                              {
                                "id": 1,
                                "node_id": "R_1",
                                "tag_name": "0.0.8",
                                "name": "v0.0.8",
                                "html_url": "https://github.com/demo/app/releases/tag/0.0.8",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-04-13T10:28:20Z"
                              },
                              {
                                "id": 2,
                                "node_id": "R_2",
                                "tag_name": "0.0.7",
                                "name": "v0.0.7",
                                "html_url": "https://github.com/demo/app/releases/tag/0.0.7",
                                "body": "preview",
                                "draft": false,
                                "prerelease": true,
                                "published_at": "2026-04-11T11:03:39Z"
                              }
                            ]
                        """.trimIndent()
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
     * These histories all carry an ordinary stable release, so the strategy must decide from the
     * list alone. Answer an unqueued request at once instead of blocking until the read timeout, so a
     * regression that starts asking `releases/latest` fails on the request count straight away.
     */
    private fun MockWebServer.failFastOnUnqueuedRequest() {
        dispatcher = QueueDispatcher().apply { setFailFast(true) }
    }

    private fun successReleaseListResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                    [
                      {
                        "id": 1,
                        "node_id": "R_1",
                        "tag_name": "v1.1.0",
                        "name": "Version 1.1.0",
                        "html_url": "https://github.com/demo/app/releases/tag/v1.1.0",
                        "body": "Stable build",
                        "draft": false,
                        "prerelease": false,
                        "published_at": "2026-04-12T08:00:00Z",
                        "author": {
                          "login": "demo",
                          "avatar_url": "https://avatars.githubusercontent.com/u/1"
                        }
                      }
                    ]
                """.trimIndent()
            )
    }

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
