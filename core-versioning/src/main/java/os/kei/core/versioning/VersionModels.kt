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
)

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
