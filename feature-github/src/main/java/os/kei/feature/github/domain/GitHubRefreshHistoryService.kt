package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubRefreshHistoryStore
import os.kei.feature.github.model.GitHubRefreshHistoryOutcome
import os.kei.feature.github.model.GitHubRefreshHistoryRecord
import os.kei.feature.github.model.GitHubRefreshSchedulerDiagnostics
import os.kei.feature.github.model.toGitHubRefreshHistoryFailureSummary
import os.kei.feature.github.model.toGitHubRefreshHistorySlowItem

class GitHubRefreshHistoryService(
    private val localDispatcher: CoroutineDispatcher = AppDispatchers.githubLocal,
) {
    suspend fun loadHistory(): List<GitHubRefreshHistoryRecord> =
        withContext(localDispatcher) {
            GitHubRefreshHistoryStore.load()
        }

    suspend fun queryHistory(
        query: GitHubRefreshHistoryQuery,
    ): List<GitHubRefreshHistoryRecord> =
        withContext(localDispatcher) {
            GitHubRefreshHistoryExportService.filterRecords(
                records = GitHubRefreshHistoryStore.load(),
                query = query,
            )
        }

    suspend fun summarizeHistory(
        query: GitHubRefreshHistoryQuery,
    ): GitHubRefreshHistorySummary =
        withContext(localDispatcher) {
            val records = GitHubRefreshHistoryStore.load()
            GitHubRefreshHistoryExportService.summarize(
                allRecords = records,
                records =
                    GitHubRefreshHistoryExportService.filterRecords(
                        records = records,
                        query = query,
                    ),
            )
        }

    suspend fun buildExportJson(
        query: GitHubRefreshHistoryQuery,
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String =
        withContext(localDispatcher) {
            GitHubRefreshHistoryExportService.buildExportJson(
                allRecords = GitHubRefreshHistoryStore.load(),
                query = query,
                exportedAtMillis = exportedAtMillis,
            )
        }

    suspend fun recordCompleted(
        session: GitHubRefreshRuntimeSession,
        totalTrackedCount: Int,
        result: GitHubTrackedRefreshBatchResult,
        startedAtMillis: Long,
        finishedAtMillis: Long = System.currentTimeMillis(),
        schedulerDiagnostics: GitHubRefreshSchedulerDiagnostics = GitHubRefreshSchedulerDiagnostics(),
    ) {
        record(
            buildCompletedRecord(
                session = session,
                totalTrackedCount = totalTrackedCount,
                result = result,
                startedAtMillis = startedAtMillis,
                finishedAtMillis = finishedAtMillis,
                schedulerDiagnostics = schedulerDiagnostics,
            ),
        )
    }

    suspend fun recordRuntimeState(
        runtime: GitHubRefreshRuntimeState,
        outcome: GitHubRefreshHistoryOutcome,
        note: String = "",
        finishedAtMillis: Long = System.currentTimeMillis(),
        schedulerDiagnostics: GitHubRefreshSchedulerDiagnostics = GitHubRefreshSchedulerDiagnostics(),
    ) {
        if (runtime.startedAtMs <= 0L) return
        record(
            GitHubRefreshHistoryRecord(
                id = "",
                sessionId = runtime.sessionId,
                scope = runtime.scope,
                source = runtime.source,
                outcome = outcome,
                totalTrackedCount = runtime.totalTrackedCount,
                targetCount = runtime.targetCount,
                targetTrackIds = runtime.targetTrackIds,
                completedCount = runtime.completedCount,
                updatableCount = runtime.updatableCount,
                preReleaseUpdateCount = runtime.preReleaseUpdateCount,
                failedCount = runtime.failedCount,
                startedAtMillis = runtime.startedAtMs,
                finishedAtMillis = finishedAtMillis.coerceAtLeast(runtime.startedAtMs),
                elapsedMs = (finishedAtMillis - runtime.startedAtMs).coerceAtLeast(0L),
                schedulerJobId = schedulerDiagnostics.jobId,
                schedulerEnqueuedAtMillis = schedulerDiagnostics.enqueuedAtMillis,
                schedulerStartedAtMillis = schedulerDiagnostics.startedAtMillis,
                schedulerStopReason = schedulerDiagnostics.stopReason,
                schedulerRescheduled = schedulerDiagnostics.rescheduled,
                note = note,
            ),
        )
    }

    suspend fun record(record: GitHubRefreshHistoryRecord) {
        withContext(localDispatcher) {
            GitHubRefreshHistoryStore.recordRefresh(record)
        }
    }

    suspend fun pruneBefore(cutoffMillis: Long): Int =
        withContext(localDispatcher) {
            GitHubRefreshHistoryStore.pruneBefore(cutoffMillis)
        }

    private fun buildCompletedRecord(
        session: GitHubRefreshRuntimeSession,
        totalTrackedCount: Int,
        result: GitHubTrackedRefreshBatchResult,
        startedAtMillis: Long,
        finishedAtMillis: Long,
        schedulerDiagnostics: GitHubRefreshSchedulerDiagnostics,
    ): GitHubRefreshHistoryRecord {
        val outcome =
            if (result.failedCount >= result.totalCount && result.totalCount > 0) {
                GitHubRefreshHistoryOutcome.Failed
            } else {
                GitHubRefreshHistoryOutcome.Completed
            }
        return GitHubRefreshHistoryRecord(
            id = "",
            sessionId = session.id,
            scope = session.scope,
            source = session.source,
            outcome = outcome,
            totalTrackedCount = totalTrackedCount.coerceAtLeast(result.totalCount),
            targetCount = result.totalCount,
            targetTrackIds = session.targetTrackIds,
            completedCount = result.totalCount,
            updatableCount = result.updatableCount,
            preReleaseUpdateCount = result.preReleaseUpdateCount,
            failedCount = result.failedCount,
            startedAtMillis = startedAtMillis,
            finishedAtMillis = finishedAtMillis.coerceAtLeast(startedAtMillis),
            elapsedMs = result.performance.elapsedMs.takeIf { it > 0L }
                ?: (finishedAtMillis - startedAtMillis).coerceAtLeast(0L),
            p50ItemMs = result.performance.p50ItemMs,
            p95ItemMs = result.performance.p95ItemMs,
            maxItemMs = result.performance.maxItemMs,
            maxConcurrency = result.performance.maxConcurrency,
            peakConcurrentCalls = result.performance.peakConcurrentCalls,
            networkKind = result.performance.networkKind,
            networkMetered = result.performance.networkMetered,
            directApkConcurrency = result.performance.directApkConcurrency,
            fdroidConcurrency = result.performance.fdroidConcurrency,
            repositoryItemCount = result.performance.repositoryItemCount,
            directApkItemCount = result.performance.directApkItemCount,
            fdroidItemCount = result.performance.fdroidItemCount,
            otherItemCount = result.performance.otherItemCount,
            schedulerJobId = schedulerDiagnostics.jobId,
            schedulerEnqueuedAtMillis = schedulerDiagnostics.enqueuedAtMillis,
            schedulerStartedAtMillis = schedulerDiagnostics.startedAtMillis,
            schedulerStopReason = schedulerDiagnostics.stopReason,
            schedulerRescheduled = schedulerDiagnostics.rescheduled,
            slowItems = result.performance.slowItems.map { it.toGitHubRefreshHistorySlowItem() },
            failureSummaries = result.failures.map { it.toGitHubRefreshHistoryFailureSummary() },
        )
    }
}
