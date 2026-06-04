package os.kei.ui.page.main.github.history

import androidx.annotation.StringRes
import os.kei.R
import java.util.Locale

internal enum class GitHubActionsHistoryFilterMode(
    @param:StringRes val labelRes: Int,
) {
    All(R.string.github_actions_history_filter_all),
    Success(R.string.github_actions_history_filter_success),
    Failed(R.string.github_actions_history_filter_failed),
    Running(R.string.github_actions_history_filter_running),
    AndroidArtifacts(R.string.github_actions_history_filter_android_artifacts),
}

internal enum class GitHubActionsHistorySortMode(
    @param:StringRes val labelRes: Int,
) {
    NotifiedAt(R.string.github_actions_history_sort_notified),
    App(R.string.github_actions_history_sort_app),
    Repository(R.string.github_actions_history_sort_repository),
    Workflow(R.string.github_actions_history_sort_workflow),
    RunNumber(R.string.github_actions_history_sort_run),
}

internal enum class GitHubActionsHistorySortDirection(
    @param:StringRes val labelRes: Int,
) {
    Descending(R.string.github_actions_history_sort_descending),
    Ascending(R.string.github_actions_history_sort_ascending),
}

internal enum class GitHubActionsHistoryCleanupAge(
    val days: Int,
    @param:StringRes val labelRes: Int,
) {
    SevenDays(7, R.string.github_actions_history_cleanup_7d),
    ThirtyDays(30, R.string.github_actions_history_cleanup_30d),
    NinetyDays(90, R.string.github_actions_history_cleanup_90d),
}

internal fun buildGitHubActionsHistoryDisplayRecords(
    records: List<GitHubActionsNotificationHistoryUiRecord>,
    filterMode: GitHubActionsHistoryFilterMode,
    sortMode: GitHubActionsHistorySortMode,
    sortDirection: GitHubActionsHistorySortDirection,
): List<GitHubActionsNotificationHistoryUiRecord> {
    val filtered =
        records.filter { item ->
            val record = item.record
            when (filterMode) {
                GitHubActionsHistoryFilterMode.All -> true
                GitHubActionsHistoryFilterMode.Success ->
                    record.conclusion.equals("success", ignoreCase = true)
                GitHubActionsHistoryFilterMode.Failed ->
                    record.conclusion.equals("failure", ignoreCase = true) ||
                        record.conclusion.equals("cancelled", ignoreCase = true) ||
                        record.conclusion.equals("timed_out", ignoreCase = true) ||
                        record.conclusion.equals("action_required", ignoreCase = true) ||
                        record.conclusion.equals("startup_failure", ignoreCase = true)
                GitHubActionsHistoryFilterMode.Running ->
                    record.status.equals("in_progress", ignoreCase = true) ||
                        record.status.equals("queued", ignoreCase = true) ||
                        record.status.equals("waiting", ignoreCase = true) ||
                        record.status.equals("pending", ignoreCase = true)
                GitHubActionsHistoryFilterMode.AndroidArtifacts -> record.androidArtifactCount > 0
            }
        }
    val comparator = gitHubActionsHistoryComparator(sortMode)
    val sorted = filtered.sortedWith(comparator)
    return when (sortDirection) {
        GitHubActionsHistorySortDirection.Descending -> sorted
        GitHubActionsHistorySortDirection.Ascending -> sorted.asReversed()
    }
}

private fun gitHubActionsHistoryComparator(
    sortMode: GitHubActionsHistorySortMode,
): Comparator<GitHubActionsNotificationHistoryUiRecord> {
    val textComparator = compareByDescending<GitHubActionsNotificationHistoryUiRecord> {
        it.record.appLabel.ifBlank { it.record.repositoryLabel }.lowercase(Locale.ROOT)
    }
    val tieBreakers =
        compareByDescending<GitHubActionsNotificationHistoryUiRecord> { it.record.notifiedAtMillis }
            .thenByDescending { it.record.runNumber }
    return when (sortMode) {
        GitHubActionsHistorySortMode.NotifiedAt ->
            compareByDescending<GitHubActionsNotificationHistoryUiRecord> { it.record.notifiedAtMillis }
                .thenByDescending { it.record.runNumber }
                .thenBy { it.record.appLabel.lowercase(Locale.ROOT) }
        GitHubActionsHistorySortMode.App ->
            textComparator.then(tieBreakers)
        GitHubActionsHistorySortMode.Repository ->
            compareByDescending<GitHubActionsNotificationHistoryUiRecord> {
                it.record.repositoryLabel.lowercase(Locale.ROOT)
            }.then(tieBreakers)
        GitHubActionsHistorySortMode.Workflow ->
            compareByDescending<GitHubActionsNotificationHistoryUiRecord> {
                it.record.workflowName
                    .ifBlank { it.record.workflowPath }
                    .lowercase(Locale.ROOT)
            }.then(tieBreakers)
        GitHubActionsHistorySortMode.RunNumber ->
            compareByDescending<GitHubActionsNotificationHistoryUiRecord> { it.record.runNumber }
                .thenByDescending { it.record.runId }
                .thenByDescending { it.record.notifiedAtMillis }
    }
}
