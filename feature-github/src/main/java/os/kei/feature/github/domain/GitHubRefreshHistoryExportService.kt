package os.kei.feature.github.domain

import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.KeiJson
import os.kei.core.json.encodeCompact
import os.kei.feature.github.model.GitHubRefreshHistoryFailureSummary
import os.kei.feature.github.model.GitHubRefreshHistoryOutcome
import os.kei.feature.github.model.GitHubRefreshHistoryRecord
import os.kei.feature.github.model.GitHubRefreshHistorySlowItem

private const val REFRESH_HISTORY_EXPORT_FORMAT = "keios.github.refresh-history"
private const val REFRESH_HISTORY_EXPORT_SCHEMA_VERSION = 2

data class GitHubRefreshHistoryQuery(
    val outcome: GitHubRefreshHistoryOutcomeFilter = GitHubRefreshHistoryOutcomeFilter.All,
    val source: GitHubRefreshSource? = null,
    val scope: GitHubRefreshScope? = null,
    val sinceMillis: Long = 0L,
    val limit: Int = GitHubRefreshHistoryQueryDefaults.DEFAULT_LIMIT,
)

object GitHubRefreshHistoryQueryDefaults {
    const val DEFAULT_LIMIT = 80
    const val MAX_LIMIT = 240
}

enum class GitHubRefreshHistoryOutcomeFilter(
    val storageId: String,
) {
    All("all"),
    Completed("completed"),
    Updatable("updatable"),
    Failed("failed"),
    Cancelled("cancelled"),
    PartialFailed("partial_failed"),
}

data class GitHubRefreshHistorySummary(
    val storedCount: Int,
    val matchedCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val cancelledCount: Int,
    val partialFailedCount: Int,
    val updatableRecordCount: Int,
    val totalTargetCount: Int,
    val totalCompletedCount: Int,
    val totalFailedItemCount: Int,
    val totalStableUpdateCount: Int,
    val totalPreReleaseUpdateCount: Int,
    val totalRepositoryItemCount: Int,
    val totalDirectApkItemCount: Int,
    val totalFdroidItemCount: Int,
    val totalOtherItemCount: Int,
    /**
     * The largest concurrency any of these refreshes was *configured* with.
     *
     * It was called observed and is not: it is a setting read back. [maxPeakConcurrentCalls] is what
     * the device actually managed, and the two differing is the whole reason this distinction now
     * has two names.
     */
    val maxRequestedConcurrency: Int,
    val maxPeakConcurrentCalls: Int,
    val averageElapsedMs: Long,
    val p95ElapsedMs: Long,
    val latestStartedAtMillis: Long,
    val latestFinishedAtMillis: Long,
)

object GitHubRefreshHistoryExportService {
    fun filterRecords(
        records: List<GitHubRefreshHistoryRecord>,
        query: GitHubRefreshHistoryQuery,
    ): List<GitHubRefreshHistoryRecord> {
        val boundedLimit =
            query.limit.coerceIn(1, GitHubRefreshHistoryQueryDefaults.MAX_LIMIT)
        return records
            .asSequence()
            .filter { record -> query.source == null || record.source == query.source }
            .filter { record -> query.scope == null || record.scope == query.scope }
            .filter { record ->
                query.sinceMillis <= 0L ||
                    (record.finishedAtMillis.takeIf { it > 0L } ?: record.startedAtMillis) >= query.sinceMillis
            }
            .filter { record -> record.matchesOutcome(query.outcome) }
            .sortedByDescending { record -> record.finishedAtMillis.takeIf { it > 0L } ?: record.startedAtMillis }
            .take(boundedLimit)
            .toList()
    }

    fun summarize(
        allRecords: List<GitHubRefreshHistoryRecord>,
        records: List<GitHubRefreshHistoryRecord>,
    ): GitHubRefreshHistorySummary {
        val elapsed = records.map { it.elapsedMs.coerceAtLeast(0L) }.sorted()
        return GitHubRefreshHistorySummary(
            storedCount = allRecords.size,
            matchedCount = records.size,
            completedCount =
                records.count { record ->
                    record.outcome == GitHubRefreshHistoryOutcome.Completed && record.failedCount == 0
                },
            failedCount = records.count { it.outcome == GitHubRefreshHistoryOutcome.Failed },
            cancelledCount = records.count { it.outcome == GitHubRefreshHistoryOutcome.Cancelled },
            partialFailedCount =
                records.count { record ->
                    record.outcome == GitHubRefreshHistoryOutcome.Completed && record.failedCount > 0
                },
            updatableRecordCount =
                records.count { record ->
                    record.updatableCount > 0 || record.preReleaseUpdateCount > 0
                },
            totalTargetCount = records.sumOf { it.targetCount.coerceAtLeast(0) },
            totalCompletedCount = records.sumOf { it.completedCount.coerceAtLeast(0) },
            totalFailedItemCount = records.sumOf { it.failedCount.coerceAtLeast(0) },
            totalStableUpdateCount = records.sumOf { it.updatableCount.coerceAtLeast(0) },
            totalPreReleaseUpdateCount = records.sumOf { it.preReleaseUpdateCount.coerceAtLeast(0) },
            totalRepositoryItemCount = records.sumOf { it.repositoryItemCount.coerceAtLeast(0) },
            totalDirectApkItemCount = records.sumOf { it.directApkItemCount.coerceAtLeast(0) },
            totalFdroidItemCount = records.sumOf { it.fdroidItemCount.coerceAtLeast(0) },
            totalOtherItemCount = records.sumOf { it.otherItemCount.coerceAtLeast(0) },
            maxRequestedConcurrency = records.maxOfOrNull { it.maxConcurrency.coerceAtLeast(0) } ?: 0,
            maxPeakConcurrentCalls =
                records.maxOfOrNull { it.peakConcurrentCalls.coerceAtLeast(0) } ?: 0,
            averageElapsedMs =
                if (elapsed.isEmpty()) 0L else elapsed.sum() / elapsed.size,
            p95ElapsedMs = elapsed.percentile95OrZero(),
            latestStartedAtMillis = records.maxOfOrNull { it.startedAtMillis } ?: 0L,
            latestFinishedAtMillis =
                records.maxOfOrNull { record ->
                    record.finishedAtMillis.takeIf { it > 0L } ?: record.startedAtMillis
                } ?: 0L,
        )
    }

    fun buildExportJson(
        allRecords: List<GitHubRefreshHistoryRecord>,
        query: GitHubRefreshHistoryQuery,
        exportedAtMillis: Long,
    ): String {
        val records = filterRecords(allRecords, query)
        val summary = summarize(allRecords = allRecords, records = records)
        return buildJsonObject {
            put("format", REFRESH_HISTORY_EXPORT_FORMAT)
            put("schemaVersion", REFRESH_HISTORY_EXPORT_SCHEMA_VERSION)
            put("syncScope", "local_only")
            put("exportedAtMillis", exportedAtMillis)
            put("filters", query.toJson())
            put("summary", summary.toJson())
            put(
                "records",
                buildJsonArray {
                    records.forEach { record -> add(record.toJson()) }
                },
            )
        }.encodeCompact(KeiJson.pretty)
    }

    fun parseOutcomeFilter(raw: String): GitHubRefreshHistoryOutcomeFilter {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        return GitHubRefreshHistoryOutcomeFilter.entries.firstOrNull { filter ->
            filter.storageId == normalized || filter.name.equals(normalized, ignoreCase = true)
        } ?: when (normalized) {
            "", "all" -> GitHubRefreshHistoryOutcomeFilter.All
            "success", "succeeded", "done" -> GitHubRefreshHistoryOutcomeFilter.Completed
            "updates", "update_available", "has_updates" -> GitHubRefreshHistoryOutcomeFilter.Updatable
            "failure", "error", "errors" -> GitHubRefreshHistoryOutcomeFilter.Failed
            "cancelled", "canceled", "interrupted" -> GitHubRefreshHistoryOutcomeFilter.Cancelled
            "partial", "partial_failed", "partial_failure" -> GitHubRefreshHistoryOutcomeFilter.PartialFailed
            else -> GitHubRefreshHistoryOutcomeFilter.All
        }
    }

    fun parseSourceFilter(raw: String): GitHubRefreshSource? {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        return when (normalized) {
            "", "all" -> null
            "page", "manual", "ui" -> GitHubRefreshSource.Page
            "background", "background_tick", "auto", "automatic" -> GitHubRefreshSource.BackgroundTick
            "shortcut" -> GitHubRefreshSource.Shortcut
            "debug" -> GitHubRefreshSource.Debug
            else ->
                GitHubRefreshSource.entries.firstOrNull { source ->
                    source.name.equals(raw.trim(), ignoreCase = true)
                }
        }
    }

    fun parseScopeFilter(raw: String): GitHubRefreshScope? {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        return when (normalized) {
            "", "all" -> null
            "all_tracked", "alltracked" -> GitHubRefreshScope.AllTracked
            "due", "due_tracked", "duetracked" -> GitHubRefreshScope.DueTracked
            "visible", "visible_tracked", "visibletracked" -> GitHubRefreshScope.VisibleTracked
            "requested", "requested_tracked", "requestedtracked" -> GitHubRefreshScope.RequestedTracked
            "missing", "missing_cache", "missingcache" -> GitHubRefreshScope.MissingCache
            "single", "single_tracked", "singletracked" -> GitHubRefreshScope.SingleTracked
            "shortcut", "shortcut_all", "shortcut_all_tracked", "shortcutalltracked" ->
                GitHubRefreshScope.ShortcutAllTracked
            else ->
                GitHubRefreshScope.entries.firstOrNull { scope ->
                    scope.name.equals(raw.trim(), ignoreCase = true)
                }
        }
    }
}

private fun GitHubRefreshHistoryRecord.matchesOutcome(
    filter: GitHubRefreshHistoryOutcomeFilter,
): Boolean =
    when (filter) {
        GitHubRefreshHistoryOutcomeFilter.All -> true
        GitHubRefreshHistoryOutcomeFilter.Completed ->
            outcome == GitHubRefreshHistoryOutcome.Completed && failedCount == 0
        GitHubRefreshHistoryOutcomeFilter.Updatable ->
            updatableCount > 0 || preReleaseUpdateCount > 0
        GitHubRefreshHistoryOutcomeFilter.Failed ->
            outcome == GitHubRefreshHistoryOutcome.Failed || failedCount > 0
        GitHubRefreshHistoryOutcomeFilter.Cancelled ->
            outcome == GitHubRefreshHistoryOutcome.Cancelled
        GitHubRefreshHistoryOutcomeFilter.PartialFailed ->
            outcome == GitHubRefreshHistoryOutcome.Completed && failedCount > 0
    }

private fun GitHubRefreshHistoryQuery.toJson() =
    buildJsonObject {
        put("outcome", outcome.storageId)
        put("source", source?.name.orEmpty())
        put("scope", scope?.name.orEmpty())
        put("sinceMillis", sinceMillis.coerceAtLeast(0L))
        put("limit", limit.coerceIn(1, GitHubRefreshHistoryQueryDefaults.MAX_LIMIT))
    }

private fun GitHubRefreshHistorySummary.toJson() =
    buildJsonObject {
        put("storedCount", storedCount)
        put("matchedCount", matchedCount)
        put("completedCount", completedCount)
        put("failedCount", failedCount)
        put("cancelledCount", cancelledCount)
        put("partialFailedCount", partialFailedCount)
        put("updatableRecordCount", updatableRecordCount)
        put("totalTargetCount", totalTargetCount)
        put("totalCompletedCount", totalCompletedCount)
        put("totalFailedItemCount", totalFailedItemCount)
        put("totalStableUpdateCount", totalStableUpdateCount)
        put("totalPreReleaseUpdateCount", totalPreReleaseUpdateCount)
        put("totalRepositoryItemCount", totalRepositoryItemCount)
        put("totalDirectApkItemCount", totalDirectApkItemCount)
        put("totalFdroidItemCount", totalFdroidItemCount)
        put("totalOtherItemCount", totalOtherItemCount)
        put("maxRequestedConcurrency", maxRequestedConcurrency)
        put("maxPeakConcurrentCalls", maxPeakConcurrentCalls)
        put("averageElapsedMs", averageElapsedMs)
        put("p95ElapsedMs", p95ElapsedMs)
        put("latestStartedAtMillis", latestStartedAtMillis)
        put("latestFinishedAtMillis", latestFinishedAtMillis)
    }

private fun GitHubRefreshHistoryRecord.toJson() =
    buildJsonObject {
        put("id", id)
        put("sessionId", sessionId)
        put("scope", scope.name)
        put("source", source.name)
        put("outcome", outcome.name)
        put("totalTrackedCount", totalTrackedCount)
        put("targetCount", targetCount)
        put(
            "targetTrackIds",
            buildJsonArray {
                targetTrackIds.forEach { trackId -> add(JsonPrimitive(trackId)) }
            },
        )
        put("completedCount", completedCount)
        put("updatableCount", updatableCount)
        put("preReleaseUpdateCount", preReleaseUpdateCount)
        put("failedCount", failedCount)
        put("startedAtMillis", startedAtMillis)
        put("finishedAtMillis", finishedAtMillis)
        put("elapsedMs", elapsedMs)
        put("p50ItemMs", p50ItemMs)
        put("p95ItemMs", p95ItemMs)
        put("maxItemMs", maxItemMs)
        put("maxConcurrency", maxConcurrency)
        put("peakConcurrentCalls", peakConcurrentCalls)
        if (networkKind.isNotBlank()) put("networkKind", networkKind)
        put("networkMetered", networkMetered)
        put("directApkConcurrency", directApkConcurrency)
        put("fdroidConcurrency", fdroidConcurrency)
        put("repositoryItemCount", repositoryItemCount)
        put("directApkItemCount", directApkItemCount)
        put("fdroidItemCount", fdroidItemCount)
        put("otherItemCount", otherItemCount)
        put("schedulerJobId", schedulerJobId)
        put("schedulerEnqueuedAtMillis", schedulerEnqueuedAtMillis)
        put("schedulerStartedAtMillis", schedulerStartedAtMillis)
        put("schedulerStopReason", schedulerStopReason)
        put("schedulerRescheduled", schedulerRescheduled)
        put("note", note)
        put(
            "slowItems",
            buildJsonArray {
                slowItems.forEach { slowItem -> add(slowItem.toJson()) }
            },
        )
        put(
            "failureSummaries",
            buildJsonArray {
                failureSummaries.forEach { failure -> add(failure.toJson()) }
            },
        )
    }

private fun GitHubRefreshHistorySlowItem.toJson() =
    buildJsonObject {
        put("trackId", trackId)
        put("owner", owner)
        put("repo", repo)
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("sourceMode", sourceMode)
        put("elapsedMs", elapsedMs)
        put("status", status)
        put("message", message)
        put("strategyId", strategyId)
        put("localVersionElapsedMs", localVersionElapsedMs)
        put("snapshotElapsedMs", snapshotElapsedMs)
        put("snapshotFromCache", snapshotFromCache)
        put("profileElapsedMs", profileElapsedMs)
        put("profileFromCache", profileFromCache)
        put("preciseApkElapsedMs", preciseApkElapsedMs)
        put("preciseApkRequested", preciseApkRequested)
        put("comparisonElapsedMs", comparisonElapsedMs)
        put("unclassifiedElapsedMs", unclassifiedElapsedMs)
        if (!network.isEmpty) {
            put("networkCallCount", network.callCount)
            put("networkQueuedMs", network.queuedMs)
            put("networkDnsMs", network.dnsMs)
            put("networkConnectMs", network.connectMs)
            put("networkWaitingMs", network.waitingMs)
            put("networkBodyMs", network.bodyMs)
            put("networkBytes", network.bytes)
            put("networkReusedConnectionCalls", network.reusedConnectionCalls)
            // Measured and stored since the instrument was built, but never exported, which left
            // the one field that says "these phase timings are of calls that died" out of the file
            // a user sends us.
            put("networkFailedCalls", network.failedCalls)
            put("networkDominantPhase", network.dominantPhase)
            if (network.protocol.isNotBlank()) put("networkProtocol", network.protocol)
        }
        put("fallbackStrategyId", fallbackStrategyId)
    }

private fun GitHubRefreshHistoryFailureSummary.toJson() =
    buildJsonObject {
        put("trackId", trackId)
        put("owner", owner)
        put("repo", repo)
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("sourceMode", sourceMode)
        put("message", message)
        put("elapsedMs", elapsedMs)
        put("failureCategory", failureCategory)
        put("responseType", responseType)
        put("limitBytes", limitBytes)
        put("declaredBytes", declaredBytes)
        put("observedBytes", observedBytes)
        put("limitStage", limitStage)
    }

private fun List<Long>.percentile95OrZero(): Long {
    if (isEmpty()) return 0L
    val index = ((size - 1) * 95) / 100
    return this[index.coerceIn(0, lastIndex)]
}
