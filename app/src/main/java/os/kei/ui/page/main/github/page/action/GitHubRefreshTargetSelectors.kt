package os.kei.ui.page.main.github.page.action

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.updateIntervalMs

internal fun selectDueTrackedUpdateItems(
    trackedItems: List<GitHubTrackedApp>,
    checkedAtMillisById: Map<String, Long>,
    lastRefreshMs: Long,
    refreshIntervalHours: Int,
    nowMs: Long,
): List<GitHubTrackedApp> {
    if (trackedItems.isEmpty()) return emptyList()
    return trackedItems.filter { item ->
        val checkedAtMillis =
            checkedAtMillisById[item.id]
                ?.takeIf { it > 0L }
                ?: lastRefreshMs
        checkedAtMillis <= 0L ||
            (nowMs - checkedAtMillis).coerceAtLeast(0L) >=
            item.updateIntervalMs(refreshIntervalHours)
    }
}

internal fun selectImmediateTrackMutationRefreshIds(
    affectedTrackIds: Set<String>,
    validTrackIds: Set<String>,
    limit: Int = GITHUB_TRACK_MUTATION_IMMEDIATE_REFRESH_LIMIT,
): List<String> {
    if (affectedTrackIds.isEmpty() || validTrackIds.isEmpty() || limit <= 0) return emptyList()
    return affectedTrackIds
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it in validTrackIds }
        .distinct()
        .take(limit)
        .toList()
}

internal fun selectActiveTrackedRefreshTargetIds(
    requestedTrackIds: Iterable<String>,
    validTrackIds: Set<String>,
): List<String> {
    if (validTrackIds.isEmpty()) return emptyList()
    return requestedTrackIds
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it in validTrackIds }
        .distinct()
        .toList()
}

internal fun selectActiveTrackedRefreshTargets(
    requestedTrackIds: Iterable<String>,
    activeItems: List<GitHubTrackedApp>,
): List<GitHubTrackedApp> {
    if (activeItems.isEmpty()) return emptyList()
    val activeItemsById = linkedMapOf<String, GitHubTrackedApp>()
    activeItems.forEach { item ->
        activeItemsById[item.id] = item
    }
    return selectActiveTrackedRefreshTargetIds(
        requestedTrackIds = requestedTrackIds,
        validTrackIds = activeItemsById.keys,
    ).mapNotNull(activeItemsById::get)
}
