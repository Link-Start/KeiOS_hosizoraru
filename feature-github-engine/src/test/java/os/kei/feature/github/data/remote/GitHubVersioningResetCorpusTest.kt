package os.kei.feature.github.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.engine.release.GitHubReleaseCandidateRanker
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationEngine
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationPolicy
import os.kei.feature.github.fixture.ReleaseCorpusResources
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.toReleaseVersionSignals

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
 *
 * Captured before the parser read asset lists, so every release here claimed to have nothing
 * attached and the installability rule was silently suppressed across the whole corpus. The asset
 * timestamps have since been backfilled from the same repository -- the projection
 * `scripts/qa/capture_release_fixture.sh` would have written at capture time, which is now the one
 * place the field list is recorded.
 */
class GitHubVersioningResetCorpusTest {
    private val entries by lazy {
        ReleaseCorpusResources.entries("stratumauth-releases.json", owner = "stratumauth", repo = "app")
    }

    @Test
    fun `the fixture is the shape the report describes`() {
        assertEquals(30, entries.size)
        assertEquals("v1.6.2", entries.first().tag, "entries arrive newest-published first")
        assertTrue(entries.any { it.tag == "1.25.2" }, "the old high tag must be in range")
        assertTrue(
            entries.all { it.hasDownloadableAsset == true },
            "every release here ships APKs -- a fixture missing its asset lists would claim the " +
                "opposite and silently switch off the installability rule for the whole corpus",
        )
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
     * The same repository's other lane, reported after the stable one was fixed. Its newest
     * pre-release is `1.24.0-beta` from December 2023 and the rebrand produced none since, so a 2023
     * beta outranks the 2026 stable and is offered as an update to it.
     *
     * The reset rule cannot reach this: it needs a run of newer releases ranking lower, and in this
     * lane there are no newer releases at all. What settles it is that the project has shipped for
     * two and a half years without touching the line.
     */
    @Test
    fun `the abandoned preview line is neither an update nor a row`() {
        val stable = requireNotNull(
            GitHubReleaseCandidateRanker.latest(entries.filter { !it.isLikelyPreRelease }),
        )
        val pre = requireNotNull(
            GitHubReleaseCandidateRanker.latest(entries.filter { it.isLikelyPreRelease }),
        )
        assertEquals("1.24.0-beta", pre.tag, "the newest pre-release predates the rebrand")
        assertTrue(
            GitHubVersionUtils.isAbandonedPreRelease(
                preReleaseFreshnessMillis = pre.effectiveFreshnessMillis,
                nowMillis = NOW_MILLIS,
            ),
        )

        val result = GitHubReleaseEvaluationEngine.evaluate(
            localVersion = "1.6.2",
            localVersionCode = 16L,
            snapshot = GitHubRepositoryReleaseSnapshot(
                strategyId = "test",
                feed = GitHubAtomFeed(entries = entries),
                latestStable = stable.toReleaseVersionSignals(),
                hasStableRelease = true,
                latestPreRelease = pre.toReleaseVersionSignals(),
            ),
            // The path the report came from: the reader had pre-release tracking switched on.
            policy = GitHubReleaseEvaluationPolicy(preferPreRelease = true),
            nowMillis = NOW_MILLIS,
        )

        assertFalse(result.hasPreReleaseUpdate, "a 2023 beta is not an update to a 2026 release")
        assertFalse(result.recommendsPreRelease)
        assertNull(result.preRelease, "and a closed line does not keep a row on the card")
        assertEquals("", result.preReleaseInfo)
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

/** The day `v1.6.2` shipped, so the corpus reads the same whenever this test is run. */
private const val NOW_MILLIS = 1778237773000L

/** 2024-06-25T17:09:47Z, the moment `1.25.2` shipped and the old numbering ended. */
private const val REBRAND_CUTOFF_MILLIS = 1719335387000L
