package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.core.io.BoundedContentTextReadTooLargeException
import kotlin.test.assertTrue

class GitHubRepositoryProfileHttpClientTest {
    @Test
    fun `profile json rejects oversized chunked response`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setChunkedBody("x".repeat(9 * 1024 * 1024), 32 * 1024),
            )
            val http = profileClient(server)

            val error = http.fetchJson(server.url("/repos/demo/app").toString(), "")
                .exceptionOrNull()

            assertTrue(error is BoundedContentTextReadTooLargeException)
        }
    }

    private fun profileClient(server: MockWebServer): GitHubRepositoryProfileHttpClient {
        return GitHubRepositoryProfileHttpClient(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/").toString(),
            htmlBaseUrl = server.url("/").toString(),
        )
    }
}
