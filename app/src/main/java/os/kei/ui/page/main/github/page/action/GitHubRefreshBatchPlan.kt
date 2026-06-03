package os.kei.ui.page.main.github.page.action

import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.model.GitHubTrackedApp

internal data class GitHubRefreshBatchPlan(
    val targets: List<GitHubTrackedApp>,
    val targetIds: List<String>,
    val coversAllActiveItems: Boolean,
    val refreshScope: GitHubRefreshScope,
    val updateGlobalRefreshTimestamp: Boolean,
)

internal fun planGitHubTrackedBatchRefresh(
    requestedTargetIds: List<String>,
    activeItems: List<GitHubTrackedApp>,
    refreshScope: GitHubRefreshScope,
    updateGlobalRefreshTimestamp: Boolean,
): GitHubRefreshBatchPlan {
    val targets = selectActiveTrackedRefreshTargets(
        requestedTrackIds = requestedTargetIds,
        activeItems = activeItems,
    )
    val targetIds = targets.map { it.id }
    val activeIds = activeItems.mapTo(LinkedHashSet()) { it.id }
    val coversAllActiveItems =
        activeIds.isNotEmpty() &&
            targetIds.size == activeIds.size &&
            targetIds.toSet() == activeIds
    val effectiveRefreshScope =
        if (refreshScope == GitHubRefreshScope.VisibleTracked && coversAllActiveItems) {
            GitHubRefreshScope.AllTracked
        } else {
            refreshScope
        }
    return GitHubRefreshBatchPlan(
        targets = targets,
        targetIds = targetIds,
        coversAllActiveItems = coversAllActiveItems,
        refreshScope = effectiveRefreshScope,
        updateGlobalRefreshTimestamp = updateGlobalRefreshTimestamp || coversAllActiveItems,
    )
}
