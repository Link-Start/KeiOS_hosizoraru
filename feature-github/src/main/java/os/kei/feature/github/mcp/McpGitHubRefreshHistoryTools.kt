package os.kei.feature.github.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.core.io.NetworkTimingSummary
import os.kei.feature.github.domain.GitHubRefreshHistoryExportService
import os.kei.feature.github.domain.GitHubRefreshHistoryQuery
import os.kei.feature.github.domain.GitHubRefreshHistoryQueryDefaults
import os.kei.feature.github.domain.GitHubRefreshHistoryService
import os.kei.feature.github.domain.GitHubRefreshHistorySummary
import os.kei.feature.github.model.GitHubRefreshHistoryFailureSummary
import os.kei.feature.github.model.GitHubRefreshHistoryOutcome
import os.kei.feature.github.model.GitHubRefreshHistoryRecord
import os.kei.feature.github.model.GitHubRefreshHistorySlowItem
import os.kei.mcp.server.McpToolEnvironment
import os.kei.mcp.server.addMcpTextTool
import os.kei.mcp.server.argBoolean
import os.kei.mcp.server.argInt
import os.kei.mcp.server.argString
import java.util.Locale

internal class McpGitHubRefreshHistoryTools(
    private val environment: McpToolEnvironment,
) {
    private val refreshHistoryService = GitHubRefreshHistoryService()

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.github.refresh.history") { request ->
            val query =
                buildRefreshHistoryQuery(
                    arguments = request.arguments,
                    defaultLimit = GitHubRefreshHistoryQueryDefaults.DEFAULT_LIMIT,
                )
            val mode =
                argString(request.arguments?.get("mode"))
                    .trim()
                    .lowercase(Locale.ROOT)
                    .ifBlank { "summary" }
            val includeFailures = argBoolean(request.arguments?.get("includeFailures"), false)
            val includeSlowItems = argBoolean(request.arguments?.get("includeSlowItems"), true)
            buildRefreshHistoryText(
                query = query,
                mode = mode,
                includeFailures = includeFailures,
                includeSlowItems = includeSlowItems,
            )
        }

        server.addMcpTextTool(environment, name = "keios.github.refresh.history.export") { request ->
            refreshHistoryService.buildExportJson(
                query =
                    buildRefreshHistoryQuery(
                        arguments = request.arguments,
                        defaultLimit = GitHubRefreshHistoryQueryDefaults.MAX_LIMIT,
                    ),
                exportedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun buildRefreshHistoryText(
        query: GitHubRefreshHistoryQuery,
        mode: String,
        includeFailures: Boolean,
        includeSlowItems: Boolean,
    ): String {
        val allRecords = refreshHistoryService.loadHistory()
        val records =
            GitHubRefreshHistoryExportService.filterRecords(
                records = allRecords,
                query = query,
            )
        val summary =
            GitHubRefreshHistoryExportService.summarize(
                allRecords = allRecords,
                records = records,
            )
        return buildString {
            appendSummary(summary)
            appendLine("outcomeFilter=${query.outcome.storageId}")
            appendLine("sourceFilter=${query.source?.name.orEmpty()}")
            appendLine("scopeFilter=${query.scope?.name.orEmpty()}")
            appendLine("sinceMillis=${query.sinceMillis.coerceAtLeast(0L)}")
            appendLine("limit=${query.limit.coerceIn(1, GitHubRefreshHistoryQueryDefaults.MAX_LIMIT)}")
            if (mode == "list" || mode == "detail") {
                records.forEachIndexed { index, record ->
                    appendLine(record.toMcpLine("refresh[$index]"))
                    if (includeSlowItems || mode == "detail") {
                        record.slowItems.forEachIndexed { slowIndex, slowItem ->
                            appendLine(slowItem.toMcpLine("refresh[$index].slow[$slowIndex]"))
                        }
                    }
                    if (includeFailures || mode == "detail") {
                        record.failureSummaries.forEachIndexed { failureIndex, failure ->
                            appendLine(failure.toMcpLine("refresh[$index].failure[$failureIndex]"))
                        }
                    }
                }
            }
        }.trim()
    }

    private fun StringBuilder.appendSummary(summary: GitHubRefreshHistorySummary) {
        appendLine("ok=true")
        appendLine("storedCount=${summary.storedCount}")
        appendLine("matchedCount=${summary.matchedCount}")
        appendLine("completedCount=${summary.completedCount}")
        appendLine("failedCount=${summary.failedCount}")
        appendLine("cancelledCount=${summary.cancelledCount}")
        appendLine("partialFailedCount=${summary.partialFailedCount}")
        appendLine("updatableRecordCount=${summary.updatableRecordCount}")
        appendLine("totalTargetCount=${summary.totalTargetCount}")
        appendLine("totalCompletedCount=${summary.totalCompletedCount}")
        appendLine("totalFailedItemCount=${summary.totalFailedItemCount}")
        appendLine("totalStableUpdateCount=${summary.totalStableUpdateCount}")
        appendLine("totalPreReleaseUpdateCount=${summary.totalPreReleaseUpdateCount}")
        appendLine("totalRepositoryItemCount=${summary.totalRepositoryItemCount}")
        appendLine("totalDirectApkItemCount=${summary.totalDirectApkItemCount}")
        appendLine("totalFdroidItemCount=${summary.totalFdroidItemCount}")
        appendLine("totalOtherItemCount=${summary.totalOtherItemCount}")
        appendLine("maxRequestedConcurrency=${summary.maxRequestedConcurrency}")
        appendLine("maxPeakConcurrentCalls=${summary.maxPeakConcurrentCalls}")
        appendLine("averageElapsedMs=${summary.averageElapsedMs}")
        appendLine("p95ElapsedMs=${summary.p95ElapsedMs}")
        appendLine("latestStartedAtMillis=${summary.latestStartedAtMillis}")
        appendLine("latestFinishedAtMillis=${summary.latestFinishedAtMillis}")
    }

    private fun GitHubRefreshHistoryRecord.toMcpLine(prefix: String): String {
        val status =
            when {
                outcome == GitHubRefreshHistoryOutcome.Completed && failedCount > 0 -> "partial_failed"
                else -> outcome.name.lowercase(Locale.ROOT)
            }
        return "$prefix=id:$id | sessionId:$sessionId | source:${source.name} | scope:${scope.name} | outcome:$status | target:$targetCount | completed:$completedCount | updates:$updatableCount | preUpdates:$preReleaseUpdateCount | failed:$failedCount | elapsedMs:$elapsedMs | p50ItemMs:$p50ItemMs | p95ItemMs:$p95ItemMs | maxItemMs:$maxItemMs | maxConcurrency:$maxConcurrency | peakConcurrentCalls:$peakConcurrentCalls | network:${networkKind.ifBlank { "unknown" }}${if (networkMetered) "/metered" else ""} | directApkConcurrency:$directApkConcurrency | fdroidConcurrency:$fdroidConcurrency | repositoryItems:$repositoryItemCount | directApkItems:$directApkItemCount | fdroidItems:$fdroidItemCount | otherItems:$otherItemCount | startedAtMillis:$startedAtMillis | finishedAtMillis:$finishedAtMillis | note:${note.toMcpValue()}"
    }

    private fun GitHubRefreshHistorySlowItem.toMcpLine(prefix: String): String {
        val repoLabel =
            listOf(owner, repo)
                .filter { it.isNotBlank() }
                .joinToString("/")
        return "$prefix=trackId:${trackId.toMcpValue()} | repo:${repoLabel.toMcpValue()} | package:${packageName.toMcpValue()} | label:${appLabel.toMcpValue()} | sourceMode:${sourceMode.toMcpValue()} | elapsedMs:$elapsedMs | strategy:${strategyId.toMcpValue()} | localVersionElapsedMs:$localVersionElapsedMs | snapshotElapsedMs:$snapshotElapsedMs | snapshotFromCache:$snapshotFromCache | profileElapsedMs:$profileElapsedMs | profileFromCache:$profileFromCache | preciseApkElapsedMs:$preciseApkElapsedMs | preciseApkRequested:$preciseApkRequested | comparisonElapsedMs:$comparisonElapsedMs | unclassifiedElapsedMs:$unclassifiedElapsedMs | fallbackStrategy:${fallbackStrategyId.toMcpValue()} | status:${status.toMcpValue()} | message:${message.toMcpValue()}${network.toMcpSuffix()}"
    }

    /**
     * The phase breakdown for one slow item, which is the line somebody actually asks about.
     *
     * The record line has carried the network profile since it was measured, but a record says
     * "this refresh was slow" and a reader who has got as far as asking wants "this repository was
     * slow, connecting". Blank rather than a row of zeros when nothing was measured: a refresh
     * served entirely from cache made no calls, and printing zeroes for it reads as a fast network
     * rather than as no network.
     */
    private fun NetworkTimingSummary.toMcpSuffix(): String {
        if (isEmpty) return ""
        return " | networkCalls:$callCount | networkQueuedMs:$queuedMs | networkDnsMs:$dnsMs | networkConnectMs:$connectMs | networkWaitingMs:$waitingMs | networkBodyMs:$bodyMs | networkBytes:$bytes | networkReusedCalls:$reusedConnectionCalls | networkFailedCalls:$failedCalls | networkDominantPhase:${dominantPhase.toMcpValue()}" +
            if (protocol.isNotBlank()) " | networkProtocol:${protocol.toMcpValue()}" else ""
    }

    private fun GitHubRefreshHistoryFailureSummary.toMcpLine(prefix: String): String {
        val repoLabel =
            listOf(owner, repo)
                .filter { it.isNotBlank() }
                .joinToString("/")
        return "$prefix=trackId:${trackId.toMcpValue()} | repo:${repoLabel.toMcpValue()} | package:${packageName.toMcpValue()} | label:${appLabel.toMcpValue()} | sourceMode:${sourceMode.toMcpValue()} | elapsedMs:$elapsedMs | category:${failureCategory.toMcpValue()} | responseType:${responseType.toMcpValue()} | limitBytes:$limitBytes | declaredBytes:$declaredBytes | observedBytes:$observedBytes | limitStage:${limitStage.toMcpValue()} | message:${message.toMcpValue()}"
    }

    private fun buildRefreshHistoryQuery(
        arguments: Map<String, Any?>?,
        defaultLimit: Int,
    ): GitHubRefreshHistoryQuery {
        return GitHubRefreshHistoryQuery(
            outcome =
                GitHubRefreshHistoryExportService.parseOutcomeFilter(
                    argString(arguments?.get("outcome")),
                ),
            source =
                GitHubRefreshHistoryExportService.parseSourceFilter(
                    argString(arguments?.get("source")),
                ),
            scope =
                GitHubRefreshHistoryExportService.parseScopeFilter(
                    argString(arguments?.get("scope")),
                ),
            sinceMillis = argLong(arguments?.get("sinceMillis"), 0L).coerceAtLeast(0L),
            limit =
                argInt(arguments?.get("limit"), defaultLimit)
                    .coerceIn(1, GitHubRefreshHistoryQueryDefaults.MAX_LIMIT),
        )
    }

    private fun String.toMcpValue(): String =
        trim()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .ifBlank { "-" }

    private fun argLong(
        value: Any?,
        defaultValue: Long,
    ): Long = argString(value).trim().toLongOrNull() ?: defaultValue
}
