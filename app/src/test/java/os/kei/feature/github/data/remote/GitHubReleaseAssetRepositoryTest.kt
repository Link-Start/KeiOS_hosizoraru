package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class GitHubReleaseAssetRepositoryTest {
    @Test
    fun `concurrent identical html asset fetches share one in flight request`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when {
                        request.path == "/release" -> {
                            Thread.sleep(80)
                            MockResponse().setBody(releaseHtml(server))
                        }

                        request.path == "/demo/app/releases/expanded_assets/v1" ->
                            MockResponse().setBody(expandedAssetsHtml())

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futures = List(2) {
                    executor.submit<GitHubReleaseAssetBundle> {
                        start.await(2, TimeUnit.SECONDS)
                        GitHubReleaseAssetRepository.fetchApkAssets(
                            owner = "demo",
                            repo = "app",
                            rawTag = "v1",
                            releaseUrl = server.url("/release").toString(),
                            preferHtml = true,
                            includeAllAssets = true
                        ).getOrThrow()
                    }
                }
                start.countDown()

                val bundles = futures.map { future -> future.get(10, TimeUnit.SECONDS) }

                bundles.forEach { bundle ->
                    assertEquals(listOf("demo.apk"), bundle.assets.map { it.name })
                    assertEquals("Notes", bundle.releaseNotesBody)
                }
                val requestPaths = server.takeRequestPaths()
                assertEquals(1, requestPaths.count { it == "/release" })
                assertEquals(
                    1,
                    requestPaths.count { it == "/demo/app/releases/expanded_assets/v1" })
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun MockWebServer.takeRequestPaths(): List<String> {
        return buildList {
            repeat(requestCount) {
                add(takeRequest().path.orEmpty())
            }
        }
    }

    private fun releaseHtml(server: MockWebServer): String {
        return """
            <html>
              <body>
                <h1 class="d-inline mr-3">Version 1</h1>
                <relative-time datetime="2026-05-01T10:00:00Z"></relative-time>
                <include-fragment src="${server.url("/demo/app/releases/expanded_assets/v1")}"></include-fragment>
                <div class="markdown-body"><p>Notes</p></div>
              </body>
            </html>
        """.trimIndent()
    }

    private fun expandedAssetsHtml(): String {
        return """
            <li class="Box-row">
              <a href="/demo/app/releases/download/v1/demo.apk" class="Truncate">demo.apk</a>
              <span>1 KB</span>
              <relative-time datetime="2026-05-01T10:05:00Z"></relative-time>
            </li>
        """.trimIndent()
    }
}
