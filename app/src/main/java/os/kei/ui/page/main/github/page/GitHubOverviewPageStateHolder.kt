package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.section.GitHubOverviewUiState

@Stable
internal class GitHubOverviewPageStateHolder(
    overviewUiState: GitHubOverviewUiState,
) {
    var overviewExpanded by mutableStateOf(overviewUiState.expanded)
    var overviewVisibleEntries by mutableStateOf(overviewUiState.visibleEntries)
    var overviewRefreshState by mutableStateOf(OverviewRefreshState.Idle)
    var lastRefreshMs by mutableStateOf(0L)
    var refreshIntervalHours by mutableStateOf(3)
    var refreshProgress by mutableStateOf(0f)
    var refreshAllJob by mutableStateOf<Job?>(null)
    var refreshSessionId by mutableStateOf(0L)
    var refreshTargetIds by mutableStateOf<Set<String>>(emptySet())
    var deleteInProgress by mutableStateOf(false)
}
