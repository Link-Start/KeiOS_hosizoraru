package os.kei.feature.github.data.remote

import os.kei.feature.github.GitHubBoundedRunner
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
        return GitHubBoundedRunner.mapOrdered(
            items = runs,
            maxConcurrency = concurrency,
            threadName = "github-actions-artifact-fetch"
        ) { run ->
            run to fetchRunArtifacts(owner, repo, run.id, limit)
        }
    }
}
