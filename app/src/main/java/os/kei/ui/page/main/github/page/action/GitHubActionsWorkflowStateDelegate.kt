package os.kei.ui.page.main.github.page.action

import os.kei.feature.github.domain.GitHubActionsBranchSelector
import os.kei.feature.github.domain.GitHubActionsWorkflowSelector
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflowMatch

internal class GitHubActionsWorkflowStateDelegate(
    private val env: GitHubPageActionEnvironment,
    private val selectionDelegate: GitHubActionsSelectionDelegate,
) {
    private val state get() = env.state

    suspend fun updateWorkflowSignalFromSnapshot(
        workflow: GitHubActionsWorkflow,
        snapshot: GitHubActionsWorkflowArtifactsSnapshot,
    ) {
        if (state.actionsRawWorkflows.none { it.id == workflow.id }) return
        val signal =
            GitHubActionsWorkflowSelector.buildArtifactSignal(
                workflow = workflow,
                runs = snapshot.runs,
                defaultBranch = state.actionsDefaultBranch,
            )
        val mergedSignals = state.actionsWorkflowSignals + (workflow.id to signal)
        state.actionsWorkflowSignals = mergedSignals
        state.actionsWorkflows =
            selectionDelegate.selectWorkflows(
                workflows = state.actionsRawWorkflows,
                signals = mergedSignals,
                history = state.actionsDownloadHistory,
            )
    }

    fun refreshBranchSelection(
        workflow: GitHubActionsWorkflow,
        snapshot: GitHubActionsWorkflowArtifactsSnapshot?,
    ) {
        val signal = state.actionsWorkflowSignals[workflow.id]
        val options =
            GitHubActionsBranchSelector.buildOptions(
                defaultBranch = state.actionsDefaultBranch,
                workflow = workflow,
                signal = signal,
                snapshot = snapshot,
            )
        state.actionsBranchOptions = options
        if (state.actionsBranchManuallySelected && state.actionsSelectedBranch.isNotBlank()) {
            return
        }
        val recommendedBranch =
            GitHubActionsBranchSelector.recommendBranch(
                defaultBranch = state.actionsDefaultBranch,
                workflow = workflow,
                signal = signal,
                snapshot = snapshot,
            )
        state.actionsSelectedBranch = recommendedBranch.ifBlank { state.actionsDefaultBranch }
    }

    fun selectedWorkflowMatch(): GitHubActionsWorkflowMatch? {
        val workflowId = state.actionsSelectedWorkflowId ?: return null
        return state.actionsWorkflows.firstOrNull { it.workflow.id == workflowId }
    }
}
