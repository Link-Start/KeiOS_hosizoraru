package os.kei.feature.github.domain

import org.junit.Test
import os.kei.core.io.NetworkPhase
import os.kei.core.io.NetworkTimingSummary
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import os.kei.core.json.optArray
import os.kei.core.json.optInt
import os.kei.core.json.optLong
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.model.GitHubRefreshHistoryFailureSummary
import os.kei.feature.github.model.GitHubRefreshHistoryOutcome
import os.kei.feature.github.model.GitHubRefreshHistoryRecord
import os.kei.feature.github.model.GitHubRefreshHistorySlowItem

class GitHubRefreshHistoryExportServiceTest {
    @Test
    fun `export json includes metadata filters summary and records`() {
        val records =
            listOf(
                createRecord(
                    id = "newer",
                    sessionId = 2L,
                    finishedAtMillis = 2_000L,
                    updatableCount = 2,
                ),
                createRecord(
                    id = "older",
                    sessionId = 1L,
                    finishedAtMillis = 1_000L,
                    failedCount = 1,
                ),
            )

        val raw =
            GitHubRefreshHistoryExportService.buildExportJson(
                allRecords = records,
                query =
                    GitHubRefreshHistoryQuery(
                        outcome = GitHubRefreshHistoryOutcomeFilter.Updatable,
                        limit = 10,
                    ),
                exportedAtMillis = 3_000L,
            )
        val root = raw.parseJsonObjectOrNull()

        requireNotNull(root)
        assertEquals("keios.github.refresh-history", root.optString("format"))
        assertEquals(2, root.optInt("schemaVersion"))
        assertEquals("local_only", root.optString("syncScope"))
        assertEquals(3_000L, root.optLong("exportedAtMillis"))
        assertEquals("updatable", root.optObject("filters")?.optString("outcome"))
        assertEquals(2, root.optObject("summary")?.optInt("storedCount"))
        assertEquals(1, root.optObject("summary")?.optInt("matchedCount"))
        assertEquals(3, root.optObject("summary")?.optInt("maxRequestedConcurrency"))
        assertEquals(0, root.optObject("summary")?.optInt("maxPeakConcurrentCalls"))
        val recordJson = root.optArray("records")?.optObject(0)
        assertEquals("newer", recordJson?.optString("id"))
        assertEquals(3, recordJson?.optInt("maxConcurrency"))
        // This is the shape somebody pastes into a bug report, so the phase split is part of the
        // contract rather than an implementation detail: a stage duration alone cannot say whether
        // the phone was queued behind our own budget, shaking hands, or waiting on GitHub.
        val slowItemJson = recordJson?.optArray("slowItems")?.optObject(0)
        assertEquals("track-2", slowItemJson?.optString("trackId"))
        assertEquals(30L, slowItemJson?.optLong("snapshotElapsedMs"))
        assertEquals(2, slowItemJson?.optInt("networkCallCount"))
        assertEquals(18L, slowItemJson?.optLong("networkWaitingMs"))
        assertEquals(21_504L, slowItemJson?.optLong("networkBytes"))
        assertEquals(1, slowItemJson?.optInt("networkReusedConnectionCalls"))
        assertEquals(NetworkPhase.WAITING, slowItemJson?.optString("networkDominantPhase"))
        assertEquals("h2", slowItemJson?.optString("networkProtocol"))
        assertEquals(3L, slowItemJson?.optLong("localVersionElapsedMs"))
        assertEquals(2L, slowItemJson?.optLong("comparisonElapsedMs"))
        assertEquals(7L, slowItemJson?.optLong("unclassifiedElapsedMs"))
    }

    @Test
    fun `export includes structured failure diagnostics`() {
        val raw = GitHubRefreshHistoryExportService.buildExportJson(
            allRecords = listOf(createRecord(id = "failed", sessionId = 1L, failedCount = 1)),
            query = GitHubRefreshHistoryQuery(outcome = GitHubRefreshHistoryOutcomeFilter.Failed),
            exportedAtMillis = 3_000L,
        )
        val failure = raw.parseJsonObjectOrNull()
            ?.optArray("records")
            ?.optObject(0)
            ?.optArray("failureSummaries")
            ?.optObject(0)

        requireNotNull(failure)
        assertEquals("response_too_large", failure.optString("failureCategory"))
        assertEquals("releases_api", failure.optString("responseType"))
        assertEquals(12L * 1024L * 1024L, failure.optLong("limitBytes"))
        assertEquals(14L * 1024L * 1024L, failure.optLong("declaredBytes"))
        assertEquals(14L * 1024L * 1024L, failure.optLong("observedBytes"))
        assertEquals("DeclaredLength", failure.optString("limitStage"))
    }

    @Test
    fun `failed filter keeps failed outcome and partial failures`() {
        val records =
            listOf(
                createRecord(
                    id = "completed",
                    sessionId = 1L,
                    outcome = GitHubRefreshHistoryOutcome.Completed,
                ),
                createRecord(
                    id = "partial",
                    sessionId = 2L,
                    outcome = GitHubRefreshHistoryOutcome.Completed,
                    failedCount = 1,
                ),
                createRecord(
                    id = "failed",
                    sessionId = 3L,
                    outcome = GitHubRefreshHistoryOutcome.Failed,
                ),
            )

        val filtered =
            GitHubRefreshHistoryExportService.filterRecords(
                records = records,
                query = GitHubRefreshHistoryQuery(outcome = GitHubRefreshHistoryOutcomeFilter.Failed),
            )

        assertEquals(listOf("failed", "partial"), filtered.map { it.id })
    }

    @Test
    fun `summary calculates totals from matched records`() {
        val records =
            listOf(
                createRecord(
                    id = "one",
                    sessionId = 1L,
                    targetCount = 4,
                    completedCount = 4,
                    updatableCount = 1,
                    preReleaseUpdateCount = 2,
                    elapsedMs = 100L,
                ),
                createRecord(
                    id = "two",
                    sessionId = 2L,
                    targetCount = 6,
                    completedCount = 6,
                    failedCount = 2,
                    elapsedMs = 300L,
                ),
            )

        val summary =
            GitHubRefreshHistoryExportService.summarize(
                allRecords = records,
                records = records,
            )

        assertEquals(2, summary.storedCount)
        assertEquals(2, summary.matchedCount)
        assertEquals(10, summary.totalTargetCount)
        assertEquals(10, summary.totalCompletedCount)
        assertEquals(2, summary.totalFailedItemCount)
        assertEquals(1, summary.totalStableUpdateCount)
        assertEquals(2, summary.totalPreReleaseUpdateCount)
        assertEquals(4, summary.totalRepositoryItemCount)
        assertEquals(2, summary.totalDirectApkItemCount)
        assertEquals(2, summary.totalFdroidItemCount)
        assertEquals(0, summary.totalOtherItemCount)
        // The configured number and the one the device managed are now separate: they were
        // silently different for months and the old name claimed to be the latter.
        assertEquals(3, summary.maxRequestedConcurrency)
        assertEquals(0, summary.maxPeakConcurrentCalls, "these records predate the instrument")
        assertEquals(200L, summary.averageElapsedMs)
        assertTrue(summary.latestFinishedAtMillis > 0L)
    }

    private fun createRecord(
        id: String,
        sessionId: Long,
        outcome: GitHubRefreshHistoryOutcome = GitHubRefreshHistoryOutcome.Completed,
        targetCount: Int = 3,
        completedCount: Int = 3,
        updatableCount: Int = 0,
        preReleaseUpdateCount: Int = 0,
        failedCount: Int = 0,
        elapsedMs: Long = 200L,
        finishedAtMillis: Long = 1_778_000_000_000L + sessionId,
    ): GitHubRefreshHistoryRecord =
        GitHubRefreshHistoryRecord(
            id = id,
            sessionId = sessionId,
            scope = GitHubRefreshScope.AllTracked,
            source = GitHubRefreshSource.Page,
            outcome = outcome,
            totalTrackedCount = targetCount,
            targetCount = targetCount,
            completedCount = completedCount,
            updatableCount = updatableCount,
            preReleaseUpdateCount = preReleaseUpdateCount,
            failedCount = failedCount,
            startedAtMillis = finishedAtMillis - elapsedMs,
            finishedAtMillis = finishedAtMillis,
            elapsedMs = elapsedMs,
            p50ItemMs = 10L,
            p95ItemMs = 20L,
            maxItemMs = 30L,
            maxConcurrency = 3,
            directApkConcurrency = 1,
            fdroidConcurrency = 1,
            repositoryItemCount = 2,
            directApkItemCount = 1,
            fdroidItemCount = 1,
            otherItemCount = 0,
            slowItems =
                listOf(
                    GitHubRefreshHistorySlowItem(
                        trackId = "track-$sessionId",
                        owner = "owner",
                        repo = "repo",
                        packageName = "dev.example",
                        appLabel = "Example",
                        sourceMode = "github_repository",
                        elapsedMs = 30L,
                        status = "UpToDate",
                        message = "ok",
                        strategyId = "atom_feed",
                        localVersionElapsedMs = 3L,
                        snapshotElapsedMs = 30L,
                        network = NetworkTimingSummary(
                            callCount = 2,
                            queuedMs = 4L,
                            connectMs = 6L,
                            waitingMs = 18L,
                            bodyMs = 2L,
                            bytes = 21_504L,
                            reusedConnectionCalls = 1,
                            protocol = "h2",
                        ),
                        profileElapsedMs = 10L,
                        profileFromCache = true,
                        comparisonElapsedMs = 2L,
                        unclassifiedElapsedMs = 7L,
                    ),
                ),
            failureSummaries =
                if (failedCount > 0) {
                    listOf(
                        GitHubRefreshHistoryFailureSummary(
                            trackId = "track-$sessionId",
                            owner = "owner",
                            repo = "repo",
                            packageName = "dev.example",
                            appLabel = "Example",
                            sourceMode = "github_repository",
                            message = "timeout",
                            elapsedMs = 30L,
                            failureCategory = "response_too_large",
                            responseType = "releases_api",
                            limitBytes = 12L * 1024L * 1024L,
                            declaredBytes = 14L * 1024L * 1024L,
                            observedBytes = 14L * 1024L * 1024L,
                            limitStage = "DeclaredLength",
                        ),
                    )
                } else {
                    emptyList()
                },
            note = "",
        )
}
