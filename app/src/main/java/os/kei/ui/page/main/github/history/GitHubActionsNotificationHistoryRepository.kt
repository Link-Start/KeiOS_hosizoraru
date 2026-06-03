package os.kei.ui.page.main.github.history

import os.kei.feature.github.domain.GitHubActionsService
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord

internal class GitHubActionsNotificationHistoryRepository(
    private val actionsService: GitHubActionsService = GitHubActionsService(),
) {
    suspend fun loadHistory(): List<GitHubActionsNotificationHistoryRecord> {
        return actionsService.loadGitHubActionsNotificationHistory()
    }
}
