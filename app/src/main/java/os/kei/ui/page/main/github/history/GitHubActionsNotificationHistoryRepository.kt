package os.kei.ui.page.main.github.history

import os.kei.feature.github.domain.GitHubActionsService
import os.kei.feature.github.domain.GitHubTrackService

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

internal class GitHubActionsNotificationHistoryRepository(
    private val actionsService: GitHubActionsService = GitHubActionsService(),
    private val trackService: GitHubTrackService = GitHubTrackService(),
) {
    suspend fun loadHistory(): List<GitHubActionsNotificationHistoryUiRecord> {
        val records = actionsService.loadGitHubActionsNotificationHistory()
        if (records.isEmpty()) return emptyList()
        val packageNameByTrackId =
            runCatching {
                trackService
                    .loadTrackSnapshot()
                    .items
                    .associate { item -> item.id.trim() to item.packageName.trim() }
            }.getOrDefault(emptyMap())
        return records.map { record ->
            GitHubActionsNotificationHistoryUiRecord(
                record = record,
                packageName = packageNameByTrackId[record.trackId.trim()].orEmpty(),
            )
        }
    }

    suspend fun pruneOlderThanDays(
        days: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        if (days <= 0) return 0
        val cutoffMillis = nowMillis - days * MILLIS_PER_DAY
        return actionsService.pruneGitHubActionsNotificationHistoryBefore(cutoffMillis)
    }
}
