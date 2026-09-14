package os.kei.feature.github.engine.release

import os.kei.core.versioning.ReleaseSelectionRule
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubRejectedRelease
import os.kei.feature.github.model.GitHubReleaseRejection
import os.kei.feature.github.model.GitHubReleaseSelection
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.toReleaseVersionSignals

/**
 * The one place that turns a repository's release list into "the stable" and "the pre-release".
 *
 * That reduction used to live inline in the API strategy, interleaved with the HTTP calls that fed
 * it. Which meant the rules could only be read by reading a network method, could only be tested
 * through one, and — the part that actually cost time — left no record, so each new report started
 * by reconstructing the discarded twenty-eight releases from a captured response by hand.
 *
 * Split in two so that a rule can decide whether a request is worth making without being able to
 * make one: [plan] is pure and says whether the forge's own *latest release* flag is worth asking
 * for, and [GitHubReleaseSelectionPlan.resolve] takes the answer if the caller chose to fetch it.
 */
object GitHubReleaseSelector {
    /**
     * Sort the list into candidates and rank them, without touching the network.
     *
     * [windowWasFull] should say whether the page of releases came back at its limit, because a
     * selection made from a truncated history is a different claim from one made from all of it,
     * and the caller is the only one who knows.
     */
    fun plan(
        entries: List<GitHubAtomReleaseEntry>,
        windowWasFull: Boolean = false,
        /**
         * Which source these entries came from, stamped onto the signals this produces.
         *
         * Not cosmetic: the source carries how precise its clock is and whether it was *told* which
         * releases are pre-releases or had to read it out of prose. Rules downstream ask both.
         */
        source: GitHubReleaseSignalSource = GitHubReleaseSignalSource.GitHubApi,
    ): GitHubReleaseSelectionPlan {
        val rejected = mutableListOf<GitHubRejectedRelease>()
        val stableCandidates = entries.filter { entry -> !entry.isLikelyPreRelease }
        val preReleaseCandidates = entries.filter { entry ->
            // A stable release not appearing in the pre-release lane is the lane working, not a
            // rejection. Recording one per entry buried the handful that say something -- ten rows of
            // noise around the one line a reader needed.
            if (!entry.isLikelyPreRelease) return@filter false
            // A pre-release whose tag, title and link hold nothing comparable cannot be ranked
            // against anything, and offering it means offering a move the reader cannot evaluate.
            val comparable = GitHubVersionUtils.hasMeaningfulPreReleaseVersionCandidates(
                entry.versionCandidates,
                GitHubVersionCandidateSource.Link.priority,
            )
            if (!comparable) {
                rejected += GitHubRejectedRelease(
                    entry.tag,
                    GitHubReleaseRejection.NoComparableVersion,
                )
            }
            comparable
        }
        val stable = GitHubReleaseCandidateRanker.select(stableCandidates)
        val preRelease = GitHubReleaseCandidateRanker.select(preReleaseCandidates)
        return GitHubReleaseSelectionPlan(
            stable = stable,
            preRelease = preRelease,
            source = source,
            rejected = rejected.toList(),
            consideredCount = entries.size,
            windowWasFull = windowWasFull,
        )
    }
}

/**
 * A ranking that has been made but not yet committed to, so a caller can settle it authoritatively.
 *
 * @see GitHubReleaseSelectionPlan.shouldConsultForgeLatest
 */
data class GitHubReleaseSelectionPlan internal constructor(
    val stable: GitHubRankedRelease,
    val preRelease: GitHubRankedRelease,
    val source: GitHubReleaseSignalSource,
    val rejected: List<GitHubRejectedRelease>,
    val consideredCount: Int,
    val windowWasFull: Boolean,
) {
    /**
     * Whether `releases/latest` is worth a request for this repository.
     *
     * It is the one authority on a project that restarted its numbering — it honours the
     * maintainer's own *Set as the latest release* flag rather than sorting by anything — and it is
     * also a second request per repository per refresh. So it is spent on the two lists that cannot
     * be read without it: one with no stable release in the window at all, and one whose highest
     * version number looks like something a rename left behind. An ordinary history pays nothing.
     */
    val shouldConsultForgeLatest: Boolean
        get() = stable.entry == null || stable.rule == ReleaseSelectionRule.VersioningReset

    /**
     * Commit the ranking, optionally overridden by the forge's own answer.
     *
     * [authoritativeStable] wins when it is present, because a maintainer's flag beats any reading
     * of the list. When it is absent — not asked for, or asked for and failed — the ranking stands,
     * which is the right fallback: a repository whose numbering restarted still reports the release
     * it is actually shipping, just without the forge's confirmation of it.
     */
    fun resolve(
        authoritativeStable: GitHubReleaseVersionSignals? = null,
    ): GitHubReleaseSelection {
        val rankedStable = stable.entry?.toReleaseVersionSignals(source)
        val chosenStable = authoritativeStable ?: rankedStable
        val rejected = rejected.toMutableList()
        stable.runnerUpTag.takeIf { it.isNotBlank() }?.let { tag ->
            rejected += GitHubRejectedRelease(tag, GitHubReleaseRejection.Outranked)
        }
        preRelease.runnerUpTag.takeIf { it.isNotBlank() }?.let { tag ->
            rejected += GitHubRejectedRelease(tag, GitHubReleaseRejection.Outranked)
        }
        // A pre-release that is the stable under another tag is not a choice the reader has.
        val chosenPreRelease = preRelease.entry
            ?.toReleaseVersionSignals(source)
            ?.takeUnless { candidate ->
                val duplicate = chosenStable != null && GitHubVersionUtils.referToSameReleaseVersion(
                    candidate.versionCandidates,
                    chosenStable.versionCandidates,
                    leftChannel = candidate.channel,
                    rightChannel = chosenStable.channel,
                )
                if (duplicate) {
                    rejected += GitHubRejectedRelease(
                        candidate.rawTag,
                        GitHubReleaseRejection.SameVersionAsStable,
                    )
                }
                duplicate
            }
        return GitHubReleaseSelection(
            stable = chosenStable,
            stableRule = stable.rule,
            stableRunnerUpTag = stable.runnerUpTag,
            preRelease = chosenPreRelease,
            preReleaseRule = preRelease.rule,
            preReleaseRunnerUpTag = preRelease.runnerUpTag,
            rejected = rejected.toList(),
            consideredCount = consideredCount,
            windowWasFull = windowWasFull,
            stableCameFromForgeLatest = authoritativeStable != null,
        )
    }
}

/** One channel's ranking: what won, by which rule, and what it won against. */
data class GitHubRankedRelease(
    val entry: GitHubAtomReleaseEntry?,
    val rule: ReleaseSelectionRule,
    val runnerUpTag: String,
)
