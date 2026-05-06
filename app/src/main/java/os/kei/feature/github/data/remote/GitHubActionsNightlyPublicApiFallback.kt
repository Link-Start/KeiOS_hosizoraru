package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsRunArtifacts
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot

internal class GitHubActionsNightlyPublicApiFallback(
    private val apiUrls: GitHubActionsApiUrlBuilder,
    private val fetchJson: (url: String, cacheTtlMillis: Long) -> Result<String>,
    private val defaultWorkflowLimit: Int,
    private val runsCacheTtlMillis: Long,
    private val artifactCacheTtlMillis: Long,
    private val metadataCacheTtlMillis: Long
) {
    fun fetchWorkflowArtifactSnapshot(
        owner: String,
        repo: String,
        workflowId: String,
        runLimit: Int,
        artifactsPerRun: Int,
        artifactRunLimit: Int,
        branch: String,
        event: String,
        status: String,
        actor: String,
        created: String,
        headSha: String,
        excludePullRequests: Boolean
    ): Result<GitHubActionsWorkflowArtifactsSnapshot> = runCatching {
        val workflow = findPublicApiWorkflow(owner, repo, workflowId).getOrThrow()
        val runs = fetchJson(
            apiUrls.workflowRuns(
                owner = owner,
                repo = repo,
                workflowId = workflow.id.toString(),
                limit = runLimit,
                branch = branch,
                event = event,
                status = status.nightlyPublicApiRunStatus(),
                actor = actor,
                created = created,
                headSha = headSha,
                excludePullRequests = excludePullRequests
            ),
            runsCacheTtlMillis
        ).getOrThrow().let(GitHubActionsJsonParser::parseWorkflowRuns)
        val artifactRuns = runs.take(artifactRunLimit.coerceAtLeast(0))
        val artifactsByRunId = mutableMapOf<Long, List<GitHubActionsArtifact>>()
        artifactRuns.forEach { run ->
            val artifacts = fetchJson(
                apiUrls.runArtifacts(owner, repo, run.id, artifactsPerRun),
                artifactCacheTtlMillis
            ).getOrThrow()
                .let { json ->
                    GitHubActionsJsonParser.parseArtifacts(
                        json = json,
                        fallbackWorkflowRunId = run.id
                    )
                }
                .map { artifact ->
                    artifact.copy(
                        archiveDownloadUrl = apiUrls.nightlyRunArtifactDownload(
                            owner = owner,
                            repo = repo,
                            runId = run.id,
                            artifactName = artifact.name
                        )
                    )
                }
            artifactsByRunId[run.id] = artifacts
        }
        GitHubActionsWorkflowArtifactsSnapshot(
            owner = owner,
            repo = repo,
            workflowId = workflow.id.toString(),
            runs = runs.map { run ->
                GitHubActionsRunArtifacts(
                    run = run,
                    artifacts = artifactsByRunId[run.id].orEmpty()
                )
            }
        )
    }

    private fun findPublicApiWorkflow(
        owner: String,
        repo: String,
        workflowId: String
    ): Result<GitHubActionsWorkflow> = runCatching {
        workflowId.trim().toLongOrNull()?.takeIf { it > 0L }?.let { id ->
            return@runCatching GitHubActionsWorkflow(
                id = id,
                name = id.toString(),
                path = workflowId.trim()
            )
        }
        val workflows = fetchJson(
            apiUrls.workflows(owner, repo, defaultWorkflowLimit),
            metadataCacheTtlMillis
        ).getOrThrow().let(GitHubActionsJsonParser::parseWorkflows)
        selectPublicApiWorkflow(workflows, workflowId)
            ?: error("GitHub public API found no matching workflow: $workflowId")
    }

    private fun selectPublicApiWorkflow(
        workflows: List<GitHubActionsWorkflow>,
        workflowId: String
    ): GitHubActionsWorkflow? {
        val lookup = workflowId.trim()
            .substringBefore('?')
            .trim()
        val lookupFile = lookup.substringAfterLast('/').trim()
        val lookupPath = lookup.trimStart('/')
        return workflows.firstOrNull { workflow -> workflow.id.toString() == lookup }
            ?: workflows.firstOrNull { workflow ->
                workflow.path.equals(
                    lookupPath,
                    ignoreCase = true
                )
            } ?: workflows.firstOrNull { workflow ->
                workflow.path.substringAfterLast('/').equals(lookupFile, ignoreCase = true)
            } ?: workflows.firstOrNull { workflow ->
                workflow.name.equals(
                    lookup,
                    ignoreCase = true
                )
            }
    }

    private fun String.nightlyPublicApiRunStatus(): String {
        val normalized = trim()
        return when {
            normalized.isBlank() -> "success"
            normalized.equals("completed", ignoreCase = true) -> "success"
            else -> normalized
        }
    }
}
