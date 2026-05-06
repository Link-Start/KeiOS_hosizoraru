package os.kei.feature.github.data.remote

import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsRepositoryInfo
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import java.time.Instant

internal object GitHubActionsJsonParser {
    fun parseWorkflows(json: String): List<GitHubActionsWorkflow> {
        val root = JSONObject(json)
        val array = root.optJSONArray("workflows") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val workflow = array.optJSONObject(index) ?: continue
                val id = workflow.optLong("id", 0L).takeIf { it > 0L } ?: continue
                add(
                    GitHubActionsWorkflow(
                        id = id,
                        nodeId = workflow.optString("node_id").trim(),
                        name = workflow.optString("name").trim(),
                        path = workflow.optString("path").trim(),
                        state = workflow.optString("state").trim(),
                        htmlUrl = workflow.optString("html_url").trim(),
                        badgeUrl = workflow.optString("badge_url").trim(),
                        createdAtMillis = workflow.optString("created_at").parseIsoInstantOrNull(),
                        updatedAtMillis = workflow.optString("updated_at").parseIsoInstantOrNull()
                    )
                )
            }
        }.sortedWith(
            compareBy<GitHubActionsWorkflow> { it.state != "active" }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path.lowercase() }
        )
    }

    fun parseRepositoryInfo(
        json: String,
        fallbackOwner: String,
        fallbackRepo: String
    ): GitHubActionsRepositoryInfo {
        val root = JSONObject(json)
        return GitHubActionsRepositoryInfo(
            owner = root.optJSONObject("owner")
                ?.optString("login")
                ?.trim()
                .orEmpty()
                .ifBlank { fallbackOwner },
            repo = root.optString("name").trim().ifBlank { fallbackRepo },
            fullName = root.optString("full_name").trim(),
            defaultBranch = root.optString("default_branch").trim()
        )
    }

    fun parseWorkflowRuns(json: String): List<GitHubActionsWorkflowRun> {
        val root = JSONObject(json)
        val array = root.optJSONArray("workflow_runs") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val run = array.optJSONObject(index) ?: continue
                parseWorkflowRunObject(run)?.let(::add)
            }
        }.sortedWith(
            compareByDescending<GitHubActionsWorkflowRun> { it.createdAtMillis ?: Long.MIN_VALUE }
                .thenByDescending { it.id }
        )
    }

    fun parseWorkflowRun(json: String): GitHubActionsWorkflowRun {
        val root = JSONObject(json)
        return parseWorkflowRunObject(root)
            ?: throw IllegalArgumentException("workflow run payload missing id")
    }

    fun parseArtifacts(
        json: String,
        fallbackWorkflowRunId: Long = 0L
    ): List<GitHubActionsArtifact> {
        val root = JSONObject(json)
        val array = root.optJSONArray("artifacts") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val artifact = array.optJSONObject(index) ?: continue
                val id = artifact.optLong("id", 0L).takeIf { it > 0L } ?: continue
                val workflowRun = artifact.optJSONObject("workflow_run")
                add(
                    GitHubActionsArtifact(
                        id = id,
                        nodeId = artifact.optString("node_id").trim(),
                        name = artifact.optString("name").trim(),
                        sizeBytes = artifact.optLong("size_in_bytes", 0L),
                        expired = artifact.optBoolean("expired", false),
                        digest = artifact.optString("digest").trim(),
                        archiveDownloadUrl = artifact.optString("archive_download_url").trim(),
                        workflowRunId = workflowRun?.optLong("id", 0L)
                            ?.takeIf { it > 0L }
                            ?: fallbackWorkflowRunId,
                        workflowRunHeadBranch = workflowRun?.optString("head_branch").orEmpty()
                            .trim(),
                        workflowRunHeadSha = workflowRun?.optString("head_sha").orEmpty().trim(),
                        createdAtMillis = artifact.optString("created_at").parseIsoInstantOrNull(),
                        updatedAtMillis = artifact.optString("updated_at").parseIsoInstantOrNull(),
                        expiresAtMillis = artifact.optString("expires_at").parseIsoInstantOrNull()
                    )
                )
            }
        }.sortedWith(
            compareBy<GitHubActionsArtifact> { it.expired }
                .thenByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun parseWorkflowRunObject(run: JSONObject): GitHubActionsWorkflowRun? {
        val id = run.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val actor = run.optJSONObject("actor")
        val triggeringActor = run.optJSONObject("triggering_actor")
        val repository = run.optJSONObject("repository")
        val headRepository = run.optJSONObject("head_repository")
        val pullRequests = run.optJSONArray("pull_requests")
        return GitHubActionsWorkflowRun(
            id = id,
            name = run.optString("name").trim(),
            displayTitle = run.optString("display_title").trim(),
            workflowId = run.optLong("workflow_id", 0L),
            workflowName = run.optString("workflow_name").trim(),
            runNumber = run.optLong("run_number", 0L),
            runAttempt = run.optInt("run_attempt", 0),
            event = run.optString("event").trim(),
            status = run.optString("status").trim(),
            conclusion = run.optString("conclusion").trim(),
            headBranch = run.optString("head_branch").trim(),
            headSha = run.optString("head_sha").trim(),
            htmlUrl = run.optString("html_url").trim(),
            artifactsUrl = run.optString("artifacts_url").trim(),
            actorLogin = actor?.optString("login").orEmpty().trim(),
            triggeringActorLogin = triggeringActor?.optString("login").orEmpty().trim(),
            repositoryFullName = repository?.optString("full_name").orEmpty().trim(),
            headRepositoryFullName = headRepository?.optString("full_name").orEmpty().trim(),
            headRepositoryFork = headRepository?.optBoolean("fork", false) ?: false,
            pullRequestCount = pullRequests?.length() ?: 0,
            checkSuiteId = run.optLong("check_suite_id", 0L),
            createdAtMillis = run.optString("created_at").parseIsoInstantOrNull(),
            runStartedAtMillis = run.optString("run_started_at").parseIsoInstantOrNull(),
            updatedAtMillis = run.optString("updated_at").parseIsoInstantOrNull()
        )
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching {
            if (isBlank()) null else Instant.parse(this).toEpochMilli()
        }.getOrNull()
    }
}
