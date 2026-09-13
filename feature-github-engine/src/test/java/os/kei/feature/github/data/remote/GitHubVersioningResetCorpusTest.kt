package os.kei.feature.github.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.engine.release.GitHubReleaseCandidateRanker

/**
 * `stratumauth/app`, from the real `releases?per_page=30` response.
 *
 * The project shipped as Authenticator Pro up to `1.25.2` in June 2024, was rebranded to Stratum,
 * and restarted at `v1.0.1`. Ranked by version alone, `1.25.2` wins and always will — which is why
 * the bug is not "shows the wrong number" but "can never report an update again".
 *
 * Driven through the strategy's own parser rather than hand-written evidence, because the thing that
 * decides this is the candidate set the parser builds, and these tags carry a trap: the old release
 * titles read `1.25.2 + Wear OS 2.15.4`, so a second, higher version number is in the text of every
 * one of them.
 */
class GitHubVersioningResetCorpusTest {
    private val strategy = GitHubApiTokenReleaseStrategy()

    private val entries by lazy {
        val json = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("stratumauth-releases.json"),
        ) { "missing stratumauth-releases.json fixture" }.use { stream ->
            stream.readBytes().decodeToString()
        }
        strategy.parseReleaseEntries(json = json, owner = "stratumauth", repo = "app")
    }

    @Test
    fun `the fixture is the shape the report describes`() {
        assertEquals(30, entries.size)
        assertEquals("v1.6.2", entries.first().tag, "entries arrive newest-published first")
        assertTrue(entries.any { it.tag == "1.25.2" }, "the old high tag must be in range")
    }

    @Test
    fun `the restart is recognised and the current release wins`() {
        val stable = entries.filter { !it.isLikelyPreRelease }

        assertTrue(
            GitHubReleaseCandidateRanker.suspectsVersioningReset(stable),
            "fifteen lower releases over two years after the high tag is a restart",
        )
        assertEquals(
            "v1.6.2",
            GitHubReleaseCandidateRanker.latest(stable)?.tag,
            "the ranker must stop returning the tag the project left behind",
        )
    }

    /**
     * The half that keeps the rule honest: the same corpus with everything published after the high
     * tag removed is an ordinary history, and must rank by version exactly as before.
     */
    @Test
    fun `the same corpus without the restart still ranks by version`() {
        val beforeRebrand = entries
            .filter { !it.isLikelyPreRelease }
            .filter { entry -> (entry.updatedAtMillis ?: 0L) <= REBRAND_CUTOFF_MILLIS }

        assertTrue(beforeRebrand.size >= 5, "fixture must still hold the pre-rebrand line")
        assertEquals(false, GitHubReleaseCandidateRanker.suspectsVersioningReset(beforeRebrand))
        assertEquals("1.25.2", GitHubReleaseCandidateRanker.latest(beforeRebrand)?.tag)
    }
}

/** 2024-06-25T17:09:47Z, the moment `1.25.2` shipped and the old numbering ended. */
private const val REBRAND_CUTOFF_MILLIS = 1719335387000L
