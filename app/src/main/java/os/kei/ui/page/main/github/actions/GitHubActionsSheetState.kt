package os.kei.ui.page.main.github.actions

import androidx.compose.runtime.Immutable
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.github.page.GitHubActionsArtifactFilter

@Immutable
internal data class GitHubActionsVisibleArtifactMatch(
    val index: Int,
    val match: GitHubActionsArtifactMatch,
    val recommended: Boolean,
)

@Immutable
internal data class GitHubActionsSheetInput(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val runsLoading: Boolean = false,
    val workflows: List<GitHubActionsWorkflowMatch> = emptyList(),
    val runs: List<GitHubActionsRunMatch> = emptyList(),
    val selectedWorkflowId: Long? = null,
    val selectedRunId: Long? = null,
    val refreshingRunIds: Map<Long, Boolean> = emptyMap(),
    val artifactFilter: GitHubActionsArtifactFilter = GitHubActionsArtifactFilter.Recommended,
    val lookupConfig: GitHubLookupConfig = GitHubLookupConfig(),
    val relativeTimeNowMillis: Long = 0L,
)

@Immutable
internal data class GitHubActionsSheetUiState(
    val refreshing: Boolean = false,
    val selectedWorkflowId: Long? = null,
    val selectedRun: GitHubActionsRunMatch? = null,
    val recommendedWorkflowId: Long? = null,
    val recommendedRunId: Long? = null,
    val selectedRunArtifactsLoading: Boolean = false,
    val canResolveArtifacts: Boolean = false,
    val recommendedArtifactCount: Int = 0,
    val alternativesArtifactCount: Int = 0,
    val visibleArtifactMatches: List<GitHubActionsVisibleArtifactMatch> = emptyList(),
    val relativeTimeNowMillis: Long = 0L,
)

internal fun deriveGitHubActionsSheetState(input: GitHubActionsSheetInput): GitHubActionsSheetUiState {
    if (!input.visible) return GitHubActionsSheetUiState()
    val selectedRun =
        input.runs.firstOrNull {
            it.runArtifacts.run.id == input.selectedRunId
        }
    val recommendedRunId =
        (
            input.runs.firstOrNull { match ->
                match.traits.completed &&
                    match.traits.successful &&
                    !match.traits.pullRequestLike &&
                    match.artifactMatches.isNotEmpty()
            } ?: input.runs.firstOrNull { it.traits.safeForRecommendation }
        )?.runArtifacts
            ?.run
            ?.id
    val selectedRunArtifactsLoading =
        selectedRun?.let { runMatch ->
            val runId = runMatch.runArtifacts.run.id
            runMatch.traits.completed &&
                runMatch.runArtifacts.artifacts.isEmpty() &&
                input.refreshingRunIds[runId] == true
        } == true
    val visibleMatches =
        selectedRun
            ?.artifactMatches
            .orEmpty()
            .mapIndexed { index, match ->
                GitHubActionsVisibleArtifactMatch(
                    index = index,
                    match = match,
                    recommended = index == 0,
                )
            }.filter { visibleMatch ->
                when (input.artifactFilter) {
                    GitHubActionsArtifactFilter.Recommended -> visibleMatch.index == 0
                    GitHubActionsArtifactFilter.Alternatives -> visibleMatch.index > 0
                    GitHubActionsArtifactFilter.All -> true
                }
            }
    return GitHubActionsSheetUiState(
        refreshing =
            input.loading ||
                input.runsLoading ||
                input.refreshingRunIds.any { it.value },
        selectedWorkflowId = input.selectedWorkflowId,
        selectedRun = selectedRun,
        recommendedWorkflowId =
            input.workflows
                .firstOrNull()
                ?.workflow
                ?.id,
        recommendedRunId = recommendedRunId,
        selectedRunArtifactsLoading = selectedRunArtifactsLoading,
        canResolveArtifacts = input.lookupConfig.actionsArtifactDownloadsAvailable,
        recommendedArtifactCount = selectedRun?.artifactMatches?.take(1)?.size ?: 0,
        alternativesArtifactCount = (selectedRun?.artifactMatches?.size ?: 0).let { (it - 1).coerceAtLeast(0) },
        visibleArtifactMatches = visibleMatches,
        relativeTimeNowMillis = input.relativeTimeNowMillis,
    )
}
