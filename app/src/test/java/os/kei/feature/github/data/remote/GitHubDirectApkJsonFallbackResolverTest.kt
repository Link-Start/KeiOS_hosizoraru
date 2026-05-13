package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubDirectApkJsonFallbackResolverTest {
    @Test
    fun `resolve reads companion json and exposes apk asset`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "version": "12.7.0",
                          "version_code": 67369,
                          "file_url": "${server.url("/file/Telegram-Beta.apk?token=demo")}",
                          "changelog": "beta fixes"
                        }
                        """.trimIndent()
                    )
            )

            val result = GitHubDirectApkJsonFallbackResolver()
                .resolve(server.url("/dl/android/apk-public-beta").toString())
                .getOrThrow()

            assertEquals(
                server.url("/dl/android/apk-public-beta.json").encodedPath,
                server.takeRequest().path?.substringBefore('?')
            )
            assertEquals("12.7.0", result?.versionName)
            assertEquals("67369", result?.versionCode)
            assertEquals("Telegram-Beta.apk", result?.toAsset()?.name)
            assertEquals("beta fixes", result?.changelog)
        }
    }

    @Test
    fun `resolve skips urls already pointing to json`() {
        MockWebServer().use { server ->
            val result = GitHubDirectApkJsonFallbackResolver()
                .resolve(server.url("/dl/android/apk-public-beta.json").toString())
                .getOrThrow()

            assertNull(result)
            assertEquals(0, server.requestCount)
        }
    }
}
