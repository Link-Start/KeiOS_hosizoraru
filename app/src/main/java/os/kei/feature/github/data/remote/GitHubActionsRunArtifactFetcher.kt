package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import java.util.concurrent.Executors

internal class GitHubActionsRunArtifactFetcher(
    private val maxConcurrency: Int,
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
        if (runs.isEmpty()) return emptyList()
        val concurrency = runs.size.coerceAtMost(maxConcurrency)
        if (concurrency <= 1) {
            return runs.map { run ->
                run to fetchRunArtifacts(owner, repo, run.id, limit)
            }
        }
        val executor = Executors.newFixedThreadPool(concurrency)
        return try {
            runs.map { run ->
                executor.submit<Pair<GitHubActionsWorkflowRun, Result<List<GitHubActionsArtifact>>>> {
                    run to fetchRunArtifacts(owner, repo, run.id, limit)
                }
            }.map { future ->
                future.get()
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
