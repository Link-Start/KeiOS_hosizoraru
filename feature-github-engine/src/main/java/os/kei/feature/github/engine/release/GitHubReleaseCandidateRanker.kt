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

    fun latest(entries: List<GitHubAtomReleaseEntry>): GitHubAtomReleaseEntry? = select(entries).entry

    /** [latest], keeping which rule chose it and which release it was chosen over. */
    fun select(entries: List<GitHubAtomReleaseEntry>): GitHubRankedRelease {
        val ranked = entries.map { entry -> RankedReleaseEntry(entry, entry.toRankingEvidence()) }
        val selection = ReleaseCandidateRanker.select(ranked.map { it.evidence })
        // The ranker returns the very values it was given, so identity finds each entry back.
        fun entryOf(evidence: ReleaseRankingEvidence?): GitHubAtomReleaseEntry? =
            evidence?.let { match -> ranked.firstOrNull { it.evidence === match }?.entry }
        return GitHubRankedRelease(
            entry = entryOf(selection.chosen),
            rule = selection.rule,
            runnerUpTag = entryOf(selection.runnerUp)?.tag.orEmpty(),
        )
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
