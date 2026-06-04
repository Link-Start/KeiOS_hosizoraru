package os.kei.ui.page.main.github.history

import org.junit.Test
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord
import kotlin.test.assertEquals

class GitHubActionsNotificationHistoryOptionsTest {
    @Test
    fun `filters by actions outcome and android artifacts`() {
        val records =
            listOf(
                createUiRecord(appLabel = "Stable", conclusion = "success", androidArtifactCount = 1),
                createUiRecord(appLabel = "Failed", conclusion = "failure"),
                createUiRecord(appLabel = "Cancelled", conclusion = "cancelled"),
                createUiRecord(appLabel = "Running", status = "in_progress", conclusion = ""),
            )

        assertEquals(
            listOf("Stable"),
            records.display(filterMode = GitHubActionsHistoryFilterMode.Success).appLabels(),
        )
        assertEquals(
            listOf("Cancelled", "Failed"),
            records.display(filterMode = GitHubActionsHistoryFilterMode.Failed).appLabels(),
        )
        assertEquals(
            listOf("Running"),
            records.display(filterMode = GitHubActionsHistoryFilterMode.Running).appLabels(),
        )
        assertEquals(
            listOf("Stable"),
            records.display(filterMode = GitHubActionsHistoryFilterMode.AndroidArtifacts).appLabels(),
        )
    }

    @Test
    fun `sorts by time app repository workflow and run`() {
        val records =
            listOf(
                createUiRecord(
                    appLabel = "Beta",
                    repo = "middle",
                    workflowName = "Package",
                    runNumber = 20L,
                    notifiedAtMillis = 2000L,
                ),
                createUiRecord(
                    appLabel = "Alpha",
                    repo = "omega",
                    workflowName = "Release",
                    runNumber = 10L,
                    notifiedAtMillis = 1000L,
                ),
                createUiRecord(
                    appLabel = "Zulu",
                    repo = "alpha",
                    workflowName = "Build",
                    runNumber = 30L,
                    notifiedAtMillis = 3000L,
                ),
            )

        assertEquals(
            listOf("Zulu", "Beta", "Alpha"),
            records.display(sortMode = GitHubActionsHistorySortMode.NotifiedAt).appLabels(),
        )
        assertEquals(
            listOf("Alpha", "Beta", "Zulu"),
            records.display(
                sortMode = GitHubActionsHistorySortMode.App,
                sortDirection = GitHubActionsHistorySortDirection.Ascending,
            ).appLabels(),
        )
        assertEquals(
            listOf("Alpha", "Beta", "Zulu"),
            records.display(sortMode = GitHubActionsHistorySortMode.Repository).appLabels(),
        )
        assertEquals(
            listOf("Alpha", "Beta", "Zulu"),
            records.display(sortMode = GitHubActionsHistorySortMode.Workflow).appLabels(),
        )
        assertEquals(
            listOf("Zulu", "Beta", "Alpha"),
            records.display(sortMode = GitHubActionsHistorySortMode.RunNumber).appLabels(),
        )
    }

    private fun List<GitHubActionsNotificationHistoryUiRecord>.display(
        filterMode: GitHubActionsHistoryFilterMode = GitHubActionsHistoryFilterMode.All,
        sortMode: GitHubActionsHistorySortMode = GitHubActionsHistorySortMode.NotifiedAt,
        sortDirection: GitHubActionsHistorySortDirection = GitHubActionsHistorySortDirection.Descending,
    ): List<GitHubActionsNotificationHistoryUiRecord> =
        buildGitHubActionsHistoryDisplayRecords(
            records = this,
            filterMode = filterMode,
            sortMode = sortMode,
            sortDirection = sortDirection,
        )

    private fun List<GitHubActionsNotificationHistoryUiRecord>.appLabels(): List<String> =
        map { item -> item.record.appLabel }

    private fun createUiRecord(
        appLabel: String,
        repo: String = appLabel.lowercase(),
        workflowName: String = "CI",
        status: String = "completed",
        conclusion: String = "success",
        runNumber: Long = 1L,
        notifiedAtMillis: Long = runNumber * 1000L,
        androidArtifactCount: Int = 0,
    ): GitHubActionsNotificationHistoryUiRecord =
        GitHubActionsNotificationHistoryUiRecord(
            record =
                GitHubActionsNotificationHistoryRecord(
                    trackId = "owner/$repo|pkg.$repo",
                    owner = "owner",
                    repo = repo,
                    appLabel = appLabel,
                    workflowId = runNumber,
                    workflowName = workflowName,
                    workflowPath = ".github/workflows/$repo.yml",
                    runId = runNumber * 100L,
                    runNumber = runNumber,
                    runAttempt = 1,
                    runDisplayName = "Run $runNumber",
                    headBranch = "main",
                    headSha = "abcdef$runNumber",
                    event = "workflow_dispatch",
                    status = status,
                    conclusion = conclusion,
                    htmlUrl = "https://github.com/owner/$repo/actions/runs/${runNumber * 100L}",
                    artifactCount = androidArtifactCount,
                    androidArtifactCount = androidArtifactCount,
                    checkedAtMillis = notifiedAtMillis - 100L,
                    notifiedAtMillis = notifiedAtMillis,
                    notificationTitle = "Actions",
                    notificationContent = "$appLabel #$runNumber",
                ),
            packageName = "pkg.$repo",
        )
}
