package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals

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
    fun `resolve reads json feed url directly`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "version_name": "4.7.5",
                          "version_code": "71",
                          "download_url": "${server.url("/downloads/AuroraStore-4.7.5.apk")}",
                          "changelog": "No commits today."
                        }
                        """.trimIndent()
                    )
            )

            val result = GitHubDirectApkJsonFallbackResolver()
                .resolve(server.url("/downloads/AuroraStore/Feeds/release_feed.json").toString())
                .getOrThrow()

            assertEquals(
                "/downloads/AuroraStore/Feeds/release_feed.json",
                server.takeRequest().path
            )
            assertEquals("4.7.5", result?.versionName)
            assertEquals("71", result?.versionCode)
            assertEquals("AuroraStore-4.7.5.apk", result?.toAsset()?.name)
            assertEquals("No commits today.", result?.changelog)
        }
    }

    @Test
    fun `resolve reads nested release array`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "releases": [
                            {
                              "versionName": "1.2.0",
                              "versionCode": 120,
                              "apk_url": "${server.url("/files/app.apk")}",
                              "release_notes": "Nested notes"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

            val result = GitHubDirectApkJsonFallbackResolver()
                .resolve(server.url("/feeds/app.json").toString())
                .getOrThrow()

            assertEquals("1.2.0", result?.versionName)
            assertEquals("120", result?.versionCode)
            assertEquals("Nested notes", result?.changelog)
        }
    }
}
