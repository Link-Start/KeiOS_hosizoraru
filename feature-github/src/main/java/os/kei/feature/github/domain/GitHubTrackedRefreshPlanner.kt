package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.excludesAutomaticReleaseRefresh

object GitHubTrackedRefreshPlanner {
    fun selectPartialMissingCheckStateItems(
        trackedItems: List<GitHubTrackedApp>,
        cachedTrackIds: Set<String>
    ): List<GitHubTrackedApp> {
        if (trackedItems.isEmpty()) return emptyList()
        val normalizedCachedIds = cachedTrackIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val missingItems = trackedItems.filter { item ->
            item.id !in normalizedCachedIds && !item.excludesAutomaticReleaseRefresh()
        }
        if (missingItems.isEmpty() || missingItems.size == trackedItems.size) return emptyList()
        return GitHubTrackedRefreshBatchScheduler
            .buildFairRefreshOrder(missingItems)
            .map { it.item }
    }
}
