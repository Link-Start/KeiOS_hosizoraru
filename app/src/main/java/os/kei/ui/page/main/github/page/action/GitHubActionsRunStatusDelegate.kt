package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.model.GitHubActionsRunArtifacts
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.localizedGitHubActionsErrorMessage
import kotlin.time.Duration.Companion.milliseconds

internal class GitHubActionsRunStatusDelegate(
    private val env: GitHubPageActionEnvironment,
    private val selectionDelegate: GitHubActionsSelectionDelegate,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val actionsRepository get() = env.actionsRepository

    fun cancelRunWatch() {
        state.actionsRunWatchJob?.cancel()
    }

    fun loadRunArtifactsIfNeeded(
        item: GitHubTrackedApp,
        workflowId: Long,
        runId: Long,
    ) {
        if (!isCurrentTarget(item) || state.actionsSelectedWorkflowId != workflowId) return
        if (state.actionsStatusRefreshingRunIds[runId] == true) return
        val runMatch = state.actionsRuns.firstOrNull { it.runArtifacts.run.id == runId } ?: return
        if (!runMatch.traits.completed) return
        if (runMatch.runArtifacts.artifacts.isNotEmpty()) return
        scope.launch {
            if (!isCurrentTarget(item) || state.actionsSelectedWorkflowId != workflowId) return@launch
            refreshRunStatus(runId = runId, showToast = false)
        }
    }

    suspend fun refreshRunStatus(
        runId: Long,
        showToast: Boolean,
    ) {
        val item = state.actionsTargetItem ?: return
        val workflow = selectedWorkflowMatch()?.workflow ?: return
        val currentSnapshot = state.actionsSnapshot ?: return
        if (state.actionsStatusRefreshingRunIds[runId] == true) return
        state.actionsStatusRefreshingRunIds[runId] = true
        try {
            val statusTrace =
                actionsRepository.fetchGitHubActionsRunStatusSnapshot(
                    owner = item.owner,
                    repo = item.repo,
                    runId = runId,
                    lookupConfig = state.lookupConfig,
                    artifactsLimit = 100,
                    includeArtifactsWhenCompleted = true,
                )
            val statusSnapshot = statusTrace.result.getOrThrow()
            if (!isCurrentTarget(item) || state.actionsSelectedWorkflowId != workflow.id) return
            state.actionsAuthMode = statusTrace.authMode ?: state.actionsAuthMode
            val updatedRunArtifacts =
                GitHubActionsRunArtifacts(
                    run = statusSnapshot.run,
                    artifacts = statusSnapshot.artifacts,
                )
            val replaced = currentSnapshot.runs.any { it.run.id == runId }
            val updatedRuns =
                if (replaced) {
                    currentSnapshot.runs.map { runArtifacts ->
                        if (runArtifacts.run.id == runId) updatedRunArtifacts else runArtifacts
                    }
                } else {
                    listOf(updatedRunArtifacts) + currentSnapshot.runs
                }
            val updatedSnapshot = currentSnapshot.copy(runs = updatedRuns)
            val runMatches =
                selectionDelegate.selectRunsForSnapshot(
                    workflow = workflow,
                    snapshot = updatedSnapshot,
                    history = state.actionsDownloadHistory,
                )
            state.actionsSnapshot = updatedSnapshot
            state.actionsRuns = runMatches
            state.actionsRunTrackingPlans = selectionDelegate.buildTrackingPlans(runMatches)
            state.actionsSelectedRunId = runId
                .takeIf { id -> runMatches.any { it.runArtifacts.run.id == id } }
                ?: runMatches
                    .firstOrNull()
                    ?.runArtifacts
                    ?.run
                    ?.id
            if (showToast) {
                env.toast(R.string.common_refreshed)
            }
        } catch (error: Throwable) {
            if (showToast) {
                env.toast(
                    context.getString(
                        R.string.github_actions_toast_refresh_run_failed,
                        localizedGitHubActionsErrorMessage(
                            context = context,
                            rawMessage = error.message ?: error.javaClass.simpleName,
                        ),
                    ),
                )
            }
        } finally {
            state.actionsStatusRefreshingRunIds.remove(runId)
            scheduleSelectedRunWatch()
        }
    }

    fun scheduleSelectedRunWatch() {
        state.actionsRunWatchJob?.cancel()
        val runId = state.actionsSelectedRunId ?: return
        val plan = state.actionsRunTrackingPlans[runId] ?: return
        if (!state.showActionsSheet || !plan.pollable) return
        val delayMillis = plan.nextPollDelayMillis.coerceAtLeast(5_000L)
        state.actionsRunWatchJob =
            scope.launch {
                delay(delayMillis.milliseconds)
                if (!state.showActionsSheet || state.actionsSelectedRunId != runId) return@launch
                refreshRunStatus(runId = runId, showToast = false)
            }
    }

    private fun selectedWorkflowMatch() =
        state.actionsSelectedWorkflowId
            ?.let { workflowId -> state.actionsWorkflows.firstOrNull { it.workflow.id == workflowId } }

    private fun isCurrentTarget(item: GitHubTrackedApp): Boolean = state.showActionsSheet && state.actionsTargetItem?.id == item.id
}
