package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.launch
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.actions.GitHubActionsUiStateStore

internal class GitHubActionsActions(
    private val env: GitHubPageActionEnvironment,
    assetActions: GitHubAssetActions,
) {
    private val scope get() = env.scope
    private val state get() = env.state
    private val actionsRepository get() = env.actionsRepository
    private val selectionDelegate = GitHubActionsSelectionDelegate(env)
    private val runStatusDelegate = GitHubActionsRunStatusDelegate(env, selectionDelegate)
    private val workflowStateDelegate = GitHubActionsWorkflowStateDelegate(env, selectionDelegate)
    private val sheetLoader =
        GitHubActionsSheetLoader(
            env = env,
            selectionDelegate = selectionDelegate,
            runStatusDelegate = runStatusDelegate,
            workflowStateDelegate = workflowStateDelegate,
        )
    private val artifactActions =
        GitHubActionsArtifactActions(
            env = env,
            actionsRepository = actionsRepository,
            assetActions = assetActions,
            onDownloadHistoryChanged = { sheetLoader.reselectActionsMatchesAfterHistoryChange() },
        )

    fun openActionsSheet(item: GitHubTrackedApp) {
        if (state.trackedItems.none { it.id == item.id }) return
        state.resetActionsSheetState()
        state.actionsTargetItem = item
        state.showActionsSheet = true
        scope.launch {
            sheetLoader.loadActionsOverview(item = item, preferredWorkflowId = null)
        }
    }

    fun closeActionsSheet() {
        state.dismissActionsSheet()
    }

    fun refreshActionsSheet() {
        val item = state.actionsTargetItem ?: return
        if (state.actionsLoading || state.actionsRunsLoading) return
        val selectedWorkflowId = state.actionsSelectedWorkflowId
        scope.launch {
            sheetLoader.loadActionsOverview(item = item, preferredWorkflowId = selectedWorkflowId)
        }
    }

    fun selectActionsWorkflow(workflowId: Long) {
        val item = state.actionsTargetItem ?: return
        val workflow =
            state.actionsWorkflows
                .firstOrNull { it.workflow.id == workflowId }
                ?.workflow
                ?: return
        state.actionsSelectedWorkflowId = workflow.id
        state.actionsWorkflowManuallySelected = true
        state.actionsRunLimit = GitHubActionsSheetLimits.DEFAULT_RUN_LIMIT
        state.actionsSelectedBranch = ""
        state.actionsBranchManuallySelected = false
        state.actionsBranchOptions = emptyList()
        state.actionsSelectedRunId = null
        runStatusDelegate.cancelRunWatch()
        scope.launch {
            sheetLoader.loadWorkflowSnapshot(
                item = item,
                workflow = workflow,
                preferredRunId = null,
            )
        }
    }

    fun loadMoreActionsRuns() {
        val item = state.actionsTargetItem ?: return
        val workflow = selectedWorkflowMatch()?.workflow ?: return
        val currentRunId = state.actionsSelectedRunId
        val nextLimit =
            (state.actionsRunLimit + GitHubActionsSheetLimits.RUN_PAGE_SIZE)
                .coerceAtMost(GitHubActionsSheetLimits.MAX_RUN_LIMIT)
        if (nextLimit <= state.actionsRunLimit) return
        state.actionsRunLimit = nextLimit
        scope.launch {
            sheetLoader.loadWorkflowSnapshot(
                item = item,
                workflow = workflow,
                preferredRunId = currentRunId,
                keepCurrentRunsWhileLoading = true,
            )
        }
    }

    fun selectActionsBranch(branch: String) {
        val item = state.actionsTargetItem ?: return
        val workflow = selectedWorkflowMatch()?.workflow ?: return
        val normalized = branch.trim()
        if (normalized.isBlank()) return
        if (state.actionsSelectedBranch.equals(normalized, ignoreCase = true)) return
        state.actionsSelectedBranch = normalized
        state.actionsBranchManuallySelected = true
        state.actionsRunLimit = GitHubActionsSheetLimits.DEFAULT_RUN_LIMIT
        state.actionsSelectedRunId = null
        runStatusDelegate.cancelRunWatch()
        scope.launch {
            sheetLoader.loadWorkflowSnapshot(
                item = item,
                workflow = workflow,
                preferredRunId = null,
            )
        }
    }

    fun setBranchesExpanded(value: Boolean) {
        state.actionsBranchesExpanded = value
        GitHubActionsUiStateStore.setBranchesExpanded(value)
    }

    fun setWorkflowsExpanded(value: Boolean) {
        state.actionsWorkflowsExpanded = value
        GitHubActionsUiStateStore.setWorkflowsExpanded(value)
    }

    fun setRunsExpanded(value: Boolean) {
        state.actionsRunsExpanded = value
        GitHubActionsUiStateStore.setRunsExpanded(value)
    }

    fun selectActionsRun(runId: Long) {
        val item = state.actionsTargetItem ?: return
        val workflow = selectedWorkflowMatch()?.workflow ?: return
        if (state.actionsRuns.none { it.runArtifacts.run.id == runId }) return
        state.actionsSelectedRunId = runId
        runStatusDelegate.scheduleSelectedRunWatch()
        runStatusDelegate.loadRunArtifactsIfNeeded(
            item = item,
            workflowId = workflow.id,
            runId = runId,
        )
    }

    fun refreshActionsRunStatus(runId: Long) {
        scope.launch {
            runStatusDelegate.refreshRunStatus(runId = runId, showToast = true)
        }
    }

    fun installActionsArtifact(
        runId: Long,
        artifactId: Long,
    ) {
        artifactActions.downloadActionsArtifact(
            runId = runId,
            artifactId = artifactId,
            forceExternalDownload = false,
        )
    }

    fun downloadActionsArtifact(
        runId: Long,
        artifactId: Long,
    ) {
        artifactActions.downloadActionsArtifact(
            runId = runId,
            artifactId = artifactId,
            forceExternalDownload = true,
        )
    }

    fun shareActionsArtifact(
        runId: Long,
        artifactId: Long,
    ) {
        artifactActions.shareActionsArtifact(runId = runId, artifactId = artifactId)
    }

    fun openSelectedActionsRun() {
        artifactActions.openSelectedActionsRun()
    }

    private fun selectedWorkflowMatch(): GitHubActionsWorkflowMatch? = workflowStateDelegate.selectedWorkflowMatch()
}
