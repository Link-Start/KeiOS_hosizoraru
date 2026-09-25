package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.core.io.BoundedContentTextReadTooLargeException
import os.kei.feature.github.model.GitHubReleaseChannel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubDirectApkVersionedDirectoryResolverTest {
    @Test
    fun `resolve rejects oversized version directory index before parsing`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("x".repeat(600 * 1024)),
            )

            val error = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(server.url("/builds/1.0.0/android/app.apk").toString())
                .exceptionOrNull()

            assertTrue(error is BoundedContentTextReadTooLargeException)
        }
    }

    @Test
    fun `resolve picks latest version directory and keeps apk suffix`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """
                        <a href="/stable/1.9.10/">1.9.10</a>
                        <a href="/stable/1.22.1/">1.22.1</a>
                        <a href="/stable/1.22.2/">1.22.2</a>
                        """.trimIndent()
                    )
            )

            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(
                    server.url("/stable/1.22.1/android/RetroArch_aarch64.apk").toString()
                )
                .getOrThrow()

            assertEquals("/stable/", server.takeRequest().path)
            assertEquals("1.22.2", result?.version)
            assertEquals(
                server.url("/stable/1.22.2/android/RetroArch_aarch64.apk").toString(),
                result?.downloadUrl
            )
            assertEquals(GitHubReleaseChannel.STABLE, result?.channel)
            assertEquals("RetroArch_aarch64.apk", result?.toAsset("fallback.apk")?.name)
        }
    }

    @Test
    fun `resolve targets returns stable and pre-release with one index request`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """
                        <a href="/stable/1.22.2/">1.22.2</a>
                        <a href="/stable/1.23.0-beta1/">1.23.0-beta1</a>
                        <a href="/stable/1.23.0-beta2/">1.23.0-beta2</a>
                        """.trimIndent()
                    )
            )

            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolveTargets(
                    directApkUrl = server.url(
                        "/stable/1.22.2/android/RetroArch_aarch64.apk"
                    ).toString(),
                    includePreRelease = true
                )
                .getOrThrow()

            assertEquals("/stable/", server.takeRequest().path)
            assertEquals(1, server.requestCount)
            assertEquals("1.22.2", result?.stable?.version)
            assertEquals("1.23.0-beta2", result?.preRelease?.version)
        }
    }

    @Test
    fun `resolve version directories share unstable build ordering`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """
                        <a href="/builds/2.4.0-unstable2/">2.4.0-unstable2</a>
                        <a href="/builds/2.4.0-unstable10/">2.4.0-unstable10</a>
                        """.trimIndent(),
                    ),
            )

            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(
                    directApkUrl = server.url(
                        "/builds/2.4.0-unstable2/android/app.apk",
                    ).toString(),
                    preferPreRelease = true,
                )
                .getOrThrow()

            assertEquals("2.4.0-unstable10", result?.version)
            assertEquals(GitHubReleaseChannel.DEV, result?.channel)
            assertEquals(
                server.url("/builds/2.4.0-unstable10/android/app.apk").toString(),
                result?.downloadUrl,
            )
        }
    }

    @Test
    fun `resolve keeps custom version directory suffix compatibility`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """
                        <a href="/stable/1.22.2/">1.22.2</a>
                        <a href="/stable/1.22.3-hotfix/">1.22.3-hotfix</a>
                        """.trimIndent(),
                    ),
            )

            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(
                    server.url("/stable/1.22.2/android/app.apk").toString(),
                )
                .getOrThrow()

            assertEquals("1.22.3-hotfix", result?.version)
            assertEquals(GitHubReleaseChannel.UNKNOWN, result?.channel)
        }
    }

    @Test
    fun `resolve returns null when url has no version segment`() = runBlocking {
        MockWebServer().use { server ->
            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(server.url("/dl/android/apk").toString())
                .getOrThrow()

            assertNull(result)
            assertEquals(0, server.requestCount)
        }
    }
}
