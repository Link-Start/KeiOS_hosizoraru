package os.kei.core.versioning

enum class VersionChannel(val isPreRelease: Boolean) {
    DEV(true),
    ALPHA(true),
    BETA(true),
    RC(true),
    PREVIEW(true),
    STABLE(false),
    UNKNOWN(false),
}

data class VersionCandidate(
    val value: String,
    val sourcePriority: Int,
    val channelHint: VersionChannel? = null,
)

enum class VersionOrder(val legacyValue: Int) {
    Older(-1),
    Same(0),
    Newer(1),
}

enum class VersionConfidence {
    Exact,
    High,
    Medium,
    Low,
}

enum class VersionComparisonReason {
    ExactCandidate,
    SemanticVersion,
    VersionCode,
    ReleaseRanking,
}

data class VersionComparison(
    val order: VersionOrder,
    val confidence: VersionConfidence,
    val reason: VersionComparisonReason,
    val leftEvidence: String,
    val rightEvidence: String,
)

data class ReleaseRankingEvidence(
    val versionCandidates: List<VersionCandidate>,
    val publishedAtMillis: Long? = null,
    val stableKey: String = "",
    /**
     * When this release's attached artifacts last moved, if the source can see them.
     *
     * Separate from [publishedAtMillis] because a rolling tag is published once and then only its
     * contents change. Nothing reads this directly — [freshnessMillis] is the field the rules use.
     */
    val assetsUpdatedAtMillis: Long? = null,
) {
    /**
     * The one clock every time-sensitive rule reads: when this release last moved, by any measure.
     *
     * There was briefly more than one. The staleness rule was taught to read the asset clock while
     * ranking and relevance still read the publish date, which meant a rolling CI tag was retired
     * by one rule and ordered by another on different evidence. Two clocks in one pipeline is a
     * report waiting to happen, so there is exactly one and it lives here.
     */
    val freshnessMillis: Long?
        get() = listOfNotNull(publishedAtMillis, assetsUpdatedAtMillis).maxOrNull()
}

/**
 * A release list where the highest version number is not the current release.
 *
 * [outranking] is the tag that wins on version and should not: an old high number left behind by a
 * project that restarted its numbering. [newest] is the most recently published release, which is
 * what the project is actually shipping.
 *
 * Produced by [ReleaseCandidateRanker.suspectVersioningReset]. A caller with an authoritative
 * "current release" endpoint should confirm against it before acting; one without should act on it,
 * because the alternative is a track that reports an ancient release forever.
 */
data class VersioningResetSuspicion(
    val outranking: ReleaseRankingEvidence,
    val newest: ReleaseRankingEvidence,
)

/**
 * Which rule actually separated the chosen release from its closest rival.
 *
 * Recorded rather than inferred. Every report this ranker has had was diagnosed by working out, by
 * hand and from a captured response, which of these four branches ran — so it now says.
 */
enum class ReleaseSelectionRule {
    /** Nothing to choose between: the list was empty, or held one release. */
    OnlyCandidate,

    /** The version numbers were comparable and settled it. */
    Version,

    /** The versions could not be ordered, so the clock did. */
    Freshness,

    /** The version winner was an old high number a restarted project left behind. */
    VersioningReset,

    /** Neither version nor clock could separate the two. */
    Indistinguishable,
}

/**
 * The chosen release and the evidence for choosing it.
 *
 * [runnerUp] is what the decision was made *against* — the best of the rest under the same rules.
 * It is the field that makes a wrong answer legible: "picked A over B by version" is a sentence
 * somebody can check, where "picked A" is not.
 */
data class ReleaseSelection(
    val chosen: ReleaseRankingEvidence?,
    val rule: ReleaseSelectionRule,
    val runnerUp: ReleaseRankingEvidence? = null,
)
