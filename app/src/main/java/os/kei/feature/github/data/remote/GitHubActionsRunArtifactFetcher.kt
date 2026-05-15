package os.kei.feature.github.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsWorkflowRun

internal class GitHubActionsRunArtifactFetcher(
    private val maxConcurrency: Int,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fetchRunArtifacts: (
        owner: String,
        repo: String,
        runId: Long,
        limit: Int
    ) -> Result<List<GitHubActionsArtifact>>
) {
    fun fetchForRuns(
        owner: String,
        repo: String,
        runs: List<GitHubActionsWorkflowRun>,
        limit: Int
    ): List<Pair<GitHubActionsWorkflowRun, Result<List<GitHubActionsArtifact>>>> {
        return GitHubExecution.runBlockingIo {
            fetchForRunsAsync(
                owner = owner,
                repo = repo,
                runs = runs,
                limit = limit
            )
        }
    }

    suspend fun fetchForRunsAsync(
        owner: String,
        repo: String,
        runs: List<GitHubActionsWorkflowRun>,
        limit: Int
    ): List<Pair<GitHubActionsWorkflowRun, Result<List<GitHubActionsArtifact>>>> {
        if (runs.isEmpty()) return emptyList()
        val concurrency = runs.size.coerceAtMost(maxConcurrency)
        if (concurrency <= 1) {
            return withContext(ioDispatcher) {
                runs.map { run ->
                    run to fetchRunArtifacts(owner, repo, run.id, limit)
                }
            }
        }
        return GitHubExecution.mapOrderedBounded(
            items = runs,
            maxConcurrency = concurrency,
            dispatcher = ioDispatcher
        ) { run ->
            run to fetchRunArtifacts(owner, repo, run.id, limit)
        }
    }
}
