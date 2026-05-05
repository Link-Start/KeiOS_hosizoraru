package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubRepositoryDiscoveryRepositoryTest {
    @Test
    fun `authenticated stars request sends bearer token and follows pagination`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader(
                        "Link",
                        """<${server.url("/user/starred?per_page=2&page=2")}>; rel="next""""
                    )
                    .setBody(
                        repositoryArrayJson(
                            repositoryJson(
                                owner = "alpha",
                                repo = "one",
                                stars = 12
                            ),
                            repositoryJson(
                                owner = "beta",
                                repo = "two",
                                stars = 4
                            )
                        )
                    )
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        repositoryArrayJson(
                            repositoryJson(
                                owner = "gamma",
                                repo = "three",
                                stars = 30
                            )
                        )
                    )
            )
            val repository = GitHubRepositoryDiscoveryRepository(
                apiToken = "token-123",
                client = OkHttpClient(),
                apiBaseUrl = server.url("/").toString()
            )

            val candidates =
                repository.fetchAuthenticatedStarredRepositories(limit = 3).getOrThrow()

            assertEquals(
                listOf("alpha/one", "beta/two", "gamma/three"),
                candidates.map { it.fullName })
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("/user/starred?per_page=3&page=1", first.path)
            assertEquals("/user/starred?per_page=3&page=2", second.path)
            assertEquals("Bearer token-123", first.getHeader("Authorization"))
            assertEquals("Bearer token-123", second.getHeader("Authorization"))
        }
    }

    @Test
    fun `public user stars request works without authorization header`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(repositoryArrayJson(repositoryJson(owner = "demo", repo = "app")))
            )
            val repository = GitHubRepositoryDiscoveryRepository(
                apiToken = "",
                client = OkHttpClient(),
                apiBaseUrl = server.url("/").toString()
            )

            val candidates =
                repository.fetchUserStarredRepositories(username = "voyager", limit = 1)
                    .getOrThrow()

            assertEquals("demo/app", candidates.single().fullName)
            val request = server.takeRequest()
            assertEquals("/users/voyager/starred?per_page=1&page=1", request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test
    fun `repository search keeps query intact and parses candidates`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "total_count": 1,
                          "items": [
                            ${
                            repositoryJson(
                                owner = "hosizoraru",
                                repo = "KeiOS",
                                description = "Android utility for os.kei",
                                language = "Kotlin",
                                stars = 88
                            )
                        }
                          ]
                        }
                        """.trimIndent()
                    )
            )
            val repository = GitHubRepositoryDiscoveryRepository(
                apiToken = "token-123",
                client = OkHttpClient(),
                apiBaseUrl = server.url("/").toString()
            )

            val query = "KeiOS android in:name,description,readme"
            val candidates = repository.searchRepositories(query = query, limit = 2).getOrThrow()

            assertEquals("hosizoraru/KeiOS", candidates.single().fullName)
            assertEquals("Kotlin", candidates.single().language)
            val request = server.takeRequest()
            assertEquals("/search/repositories", request.requestUrl?.encodedPath)
            assertEquals(query, request.requestUrl?.queryParameter("q"))
            assertEquals("2", request.requestUrl?.queryParameter("per_page"))
            assertEquals("Bearer token-123", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `star list url parser reads repository links from github stars page`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        <html>
                          <body>
                            <a href="/alpha/one">alpha/one</a>
                            <a href="/stars/voyager/lists/android">list</a>
                            <a href="https://github.com/beta/two">beta/two</a>
                            <a href="/alpha/one">duplicate</a>
                          </body>
                        </html>
                        """.trimIndent()
                    )
            )
            val repository = GitHubRepositoryDiscoveryRepository(
                apiToken = "token-123",
                client = OkHttpClient(),
                apiBaseUrl = server.url("/api").toString(),
                webBaseUrl = server.url("/").toString()
            )

            val candidates = repository.fetchStarListRepositories(
                starListUrl = "https://github.com/stars/voyager/lists/android",
                limit = 10
            ).getOrThrow()

            assertEquals(listOf("alpha/one", "beta/two"), candidates.map { it.fullName })
            val request = server.takeRequest()
            assertEquals("/stars/voyager/lists/android?page=1", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))
        }
    }


    private fun repositoryArrayJson(vararg items: String): String {
        return items.joinToString(prefix = "[", postfix = "]")
    }

    private fun repositoryJson(
        owner: String,
        repo: String,
        description: String = "Sample repository",
        language: String = "Kotlin",
        stars: Int = 0
    ): String {
        return """
            {
              "full_name": "$owner/$repo",
              "name": "$repo",
              "html_url": "https://github.com/$owner/$repo",
              "description": "$description",
              "language": "$language",
              "stargazers_count": $stars,
              "forks_count": 2,
              "archived": false,
              "fork": false,
              "updated_at": "2026-05-01T12:00:00Z",
              "owner": {
                "login": "$owner"
              }
            }
        """.trimIndent()
    }
}
