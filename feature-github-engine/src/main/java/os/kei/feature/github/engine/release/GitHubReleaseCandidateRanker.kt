package os.kei.feature.github.engine.release

import os.kei.core.versioning.ReleaseCandidateRanker
import os.kei.core.versioning.ReleaseRankingEvidence
import os.kei.core.versioning.VersionCandidate
import os.kei.feature.github.data.remote.toCoreVersionChannelHint
import os.kei.feature.github.model.GitHubAtomReleaseEntry

object GitHubReleaseCandidateRanker {
    fun compare(
        left: GitHubAtomReleaseEntry,
        right: GitHubAtomReleaseEntry,
    ): Int {
        return ReleaseCandidateRanker.compare(
            left = left.toRankingEvidence(),
            right = right.toRankingEvidence(),
        )
    }

    fun latest(entries: List<GitHubAtomReleaseEntry>): GitHubAtomReleaseEntry? {
        val ranked = entries.map { entry -> RankedReleaseEntry(entry, entry.toRankingEvidence()) }
        val chosen = ReleaseCandidateRanker.pickLatest(ranked.map { it.evidence }) ?: return null
        // pickLatest returns one of the values it was given, so identity finds its entry back.
        return ranked.firstOrNull { it.evidence === chosen }?.entry
    }

    /**
     * Whether this list's highest version number looks like one a restarted project left behind.
     *
     * Callers that can ask the forge which release is current should do so only when this is true;
     * see [ReleaseCandidateRanker.suspectVersioningReset] for why the two steps are separate.
     */
    fun suspectsVersioningReset(entries: List<GitHubAtomReleaseEntry>): Boolean =
        ReleaseCandidateRanker.suspectVersioningReset(entries.map { it.toRankingEvidence() }) != null

    fun newestFirst(entries: List<GitHubAtomReleaseEntry>): List<GitHubAtomReleaseEntry> {
        return entries
            .map { entry -> RankedReleaseEntry(entry, entry.toRankingEvidence()) }
            .sortedWith { left, right ->
                ReleaseCandidateRanker.compare(right.evidence, left.evidence)
            }
            .map { ranked -> ranked.entry }
    }

    private data class RankedReleaseEntry(
        val entry: GitHubAtomReleaseEntry,
        val evidence: ReleaseRankingEvidence,
    )
}

private fun GitHubAtomReleaseEntry.toRankingEvidence(): ReleaseRankingEvidence {
    val channelHint = channel.toCoreVersionChannelHint()
    return ReleaseRankingEvidence(
        versionCandidates = versionCandidates.map { candidate ->
            VersionCandidate(
                value = candidate.value,
                sourcePriority = candidate.source.priority,
                channelHint = channelHint,
            )
        },
        publishedAtMillis = updatedAtMillis,
        assetsUpdatedAtMillis = assetsUpdatedAtMillis,
        stableKey = link.trim()
            .ifBlank { entryId.trim() }
            .ifBlank { tag.trim() },
    )
}
