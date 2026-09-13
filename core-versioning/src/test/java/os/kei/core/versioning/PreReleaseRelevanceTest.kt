package os.kei.core.versioning

import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Whether a pre-release is still worth showing to someone on the current stable.
 *
 * The rule under test is the one that says no: a pre-release *of* a version that has since shipped
 * is spent. It has to be narrow, because the same shape — a pre-release numbered above the stable —
 * is the ordinary, still-relevant case, and suppressing that would hide real beta tracks.
 */
class PreReleaseRelevanceTest {
    /**
     * `MatsuriDayo/NekoBoxForAndroid`. The rolling `preview` tag was named `pre-1.4.2-20260202-1`
     * and `1.4.2` itself shipped a week later, so a user on 1.4.2 is ahead of it. The name parses as
     * 1.4.2 with a build stamp appended, which compares as *newer* — semver puts a pre-release
     * before the release it precedes, and the parser cannot see that from the digits alone.
     */
    @Test
    fun `a preview of a version that has since shipped is spent`() {
        assertFalse(
            relevance(
                preRelease = "pre-1.4.2-20260202-1" at "2026-02-02T08:35:00Z",
                stable = "1.4.2" at "2026-02-09T03:55:00Z",
            ),
        )
    }

    /**
     * The ordinary beta track, and the case this must never touch: a pre-release on a higher line
     * than the stable stays relevant however the dates fall. A maintenance release on the old line
     * landing later does not retire the beta.
     */
    @Test
    fun `a beta on a higher line survives a later stable on the old line`() {
        assertTrue(
            relevance(
                preRelease = "2.0.0-beta" at "2026-01-10T00:00:00Z",
                stable = "1.9.9" at "2026-02-14T00:00:00Z",
            ),
        )
    }

    /** From the tracked corpus: `v1.4.7-prerelease3` against a `v1.4.4` stable shares no prefix. */
    @Test
    fun `a prerelease numbered above the stable is untouched`() {
        assertTrue(
            relevance(
                preRelease = "v1.4.7-prerelease3" at "2026-01-10T00:00:00Z",
                stable = "v1.4.4" at "2026-02-14T00:00:00Z",
            ),
        )
    }

    /**
     * A rolling nightly does extend its stable's numbers, which is the same prefix shape. What
     * separates it is time: it keeps building *past* the release, so it is still ahead of it.
     */
    @Test
    fun `a nightly still building past its stable stays relevant`() {
        assertTrue(
            relevance(
                preRelease = "1.9.0.n488.nightly" at "2026-03-01T00:00:00Z",
                stable = "1.9.0" at "2026-02-01T00:00:00Z",
            ),
        )
    }

    /** Without both timestamps there is no supersession claim to make. */
    @Test
    fun `an undated preview is left to the version comparison`() {
        assertTrue(
            VersioningEngine.isRelevantPreRelease(
                preReleaseCandidates = candidates("pre-1.4.2-20260202-1"),
                stableCandidates = candidates("1.4.2"),
            ),
        )
    }
}

private infix fun String.at(publishedAt: String): Pair<String, Long> =
    this to Instant.parse(publishedAt).toEpochMilli()

private fun candidates(value: String): List<VersionCandidate> =
    listOf(VersionCandidate(value = value, sourcePriority = 0))

private fun relevance(
    preRelease: Pair<String, Long>,
    stable: Pair<String, Long>,
): Boolean = VersioningEngine.isRelevantPreRelease(
    preReleaseCandidates = candidates(preRelease.first),
    stableCandidates = candidates(stable.first),
    preReleaseUpdatedAtMillis = preRelease.second,
    stableUpdatedAtMillis = stable.second,
)
