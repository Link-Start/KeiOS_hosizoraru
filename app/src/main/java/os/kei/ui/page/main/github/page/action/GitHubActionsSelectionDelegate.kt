package os.kei.ui.page.main.github.page.action

import android.os.Build
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.domain.GitHubActionsWorkflowSelector
import os.kei.feature.github.model.GitHubActionsArtifactSelectionOptions
import os.kei.feature.github.model.GitHubActionsDownloadRecord
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunSelectionOptions
import os.kei.feature.github.model.GitHubActionsRunTrackingPlan
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactSignal
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubActionsWorkflowSelectionOptions
import os.kei.feature.github.model.GitHubTrackedApp

internal class GitHubActionsSelectionDelegate(
    private val env: GitHubPageActionEnvironment,
) {
    private val state get() = env.state
    private val actionsRepository get() = env.actionsRepository

    suspend fun selectWorkflows(
        workflows: List<GitHubActionsWorkflow>,
        signals: Map<Long, GitHubActionsWorkflowArtifactSignal>,
        history: List<GitHubActionsDownloadRecord>,
    ): List<GitHubActionsWorkflowMatch> =
        actionsRepository.selectGitHubActionsWorkflows(
            workflows = workflows,
            artifactSignals = signals,
            options =
                GitHubActionsWorkflowSelectionOptions(
                    includeDisabled = false,
                    requireArtifacts = false,
                    actionsStrategy = state.lookupConfig.actionsStrategy,
                    downloadHistory = history,
                ),
        )

    fun selectWorkflowSignalCandidates(
        workflows: List<GitHubActionsWorkflow>,
        preliminaryMatches: List<GitHubActionsWorkflowMatch>,
        history: List<GitHubActionsDownloadRecord>,
    ): List<GitHubActionsWorkflow> {
        val historyWorkflowIds =
            history
                .mapNotNull { record -> record.workflowId.takeIf { it > 0L } }
                .toSet()
        val historyWorkflows = workflows.filter { it.id in historyWorkflowIds }
        return (historyWorkflows + preliminaryMatches.map { it.workflow })
            .distinctBy { it.id }
            .take(WORKFLOW_SIGNAL_LIMIT)
    }

    suspend fun selectRunsForSnapshot(
        workflow: GitHubActionsWorkflow,
        snapshot: GitHubActionsWorkflowArtifactsSnapshot,
        history: List<GitHubActionsDownloadRecord>,
    ): List<GitHubActionsRunMatch> {
        val artifactOptions =
            GitHubActionsArtifactSelectionOptions(
                preferredAbis = Build.SUPPORTED_ABIS.toList(),
                aggressiveAbiFiltering = state.lookupConfig.aggressiveApkFiltering,
                fallbackToAllArtifacts = true,
                downloadHistory = history,
            )
        return actionsRepository.selectGitHubActionsRuns(
            runs = snapshot.runs,
            workflow = workflow,
            options =
                GitHubActionsRunSelectionOptions(
                    defaultBranch = state.actionsDefaultBranch,
                    preferredBranches = preferredBranchesForRunSelection(),
                    includePullRequests = true,
                    includeNonDefaultBranches = true,
                    includeUnsuccessful = true,
                    requireArtifacts = false,
                    requireAndroidArtifacts = false,
                    actionsStrategy = state.lookupConfig.actionsStrategy,
                    artifactOptions = artifactOptions,
                    downloadHistory = history,
                ),
        )
    }

    suspend fun buildTrackingPlans(runs: List<GitHubActionsRunMatch>): Map<Long, GitHubActionsRunTrackingPlan> =
        runs.associate { match ->
            val run = match.runArtifacts.run
            run.id to actionsRepository.buildGitHubActionsRunTrackingPlan(run)
        }

    fun recordRecommendedRunSnapshot(
        item: GitHubTrackedApp,
        workflow: GitHubActionsWorkflow,
        runMatches: List<GitHubActionsRunMatch>,
    ) {
        val match = runMatches.firstOrNull() ?: return
        val run = match.runArtifacts.run
        if (run.id <= 0L) return
        val snapshot =
            GitHubActionsRecommendedRunSnapshot(
                trackId = item.id,
                owner = item.owner,
                repo = item.repo,
                appLabel = item.appLabel,
                workflowId = workflow.id,
                workflowName = workflow.displayName,
                workflowPath = workflow.path,
                runId = run.id,
                runNumber = run.runNumber,
                runAttempt = run.runAttempt,
                runDisplayName = run.displayName,
                headBranch = run.headBranch,
                headSha = run.headSha,
                event = run.event,
                status = run.status,
                conclusion = run.conclusion,
                htmlUrl = run.htmlUrl,
                artifactCount = match.runArtifacts.artifacts.count { !it.expired },
                androidArtifactCount = match.artifactMatches.count { it.traits.androidLike },
                createdAtMillis = run.createdAtMillis ?: 0L,
                updatedAtMillis = run.updatedAtMillis ?: 0L,
                checkedAtMillis = System.currentTimeMillis(),
            )
        GitHubActionsRecommendedRunStore.save(snapshot)
        state.actionsRecommendedRunSnapshots[item.id] = snapshot
    }

    fun workflowLookupId(workflow: GitHubActionsWorkflow): String =
        if (state.lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink) {
            workflow.path.ifBlank { workflow.displayName }
        } else {
            workflow.id.toString()
        }

    fun selectedBranchForRequest(): String = state.actionsSelectedBranch.trim().ifBlank { state.actionsDefaultBranch.trim() }

    fun branchForSnapshotRequest(workflow: GitHubActionsWorkflow): String {
        val selectedBranch = selectedBranchForRequest()
        return if (
            state.lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.GitHubApiToken &&
            !state.actionsBranchManuallySelected &&
            state.actionsWorkflowSignals[workflow.id] == null
        ) {
            ""
        } else {
            selectedBranch
        }
    }

    private fun preferredBranchesForRunSelection(): Set<String> {
        val baseBranches =
            if (state.lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink) {
                listOf(selectedBranchForRequest(), state.actionsDefaultBranch, "dev", "develop")
            } else {
                listOf(selectedBranchForRequest(), state.actionsDefaultBranch)
            }
        return baseBranches
            .map { branch -> branch.trim() }
            .filter { branch -> branch.isNotBlank() }
            .toSet()
    }

    private companion object {
        const val WORKFLOW_SIGNAL_LIMIT = 8
    }
}
