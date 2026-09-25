package os.kei.feature.github.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Test
import os.kei.core.versioning.ReleaseSelectionRule
import os.kei.feature.github.fixture.ReleaseCorpusResources

/**
 * The selection, read off a snapshot the strategy actually built over HTTP.
 *
 * The unit tests around the selector prove the rules; this proves the wiring, and pins the one
 * promise the rules make about cost: `releases/latest` is a second request per repository per
 * refresh, and an ordinary history must not pay it. That claim had only ever been argued.
 */
class GitHubReleaseSelectionEndToEndTest {
    @After
    fun tearDown() {
        GitHubApiTokenReleaseStrategy.clearSharedCaches()
    }

    @Test
    fun `a suspected reset is confirmed against the forge, and the snapshot says so`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = fixtureDispatcher(
                listBody = ReleaseCorpusResources.text("stratumauth-releases.json"),
                latestBody = LATEST_V1_6_2,
            )
            val strategy = GitHubApiTokenReleaseStrategy(apiBaseUrl = server.url("/").toString())

            val snapshot = strategy.loadSnapshotTrace("stratumauth", "app").result.getOrThrow()
            val selection = requireNotNull(snapshot.selection)

            assertEquals(2, server.requestCount, "the list, then the forge's own answer")
            assertEquals("v1.6.2", snapshot.latestStable.rawTag)
            assertTrue(selection.stableCameFromForgeLatest)
            assertEquals(ReleaseSelectionRule.VersioningReset, selection.stableRule)
            assertEquals("1.25.2", selection.stableRunnerUpTag)
            assertEquals(30, selection.consideredCount)
            assertTrue(selection.windowWasFull, "thirty of a longer history is a window")
        }
    }

    @Test
    fun `an ordinary history is decided from the list alone`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = fixtureDispatcher(
                listBody = ReleaseCorpusResources.text("nekobox-releases.json"),
                latestBody = null,
            )
            val strategy = GitHubApiTokenReleaseStrategy(apiBaseUrl = server.url("/").toString())

            val snapshot = strategy.loadSnapshotTrace("MatsuriDayo", "NekoBoxForAndroid")
                .result
                .getOrThrow()
            val selection = requireNotNull(snapshot.selection)

            assertEquals(1, server.requestCount, "an ordinary history pays for one request")
            assertFalse(selection.stableCameFromForgeLatest)
            assertEquals("1.4.2", snapshot.latestStable.rawTag)
            assertEquals("preview", selection.preRelease?.rawTag)
        }
    }

    private fun fixtureDispatcher(listBody: String, latestBody: String?): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/releases/latest") -> latestBody
                        ?.let { MockResponse().setResponseCode(200).setBody(it) }
                        ?: MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}""")
                    path.contains("/releases") ->
                        MockResponse().setResponseCode(200).setBody(listBody)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
}

/** What `releases/latest` returns for `stratumauth/app`: the maintainer's own flagged release. */
private val LATEST_V1_6_2 = """
    {
      "id": 1,
      "node_id": "R_latest",
      "tag_name": "v1.6.2",
      "name": "v1.6.2",
      "html_url": "https://github.com/stratumauth/app/releases/tag/v1.6.2",
      "body": "",
      "draft": false,
      "prerelease": false,
      "published_at": "2026-05-08T10:56:13Z",
      "assets": [{ "updated_at": "2026-05-08T11:02:00Z" }]
    }
""".trimIndent()
