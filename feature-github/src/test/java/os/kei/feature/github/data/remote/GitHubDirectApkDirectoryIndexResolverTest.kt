package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.model.GitHubReleaseChannel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubDirectApkDirectoryIndexResolverTest {
    @Test
    fun `resolve directory index picks latest stable standard apk by default`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(server.url("/scene9/").toString())
                .getOrThrow()

            assertEquals("/scene9/", server.takeRequest().path)
            assertEquals("9.2.11", result?.version)
            assertEquals(
                "${server.url("/scene9/")}scene_9.2.11.apk",
                result?.downloadUrl
            )
            assertEquals(GitHubReleaseChannel.STABLE, result?.channel)
            assertEquals("scene_9.2.11.apk", result?.toAsset("fallback.apk")?.name)
        }
    }

    @Test
    fun `resolve directory index can prefer latest pre-release standard apk`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(
                    rawUrl = server.url("/scene9/").toString(),
                    preferPreRelease = true
                )
                .getOrThrow()

            assertEquals("/scene9/", server.takeRequest().path)
            assertEquals("9.3.0 Alpha12", result?.version)
            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12.apk",
                result?.downloadUrl
            )
            assertEquals(GitHubReleaseChannel.ALPHA, result?.channel)
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
                "${server.url("/scene9/")}scene_9.2.11.apk",
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
                "${server.url("/scene9/")}scene_9.2.11%28Core%20Edition%29.apk",
                result?.downloadUrl
            )
        }
    }

    @Test
    fun `resolve apk url keeps core variant and can prefer pre-release`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(
                    rawUrl = "${server.url("/scene9/")}scene_9.3.0%20Alpha9%28Core%20Edition%29.apk",
                    preferPreRelease = true
                )
                .getOrThrow()

            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12%28Core%20Edition%29.apk",
                result?.downloadUrl
            )
            assertEquals(GitHubReleaseChannel.ALPHA, result?.channel)
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
                "${server.url("/scene9/")}scene_9.2.11%28Core%20Edition%29.apk",
                result?.downloadUrl
            )
        }
    }

    @Test
    fun `resolve directory index can use local core variant with pre-release preference`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(
                    rawUrl = server.url("/scene9/").toString(),
                    localVersion = "9.3.0 Alpha9 Core Edition",
                    preferPreRelease = true
                )
                .getOrThrow()

            assertEquals(
                "${server.url("/scene9/")}scene_9.3.0%20Alpha12%28Core%20Edition%29.apk",
                result?.downloadUrl
            )
            assertEquals(GitHubReleaseChannel.ALPHA, result?.channel)
        }
    }

    @Test
    fun `resolve targets reads Scene release logs for stable and pre-release`() {
        MockWebServer().use { server ->
            server.enqueue(sceneIndexResponse(includeLogs = true))
            server.enqueue(sceneLogsResponse())

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolveTargets(
                    rawUrl = server.url("/scene9/").toString(),
                    includePreRelease = true
                )
                .getOrThrow()

            assertEquals("/scene9/", server.takeRequest().path)
            assertEquals("/scene9/Scene9.logs.txt", server.takeRequest().path)
            assertEquals("9.2.11", result?.stable?.version)
            assertTrue(result?.stable?.releaseNotes.orEmpty().contains("帧率记录修复"))
            assertEquals("9.3.0 Alpha12", result?.preRelease?.version)
            assertTrue(result?.preRelease?.releaseNotes.orEmpty().contains("SceneFAS适配"))
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
        return sceneIndexResponse(includeLogs = false)
    }

    private fun sceneIndexResponse(includeLogs: Boolean): MockResponse {
        val logsLink = if (includeLogs) {
            """<a href="Scene9.logs.txt">Scene9.logs.txt</a>"""
        } else {
            ""
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/html")
            .setBody(
                """
                <html><body><pre>
                <a href="../">../</a>
                <a href="compare/">compare/</a>
                $logsLink
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

    private fun sceneLogsResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain; charset=utf-8")
            .setBody(
                """
                ﻿** 更新后请重启手机！

                # 9.3.0 Alpha12
                -新增 SceneFAS适配骁龙865/855
                -优化 降低帧率记录的性能开销

                # 9.2.11
                -修复 帧率记录修复某些绿厂老登系统检测不到帧率
                """.trimIndent()
            )
    }
}
