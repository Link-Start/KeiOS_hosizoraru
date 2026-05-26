@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.actions

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.localizedGitHubActionsErrorMessage
import os.kei.ui.page.main.github.page.GitHubActionsArtifactFilter
import os.kei.ui.page.main.github.page.GitHubPageState
import os.kei.ui.page.main.widget.sheet.SheetContentColumn

@Composable
internal fun GitHubActionsSheetContent(
    state: GitHubPageState,
    derivedState: GitHubActionsSheetUiState,
    backdrop: LayerBackdrop,
    onSelectWorkflow: (Long) -> Unit,
    onSelectBranch: (String) -> Unit,
    onSelectRun: (Long) -> Unit,
    onLoadMoreRuns: () -> Unit,
    onBranchesExpandedChange: (Boolean) -> Unit,
    onWorkflowsExpandedChange: (Boolean) -> Unit,
    onRunsExpandedChange: (Boolean) -> Unit,
    onArtifactsExpandedChange: (Boolean) -> Unit,
    onArtifactFilterChange: (GitHubActionsArtifactFilter) -> Unit,
    onRefreshRun: (Long) -> Unit,
    onInstallArtifact: (Long, Long) -> Unit,
    onDownloadArtifact: (Long, Long) -> Unit,
    onShareArtifact: (Long, Long) -> Unit,
    onOpenRun: () -> Unit,
    onOpenArtifactDetail: (GitHubActionsRunMatch, GitHubActionsArtifactMatch, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val workflows = state.actionsWorkflows

    SheetContentColumn(verticalSpacing = 10.dp) {
        GitHubActionsSummaryCard(
            state = state,
            canResolveArtifacts = derivedState.canResolveArtifacts,
            isDark = isDark,
        )

        state.actionsError?.takeIf { it.isNotBlank() }?.let { message ->
            val localizedMessage = localizedGitHubActionsErrorMessage(context, message)
            val errorText =
                if (state.lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink) {
                    stringResource(R.string.github_actions_error_load_failed_nightly, localizedMessage)
                } else {
                    stringResource(R.string.github_actions_error_load_failed, localizedMessage)
                }
            GitHubActionsNoticeCard(
                text = errorText,
                accent = GitHubStatusPalette.Error,
                isDark = isDark,
            )
        }

        GitHubActionsBranchSection(
            state = state,
            isDark = isDark,
            onExpandedChange = onBranchesExpandedChange,
            onSelectBranch = onSelectBranch,
        )

        GitHubActionsWorkflowsSection(
            state = state,
            workflowsCount = workflows.size,
            selectedWorkflowId = derivedState.selectedWorkflowId,
            recommendedWorkflowId = derivedState.recommendedWorkflowId,
            isDark = isDark,
            onExpandedChange = onWorkflowsExpandedChange,
            onSelectWorkflow = onSelectWorkflow,
        )

        GitHubActionsRunsSection(
            state = state,
            selectedRunId = state.actionsSelectedRunId,
            selectedRun = derivedState.selectedRun,
            recommendedRunId = derivedState.recommendedRunId,
            isDark = isDark,
            backdrop = backdrop,
            onExpandedChange = onRunsExpandedChange,
            onSelectRun = onSelectRun,
            onRefreshRun = onRefreshRun,
            onOpenRun = onOpenRun,
            onLoadMoreRuns = onLoadMoreRuns,
        )

        GitHubActionsArtifactsSection(
            lookupConfig = state.lookupConfig,
            expanded = state.actionsArtifactsExpanded,
            selectedArtifactFilter = state.actionsArtifactFilter,
            downloadingArtifactId = state.actionsArtifactDownloadLoadingId,
            sharingArtifactId = state.actionsArtifactShareLoadingId,
            selectedRun = derivedState.selectedRun,
            selectedRunArtifactsLoading = derivedState.selectedRunArtifactsLoading,
            canResolveArtifacts = derivedState.canResolveArtifacts,
            visibleArtifactMatches = derivedState.visibleArtifactMatches,
            recommendedArtifactCount = derivedState.recommendedArtifactCount,
            alternativesArtifactCount = derivedState.alternativesArtifactCount,
            relativeTimeNowMillis = derivedState.relativeTimeNowMillis,
            isDark = isDark,
            backdrop = backdrop,
            onExpandedChange = onArtifactsExpandedChange,
            onArtifactFilterChange = onArtifactFilterChange,
            onInstallArtifact = onInstallArtifact,
            onDownloadArtifact = onDownloadArtifact,
            onShareArtifact = onShareArtifact,
            onOpenArtifactDetail = onOpenArtifactDetail,
            context = context,
        )
    }
}
