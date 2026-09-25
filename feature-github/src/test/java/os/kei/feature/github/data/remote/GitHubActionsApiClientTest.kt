package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.core.io.BoundedContentTextReadTooLargeException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubActionsApiClientTest {
    @Test
    fun `fetch json rejects oversized chunked response`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setChunkedBody("x".repeat(9 * 1024 * 1024), 32 * 1024),
            )
            val client = apiClient(server)

            val error = client.fetchJson(server.url("/actions/runs").toString())
                .exceptionOrNull()

            assertTrue(error is BoundedContentTextReadTooLargeException)
        }
    }

    private fun apiClient(
        server: MockWebServer,
        token: String = "",
    ): GitHubActionsApiClient {
        val client = OkHttpClient()
        return GitHubActionsApiClient(
            apiToken = token,
            client = client,
            noRedirectClient = client,
            apiBaseUrl = server.url("/").toString(),
        )
    }
}
