package os.kei.feature.github.model

import os.kei.core.io.NetworkTimingSummary
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import os.kei.feature.github.domain.GitHubTrackedRefreshFailure
import os.kei.feature.github.domain.GitHubTrackedRefreshSlowItem

enum class GitHubRefreshHistoryOutcome {
    Completed,
    Cancelled,
    Failed,
}

data class GitHubRefreshHistoryFailureSummary(
    val trackId: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val sourceMode: String,
    val message: String,
    val elapsedMs: Long = 0L,
    val failureCategory: String = "",
    val responseType: String = "",
    val limitBytes: Long = -1L,
    val declaredBytes: Long = -1L,
    val observedBytes: Long = -1L,
    val limitStage: String = "",
)

data class GitHubRefreshHistorySlowItem(
    val trackId: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val sourceMode: String,
    val elapsedMs: Long,
    val status: String,
    val message: String,
    val strategyId: String = "",
    val localVersionElapsedMs: Long = 0L,
    val snapshotElapsedMs: Long = 0L,
    val snapshotFromCache: Boolean = false,
    val profileElapsedMs: Long = 0L,
    val profileFromCache: Boolean = false,
    val preciseApkElapsedMs: Long = 0L,
    val preciseApkRequested: Boolean = false,
    val comparisonElapsedMs: Long = 0L,
    val unclassifiedElapsedMs: Long = 0L,
    val fallbackStrategyId: String = "",
    /** @see GitHubReleaseCheckDiagnostics.network */
    val network: NetworkTimingSummary = NetworkTimingSummary(),
)

data class GitHubRefreshHistoryRecord(
    val id: String,
    val sessionId: Long,
    val scope: GitHubRefreshScope,
    val source: GitHubRefreshSource,
    val outcome: GitHubRefreshHistoryOutcome,
    val totalTrackedCount: Int,
    val targetCount: Int,
    val targetTrackIds: List<String> = emptyList(),
    val completedCount: Int,
    val updatableCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val elapsedMs: Long,
    val p50ItemMs: Long = 0L,
    val p95ItemMs: Long = 0L,
    val maxItemMs: Long = 0L,
    val maxConcurrency: Int = 0,
    /** @see GitHubTrackedRefreshBatchPerformance.peakConcurrentCalls */
    val peakConcurrentCalls: Int = 0,
    /** What the device was connected through when this refresh ran. */
    val networkKind: String = "",
    val networkMetered: Boolean = false,
    val directApkConcurrency: Int = 0,
    val fdroidConcurrency: Int = 0,
    val repositoryItemCount: Int = 0,
    val directApkItemCount: Int = 0,
    val fdroidItemCount: Int = 0,
    val otherItemCount: Int = 0,
    val schedulerJobId: Int = 0,
    val schedulerEnqueuedAtMillis: Long = 0L,
    val schedulerStartedAtMillis: Long = 0L,
    val schedulerStopReason: String = "",
    val schedulerRescheduled: Boolean = false,
    val slowItems: List<GitHubRefreshHistorySlowItem> = emptyList(),
    val failureSummaries: List<GitHubRefreshHistoryFailureSummary> = emptyList(),
    val note: String = "",
)

data class GitHubRefreshSchedulerDiagnostics(
    val jobId: Int = 0,
    val enqueuedAtMillis: Long = 0L,
    val startedAtMillis: Long = 0L,
    val stopReason: String = "",
    val rescheduled: Boolean = false,
)

fun GitHubTrackedRefreshFailure.toGitHubRefreshHistoryFailureSummary(): GitHubRefreshHistoryFailureSummary =
    GitHubRefreshHistoryFailureSummary(
        trackId = trackId,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel,
        sourceMode = sourceMode.storageId,
        message = message,
        elapsedMs = elapsedMs,
        failureCategory = diagnostics.category,
        responseType = diagnostics.responseType,
        limitBytes = diagnostics.limitBytes,
        declaredBytes = diagnostics.declaredBytes,
        observedBytes = diagnostics.observedBytes,
        limitStage = diagnostics.limitStage,
    )

fun GitHubTrackedRefreshSlowItem.toGitHubRefreshHistorySlowItem(): GitHubRefreshHistorySlowItem =
    GitHubRefreshHistorySlowItem(
        trackId = trackId,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel,
        sourceMode = sourceMode,
        elapsedMs = elapsedMs,
        status = status,
        message = message,
        strategyId = strategyId,
        localVersionElapsedMs = localVersionElapsedMs,
        snapshotElapsedMs = snapshotElapsedMs,
        snapshotFromCache = snapshotFromCache,
        profileElapsedMs = profileElapsedMs,
        profileFromCache = profileFromCache,
        preciseApkElapsedMs = preciseApkElapsedMs,
        preciseApkRequested = preciseApkRequested,
        comparisonElapsedMs = comparisonElapsedMs,
        unclassifiedElapsedMs = unclassifiedElapsedMs,
        fallbackStrategyId = fallbackStrategyId,
        network = network,
    )
