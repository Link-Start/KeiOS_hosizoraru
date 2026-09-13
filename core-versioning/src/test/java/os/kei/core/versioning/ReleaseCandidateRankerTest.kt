package os.kei.core.versioning

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * Which release a list says is current, when the highest number is not it.
 *
 * The rule under test only exists for projects that restart their numbering, and its failure mode is
 * worse than the bug: picking the newest by date on a list that merely contains a backport hands the
 * user an older build and calls it an update. So the negative cases here matter more than the
 * positive one, and each names the shape it is protecting.
 */
class ReleaseCandidateRankerTest {
    /**
     * The report this was written for. `stratumauth/app` shipped as Authenticator Pro up to `1.25.2`
     * in June 2024, was rebranded, and restarted at `v1.0.1`. Ranked by version alone the answer is
     * `1.25.2` forever, and no release the project ever publishes can beat it — which is the part
     * that matters: the track does not merely show the wrong version, it stops reporting updates.
     */
    @Test
    fun `a project that restarted its numbering reports what it actually ships`() {
        val releases = evidenceOf(
            "v1.6.2" to "2026-05-08T10:56:13Z",
            "v1.6.1" to "2026-03-24T18:02:54Z",
            "v1.6.0" to "2026-03-21T17:52:35Z",
            "v1.5.0" to "2026-02-28T14:21:07Z",
            "v1.4.0" to "2025-08-17T12:07:29Z",
            "v1.3.0" to "2025-06-14T13:54:42Z",
            "v1.2.5" to "2025-05-28T17:58:14Z",
            "v1.1.0" to "2024-12-01T11:15:39Z",
            "v1.0.2" to "2024-10-18T18:15:02Z",
            "v1.0.1" to "2024-10-13T07:55:14Z",
            "1.25.2" to "2024-06-25T17:09:47Z",
            "1.25.1" to "2024-06-12T17:38:55Z",
            "1.25.0" to "2024-05-26T14:38:20Z",
            "1.24.1" to "2024-03-01T17:41:15Z",
        )

        assertEquals("v1.6.2", ReleaseCandidateRanker.pickLatest(releases).tagOf())

        val suspicion = assertNotNull(ReleaseCandidateRanker.suspectVersioningReset(releases))
        assertEquals("1.25.2", suspicion.outranking.tagOf())
        assertEquals("v1.6.2", suspicion.newest.tagOf())
    }

    /**
     * The case the rule must not break. A maintenance release on an older line lands after a new
     * major, so the newest by date is genuinely the older software and the version winner is right.
     * Separated from a restart by the gap: a backport follows weeks behind, not months.
     */
    @Test
    fun `a backport published after a new major does not demote the major`() {
        val releases = evidenceOf(
            "2.0.0" to "2026-01-10T00:00:00Z",
            "1.9.5" to "2026-02-14T00:00:00Z",
            "1.9.4" to "2025-12-01T00:00:00Z",
        )

        assertEquals("2.0.0", ReleaseCandidateRanker.pickLatest(releases).tagOf())
        assertNull(ReleaseCandidateRanker.suspectVersioningReset(releases))
    }

    /**
     * A long silence is not enough on its own. One stray tag published after a quiet year is a
     * backport, a hotfix or a mistake — not a project that has restarted, which leaves a run.
     */
    @Test
    fun `one late low release after a long gap is not a restart`() {
        val releases = evidenceOf(
            "3.4.0" to "2024-01-01T00:00:00Z",
            "1.0.1" to "2026-01-01T00:00:00Z",
        )

        assertEquals("3.4.0", ReleaseCandidateRanker.pickLatest(releases).tagOf())
        assertNull(ReleaseCandidateRanker.suspectVersioningReset(releases))
    }

    /**
     * A project still shipping on the high line has not restarted, however much it also publishes on
     * the old one. One release that matches or beats the version winner vetoes the whole reading.
     */
    @Test
    fun `a later release back on the high line vetoes the restart reading`() {
        val releases = evidenceOf(
            "3.4.0" to "2024-01-01T00:00:00Z",
            "1.0.1" to "2025-06-01T00:00:00Z",
            "1.0.2" to "2025-08-01T00:00:00Z",
            "1.0.3" to "2025-10-01T00:00:00Z",
            "3.5.0" to "2026-01-01T00:00:00Z",
        )

        assertEquals("3.5.0", ReleaseCandidateRanker.pickLatest(releases).tagOf())
        assertNull(ReleaseCandidateRanker.suspectVersioningReset(releases))
    }

    /** An ordinary ascending history must not pay for any of this. */
    @Test
    fun `an ordinary history still ranks by version`() {
        val releases = evidenceOf(
            "1.0.0" to "2025-01-01T00:00:00Z",
            "1.1.0" to "2025-06-01T00:00:00Z",
            "2.0.0" to "2026-01-01T00:00:00Z",
        )

        assertEquals("2.0.0", ReleaseCandidateRanker.pickLatest(releases).tagOf())
        assertNull(ReleaseCandidateRanker.suspectVersioningReset(releases))
    }

    /** Without dates there is no evidence either way, so the version winner stands. */
    @Test
    fun `undated releases cannot suggest a restart`() {
        val releases = listOf(
            ReleaseRankingEvidence(versionCandidates = tagCandidate("1.25.2"), stableKey = "1.25.2"),
            ReleaseRankingEvidence(versionCandidates = tagCandidate("v1.6.2"), stableKey = "v1.6.2"),
            ReleaseRankingEvidence(versionCandidates = tagCandidate("v1.6.1"), stableKey = "v1.6.1"),
            ReleaseRankingEvidence(versionCandidates = tagCandidate("v1.6.0"), stableKey = "v1.6.0"),
        )

        assertEquals("1.25.2", ReleaseCandidateRanker.pickLatest(releases).tagOf())
        assertNull(ReleaseCandidateRanker.suspectVersioningReset(releases))
    }

    @Test
    fun `an empty list has no latest`() {
        assertNull(ReleaseCandidateRanker.pickLatest(emptyList()))
        assertNull(ReleaseCandidateRanker.suspectVersioningReset(emptyList()))
    }
}

private fun tagCandidate(tag: String): List<VersionCandidate> =
    listOf(VersionCandidate(value = tag, sourcePriority = 0, channelHint = VersionChannel.STABLE))

private fun evidenceOf(vararg releases: Pair<String, String>): List<ReleaseRankingEvidence> =
    releases.map { (tag, publishedAt) ->
        ReleaseRankingEvidence(
            versionCandidates = tagCandidate(tag),
            publishedAtMillis = Instant.parse(publishedAt).toEpochMilli(),
            stableKey = tag,
        )
    }

/** The fixtures key every candidate on its tag, so this is the release the ranker chose. */
private fun ReleaseRankingEvidence?.tagOf(): String? = this?.stableKey
