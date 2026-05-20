package os.kei.feature.github.data.remote

import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState

class GitHubActionsProfileSource(
    private val client: okhttp3.OkHttpClient,
    private val apiBaseUrl: String,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork
) {
    suspend fun fetch(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long,
        availability: MutableList<GitHubRepositoryProfileSourceState>
    ): GitHubRepositoryActionsProfile = coroutineScope {
        val actionsRepository = GitHubActionsRepository(
            apiToken = request.lookupConfig.apiToken,
            client = client,
            apiBaseUrl = apiBaseUrl,
            actionsStrategy = GitHubActionsLookupStrategyOption.GitHubApiToken,
            requireApiTokenForApiStrategy = false,
            ioDispatcher = ioDispatcher
        )
        val runsDeferred = async(ioDispatcher) {
            actionsRepository.fetchRecentRepositoryWorkflowRuns(
                owner = request.owner,
                repo = request.repo,
                limit = ACTIONS_PROFILE_RUN_LIMIT
            ).result
        }
        val artifactsDeferred = async(ioDispatcher) {
            actionsRepository.fetchRecentRepositoryArtifacts(
                owner = request.owner,
                repo = request.repo,
                limit = ACTIONS_PROFILE_ARTIFACT_LIMIT
            ).result
        }
        val runsResult = runsDeferred.await()
        val artifactsResult = artifactsDeferred.await()
        val runs = runsResult.getOrNull().orEmpty()
        val artifacts = artifactsResult.getOrNull().orEmpty()
        if (runsResult.isSuccess) {
            availability += loaded(GitHubRepositoryProfileSource.ActionsRunsApi, fetchedAtMillis)
        } else {
            availability += failed(
                source = GitHubRepositoryProfileSource.ActionsRunsApi,
                fetchedAtMillis = fetchedAtMillis,
                error = runsResult.exceptionOrNull()
                    ?: IllegalStateException("actions runs unavailable")
            )
        }
        if (artifactsResult.isSuccess) {
            availability += loaded(
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            )
        } else {
            availability += failed(
                source = GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis = fetchedAtMillis,
                error = artifactsResult.exceptionOrNull()
                    ?: IllegalStateException("actions artifacts unavailable")
            )
        }
        build(runs, artifacts, fetchedAtMillis)
    }

    fun build(
        runs: List<GitHubActionsWorkflowRun>,
        artifacts: List<GitHubActionsArtifact>,
        fetchedAtMillis: Long
    ): GitHubRepositoryActionsProfile {
        val source = GitHubRepositoryProfileSource.ActionsRunsApi
        val latestRun = runs.maxWithOrNull(
            compareBy<GitHubActionsWorkflowRun> {
                it.updatedAtMillis ?: it.createdAtMillis ?: Long.MIN_VALUE
            }
                .thenBy { it.id }
        )
        return GitHubRepositoryActionsProfile(
            workflowRunCount = intField(runs.size, source, fetchedAtMillis),
            successfulRunCount = intField(runs.count {
                it.conclusion.equals(
                    "success",
                    ignoreCase = true
                )
            }, source, fetchedAtMillis),
            failedRunCount = intField(
                runs.count { it.conclusion.lowercase(Locale.ROOT) in failingActionConclusions },
                source,
                fetchedAtMillis
            ),
            latestRunStatus = stringField(latestRun?.status.orEmpty(), source, fetchedAtMillis),
            latestRunConclusion = stringField(
                latestRun?.conclusion.orEmpty(),
                source,
                fetchedAtMillis
            ),
            latestRunUpdatedAtMillis = longField(
                latestRun?.updatedAtMillis ?: latestRun?.createdAtMillis ?: -1L,
                source,
                fetchedAtMillis
            ),
            artifactCount = intField(
                artifacts.size,
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            ),
            nonExpiredArtifactCount = intField(
                artifacts.count { !it.expired },
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            ),
            androidArtifactCount = intField(
                artifacts.count { artifact -> artifact.name.androidBuildArtifactLike() },
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            )
        )
    }

    private companion object {
        private const val ACTIONS_PROFILE_RUN_LIMIT = 12
        private const val ACTIONS_PROFILE_ARTIFACT_LIMIT = 30
        private val failingActionConclusions = setOf(
            "failure",
            "timed_out",
            "cancelled",
            "startup_failure",
            "action_required"
        )
    }
}
