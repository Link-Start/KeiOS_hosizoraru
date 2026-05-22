package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import os.kei.feature.github.model.GitHubActionsDownloadRecord
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactSignal
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp

internal class GitHubActionsSheetLoader(
    private val env: GitHubPageActionEnvironment,
    private val selectionDelegate: GitHubActionsSelectionDelegate,
    private val runStatusDelegate: GitHubActionsRunStatusDelegate,
    private val workflowStateDelegate: GitHubActionsWorkflowStateDelegate,
) {
    private val scope get() = env.scope
    private val state get() = env.state
    private val actionsRepository get() = env.actionsRepository

    suspend fun loadActionsOverview(
        item: GitHubTrackedApp,
        preferredWorkflowId: Long?,
    ) {
        runStatusDelegate.cancelRunWatch()
        resetOverviewState()
        try {
            val lookupConfig = state.lookupConfig
            val (history, infoTrace, workflowsTrace) =
                coroutineScope {
                    val historyDeferred =
                        async {
                            actionsRepository.loadGitHubActionsDownloadHistory(
                                owner = item.owner,
                                repo = item.repo,
                            )
                        }
                    val infoDeferred =
                        async {
                            actionsRepository.fetchGitHubActionsRepositoryInfo(
                                owner = item.owner,
                                repo = item.repo,
                                lookupConfig = lookupConfig,
                            )
                        }
                    val workflowsDeferred =
                        async {
                            actionsRepository.fetchGitHubActionsWorkflows(
                                owner = item.owner,
                                repo = item.repo,
                                lookupConfig = lookupConfig,
                            )
                        }
                    Triple(
                        historyDeferred.await(),
                        infoDeferred.await(),
                        workflowsDeferred.await(),
                    )
                }
            val info = infoTrace.result.getOrThrow()
            val workflows = workflowsTrace.result.getOrThrow()
            if (!isCurrentTarget(item)) return
            state.actionsDownloadHistory = history
            state.actionsDefaultBranch = info.defaultBranch
            state.actionsSelectedBranch = info.defaultBranch
            state.actionsAuthMode = infoTrace.authMode
            state.actionsAuthMode = workflowsTrace.authMode ?: state.actionsAuthMode

            val preliminaryWorkflows =
                selectionDelegate.selectWorkflows(
                    workflows = workflows,
                    signals = emptyMap(),
                    history = history,
                )
            state.actionsRawWorkflows = workflows
            state.actionsWorkflowSignals = emptyMap()
            state.actionsWorkflows = preliminaryWorkflows
            state.actionsLoading = false
            val signalCandidateWorkflows =
                selectionDelegate.selectWorkflowSignalCandidates(
                    workflows = workflows,
                    preliminaryMatches = preliminaryWorkflows,
                    history = history,
                )

            val selectedWorkflow =
                preferredWorkflowId
                    ?.let { id -> state.actionsWorkflows.firstOrNull { it.workflow.id == id } }
                    ?: state.actionsWorkflows.firstOrNull()
            refreshWorkflowSignalsInBackground(
                item = item,
                workflows = workflows,
                candidateWorkflows = signalCandidateWorkflows,
                history = history,
                lookupConfig = lookupConfig,
                defaultBranch = info.defaultBranch,
            )
            if (selectedWorkflow != null) {
                state.actionsSelectedWorkflowId = selectedWorkflow.workflow.id
                state.actionsWorkflowManuallySelected = false
                state.actionsRunLimit = GitHubActionsSheetLimits.DEFAULT_RUN_LIMIT
                loadWorkflowSnapshot(
                    item = item,
                    workflow = selectedWorkflow.workflow,
                    preferredRunId = null,
                )
            }
        } catch (error: Throwable) {
            if (isCurrentTarget(item)) {
                state.actionsError = error.message ?: error.javaClass.simpleName
            }
        } finally {
            if (isCurrentTarget(item)) {
                state.actionsLoading = false
            }
        }
    }

    suspend fun loadWorkflowSnapshot(
        item: GitHubTrackedApp,
        workflow: GitHubActionsWorkflow,
        preferredRunId: Long?,
        keepCurrentRunsWhileLoading: Boolean = false,
    ) {
        runStatusDelegate.cancelRunWatch()
        state.actionsRunsLoading = true
        state.actionsError = null
        if (!keepCurrentRunsWhileLoading) {
            state.actionsSnapshot = null
            state.actionsRuns = emptyList()
            state.actionsSelectedRunId = null
            state.actionsRunTrackingPlans = emptyMap()
            state.actionsStatusRefreshingRunIds.clear()
        }
        workflowStateDelegate.refreshBranchSelection(
            workflow = workflow,
            snapshot = if (keepCurrentRunsWhileLoading) state.actionsSnapshot else null,
        )
        val branch = selectionDelegate.branchForSnapshotRequest(workflow)
        var requestHandled = false
        try {
            val snapshotTrace =
                actionsRepository.fetchGitHubActionsWorkflowArtifactSnapshot(
                    owner = item.owner,
                    repo = item.repo,
                    workflowId = selectionDelegate.workflowLookupId(workflow),
                    lookupConfig = state.lookupConfig,
                    runLimit =
                        state.actionsRunLimit.coerceIn(
                            GitHubActionsSheetLimits.DEFAULT_RUN_LIMIT,
                            GitHubActionsSheetLimits.MAX_RUN_LIMIT,
                        ),
                    artifactsPerRun = GitHubActionsSheetLimits.ARTIFACTS_PER_RUN,
                    artifactRunLimit = GitHubActionsSheetLimits.INITIAL_ARTIFACT_RUN_LIMIT,
                    branch = branch,
                )
            val snapshot = snapshotTrace.result.getOrThrow()
            if (!isCurrentSnapshotRequest(item, workflow, branch)) return
            requestHandled = true
            val runMatches =
                selectionDelegate.selectRunsForSnapshot(
                    workflow = workflow,
                    snapshot = snapshot,
                    history = state.actionsDownloadHistory,
                )
            selectionDelegate.recordRecommendedRunSnapshot(
                item = item,
                workflow = workflow,
                runMatches = runMatches,
            )
            state.actionsAuthMode = snapshotTrace.authMode ?: state.actionsAuthMode
            state.actionsSnapshot = snapshot
            state.actionsRuns = runMatches
            state.actionsRunTrackingPlans = selectionDelegate.buildTrackingPlans(runMatches)
            workflowStateDelegate.updateWorkflowSignalFromSnapshot(
                workflow = workflow,
                snapshot = snapshot,
            )
            workflowStateDelegate.refreshBranchSelection(
                workflow = workflow,
                snapshot = snapshot,
            )
            state.actionsSelectedRunId = preferredRunId
                ?.takeIf { runId -> runMatches.any { it.runArtifacts.run.id == runId } }
                ?: runMatches
                    .firstOrNull()
                    ?.runArtifacts
                    ?.run
                    ?.id
            runStatusDelegate.scheduleSelectedRunWatch()
            state.actionsSelectedRunId?.let { runId ->
                runStatusDelegate.loadRunArtifactsIfNeeded(
                    item = item,
                    workflowId = workflow.id,
                    runId = runId,
                )
            }
        } catch (error: Throwable) {
            if (isCurrentSnapshotRequest(item, workflow, branch)) {
                requestHandled = true
                state.actionsError = error.message ?: error.javaClass.simpleName
                if (keepCurrentRunsWhileLoading) {
                    runStatusDelegate.scheduleSelectedRunWatch()
                }
            }
        } finally {
            if (requestHandled || isCurrentSnapshotRequest(item, workflow, branch)) {
                state.actionsRunsLoading = false
            }
        }
    }

    suspend fun reselectActionsMatchesAfterHistoryChange() {
        val selectedWorkflowId = state.actionsSelectedWorkflowId
        val selectedRunId = state.actionsSelectedRunId
        state.actionsWorkflows =
            selectionDelegate.selectWorkflows(
                workflows = state.actionsRawWorkflows,
                signals = state.actionsWorkflowSignals,
                history = state.actionsDownloadHistory,
            )
        val workflow =
            state.actionsWorkflows
                .firstOrNull { it.workflow.id == selectedWorkflowId }
                ?.workflow
                ?: return
        val snapshot = state.actionsSnapshot ?: return
        val runs =
            selectionDelegate.selectRunsForSnapshot(
                workflow = workflow,
                snapshot = snapshot,
                history = state.actionsDownloadHistory,
            )
        state.actionsRuns = runs
        state.actionsRunTrackingPlans = selectionDelegate.buildTrackingPlans(runs)
        state.actionsSelectedRunId = selectedRunId
            ?.takeIf { runId -> runs.any { it.runArtifacts.run.id == runId } }
            ?: runs
                .firstOrNull()
                ?.runArtifacts
                ?.run
                ?.id
    }

    private fun resetOverviewState() {
        state.actionsLoading = true
        state.actionsRunsLoading = false
        state.actionsError = null
        state.actionsDefaultBranch = ""
        state.actionsSelectedBranch = ""
        state.actionsBranchManuallySelected = false
        state.actionsBranchOptions = emptyList()
        state.actionsRawWorkflows = emptyList()
        state.actionsWorkflowSignals = emptyMap()
        state.actionsWorkflows = emptyList()
        state.actionsSelectedWorkflowId = null
        state.actionsWorkflowManuallySelected = false
        state.actionsSnapshot = null
        state.actionsRuns = emptyList()
        state.actionsSelectedRunId = null
        state.actionsRunTrackingPlans = emptyMap()
        state.actionsStatusRefreshingRunIds.clear()
    }

    private fun refreshWorkflowSignalsInBackground(
        item: GitHubTrackedApp,
        workflows: List<GitHubActionsWorkflow>,
        candidateWorkflows: List<GitHubActionsWorkflow>,
        history: List<GitHubActionsDownloadRecord>,
        lookupConfig: GitHubLookupConfig,
        defaultBranch: String,
    ) {
        val cachedSignals = state.actionsWorkflowSignals
        val missingCandidateWorkflows =
            candidateWorkflows.filter { workflow ->
                val signal = cachedSignals[workflow.id]
                signal == null || signal.nonExpiredArtifactCount == 0
            }
        if (missingCandidateWorkflows.isEmpty()) return
        val expectedWorkflowIds = workflows.map { it.id }
        scope.launch {
            val signalsTrace =
                actionsRepository.fetchGitHubActionsWorkflowArtifactSignals(
                    owner = item.owner,
                    repo = item.repo,
                    workflows = missingCandidateWorkflows,
                    lookupConfig = lookupConfig,
                    runLimit = GitHubActionsSheetLimits.SIGNAL_RUN_LIMIT,
                    artifactsPerRun = GitHubActionsSheetLimits.SIGNAL_ARTIFACT_LIMIT,
                    defaultBranch = defaultBranch,
                )
            val signals = signalsTrace.result.getOrElse { return@launch }
            if (!isCurrentTarget(item)) return@launch
            if (state.actionsRawWorkflows.map { it.id } != expectedWorkflowIds) return@launch
            val mergedSignals = state.actionsWorkflowSignals + signals
            val updatedWorkflows =
                selectionDelegate.selectWorkflows(
                    workflows = state.actionsRawWorkflows,
                    signals = mergedSignals,
                    history = history,
                )
            if (!isCurrentTarget(item)) return@launch
            if (state.actionsRawWorkflows.map { it.id } != expectedWorkflowIds) return@launch
            state.actionsAuthMode = signalsTrace.authMode ?: state.actionsAuthMode
            state.actionsWorkflowSignals = mergedSignals
            state.actionsWorkflows = updatedWorkflows
            val autoSwitchedWorkflow =
                autoSwitchWorkflowFromSignals(
                    item = item,
                    updatedWorkflows = updatedWorkflows,
                    mergedSignals = mergedSignals,
                )
            if (autoSwitchedWorkflow) return@launch
            refreshSelectedWorkflowBranchIfNeeded(
                item = item,
                updatedWorkflows = updatedWorkflows,
            )
        }
    }

    private suspend fun autoSwitchWorkflowFromSignals(
        item: GitHubTrackedApp,
        updatedWorkflows: List<GitHubActionsWorkflowMatch>,
        mergedSignals: Map<Long, GitHubActionsWorkflowArtifactSignal>,
    ): Boolean {
        if (state.actionsWorkflowManuallySelected) return false
        val currentWorkflowId = state.actionsSelectedWorkflowId ?: return false
        val recommended = updatedWorkflows.firstOrNull() ?: return false
        if (recommended.workflow.id == currentWorkflowId) return false
        val currentSignal = mergedSignals[currentWorkflowId]
        val recommendedSignal = mergedSignals[recommended.workflow.id]
        val currentHasArtifacts =
            (currentSignal?.nonExpiredArtifactCount ?: 0) > 0 ||
                state.actionsSnapshot
                    ?.artifacts
                    .orEmpty()
                    .any { artifact -> !artifact.expired }
        val recommendedHasArtifacts = (recommendedSignal?.nonExpiredArtifactCount ?: 0) > 0
        if (currentHasArtifacts || !recommendedHasArtifacts) return false
        state.actionsSelectedWorkflowId = recommended.workflow.id
        state.actionsSelectedBranch = ""
        state.actionsBranchManuallySelected = false
        state.actionsBranchOptions = emptyList()
        state.actionsRunLimit = GitHubActionsSheetLimits.DEFAULT_RUN_LIMIT
        loadWorkflowSnapshot(
            item = item,
            workflow = recommended.workflow,
            preferredRunId = null,
        )
        return true
    }

    private suspend fun refreshSelectedWorkflowBranchIfNeeded(
        item: GitHubTrackedApp,
        updatedWorkflows: List<GitHubActionsWorkflowMatch>,
    ) {
        val selectedWorkflow =
            state.actionsSelectedWorkflowId
                ?.let { id -> updatedWorkflows.firstOrNull { it.workflow.id == id } }
                ?.workflow
        if (selectedWorkflow != null && !state.actionsBranchManuallySelected) {
            val previousBranch = selectionDelegate.selectedBranchForRequest()
            workflowStateDelegate.refreshBranchSelection(
                workflow = selectedWorkflow,
                snapshot = state.actionsSnapshot,
            )
            val nextBranch = selectionDelegate.selectedBranchForRequest()
            if (!previousBranch.equals(nextBranch, ignoreCase = true)) {
                state.actionsRunLimit = GitHubActionsSheetLimits.DEFAULT_RUN_LIMIT
                loadWorkflowSnapshot(
                    item = item,
                    workflow = selectedWorkflow,
                    preferredRunId = null,
                )
            }
        }
    }

    private fun isCurrentSnapshotRequest(
        item: GitHubTrackedApp,
        workflow: GitHubActionsWorkflow,
        requestedBranch: String,
    ): Boolean {
        if (!isCurrentTarget(item) || state.actionsSelectedWorkflowId != workflow.id) return false
        val currentBranch = selectionDelegate.selectedBranchForRequest()
        return if (requestedBranch.isBlank()) {
            !state.actionsBranchManuallySelected
        } else {
            currentBranch.equals(requestedBranch, ignoreCase = true)
        }
    }

    private fun isCurrentTarget(item: GitHubTrackedApp): Boolean = state.showActionsSheet && state.actionsTargetItem?.id == item.id
}
