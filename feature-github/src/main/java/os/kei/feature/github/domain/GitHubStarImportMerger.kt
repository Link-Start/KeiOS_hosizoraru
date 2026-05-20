package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.StarImportApplyResult
import java.util.Locale

data class GitHubStarImportMergeResult(
    val items: List<GitHubTrackedApp>,
    val result: StarImportApplyResult
)

object GitHubStarImportMerger {
    fun merge(
        existingItems: List<GitHubTrackedApp>,
        importedItems: List<GitHubTrackedApp>
    ): GitHubStarImportMergeResult {
        if (importedItems.isEmpty()) {
            return GitHubStarImportMergeResult(
                items = existingItems,
                result = StarImportApplyResult()
            )
        }

        val mergedItems = existingItems.toMutableList()
        val indexById = mergedItems.withIndex()
            .associate { it.value.id to it.index }
            .toMutableMap()
        val repoOnlyIndexByRepo = mergedItems.withIndex()
            .filter { (_, item) -> item.packageName.trim().isBlank() }
            .associate { (index, item) -> item.repoKey() to index }
            .toMutableMap()

        var addedCount = 0
        var updatedCount = 0
        var unchangedCount = 0
        val affectedTrackIds = linkedSetOf<String>()
        val removedTrackIds = linkedSetOf<String>()
        val affectedPackages = linkedSetOf<String>()

        importedItems.forEach { item ->
            val exactIndex = indexById[item.id]
            val fallbackIndex = if (exactIndex == null && item.packageName.trim().isNotBlank()) {
                repoOnlyIndexByRepo[item.repoKey()]
            } else {
                null
            }

            when {
                exactIndex != null -> {
                    val mergedItem = item.withLocalAppTypeFallback(mergedItems[exactIndex])
                    if (mergedItems[exactIndex] == mergedItem) {
                        unchangedCount += 1
                    } else {
                        mergedItems[exactIndex] = mergedItem
                        updatedCount += 1
                        affectedTrackIds += mergedItem.id
                        mergedItem.packageName.trim().takeIf { it.isNotBlank() }
                            ?.let(affectedPackages::add)
                    }
                }

                fallbackIndex != null -> {
                    val previous = mergedItems[fallbackIndex]
                    val mergedItem = item.withLocalAppTypeFallback(previous)
                    mergedItems[fallbackIndex] = mergedItem
                    indexById.remove(previous.id)
                    indexById[mergedItem.id] = fallbackIndex
                    repoOnlyIndexByRepo.remove(previous.repoKey())
                    updatedCount += 1
                    removedTrackIds += previous.id
                    affectedTrackIds += mergedItem.id
                    mergedItem.packageName.trim().takeIf { it.isNotBlank() }
                        ?.let(affectedPackages::add)
                }

                else -> {
                    mergedItems += item
                    indexById[item.id] = mergedItems.lastIndex
                    if (item.packageName.trim().isBlank()) {
                        repoOnlyIndexByRepo[item.repoKey()] = mergedItems.lastIndex
                    }
                    addedCount += 1
                    affectedTrackIds += item.id
                    item.packageName.trim().takeIf { it.isNotBlank() }?.let(affectedPackages::add)
                }
            }
        }

        return GitHubStarImportMergeResult(
            items = mergedItems,
            result = StarImportApplyResult(
                addedCount = addedCount,
                updatedCount = updatedCount,
                unchangedCount = unchangedCount,
                affectedTrackIds = affectedTrackIds,
                removedTrackIds = removedTrackIds,
                affectedPackages = affectedPackages
            )
        )
    }
}

private fun GitHubTrackedApp.repoKey(): String {
    return "${owner.trim().lowercase(Locale.ROOT)}/${repo.trim().lowercase(Locale.ROOT)}"
}

private fun GitHubTrackedApp.withLocalAppTypeFallback(
    existingItem: GitHubTrackedApp
): GitHubTrackedApp {
    return if (localAppType == GitHubTrackedLocalAppType.Unknown) {
        copy(localAppType = existingItem.localAppType)
    } else {
        this
    }
}
