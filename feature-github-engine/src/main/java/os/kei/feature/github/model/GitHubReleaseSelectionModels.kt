package os.kei.feature.github.model

import os.kei.core.versioning.ReleaseSelectionRule

/**
 * Why a release the repository publishes is not the one the reader is shown.
 *
 * Every value here is a rule that already ran; naming them only means the answer survives the call
 * that produced it. Three separate reports against this pipeline were diagnosed by reconstructing
 * this list by hand from a captured API response, which is work nobody should have to repeat.
 */
enum class GitHubReleaseRejection {
    /** A pre-release whose tag, title and link carry no version anyone could compare. */
    NoComparableVersion,

    /** A real candidate that simply lost to the chosen one. */
    Outranked,

    /** A pre-release of a version that has since shipped, so moving to it would be a downgrade. */
    SupersededByStable,

    /** A preview line nobody has published to, or built into, for a fortnight. */
    AbandonedLine,

    /** The same version as the chosen stable, wearing a pre-release tag. */
    SameVersionAsStable,
}

/** One release that was considered and set aside, with the rule that set it aside. */
data class GitHubRejectedRelease(
    val tag: String,
    val reason: GitHubReleaseRejection,
)

/**
 * What the pipeline decided about a repository's release list, and on what evidence.
 *
 * This is the record the pipeline never kept. Thirty releases were reduced to two, and the other
 * twenty-eight — along with the reason each lost — were dropped on the floor, so every new shape of
 * repository meant starting the same investigation from nothing.
 *
 * [windowWasFull] is the honest caveat on all of it: the API is asked for one page, and a repository
 * with more releases than fit has a history this selection never saw.
 */
data class GitHubReleaseSelection(
    val stable: GitHubReleaseVersionSignals? = null,
    val stableRule: ReleaseSelectionRule = ReleaseSelectionRule.OnlyCandidate,
    val stableRunnerUpTag: String = "",
    val preRelease: GitHubReleaseVersionSignals? = null,
    val preReleaseRule: ReleaseSelectionRule = ReleaseSelectionRule.OnlyCandidate,
    val preReleaseRunnerUpTag: String = "",
    val rejected: List<GitHubRejectedRelease> = emptyList(),
    val consideredCount: Int = 0,
    val windowWasFull: Boolean = false,
    /** True when the forge's own *latest release* flag, not this ranking, chose [stable]. */
    val stableCameFromForgeLatest: Boolean = false,
    /**
     * True when the forge was asked and named the release the ranking had already picked.
     *
     * Distinct from [stableCameFromForgeLatest], which is provenance: the flag is what the snapshot
     * was built from either way. This says whether asking changed the answer, and only Atom mode
     * makes the difference routine — it consults `releases/latest` on every refresh because that is
     * how it learns which entry is stable at all, so without this every Atom card would carry a
     * sentence explaining a decision that surprised nobody.
     */
    val stableForgeLatestConfirmedRanking: Boolean = false,
) {
    val hasStableRelease: Boolean
        get() = stable != null

    /** One line, for a log or a failing assertion: what was chosen, over what, by which rule. */
    fun summary(): String = buildString {
        append("stable=")
        append(stable?.rawTag.orEmpty().ifBlank { "none" })
        append(" by ")
        append(if (stableCameFromForgeLatest) "ForgeLatest" else stableRule.name)
        if (stableRunnerUpTag.isNotBlank()) {
            append(" over ")
            append(stableRunnerUpTag)
        }
        append("; pre=")
        append(preRelease?.rawTag.orEmpty().ifBlank { "none" })
        if (preRelease != null) {
            append(" by ")
            append(preReleaseRule.name)
            if (preReleaseRunnerUpTag.isNotBlank()) {
                append(" over ")
                append(preReleaseRunnerUpTag)
            }
        }
        append("; considered=")
        append(consideredCount)
        append(if (windowWasFull) " (window full)" else "")
        if (rejected.isNotEmpty()) {
            append("; rejected=")
            append(
                rejected.joinToString(", ") { rejection ->
                    "${rejection.tag}:${rejection.reason.name}"
                },
            )
        }
    }
}

/**
 * What actually decided which release the card shows, in the terms the card needs.
 *
 * Deliberately not [ReleaseSelectionRule]. That enum is the ranker's own vocabulary and belongs to
 * `core-versioning`; this one is the vocabulary of a sentence shown to a reader, and keeping them
 * apart means the ranker can grow a branch without the UI inheriting a case it has no wording for.
 */
enum class GitHubReleaseDecisionBasis {
    /** The version numbers settled it. The ordinary case, and the one with nothing to say. */
    Ranked,

    /** The maintainer's own *Set as the latest release* flag settled it. */
    ForgeLatest,

    /**
     * Nobody confirmed it: this is the newest release in the feed, and the feed is all there was.
     *
     * Only reachable from a source that infers the stable lane from text rather than being told —
     * in practice the Atom feed with its `releases/latest` lookup unanswered. Worth saying because
     * it is both the weakest answer the pipeline gives and the most recoverable: the next refresh
     * usually settles it.
     */
    FeedOnly,


    /** The highest number was one a restarted project left behind, so the ranking was overridden. */
    VersioningReset,

    /** The versions could not be ordered, so the clock decided. */
    UpdateTime,

    /** Neither version nor clock separated them, so the list's own order stands. */
    ListOrder,
}

/**
 * The part of a [GitHubReleaseSelection] that belongs on the card rather than in a diagnostic.
 *
 * The full selection is the working out: every candidate, every rejection, the page window, the
 * clock. Almost none of that changes what a reader would do. Three things do, and they are the
 * three this pipeline has had reports about:
 *
 *  - the release shown is not the one with the highest number, because the project restarted its
 *    numbering and the forge's own flag settled it;
 *  - the versions could not be compared at all, so the choice rests on when they moved;
 *  - a pre-release the reader was tracking is deliberately not there.
 *
 * Small enough to persist beside the cached check, so the explanation survives a cold start rather
 * than appearing only in the minutes after a live refresh.
 */
data class GitHubReleaseDecisionNote(
    val stableBasis: GitHubReleaseDecisionBasis = GitHubReleaseDecisionBasis.Ranked,
    /** The release the chosen one was chosen over, named only when it would surprise the reader. */
    val stableRunnerUpTag: String = "",
    val preReleaseRejection: GitHubReleaseRejection? = null,
) {
    /**
     * Whether the stable choice needs explaining.
     *
     * An ordinary history ranked by version explains itself, and saying so on every card would bury
     * the cases that do not.
     */
    val explainsStableChoice: Boolean
        get() = stableBasis != GitHubReleaseDecisionBasis.Ranked

    val isEmpty: Boolean
        get() = !explainsStableChoice && preReleaseRejection == null

    companion object {
        fun from(
            selection: GitHubReleaseSelection?,
            preReleaseRejection: GitHubReleaseRejection? = null,
        ): GitHubReleaseDecisionNote {
            if (selection == null) {
                return GitHubReleaseDecisionNote(preReleaseRejection = preReleaseRejection)
            }
            // What the ranking on its own would have to account for.
            val rankedBasis = when {
                // Ahead of every ranking rule, because it qualifies all of them: a reading of the
                // list is a different kind of answer from a confirmed one, whichever rule read it.
                selection.stable?.source?.laneIsInferred == true ->
                    GitHubReleaseDecisionBasis.FeedOnly
                selection.stableRule == ReleaseSelectionRule.VersioningReset ->
                    GitHubReleaseDecisionBasis.VersioningReset
                selection.stableRule == ReleaseSelectionRule.Freshness ->
                    GitHubReleaseDecisionBasis.UpdateTime
                selection.stableRule == ReleaseSelectionRule.Indistinguishable ->
                    GitHubReleaseDecisionBasis.ListOrder
                else -> GitHubReleaseDecisionBasis.Ranked
            }
            // The forge's flag outranks the ranking that sent us to ask for it: it is a maintainer's
            // statement rather than a reading of the list, and it is the stronger thing to tell.
            //
            // But only where the card owes an explanation at all. Atom mode asks `releases/latest`
            // on every refresh of every repository — it is how that mode learns which entry is
            // stable — so a flag that merely agreed with an unremarkable ranking would put a
            // sentence on every Atom card, which is the burial this note exists to avoid.
            val basis = when {
                !selection.stableCameFromForgeLatest -> rankedBasis
                !selection.stableForgeLatestConfirmedRanking ->
                    GitHubReleaseDecisionBasis.ForgeLatest
                rankedBasis != GitHubReleaseDecisionBasis.Ranked ->
                    GitHubReleaseDecisionBasis.ForgeLatest
                else -> GitHubReleaseDecisionBasis.Ranked
            }
            // The evaluator's verdict first: it ran last, and it is the one that removed a row the
            // reader could otherwise see. The selector's own duplicate-version rejection stands in
            // when the evaluator had nothing to say, because from the card it looks identical.
            val rejection = preReleaseRejection
                ?: selection.rejected
                    .firstOrNull { it.reason == GitHubReleaseRejection.SameVersionAsStable }
                    ?.reason
            return GitHubReleaseDecisionNote(
                stableBasis = basis,
                stableRunnerUpTag = selection.stableRunnerUpTag,
                preReleaseRejection = rejection,
            )
        }
    }
}
