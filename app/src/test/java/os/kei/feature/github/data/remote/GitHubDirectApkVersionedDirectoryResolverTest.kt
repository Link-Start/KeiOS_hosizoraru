package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.model.GitHubReleaseChannel
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubDirectApkVersionedDirectoryResolverTest {
    @Test
    fun `resolve picks latest version directory and keeps apk suffix`() {
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
    fun `resolve can prefer latest pre-release version directory`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """
                        <a href="/stable/1.22.2/">1.22.2</a>
                        <a href="/stable/1.23.0-alpha1/">1.23.0-alpha1</a>
                        <a href="/stable/1.23.0-beta1/">1.23.0-beta1</a>
                        <a href="/stable/1.23.0-beta2/">1.23.0-beta2</a>
                        """.trimIndent()
                    )
            )

            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(
                    directApkUrl = server.url(
                        "/stable/1.22.2/android/RetroArch_aarch64.apk"
                    ).toString(),
                    preferPreRelease = true
                )
                .getOrThrow()

            assertEquals("/stable/", server.takeRequest().path)
            assertEquals("1.23.0-beta2", result?.version)
            assertEquals(
                server.url("/stable/1.23.0-beta2/android/RetroArch_aarch64.apk").toString(),
                result?.downloadUrl
            )
            assertEquals(GitHubReleaseChannel.BETA, result?.channel)
        }
    }

    @Test
    fun `resolve returns null when url has no version segment`() {
        MockWebServer().use { server ->
            val result = GitHubDirectApkVersionedDirectoryResolver()
                .resolve(server.url("/dl/android/apk").toString())
                .getOrThrow()

            assertNull(result)
            assertEquals(0, server.requestCount)
        }
    }
}
