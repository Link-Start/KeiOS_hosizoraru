package os.kei.feature.github.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Test
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationEngine
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationPolicy
import os.kei.feature.github.fixture.ReleaseCorpusResources
import os.kei.feature.github.model.GitHubReleaseRejection
import os.kei.feature.github.model.GitHubTrackedReleaseStatus

/**
 * Atom mode against the two feeds that show what it cannot see, captured live.
 *
 * `releases.atom` carries no `prerelease` flag, no asset list, and a `<updated>` that is an *edit*
 * time rather than a publish time. Everything here is about what the pipeline does with those three
 * absences, and about the one thing it can still ask: where `releases/latest` redirects to.
 *
 * That lookup used to have two outcomes — a tag, or "this repository has no stable release" — and
 * the second one swallowed every way the request can fail. These tests are mostly about keeping the
 * three apart.
 */
class GitHubAtomLatestLookupCorpusTest {
    @After
    fun tearDown() {
        GitHubAtomReleaseStrategy.clearCaches()
    }

    /**
     * The report that closed in API mode and stayed open here.
     *
     * `MatsuriDayo/NekoBoxForAndroid` keeps a rolling `preview` tag with no APK. API mode retires it
     * two ways: the empty asset list, and a publish date a week older than the `1.4.2` it previewed.
     * The feed has neither — and worse, its `<updated>` puts the preview 29 seconds *newer* than the
     * stable, because shipping `1.4.2` is what edited the preview. The clock tolerance is what makes
     * those 29 seconds mean nothing.
     */
    @Test
    fun `a preview of a shipped version is retired even though the feed calls it newer`() = runBlocking {
        val result = withNekoBox(redirectingTo("1.4.2")) { snapshot ->
            GitHubReleaseEvaluationEngine.evaluate(
                localVersion = LOCAL_VERSION,
                localVersionCode = LOCAL_VERSION_CODE,
                snapshot = snapshot,
                // The reader had pre-release tracking on; that is the path the report came from.
                policy = GitHubReleaseEvaluationPolicy(preferPreRelease = true),
                nowMillis = STABLE_SHIPPED_MILLIS,
            )
        }

        assertFalse(result.hasPreReleaseUpdate, "there is no APK behind that tag and never was")
        assertFalse(result.recommendsPreRelease)
        assertEquals(GitHubTrackedReleaseStatus.UpToDate, result.status)
        assertEquals(GitHubReleaseRejection.SupersededByStable, result.preReleaseRejection)
    }

    /**
     * A rate limit is not a fact about the repository.
     *
     * Every non-redirect response used to set `hasStableRelease = false`, including on a feed the
     * strategy had just read a stable release out of. The card then carried "this project may only
     * have pre-releases" directly above the stable release it was displaying.
     */
    @Test
    fun `a lookup that could not be read falls back to the feed instead of denying the stable`() = runBlocking {
        val snapshot = withNekoBox(MockResponse().setResponseCode(429)) { it }

        assertTrue(snapshot.hasStableRelease, "the feed plainly contains stable releases")
        assertEquals("1.4.2", snapshot.latestStable.rawTag)
        assertEquals("preview", snapshot.latestPreRelease?.rawTag)
        assertEquals(
            "",
            GitHubReleaseEvaluationEngine.evaluate(
                localVersion = LOCAL_VERSION,
                localVersionCode = LOCAL_VERSION_CODE,
                snapshot = snapshot,
                nowMillis = STABLE_SHIPPED_MILLIS,
            ).releaseHint,
            "and no hint claiming otherwise",
        )
    }

    /**
     * The one answer that *is* a statement, and the one fact Atom mode can recover from it.
     *
     * `releases/latest` skips pre-releases, so its 404 means the repository has no stable release —
     * which also means every entry the text made look stable was misread. Correcting the lane is
     * free knowledge from a request already being made.
     */
    @Test
    fun `a 404 is GitHub saying there is no stable release, and it corrects the lanes`() = runBlocking {
        val snapshot = withNekoBox(MockResponse().setResponseCode(404)) { it }

        assertFalse(snapshot.hasStableRelease)
        assertTrue(
            snapshot.feed.entries.all { it.isLikelyPreRelease },
            "the feed read most of these as stable; the forge says none of them are",
        )
        assertNotNull(snapshot.latestPreRelease, "a repository with only previews still has a row")
        Unit
    }

    /**
     * `iebb/mithka` publishes CI builds as releases. Its ten-entry window today holds five nightlies
     * and five `play-version-code-…` tags its pipeline writes for its own bookkeeping — and no stable
     * release at all. That last tag parses as version 1,789,096,599.
     *
     * Offering it is the `stratumauth` failure mode: a number nothing the project ships can beat, so
     * the track goes quiet forever rather than wrong once. Two guesses have to stack for it — a lane
     * read out of prose, and a comparison sharing no leading digit with the reader's build — and the
     * evaluator now refuses that combination.
     */
    @Test
    fun `a bookkeeping tag is not offered as an update when nothing confirmed it`() = runBlocking {
        val snapshot = withFeed(MITHKA_FEED, MockResponse().setResponseCode(429)) { it }
        val result = GitHubReleaseEvaluationEngine.evaluate(
            localVersion = "1.4.6",
            localVersionCode = 146L,
            snapshot = snapshot,
        )

        assertFalse(result.hasUpdate, "1.4.6 -> 1,789,096,599 is not a version bump")
        assertEquals(GitHubTrackedReleaseStatus.ComparisonUncertain, result.status)
    }

    /** With the forge confirming, the same repository is answerable again. */
    @Test
    fun `the same repository is decided normally once the lookup answers`() = runBlocking {
        val snapshot = withFeed(MITHKA_FEED, redirectingTo("v1.4.6-nightly-2026-09-11")) { it }

        assertTrue(snapshot.hasStableRelease)
        assertEquals("v1.4.6-nightly-2026-09-11", snapshot.latestStable.rawTag)
        assertEquals(true, snapshot.selection?.stableCameFromForgeLatest)
    }

    /** The window is ten entries, fixed, and the record says so rather than implying a full history. */
    @Test
    fun `the ten entry window is recorded, not assumed to be everything`() = runBlocking {
        val snapshot = withNekoBox(redirectingTo("1.4.2")) { it }
        val selection = assertNotNull(snapshot.selection)

        assertEquals(10, selection.consideredCount)
        assertTrue(selection.windowWasFull, "releases.atom has no page parameter; ten is all there is")
    }

    /** A feed this parser cannot read is a failed load, not an exception thrown past the Result. */
    @Test
    fun `an unreadable feed comes back as a failure`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setResponseCode(200).setBody("<feed><entry><title>oops")
            }
            val outcome = runCatching { server.loadAtomSnapshotTrace().result }

            assertTrue(outcome.isSuccess, "nothing should be thrown out of the trace")
            assertTrue(outcome.getOrThrow().isFailure, "and the failure belongs in the Result")
        }
    }

    private fun redirectingTo(tag: String) = latestRedirect(tag, owner = "o", repo = "r")

    private suspend fun <T> withNekoBox(
        latest: MockResponse,
        block: suspend (os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot) -> T,
    ): T = withFeed(NEKOBOX_FEED, latest, block)

    private suspend fun <T> withFeed(
        resource: String,
        latest: MockResponse,
        block: suspend (os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot) -> T,
    ): T {
        return MockWebServer().use { server ->
            server.routeAtom(feed = ReleaseCorpusResources.text(resource), latest = latest)
            block(server.loadAtomSnapshotTrace(owner = "o", repo = "r").result.getOrThrow())
        }
    }
}

private const val NEKOBOX_FEED = "github/nekobox-releases.atom"
private const val MITHKA_FEED = "github/mithka-ci-releases.atom"

/** What the reporter had installed. */
private const val LOCAL_VERSION = "1.4.2"
private const val LOCAL_VERSION_CODE = 230L

/** The day `1.4.2` shipped, so the staleness rule reads the same whenever this runs. */
private const val STABLE_SHIPPED_MILLIS = 1770609300000L
