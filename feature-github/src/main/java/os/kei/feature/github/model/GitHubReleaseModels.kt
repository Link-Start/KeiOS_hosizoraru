package os.kei.feature.github.model

import os.kei.core.io.NetworkTimingSummary

enum class GitHubDirectApkRemoteHealth {
    Unknown,
    Available,
    Degraded
}

data class GitHubReleaseCheckDiagnostics(
    val localVersionElapsedMs: Long = 0L,
    val snapshotElapsedMs: Long = 0L,
    val snapshotFromCache: Boolean = false,
    val profileElapsedMs: Long = 0L,
    val profileFromCache: Boolean = false,
    val preciseApkElapsedMs: Long = 0L,
    val preciseApkRequested: Boolean = false,
    val comparisonElapsedMs: Long = 0L,
    val fallbackStrategyId: String = "",
    /**
     * What the network actually did, when this check ran inside a measured scope.
     *
     * The stage timings above say *which* stage was slow. This says why that stage was slow, which
     * is a different question and the only one with an action attached: time queued behind our own
     * concurrency budget, a DNS lookup, a handshake, a server taking its time, and a body coming
     * down a slow radio call for four different responses. On an emulator over wifi all but one of
     * them round to zero, which is why the numbers that matter can only come from a real device.
     */
    val network: NetworkTimingSummary = NetworkTimingSummary(),
) {
    val hasStageData: Boolean
        get() =
            localVersionElapsedMs > 0L ||
                snapshotElapsedMs > 0L ||
                profileElapsedMs > 0L ||
                preciseApkElapsedMs > 0L ||
                comparisonElapsedMs > 0L ||
                snapshotFromCache ||
                profileFromCache ||
                preciseApkRequested ||
                !network.isEmpty ||
                fallbackStrategyId.isNotBlank()
}

data class GitHubRefreshFailureDiagnostics(
    val category: String = "",
    val responseType: String = "",
    val limitBytes: Long = -1L,
    val declaredBytes: Long = -1L,
    val observedBytes: Long = -1L,
    val limitStage: String = "",
)

data class GitHubTrackedReleaseCheck(
    val strategyId: String,
    val localVersion: String,
    val localVersionCode: Long = -1L,
    val matchedRelease: GitHubAtomReleaseEntry? = null,
    val stableRelease: GitHubReleaseVersionSignals? = null,
    val preRelease: GitHubReleaseVersionSignals? = null,
    val hasStableRelease: Boolean = true,
    val hasUpdate: Boolean? = null,
    val hasPreReleaseUpdate: Boolean = false,
    val recommendsPreRelease: Boolean = false,
    val isPreReleaseInstalled: Boolean = false,
    val preReleaseInfo: String = "",
    val showPreReleaseInfo: Boolean = false,
    val releaseHint: String = "",
    val preciseStableApkVersion: GitHubRemoteApkVersionInfo? = null,
    val precisePreApkVersion: GitHubRemoteApkVersionInfo? = null,
    val repositoryArchived: Boolean = false,
    val repositoryFork: Boolean = false,
    val repositoryPushedAtMillis: Long = -1L,
    val upstreamFullName: String = "",
    val upstreamArchived: Boolean = false,
    val upstreamPushedAtMillis: Long = -1L,
    val repositoryProfile: GitHubRepositoryProfileSnapshot? = null,
    val sourceConfigSignature: String = "",
    val directApkRemoteHealth: GitHubDirectApkRemoteHealth = GitHubDirectApkRemoteHealth.Unknown,
    val directApkRemoteHealthMessage: String = "",
    val directApkRemoteCheckedAtMillis: Long = -1L,
    val status: GitHubTrackedReleaseStatus = GitHubTrackedReleaseStatus.ComparisonUncertain,
    val message: String = status.defaultMessage,
    val diagnostics: GitHubReleaseCheckDiagnostics = GitHubReleaseCheckDiagnostics(),
    val failureDiagnostics: GitHubRefreshFailureDiagnostics = GitHubRefreshFailureDiagnostics(),
    /**
     * How the source picked [stableRelease] and [preRelease] out of everything it published.
     *
     * Not persisted and not shown: this is the working out, carried so that a surface which wants to
     * explain an answer -- or a test asserting one end to end -- reads it off the check instead of
     * re-deriving it from a captured response. `null` for sources that keep no record.
     *
     * @see GitHubReleaseSelection.summary
     */
    val releaseSelection: GitHubReleaseSelection? = null,
    /** The rule that removed the pre-release from this card, when one did. */
    val preReleaseRejection: GitHubReleaseRejection? = null,
) {
    /** The part of the working out that belongs on the card, if any of it does. */
    val decisionNote: GitHubReleaseDecisionNote
        get() = GitHubReleaseDecisionNote.from(releaseSelection, preReleaseRejection)
}
