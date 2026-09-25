package os.kei.feature.github.engine.release

import org.junit.Test
import os.kei.core.versioning.VersionCandidate
import os.kei.core.versioning.VersionOrder
import os.kei.core.versioning.VersioningEngine
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubVersionCandidate
import os.kei.feature.github.model.GitHubVersionCandidateSource
import kotlin.test.assertEquals

class GitHubReleaseCandidateRankerTest {
    @Test
    fun `semantic version outranks a newer publication timestamp`() {
        val olderVersion = entry(tag = "v1.9.0", publishedAtMillis = 200L)
        val newerVersion = entry(tag = "v2.0.0", publishedAtMillis = 100L)

        assertEquals(newerVersion, GitHubReleaseCandidateRanker.latest(listOf(olderVersion, newerVersion)))
    }

    @Test
    fun `publication timestamp breaks equal version ties`() {
        val olderPublication = entry(tag = "v1.0.0", publishedAtMillis = 100L)
        val newerPublication = entry(tag = "1.0.0", publishedAtMillis = 200L)

        assertEquals(
            newerPublication,
            GitHubReleaseCandidateRanker.latest(listOf(olderPublication, newerPublication)),
        )
    }

    @Test
    fun `equal rolling builds retain source order instead of hash order`() {
        val firstFromSource = entry(
            tag = "v0.4.0-master.26071105.1aaaaaa",
            publishedAtMillis = 200L,
        )
        val secondFromSource = entry(
            tag = "v0.4.0-master.26071105.fbbbbbb",
            publishedAtMillis = 200L,
        )

        assertEquals(
            firstFromSource,
            GitHubReleaseCandidateRanker.latest(listOf(firstFromSource, secondFromSource)),
        )
    }

    /**
     * `iebb/mithka` shape: a flood of `v<base>-master.<build>.<hash>` pre-releases, listed oldest
     * build first with the oldest build carrying the newest timestamp. 105 is the count observed in
     * the 2026-07-11 capture; 1000 is the flood size.
     */
    @Test
    fun `rolling master builds rank by build number over timestamp and hash`() {
        listOf(
            Triple(105, "0.3.0", 26_070_000L),
            Triple(1_000, "9.0.0", 27_000_000L),
        ).forEach { (count, baseVersion, firstVersionCode) ->
            val builds = List(count) { index ->
                val hash = (index.toLong() * 2_654_435_761L).toString(16).takeLast(7).padStart(7, '0')
                val tag = "v$baseVersion-master.${firstVersionCode + index}.$hash"
                GitHubAtomReleaseEntry(
                    entryId = tag,
                    tag = tag,
                    title = "Build $baseVersion master $hash",
                    link = "https://github.com/fixture/app/releases/tag/$tag",
                    updatedAtMillis = (count - index).toLong(),
                    versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                        GitHubVersionCandidateSource.Tag to tag,
                    ),
                    channel = GitHubReleaseChannel.DEV,
                    isLikelyPreRelease = true,
                )
            }

            assertEquals(
                builds.last().tag,
                GitHubReleaseCandidateRanker.latest(builds)?.tag,
                "$count rolling builds on $baseVersion",
            )
        }
    }

    @Test
    fun `content noise cannot outrank a trusted release tag`() {
        val older = entry(
            tag = "v1.0.0",
            publishedAtMillis = 200L,
            contentCandidate = "migration notes mention v99.0.0",
        )
        val newer = entry(tag = "v2.0.0", publishedAtMillis = 100L)

        assertEquals(newer, GitHubReleaseCandidateRanker.latest(listOf(older, newer)))
    }

    @Test
    fun `semantic alpha tag outranks canary alias carrying the same build revision`() {
        val alpha = entry(
            tag = "Version.26.4.Alpha2_C384",
            title = "Version.26.4.Alpha2_C384",
            publishedAtMillis = 200L,
        )
        val canaryAlias = entry(
            tag = "Canary.Version_C384",
            title = "Canary Build Version.26.4.Canary_C384",
            publishedAtMillis = 100L,
        )
        val semanticComparison = VersioningEngine.compareRemoteCandidateSets(
            leftCandidates = alpha.versionCandidates.map { candidate ->
                VersionCandidate(candidate.value, candidate.source.priority)
            },
            rightCandidates = canaryAlias.versionCandidates.map { candidate ->
                VersionCandidate(candidate.value, candidate.source.priority)
            },
        )

        assertEquals(VersionOrder.Newer, semanticComparison?.order, semanticComparison.toString())
        assertEquals(
            alpha,
            GitHubReleaseCandidateRanker.latest(listOf(alpha, canaryAlias)),
            "alpha=${alpha.versionCandidates}, canary=${canaryAlias.versionCandidates}",
        )
    }

    private fun entry(
        tag: String,
        title: String = tag,
        publishedAtMillis: Long,
        contentCandidate: String = "",
    ): GitHubAtomReleaseEntry {
        return GitHubAtomReleaseEntry(
            entryId = tag,
            tag = tag,
            title = title,
            link = "https://example.test/releases/$tag",
            updatedAtMillis = publishedAtMillis,
            versionCandidates = VersioningEngine.buildCandidates(
                inputs = buildList {
                    add(GitHubVersionCandidateSource.Tag.priority to tag)
                    add(GitHubVersionCandidateSource.Title.priority to title)
                    if (contentCandidate.isNotBlank()) {
                        add(GitHubVersionCandidateSource.Content.priority to contentCandidate)
                    }
                },
            ).map { candidate ->
                GitHubVersionCandidate(
                    value = candidate.value,
                    source = GitHubVersionCandidateSource.entries.first { source ->
                        source.priority == candidate.sourcePriority
                    },
                )
            },
            channel = GitHubReleaseChannel.STABLE,
            isLikelyPreRelease = false,
        )
    }
}
