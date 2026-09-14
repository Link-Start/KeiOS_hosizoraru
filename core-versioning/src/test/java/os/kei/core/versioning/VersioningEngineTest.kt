package os.kei.core.versioning

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VersioningEngineTest {
    @Test
    fun `hma oss release suffix compares by build number`() {
        assertOrder(
            expected = VersionOrder.Same,
            localVersion = "oss-164",
            remoteVersion = "oss-164",
        )
        assertOrder(
            expected = VersionOrder.Older,
            localVersion = "oss-162",
            remoteVersion = "oss-164",
        )
        assertOrder(
            expected = VersionOrder.Same,
            localVersion = "oss-164 (7304647)",
            remoteVersion = "oss-164",
        )
    }

    @Test
    fun `unstable suffix is classified as development channel`() {
        assertEquals(
            VersionChannel.DEV,
            VersioningEngine.classifyChannel("v2.4.0-unstable7"),
        )
    }

    @Test
    fun `stable release outranks same base release candidate`() {
        assertOrder(
            expected = VersionOrder.Older,
            localVersion = "3.8.0-rc04",
            remoteVersion = "3.8.0",
        )
    }

    @Test
    fun `newer alpha outranks older stable release`() {
        assertOrder(
            expected = VersionOrder.Newer,
            localVersion = "11.2.0-alpha01",
            remoteVersion = "11.1.0",
        )
    }

    @Test
    fun `date prefix exposes semantic release version`() {
        val comparison = VersioningEngine.compareLocalVersionToRemote(
            localVersion = "1.22",
            remoteCandidates = candidates(0 to "260412_1.22"),
        )

        assertEquals(VersionOrder.Same, comparison?.order)
    }

    @Test
    fun `title semantic version outranks raw date tag during release ranking`() {
        val left = candidates(
            0 to "r20260410",
            1 to "Release v2.8.0-20260410",
        )
        val right = candidates(0 to "v2.7.0")

        val comparison = VersioningEngine.compareRemoteCandidateSets(left, right)

        assertEquals(VersionOrder.Newer, comparison?.order)
        assertTrue(comparison?.leftEvidence.orEmpty().contains("2.8.0"))
    }

    @Test
    fun `hyphenated release date does not become a semantic version component`() {
        assertOrder(
            expected = VersionOrder.Same,
            localVersion = "2.7.0",
            remoteVersion = "Release v2.7.0-20260320",
        )
    }

    @Test
    fun `release ranking comparison is antisymmetric`() {
        val older = candidates(
            0 to "v1.9.0",
            4 to "changelog from v1.8.0 to v1.9.0",
        )
        val newer = candidates(
            0 to "v2.0.0",
            4 to "mentions v99.0.0 in an example",
        )

        val forward = VersioningEngine.compareRemoteCandidateSets(older, newer)
        val reverse = VersioningEngine.compareRemoteCandidateSets(newer, older)

        assertEquals(VersionOrder.Older, forward?.order)
        assertEquals(VersionOrder.Newer, reverse?.order)
        assertEquals(
            forward?.order?.legacyValue,
            reverse?.order?.legacyValue?.let { -it },
        )
    }

    @Test
    fun `release ranking remains transitive`() {
        val first = candidates(0 to "v1.0.0")
        val second = candidates(0 to "v1.5.0")
        val third = candidates(0 to "v2.0.0")

        assertEquals(
            VersionOrder.Older,
            VersioningEngine.compareRemoteCandidateSets(first, second)?.order,
        )
        assertEquals(
            VersionOrder.Older,
            VersioningEngine.compareRemoteCandidateSets(second, third)?.order,
        )
        assertEquals(
            VersionOrder.Older,
            VersioningEngine.compareRemoteCandidateSets(first, third)?.order,
        )
    }

    @Test
    fun `long numeric component remains comparable`() {
        assertOrder(
            expected = VersionOrder.Older,
            localVersion = "1.0.2147483648",
            remoteVersion = "1.0.2147483649",
        )
    }

    @Test
    fun `hyphen separated numeric components remain comparable`() {
        assertOrder(
            expected = VersionOrder.Older,
            localVersion = "1-2-3",
            remoteVersion = "1-2-4",
        )
    }

    @Test
    fun `recognized revision tokens break same version ties`() {
        assertOrder(
            expected = VersionOrder.Older,
            localVersion = "Version.1.3.Fix2_C359",
            remoteVersion = "Version.1.3.Fix3_C360",
        )
    }

    @Test
    fun `commit hash prefix is not treated as a revision token`() {
        val olderPublication = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v0.3.0-master.26070916.c82c367"),
            publishedAtMillis = 100L,
            stableKey = "c82c367",
        )
        val newerPublication = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v0.3.0-master.26070916.f4f0677"),
            publishedAtMillis = 200L,
            stableKey = "f4f0677",
        )

        assertTrue(ReleaseCandidateRanker.compare(olderPublication, newerPublication) < 0)
    }

    @Test
    fun `calendar build code rejects unrelated same width version code`() {
        val comparison = VersioningEngine.compareLocalVersionNameAndCodeToRemote(
            localVersion = "0.3.0",
            localVersionCode = 12_345_678L,
            remoteCandidates = listOf(
                VersionCandidate(
                    value = "v0.3.0-master.26071104.7880c18",
                    sourcePriority = 0,
                    channelHint = VersionChannel.DEV,
                ),
            ),
        )

        assertEquals(VersionComparisonReason.SemanticVersion, comparison?.reason)
        assertEquals(VersionOrder.Newer, comparison?.order)
    }

    @Test
    fun `calendar build code compares within matching timestamp scheme`() {
        val comparison = VersioningEngine.compareLocalVersionNameAndCodeToRemote(
            localVersion = "0.3.0",
            localVersionCode = 26_071_018L,
            remoteCandidates = listOf(
                VersionCandidate(
                    value = "v0.3.0-master.26071104.7880c18",
                    sourcePriority = 0,
                    channelHint = VersionChannel.DEV,
                ),
            ),
        )

        assertEquals(VersionComparisonReason.VersionCode, comparison?.reason)
        assertEquals(VersionOrder.Older, comparison?.order)
    }

    @Test
    fun `complete semantic candidate outranks a revision only tag alias`() {
        val alpha = candidates(
            0 to "Version.26.4.Alpha2_C384",
        )
        val canaryAlias = candidates(
            0 to "Canary.Version_C384",
            1 to "Canary Build Version.26.4.Canary_C384",
        )

        assertEquals(
            VersionOrder.Newer,
            VersioningEngine.compareRemoteCandidateSets(alpha, canaryAlias)?.order,
        )
    }

    @Test
    fun `release ranker uses publication time after semantic equality`() {
        val older = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v1.0.0"),
            publishedAtMillis = 100L,
            stableKey = "older",
        )
        val newer = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "1.0.0"),
            publishedAtMillis = 200L,
            stableKey = "newer",
        )

        assertTrue(ReleaseCandidateRanker.compare(older, newer) < 0)
    }

    @Test
    fun `release ranker keeps structured semantic order ahead of publication time`() {
        val older = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v1.9.0"),
            publishedAtMillis = 200L,
            stableKey = "older-version",
        )
        val newer = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v2.0.0"),
            publishedAtMillis = 100L,
            stableKey = "newer-version",
        )

        assertTrue(ReleaseCandidateRanker.compare(older, newer) < 0)
    }

    @Test
    fun `release ranker uses publication time for low confidence date tag conflicts`() {
        val dateTag = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "r20260410"),
            publishedAtMillis = 100L,
            stableKey = "date-tag",
        )
        val semanticTag = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v2.7.0"),
            publishedAtMillis = 200L,
            stableKey = "semantic-tag",
        )

        assertTrue(ReleaseCandidateRanker.compare(dateTag, semanticTag) < 0)
    }

    @Test
    fun `local version and build code can match remote composite tag`() {
        assertTrue(
            VersioningEngine.remoteCandidateMatchesLocalVersionNameAndCode(
                localVersion = "1.2.18",
                localVersionCode = 2102L,
                remoteCandidates = candidates(0 to "v1.2.18.2102"),
            ),
        )
    }

    @Test
    fun `rolling branch aliases classify as development releases`() {
        assertEquals(
            VersionChannel.DEV,
            VersioningEngine.classifyChannel("v0.3.0-master.26071104.7880c18"),
        )
        assertEquals(
            VersionChannel.DEV,
            VersioningEngine.classifyChannel("Mithka 0.3.0 main 7880c18"),
        )
        assertEquals(
            VersionChannel.STABLE,
            VersioningEngine.classifyChannel("v0.3.0-maintenance1"),
        )
    }

    @Test
    fun `rolling build number compares against android version code`() {
        val remote = candidates(0 to "v0.3.0-master.26071104.7880c18")

        assertEquals(
            VersionOrder.Older,
            VersioningEngine.compareLocalVersionNameAndCodeToRemote(
                localVersion = "0.3.0",
                localVersionCode = 26_071_103L,
                remoteCandidates = remote,
            )?.order,
        )
        assertEquals(
            VersionOrder.Same,
            VersioningEngine.compareLocalVersionNameAndCodeToRemote(
                localVersion = "0.3.0",
                localVersionCode = 26_071_104L,
                remoteCandidates = remote,
            )?.order,
        )
    }

    @Test
    fun `different android version code schemes fall back to semantic comparison`() {
        val comparison = VersioningEngine.compareLocalVersionNameAndCodeToRemote(
            localVersion = "0.3.0",
            localVersionCode = 1_788_000_000L,
            remoteCandidates = candidates(0 to "v0.3.0-master.26071104.7880c18"),
        )

        assertEquals(VersionComparisonReason.SemanticVersion, comparison?.reason)
        assertEquals(VersionOrder.Newer, comparison?.order)
    }

    @Test
    fun `newer rolling build remains relevant after same base stable release`() {
        assertTrue(
            VersioningEngine.isRelevantPreRelease(
                preReleaseCandidates = candidates(
                    0 to "v0.3.0-master.26071104.7880c18",
                ),
                stableCandidates = candidates(0 to "v0.3.0"),
                preReleaseFreshnessMillis = 200L,
                stableFreshnessMillis = 100L,
            ),
        )
    }

    @Test
    fun `explicit prerelease channel keeps stable looking tag identity separate`() {
        val preRelease = listOf(
            VersionCandidate(
                value = "v2.0.0",
                sourcePriority = 0,
                channelHint = VersionChannel.PREVIEW,
            ),
        )
        val stable = listOf(
            VersionCandidate(
                value = "v2.0.0",
                sourcePriority = 0,
                channelHint = VersionChannel.STABLE,
            ),
        )

        assertFalse(VersioningEngine.referToSameReleaseVersion(preRelease, stable))
        assertTrue(
            VersioningEngine.isRelevantPreRelease(
                preReleaseCandidates = preRelease,
                stableCandidates = stable,
                preReleaseFreshnessMillis = 200L,
                stableFreshnessMillis = 100L,
            ),
        )
    }

    @Test
    fun `rolling build sequence outranks misleading publication order`() {
        val olderBuild = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v0.3.0-master.26071103.46079b6"),
            publishedAtMillis = 300L,
            stableKey = "older-build",
        )
        val newerBuild = ReleaseRankingEvidence(
            versionCandidates = candidates(0 to "v0.3.0-master.26071104.7880c18"),
            publishedAtMillis = 200L,
            stableKey = "newer-build",
        )

        assertTrue(ReleaseCandidateRanker.compare(olderBuild, newerBuild) < 0)
    }

    private fun assertOrder(
        expected: VersionOrder,
        localVersion: String,
        remoteVersion: String,
    ) {
        val comparison = VersioningEngine.compareLocalVersionToRemote(
            localVersion = localVersion,
            remoteCandidates = candidates(0 to remoteVersion),
        )
        assertNotNull(comparison)
        assertEquals(expected, comparison.order)
    }

    private fun candidates(vararg values: Pair<Int, String>): List<VersionCandidate> {
        return VersioningEngine.buildCandidates(values.asIterable())
    }
}
