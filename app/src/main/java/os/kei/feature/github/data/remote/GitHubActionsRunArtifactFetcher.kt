package os.kei.feature.github.data.remote

import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsWorkflowRun

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
        return GitHubExecution.mapOrderedBoundedBlocking(
            items = runs,
            maxConcurrency = concurrency
        ) { run ->
            run to fetchRunArtifacts(owner, repo, run.id, limit)
        }
    }
}
