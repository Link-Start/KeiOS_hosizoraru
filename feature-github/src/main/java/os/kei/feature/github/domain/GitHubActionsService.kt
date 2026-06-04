package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import os.kei.core.concurrency.AppDispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.local.GitHubActionsDownloadHistoryStore
import os.kei.feature.github.data.local.GitHubActionsNotificationHistoryStore
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.data.remote.GitHubActionsArtifactManifestProbe
import os.kei.feature.github.data.remote.GitHubActionsRepository
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsArtifactDownloadResolution
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsArtifactSelectionOptions
import os.kei.feature.github.model.GitHubActionsDownloadRecord
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubActionsRepositoryInfo
import os.kei.feature.github.model.GitHubActionsRunArtifacts
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunSelectionOptions
import os.kei.feature.github.model.GitHubActionsRunStatusSnapshot
import os.kei.feature.github.model.GitHubActionsRunTrackingPlan
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactSignal
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import os.kei.feature.github.model.GitHubActionsWorkflowSelectionOptions
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedApp

private const val nightlyLinkSignalWorkflowBatchSize = 3
private const val tokenApiSignalWorkflowBatchSize = 3
private const val tokenApiSignalBranchProbeLimit = 2

class GitHubActionsService(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
) : GitHubActionsRecommendedRunRefreshSource {
    private val artifactManifestProbe = GitHubActionsArtifactManifestProbe()

    override fun loadRecommendedRunSnapshot(trackId: String): GitHubActionsRecommendedRunSnapshot? =
        GitHubActionsRecommendedRunStore.load(trackId)

    override fun loadRecommendedRunSnapshots(): Map<String, GitHubActionsRecommendedRunSnapshot> =
        GitHubActionsRecommendedRunStore.loadAll()

    override fun saveRecommendedRunSnapshot(snapshot: GitHubActionsRecommendedRunSnapshot) {
        GitHubActionsRecommendedRunStore.save(snapshot)
    }

    override fun removeRecommendedRunSnapshot(trackId: String) {
        GitHubActionsRecommendedRunStore.remove(trackId)
    }

    override fun retainRecommendedRunSnapshots(trackIds: Set<String>) {
        GitHubActionsRecommendedRunStore.retain(trackIds)
    }

    override suspend fun fetchRecommendedRunSnapshot(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        previousWorkflowId: Long?,
        nowMs: Long,
    ): Result<GitHubActionsRecommendedRunSnapshot> =
        withContext(ioDispatcher) {
            GitHubActionsUpdateCheckService()
                .fetchRecommendedRunSnapshot(
                    item = item,
                    lookupConfig = lookupConfig,
                    previousWorkflowId = previousWorkflowId,
                    nowMs = nowMs,
                )
        }

    suspend fun fetchGitHubActionsWorkflows(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): GitHubStrategyLoadTrace<List<GitHubActionsWorkflow>> {
        return withContext(ioDispatcher) {
            GitHubActionsRepository.fromLookupConfig(lookupConfig)
                .fetchWorkflows(owner = owner, repo = repo)
        }
    }

    suspend fun fetchGitHubActionsRepositoryInfo(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): GitHubStrategyLoadTrace<GitHubActionsRepositoryInfo> {
        return withContext(ioDispatcher) {
            GitHubActionsRepository.fromLookupConfig(lookupConfig)
                .fetchRepositoryInfo(owner = owner, repo = repo)
        }
    }

    suspend fun fetchGitHubActionsRepositoryDefaultBranch(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): GitHubStrategyLoadTrace<String> {
        return withContext(ioDispatcher) {
            GitHubActionsRepository.fromLookupConfig(lookupConfig)
                .fetchRepositoryDefaultBranch(owner = owner, repo = repo)
        }
    }

    suspend fun fetchGitHubActionsWorkflowArtifactSnapshot(
        owner: String,
        repo: String,
        workflowId: String,
        lookupConfig: GitHubLookupConfig,
        runLimit: Int = 20,
        artifactsPerRun: Int = 100,
        artifactRunLimit: Int = Int.MAX_VALUE,
        branch: String = "",
        event: String = "",
        status: String = "",
        actor: String = "",
        created: String = "",
        headSha: String = "",
        excludePullRequests: Boolean = false,
        resolveNightlyRunDetail: Boolean = true
    ): GitHubStrategyLoadTrace<GitHubActionsWorkflowArtifactsSnapshot> {
        return GitHubActionsRepository.fromLookupConfig(lookupConfig)
            .fetchWorkflowArtifactSnapshot(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                runLimit = runLimit,
                artifactsPerRun = artifactsPerRun,
                artifactRunLimit = artifactRunLimit,
                branch = branch,
                event = event,
                status = status,
                actor = actor,
                created = created,
                headSha = headSha,
                excludePullRequests = excludePullRequests,
                resolveNightlyRunDetail = resolveNightlyRunDetail
            )
    }

    suspend fun fetchGitHubActionsWorkflowRun(
        owner: String,
        repo: String,
        runId: Long,
        lookupConfig: GitHubLookupConfig
    ): GitHubStrategyLoadTrace<GitHubActionsWorkflowRun> {
        return withContext(ioDispatcher) {
            GitHubActionsRepository.fromLookupConfig(lookupConfig)
                .fetchWorkflowRun(owner = owner, repo = repo, runId = runId)
        }
    }

    suspend fun fetchGitHubActionsRunStatusSnapshot(
        owner: String,
        repo: String,
        runId: Long,
        lookupConfig: GitHubLookupConfig,
        artifactsLimit: Int = 100,
        includeArtifactsWhenCompleted: Boolean = true
    ): GitHubStrategyLoadTrace<GitHubActionsRunStatusSnapshot> {
        return GitHubActionsRepository.fromLookupConfig(lookupConfig)
            .fetchRunStatusSnapshot(
                owner = owner,
                repo = repo,
                runId = runId,
                artifactsLimit = artifactsLimit,
                includeArtifactsWhenCompleted = includeArtifactsWhenCompleted
            )
    }

    suspend fun buildGitHubActionsRunTrackingPlan(
        run: GitHubActionsWorkflowRun
    ): GitHubActionsRunTrackingPlan {
        return withContext(defaultDispatcher) {
            GitHubActionsRunTracker.buildTrackingPlan(run)
        }
    }

    suspend fun fetchGitHubActionsWorkflowArtifactSignals(
        owner: String,
        repo: String,
        workflows: List<GitHubActionsWorkflow>,
        lookupConfig: GitHubLookupConfig,
        runLimit: Int = 3,
        artifactsPerRun: Int = 100,
        defaultBranch: String = ""
    ): GitHubStrategyLoadTrace<Map<Long, GitHubActionsWorkflowArtifactSignal>> {
        return coroutineScope {
            val startedAt = System.currentTimeMillis()
            val actionsRepository = GitHubActionsRepository.fromLookupConfig(lookupConfig)
            val signals = mutableMapOf<Long, GitHubActionsWorkflowArtifactSignal>()
            val useNightlyLink = lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink
            val batchSize = if (useNightlyLink) {
                nightlyLinkSignalWorkflowBatchSize
            } else {
                tokenApiSignalWorkflowBatchSize
            }
            workflows.chunked(batchSize).forEach { batch ->
                batch.map { workflow ->
                    async(ioDispatcher) {
                        val workflowId = workflowLookupId(workflow, lookupConfig)
                        val primaryBranch = if (useNightlyLink) defaultBranch else ""
                        val recentSnapshot = actionsRepository.fetchWorkflowArtifactSnapshot(
                            owner = owner,
                            repo = repo,
                            workflowId = workflowId,
                            runLimit = runLimit,
                            artifactsPerRun = artifactsPerRun,
                            branch = primaryBranch,
                            status = if (useNightlyLink) "completed" else "",
                            excludePullRequests = useNightlyLink,
                            resolveNightlyRunDetail = false
                        ).result.getOrElse {
                            return@async null
                        }
                        val recentRuns = recentSnapshot.runs
                        val branchProbeRuns = if (useNightlyLink) {
                            emptyList()
                        } else {
                            fetchTokenApiBranchProbeRuns(
                                actionsRepository = actionsRepository,
                                owner = owner,
                                repo = repo,
                                workflow = workflow,
                                workflowId = workflowId,
                                defaultBranch = defaultBranch,
                                recentRuns = recentRuns,
                                artifactsPerRun = artifactsPerRun
                            )
                        }
                        val mergedRuns = (recentRuns + branchProbeRuns)
                            .distinctBy { it.run.id }
                        workflow.id to GitHubActionsWorkflowSelector.buildArtifactSignal(
                            workflow = workflow,
                            runs = mergedRuns,
                            defaultBranch = defaultBranch,
                            actionsStrategy = lookupConfig.actionsStrategy
                        )
                    }
                }.awaitAll().filterNotNull().forEach { (workflowId, signal) ->
                    signals[workflowId] = signal
                }
            }
            GitHubStrategyLoadTrace(
                result = Result.success(signals),
                fromCache = false,
                elapsedMs = System.currentTimeMillis() - startedAt,
                authMode = actionsRepository.authMode
            )
        }
    }

    private suspend fun fetchTokenApiBranchProbeRuns(
        actionsRepository: GitHubActionsRepository,
        owner: String,
        repo: String,
        workflow: GitHubActionsWorkflow,
        workflowId: String,
        defaultBranch: String,
        recentRuns: List<GitHubActionsRunArtifacts>,
        artifactsPerRun: Int
    ): List<GitHubActionsRunArtifacts> {
        val branches = tokenApiSignalProbeBranches(
            workflow = workflow,
            defaultBranch = defaultBranch,
            recentRuns = recentRuns
        )
        if (branches.isEmpty()) return emptyList()
        return coroutineScope {
            branches.map { branch ->
                async(ioDispatcher) {
                    actionsRepository.fetchWorkflowArtifactSnapshot(
                        owner = owner,
                        repo = repo,
                        workflowId = workflowId,
                        runLimit = 1,
                        artifactsPerRun = artifactsPerRun,
                        artifactRunLimit = 1,
                        branch = branch,
                        status = "completed",
                        excludePullRequests = true,
                        resolveNightlyRunDetail = false
                    ).result.getOrNull()?.runs.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private fun tokenApiSignalProbeBranches(
        workflow: GitHubActionsWorkflow,
        defaultBranch: String,
        recentRuns: List<GitHubActionsRunArtifacts>
    ): List<String> {
        val traits = GitHubActionsWorkflowSelector.inspectWorkflow(workflow)
        val candidates = buildList {
            add(defaultBranch)
            if (traits.nightlyLike) {
                add("dev")
                add("develop")
            }
        }
        return candidates
            .map { branch -> branch.trim() }
            .filter { branch -> branch.isNotBlank() }
            .distinctBy { branch -> branch.lowercase() }
            .filter { branch -> !recentRuns.hasCompletedArtifactRun(branch) }
            .take(tokenApiSignalBranchProbeLimit)
    }

    private fun List<GitHubActionsRunArtifacts>.hasCompletedArtifactRun(branch: String): Boolean {
        return any { runArtifacts ->
            val run = runArtifacts.run
            run.headBranch.equals(branch, ignoreCase = true) &&
                run.status.equals("completed", ignoreCase = true) &&
                run.conclusion.equals("success", ignoreCase = true) &&
                runArtifacts.artifacts.any { artifact -> !artifact.expired }
        }
    }

    suspend fun loadGitHubActionsDownloadHistory(
        owner: String = "",
        repo: String = ""
    ): List<GitHubActionsDownloadRecord> {
        return withContext(ioDispatcher) {
            GitHubActionsDownloadHistoryStore.load(owner = owner, repo = repo)
        }
    }

    suspend fun loadGitHubActionsNotificationHistory(
        owner: String = "",
        repo: String = ""
    ): List<GitHubActionsNotificationHistoryRecord> {
        return withContext(ioDispatcher) {
            GitHubActionsNotificationHistoryStore.load(owner = owner, repo = repo)
        }
    }

    fun recordGitHubActionsUpdateNotification(
        snapshot: GitHubActionsRecommendedRunSnapshot,
        notificationTitle: String = "",
        notificationContent: String = "",
        notifiedAtMillis: Long = System.currentTimeMillis()
    ) {
        GitHubActionsNotificationHistoryStore.recordNotification(
            buildGitHubActionsNotificationHistoryRecord(
                snapshot = snapshot,
                notificationTitle = notificationTitle,
                notificationContent = notificationContent,
                notifiedAtMillis = notifiedAtMillis,
            )
        )
    }

    suspend fun recordGitHubActionsArtifactDownload(
        record: GitHubActionsDownloadRecord
    ) {
        withContext(ioDispatcher) {
            GitHubActionsDownloadHistoryStore.recordDownload(record)
        }
    }

    suspend fun probeGitHubActionsArtifactPackageName(
        artifact: GitHubActionsArtifact,
        resolvedDownloadUrl: String,
        lookupConfig: GitHubLookupConfig
    ): String {
        return withContext(ioDispatcher) {
            artifactManifestProbe.readPackageName(
                artifact = artifact,
                resolvedDownloadUrl = resolvedDownloadUrl,
                lookupConfig = lookupConfig
            ).getOrDefault("").trim()
        }
    }

    fun buildGitHubActionsDownloadRecord(
        owner: String,
        repo: String,
        workflow: GitHubActionsWorkflow,
        run: GitHubActionsWorkflowRun,
        artifact: GitHubActionsArtifact,
        sourceTrackId: String = "",
        packageName: String = "",
        artifactPackageName: String = "",
        downloadedAtMillis: Long = System.currentTimeMillis()
    ): GitHubActionsDownloadRecord {
        return GitHubActionsDownloadRecord(
            owner = owner,
            repo = repo,
            workflowId = workflow.id,
            workflowName = workflow.name,
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
            artifactId = artifact.id,
            artifactName = artifact.name,
            artifactDigest = artifact.digest,
            artifactSizeBytes = artifact.sizeBytes,
            sourceTrackId = sourceTrackId,
            packageName = packageName,
            artifactPackageName = artifactPackageName,
            downloadedAtMillis = downloadedAtMillis
        )
    }

    fun buildGitHubActionsNotificationHistoryRecord(
        snapshot: GitHubActionsRecommendedRunSnapshot,
        notificationTitle: String = "",
        notificationContent: String = "",
        notifiedAtMillis: Long = System.currentTimeMillis()
    ): GitHubActionsNotificationHistoryRecord {
        return GitHubActionsNotificationHistoryRecord(
            trackId = snapshot.trackId,
            owner = snapshot.owner,
            repo = snapshot.repo,
            appLabel = snapshot.appLabel,
            workflowId = snapshot.workflowId,
            workflowName = snapshot.workflowName,
            workflowPath = snapshot.workflowPath,
            runId = snapshot.runId,
            runNumber = snapshot.runNumber,
            runAttempt = snapshot.runAttempt,
            runDisplayName = snapshot.runDisplayName,
            headBranch = snapshot.headBranch,
            headSha = snapshot.headSha,
            event = snapshot.event,
            status = snapshot.status,
            conclusion = snapshot.conclusion,
            htmlUrl = snapshot.htmlUrl,
            artifactCount = snapshot.artifactCount,
            androidArtifactCount = snapshot.androidArtifactCount,
            checkedAtMillis = snapshot.checkedAtMillis,
            notifiedAtMillis = notifiedAtMillis,
            notificationTitle = notificationTitle,
            notificationContent = notificationContent,
        )
    }

    suspend fun clearGitHubActionsDownloadHistory(
        owner: String = "",
        repo: String = ""
    ) {
        withContext(ioDispatcher) {
            GitHubActionsDownloadHistoryStore.clear(owner = owner, repo = repo)
        }
    }

    suspend fun clearGitHubActionsNotificationHistory(
        owner: String = "",
        repo: String = ""
    ) {
        withContext(ioDispatcher) {
            GitHubActionsNotificationHistoryStore.clear(owner = owner, repo = repo)
        }
    }

    suspend fun pruneGitHubActionsNotificationHistoryBefore(
        cutoffMillis: Long,
        owner: String = "",
        repo: String = ""
    ): Int {
        return withContext(ioDispatcher) {
            GitHubActionsNotificationHistoryStore.pruneBefore(
                cutoffMillis = cutoffMillis,
                owner = owner,
                repo = repo
            )
        }
    }

    suspend fun selectGitHubActionsRuns(
        runs: List<GitHubActionsRunArtifacts>,
        options: GitHubActionsRunSelectionOptions,
        workflow: GitHubActionsWorkflow? = null
    ): List<GitHubActionsRunMatch> {
        return withContext(defaultDispatcher) {
            GitHubActionsRunSelector.selectRuns(
                runs = runs,
                options = options,
                workflowTraits = workflow?.let(GitHubActionsWorkflowSelector::inspectWorkflow)
            )
        }
    }

    suspend fun selectGitHubActionsArtifacts(
        artifacts: List<GitHubActionsArtifact>,
        options: GitHubActionsArtifactSelectionOptions
    ): List<GitHubActionsArtifactMatch> {
        return withContext(defaultDispatcher) {
            GitHubActionsArtifactSelector.selectDisplayArtifacts(
                artifacts = artifacts,
                options = options
            )
        }
    }

    suspend fun selectGitHubActionsWorkflows(
        workflows: List<GitHubActionsWorkflow>,
        artifactSignals: Map<Long, GitHubActionsWorkflowArtifactSignal>,
        options: GitHubActionsWorkflowSelectionOptions
    ): List<GitHubActionsWorkflowMatch> {
        return withContext(defaultDispatcher) {
            GitHubActionsWorkflowSelector.selectWorkflows(
                workflows = workflows,
                artifactSignals = artifactSignals,
                options = options
            )
        }
    }

    suspend fun resolveGitHubActionsArtifactDownloadUrl(
        artifact: GitHubActionsArtifact,
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        preferApiTokenRedirect: Boolean = false
    ): Result<GitHubActionsArtifactDownloadResolution> {
        return withContext(ioDispatcher) {
            GitHubActionsRepository.fromLookupConfig(lookupConfig)
                .resolveArtifactDownloadUrl(
                    artifact = artifact,
                    owner = owner,
                    repo = repo,
                    preferApiTokenRedirect = preferApiTokenRedirect
                )
        }
    }

    suspend fun resolveGitHubActionsArtifactShareUrl(
        artifact: GitHubActionsArtifact,
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubActionsArtifactDownloadResolution> {
        return withContext(ioDispatcher) {
            GitHubActionsRepository.fromLookupConfig(lookupConfig)
                .resolveArtifactShareUrl(
                    artifact = artifact,
                    owner = owner,
                    repo = repo
                )
        }
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
}
