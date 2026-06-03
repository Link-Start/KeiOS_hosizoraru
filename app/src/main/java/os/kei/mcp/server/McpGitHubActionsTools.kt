package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.feature.github.domain.GitHubActionsRecommendedRunRefreshService
import os.kei.feature.github.domain.GitHubActionsService
import os.kei.feature.github.domain.GitHubTrackService
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.actionsUpdateIntervalMs
import os.kei.feature.github.model.isGitHubRepositoryTrack

internal class McpGitHubActionsTools(
    private val environment: McpToolEnvironment
) {
    private val actionsService = GitHubActionsService()
    private val trackService = GitHubTrackService()

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.github.actions.recommended") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val refresh = argBoolean(request.arguments?.get("refresh"), false)
            val onlyEnabled = argBoolean(request.arguments?.get("onlyEnabled"), true)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT)
                .coerceIn(1, MAX_ENTRY_LIMIT)
            buildRecommendedActionsText(
                repoFilter = repoFilter,
                refresh = refresh,
                onlyEnabled = onlyEnabled,
                limit = limit
            )
        }
    }

    private suspend fun buildRecommendedActionsText(
        repoFilter: String,
        refresh: Boolean,
        onlyEnabled: Boolean,
        limit: Int
    ): String {
        val snapshot = trackService.loadTrackSnapshotBlocking()
        val targets = snapshot.items
            .asSequence()
            .filter { item -> item.isGitHubRepositoryTrack() }
            .filter { item -> !onlyEnabled || item.checkActionsUpdates }
            .filter { item -> item.matchesActionsFilter(repoFilter) }
            .take(limit)
            .toList()
        if (targets.isEmpty()) {
            return "ok=true\nrefresh=$refresh\nmatched=0\nmessage=no_actions_targets"
        }

        val rows = if (refresh) {
            GitHubActionsRecommendedRunRefreshService(source = actionsService)
                .refreshItems(
                    items = targets,
                    lookupConfig = snapshot.lookupConfig,
                    maxConcurrency = MAX_PARALLEL_ACTIONS_REFRESH,
                )
                .outcomes
                .map { outcome ->
                    ActionsRunRow(
                        item = outcome.item,
                        snapshot = outcome.current ?: outcome.previous,
                        globalRefreshIntervalHours = snapshot.refreshIntervalHours,
                        cacheState = when {
                            outcome.current != null -> "refreshed"
                            outcome.previous != null -> "stale"
                            else -> "failed"
                        },
                        newerThanCache = outcome.newerThanPrevious,
                        errorMessage = outcome.errorMessage,
                    )
                }
        } else {
            targets.map { item ->
                val cached = actionsService.loadRecommendedRunSnapshot(item.id)
                ActionsRunRow(
                    item = item,
                    snapshot = cached,
                    globalRefreshIntervalHours = snapshot.refreshIntervalHours,
                    cacheState = if (cached != null) "cache" else "missing"
                )
            }
        }

        return buildString {
            appendLine("ok=true")
            appendLine("refresh=$refresh")
            appendLine("onlyEnabled=$onlyEnabled")
            appendLine("matched=${targets.size}")
            appendLine("globalRefreshIntervalHours=${snapshot.refreshIntervalHours}")
            appendLine("snapshotCount=${rows.count { it.snapshot != null }}")
            appendLine("missingCount=${rows.count { it.snapshot == null }}")
            appendLine("newerCount=${rows.count { it.newerThanCache }}")
            rows.forEachIndexed { index, row ->
                appendLine(row.toMcpLine("run[$index]"))
            }
        }.trim()
    }

    private fun GitHubTrackedApp.matchesActionsFilter(repoFilter: String): Boolean {
        if (repoFilter.isBlank()) return true
        return "$owner/$repo".contains(repoFilter, ignoreCase = true) ||
                packageName.contains(repoFilter, ignoreCase = true) ||
                appLabel.contains(repoFilter, ignoreCase = true)
    }

    private data class ActionsRunRow(
        val item: GitHubTrackedApp,
        val snapshot: GitHubActionsRecommendedRunSnapshot?,
        val globalRefreshIntervalHours: Int,
        val cacheState: String,
        val newerThanCache: Boolean = false,
        val errorMessage: String = ""
    ) {
        fun toMcpLine(prefix: String): String {
            val current = snapshot
            val intervalMinutes = item.actionsUpdateIntervalMs(globalRefreshIntervalHours) / 60_000L
            val schedule =
                "actionsIntervalMode:${item.actionsUpdateIntervalMode.storageId} | actionsIntervalMinutes:$intervalMinutes"
            if (current == null) {
                return "$prefix=repo:${item.owner}/${item.repo} | label:${item.appLabel} | enabled:${item.checkActionsUpdates} | $schedule | cache:$cacheState | error:$errorMessage"
            }
            return "$prefix=repo:${current.owner}/${current.repo} | label:${current.appLabel} | enabled:${item.checkActionsUpdates} | $schedule | cache:$cacheState | newer:$newerThanCache | workflow:${current.workflowName.ifBlank { current.workflowPath }} | run:${current.runLabel} | status:${current.status} | conclusion:${current.conclusion} | branch:${current.headBranch} | artifacts:${current.androidArtifactCount}/${current.artifactCount} | checkedAtMillis:${current.checkedAtMillis} | url:${current.htmlUrl}"
        }
    }

    private companion object {
        const val MAX_PARALLEL_ACTIONS_REFRESH = 4
    }
}
