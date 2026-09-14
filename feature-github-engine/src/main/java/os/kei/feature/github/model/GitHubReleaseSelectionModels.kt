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
    /** Marked `prerelease` by the maintainer, and the reader is looking at the stable line. */
    MarkedPreRelease,

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
