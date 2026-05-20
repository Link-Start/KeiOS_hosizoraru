package os.kei.ui.page.main.github.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.ui.page.main.github.page.GitHubPageState
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.LiquidSheetInitialDetent
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet

@Composable
internal fun GitHubActionsSheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    state: GitHubPageState,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    onSelectWorkflow: (Long) -> Unit,
    onSelectBranch: (String) -> Unit,
    onSelectRun: (Long) -> Unit,
    onLoadMoreRuns: () -> Unit,
    onBranchesExpandedChange: (Boolean) -> Unit,
    onWorkflowsExpandedChange: (Boolean) -> Unit,
    onRunsExpandedChange: (Boolean) -> Unit,
    onArtifactsExpandedChange: (Boolean) -> Unit,
    onRefreshRun: (Long) -> Unit,
    onInstallArtifact: (Long, Long) -> Unit,
    onDownloadArtifact: (Long, Long) -> Unit,
    onShareArtifact: (Long, Long) -> Unit,
    onOpenRun: () -> Unit,
    onOpenArtifactDetail: (GitHubActionsRunMatch, GitHubActionsArtifactMatch, Boolean) -> Unit
) {
    val refreshing = state.actionsLoading || state.actionsRunsLoading ||
        state.actionsStatusRefreshingRunIds.any { it.value }
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.github_actions_sheet_title),
        onDismissRequest = onDismissRequest,
        initialDetent = LiquidSheetInitialDetent.Full,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(
                    if (refreshing) {
                        R.string.common_loading
                    } else {
                        R.string.github_actions_sheet_cd_refresh
                    }
                ),
                enabled = !refreshing,
                onClick = onRefresh
            )
        }
    ) {
        GitHubActionsSheetContent(
            state = state,
            backdrop = backdrop,
            onSelectWorkflow = onSelectWorkflow,
            onSelectBranch = onSelectBranch,
            onSelectRun = onSelectRun,
            onLoadMoreRuns = onLoadMoreRuns,
            onBranchesExpandedChange = onBranchesExpandedChange,
            onWorkflowsExpandedChange = onWorkflowsExpandedChange,
            onRunsExpandedChange = onRunsExpandedChange,
            onArtifactsExpandedChange = onArtifactsExpandedChange,
            onRefreshRun = onRefreshRun,
            onInstallArtifact = onInstallArtifact,
            onDownloadArtifact = onDownloadArtifact,
            onShareArtifact = onShareArtifact,
            onOpenRun = onOpenRun,
            onOpenArtifactDetail = onOpenArtifactDetail
        )
    }
}
