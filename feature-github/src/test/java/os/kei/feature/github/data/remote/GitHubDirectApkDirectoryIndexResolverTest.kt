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

class GitHubDirectApkDirectoryIndexResolverTest {
    @Test
    fun `resolve rejects oversized chunked directory index while streaming`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setChunkedBody("x".repeat(600 * 1024), 4 * 1024),
            )

            val error = GitHubDirectApkDirectoryIndexResolver()
                .resolve(server.url("/builds/").toString())
                .exceptionOrNull()

            assertTrue(error is BoundedContentTextReadTooLargeException)
        }
    }

    @Test
    fun `resolve directory index picks latest stable standard apk by default`() = runBlocking {
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
    fun `resolve directory index can prefer latest pre-release standard apk`() = runBlocking {
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
    fun `resolve directory index ranks unstable build numbers with shared version engine`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """
                        <a href="app_2.4.0-unstable2.apk">app_2.4.0-unstable2.apk</a>
                        <a href="app_2.4.0-unstable10.apk">app_2.4.0-unstable10.apk</a>
                        """.trimIndent(),
                    ),
            )

            val result = GitHubDirectApkDirectoryIndexResolver()
                .resolve(
                    rawUrl = server.url("/builds/").toString(),
                    preferPreRelease = true,
                )
                .getOrThrow()

            assertEquals("2.4.0 unstable10", result?.version)
            assertEquals(GitHubReleaseChannel.DEV, result?.channel)
        }
    }

    private data class VariantCase(
        val name: String,
        val referenceFile: String,
        val localVersion: String = "",
        val preferPreRelease: Boolean = false,
        val expectedFile: String,
        val expectedChannel: GitHubReleaseChannel? = null
    )

    @Test
    fun `resolve keeps the variant of the reference file or local version`() = runBlocking {
        val coreStable = "scene_9.2.11%28Core%20Edition%29.apk"
        val corePreRelease = "scene_9.3.0%20Alpha12%28Core%20Edition%29.apk"
        val cases = listOf(
            VariantCase(
                name = "apk url keeps standard variant from reference file",
                referenceFile = "scene_9.3.0%20Alpha9.apk",
                expectedFile = "scene_9.2.11.apk"
            ),
            VariantCase(
                name = "apk url keeps core variant from reference file",
                referenceFile = "scene_9.3.0%20Alpha9%28Core%20Edition%29.apk",
                expectedFile = coreStable
            ),
            VariantCase(
                name = "apk url keeps core variant and can prefer pre-release",
                referenceFile = "scene_9.3.0%20Alpha9%28Core%20Edition%29.apk",
                preferPreRelease = true,
                expectedFile = corePreRelease,
                expectedChannel = GitHubReleaseChannel.ALPHA
            ),
            VariantCase(
                name = "directory index can use local core variant",
                referenceFile = "",
                localVersion = "9.3.0 Alpha9 Core Edition",
                expectedFile = coreStable
            ),
            VariantCase(
                name = "directory index can use local core variant with pre-release preference",
                referenceFile = "",
                localVersion = "9.3.0 Alpha9 Core Edition",
                preferPreRelease = true,
                expectedFile = corePreRelease,
                expectedChannel = GitHubReleaseChannel.ALPHA
            )
        )

        cases.forEach { case ->
            MockWebServer().use { server ->
                server.enqueue(sceneIndexResponse())
                val directoryUrl = server.url("/scene9/").toString()

                val result = GitHubDirectApkDirectoryIndexResolver()
                    .resolve(
                        rawUrl = directoryUrl + case.referenceFile,
                        localVersion = case.localVersion,
                        preferPreRelease = case.preferPreRelease
                    )
                    .getOrThrow()

                assertEquals("/scene9/", server.takeRequest().path, case.name)
                assertEquals(directoryUrl + case.expectedFile, result?.downloadUrl, case.name)
                case.expectedChannel?.let { assertEquals(it, result?.channel, case.name) }
            }
        }
    }

    @Test
    fun `resolve targets reads Scene release logs for stable and pre-release`() = runBlocking {
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
    fun `resolve skips non directory non apk url`() = runBlocking {
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
