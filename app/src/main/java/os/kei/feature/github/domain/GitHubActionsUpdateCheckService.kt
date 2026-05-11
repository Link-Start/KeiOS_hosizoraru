package os.kei.feature.github.domain

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.local.GitHubActionsDownloadHistoryStore
import os.kei.feature.github.data.remote.GitHubActionsRepository
import os.kei.feature.github.model.GitHubActionsArtifactSelectionOptions
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunSelectionOptions
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowSelectionOptions
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem

class GitHubActionsUpdateCheckService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun fetchRecommendedRunSnapshot(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        previousWorkflowId: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Result<GitHubActionsRecommendedRunSnapshot> = withContext(ioDispatcher) {
        runCatching {
            val itemLookupConfig = lookupConfig.forTrackedItem(item)
            val repository = GitHubActionsRepository.fromLookupConfig(itemLookupConfig)
            val history =
                GitHubActionsDownloadHistoryStore.load(owner = item.owner, repo = item.repo)
            val info = repository.fetchRepositoryInfo(
                owner = item.owner,
                repo = item.repo
            ).result.getOrThrow()
            val workflows = repository.fetchWorkflows(
                owner = item.owner,
                repo = item.repo
            ).result.getOrThrow()
            val workflow = selectWorkflow(
                workflows = workflows,
                history = history,
                lookupConfig = itemLookupConfig,
                previousWorkflowId = previousWorkflowId
            ) ?: throw IllegalStateException("No GitHub Actions workflow matched")
            val workflowId = workflowLookupId(workflow, itemLookupConfig)
            val preferredBranches = preferredBranches(
                defaultBranch = info.defaultBranch,
                lookupConfig = itemLookupConfig
            )
            val branch =
                if (itemLookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink) {
                    preferredBranches.firstOrNull().orEmpty()
                } else {
                    ""
                }
            val snapshot = repository.fetchWorkflowArtifactSnapshot(
                owner = item.owner,
                repo = item.repo,
                workflowId = workflowId,
                runLimit = BACKGROUND_RUN_LIMIT,
                artifactsPerRun = BACKGROUND_ARTIFACT_LIMIT,
                artifactRunLimit = BACKGROUND_ARTIFACT_RUN_LIMIT,
                branch = branch
            ).result.getOrThrow()
            val run = selectRun(
                workflow = workflow,
                snapshotRuns = snapshot.runs,
                history = history,
                lookupConfig = itemLookupConfig,
                defaultBranch = info.defaultBranch,
                preferredBranches = preferredBranches
            ) ?: throw IllegalStateException("No GitHub Actions run matched")
            run.toSnapshot(
                item = item,
                workflow = workflow,
                checkedAtMs = nowMs
            )
        }
    }

    private suspend fun selectWorkflow(
        workflows: List<GitHubActionsWorkflow>,
        history: List<os.kei.feature.github.model.GitHubActionsDownloadRecord>,
        lookupConfig: GitHubLookupConfig,
        previousWorkflowId: Long?
    ): GitHubActionsWorkflow? = withContext(defaultDispatcher) {
        previousWorkflowId
            ?.takeIf { it > 0L }
            ?.let { id -> workflows.firstOrNull { it.id == id } }
            ?: GitHubActionsWorkflowSelector.selectWorkflows(
                workflows = workflows,
                artifactSignals = emptyMap(),
                options = GitHubActionsWorkflowSelectionOptions(
                    actionsStrategy = lookupConfig.actionsStrategy,
                    includeDisabled = false,
                    requireArtifacts = false,
                    downloadHistory = history
                )
            ).firstOrNull()?.workflow
    }

    private suspend fun selectRun(
        workflow: GitHubActionsWorkflow,
        snapshotRuns: List<os.kei.feature.github.model.GitHubActionsRunArtifacts>,
        history: List<os.kei.feature.github.model.GitHubActionsDownloadRecord>,
        lookupConfig: GitHubLookupConfig,
        defaultBranch: String,
        preferredBranches: Set<String>
    ): GitHubActionsRunMatch? = withContext(defaultDispatcher) {
        val artifactOptions = GitHubActionsArtifactSelectionOptions(
            preferredAbis = Build.SUPPORTED_ABIS.toList(),
            aggressiveAbiFiltering = lookupConfig.aggressiveApkFiltering,
            fallbackToAllArtifacts = true,
            downloadHistory = history
        )
        GitHubActionsRunSelector.selectRuns(
            runs = snapshotRuns,
            workflowTraits = GitHubActionsWorkflowSelector.inspectWorkflow(workflow),
            options = GitHubActionsRunSelectionOptions(
                defaultBranch = defaultBranch,
                preferredBranches = preferredBranches,
                includePullRequests = true,
                includeNonDefaultBranches = true,
                includeUnsuccessful = true,
                requireArtifacts = false,
                requireAndroidArtifacts = false,
                actionsStrategy = lookupConfig.actionsStrategy,
                artifactOptions = artifactOptions,
                downloadHistory = history
            )
        ).firstOrNull()
    }

    private fun workflowLookupId(
        workflow: GitHubActionsWorkflow,
        lookupConfig: GitHubLookupConfig
    ): String {
        return if (lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink) {
            workflow.path.ifBlank { workflow.displayName }
        } else {
            workflow.id.toString()
        }
    }

    private fun preferredBranches(
        defaultBranch: String,
        lookupConfig: GitHubLookupConfig
    ): Set<String> {
        val base =
            if (lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink) {
                listOf(defaultBranch, "dev", "develop")
            } else {
                listOf(defaultBranch)
            }
        return base.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun GitHubActionsRunMatch.toSnapshot(
        item: GitHubTrackedApp,
        workflow: GitHubActionsWorkflow,
        checkedAtMs: Long
    ): GitHubActionsRecommendedRunSnapshot {
        val run = runArtifacts.run
        val androidArtifactCount = artifactMatches.count { it.traits.androidLike }
        return GitHubActionsRecommendedRunSnapshot(
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
            artifactCount = runArtifacts.artifacts.count { !it.expired },
            androidArtifactCount = androidArtifactCount,
            createdAtMillis = run.createdAtMillis ?: 0L,
            updatedAtMillis = run.updatedAtMillis ?: 0L,
            checkedAtMillis = checkedAtMs
        )
    }

    private companion object {
        const val BACKGROUND_RUN_LIMIT = 8
        const val BACKGROUND_ARTIFACT_LIMIT = 80
        const val BACKGROUND_ARTIFACT_RUN_LIMIT = 4
    }
}
