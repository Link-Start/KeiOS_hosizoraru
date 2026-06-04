package os.kei.ui.page.main.github.history

import androidx.compose.runtime.Immutable
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord

@Immutable
internal data class GitHubActionsNotificationHistoryUiRecord(
    val record: GitHubActionsNotificationHistoryRecord,
    val packageName: String,
)
