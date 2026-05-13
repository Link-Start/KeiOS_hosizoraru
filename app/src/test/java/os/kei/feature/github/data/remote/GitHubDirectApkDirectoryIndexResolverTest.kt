package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubDirectApkDirectoryIndexResolverTest {
    @Test
    fun `resolve directory index picks latest standard apk by default`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(server.url("/scene9/").toString())
                .getOrThrow()

            assertEquals("/scene9/", server.takeRequest().path)
            assertEquals("9.3.0 Alpha12", result?.version)
            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12.apk",
                result?.downloadUrl
            )
            assertEquals("scene_9.3.0 Alpha12.apk", result?.toAsset("fallback.apk")?.name)
        }
    }

    @Test
    fun `resolve apk url keeps standard variant from reference file`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve("${server.url("/scene9/")}scene_9.3.0%20Alpha9.apk")
                .getOrThrow()

            assertEquals("/scene9/", server.takeRequest().path)
            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12.apk",
                result?.downloadUrl
            )
        }
    }

    @Test
    fun `resolve apk url keeps core variant from reference file`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve("${server.url("/scene9/")}scene_9.3.0%20Alpha9%28Core%20Edition%29.apk")
                .getOrThrow()

            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12%28Core%20Edition%29.apk",
                result?.downloadUrl
            )
        }
    }

    @Test
    fun `resolve directory index can use local core variant`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(
                    rawUrl = server.url("/scene9/").toString(),
                    localVersion = "9.3.0 Alpha9 Core Edition"
                )
                .getOrThrow()

            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12%28Core%20Edition%29.apk",
                result?.downloadUrl
            )
        }
    }

    @Test
    fun `resolve skips non directory non apk url`() {
        MockWebServer().use { server ->
            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(server.url("/dl/android/apk").toString())
                .getOrThrow()

            assertNull(result)
            assertEquals(0, server.requestCount)
        }
    }

    private fun sceneIndexResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/html")
            .setBody(
                """
                <html><body><pre>
                <a href="../">../</a>
                <a href="compare/">compare/</a>
                <a href="scene_9.2.11%28Core%20Edition%29.apk">scene_9.2.11(Core Edition).apk</a>
                <a href="scene_9.2.11.apk">scene_9.2.11.apk</a>
                <a href="scene_9.3.0%20Alpha9%28Core%20Edition%29.apk">scene_9.3.0 Alpha9(Core Edition).apk</a>
                <a href="scene_9.3.0%20Alpha9.apk">scene_9.3.0 Alpha9.apk</a>
                <a href="scene_9.3.0%20Alpha12%28Core%20Edition%29.apk">scene_9.3.0 Alpha12(Core Edition).apk</a>
                <a href="scene_9.3.0%20Alpha12.apk">scene_9.3.0 Alpha12.apk</a>
                </pre></body></html>
                """.trimIndent()
            )
    }
}
