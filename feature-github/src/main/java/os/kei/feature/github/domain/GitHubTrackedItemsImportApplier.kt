package os.kei.feature.github.domain

import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.hasSameGitHubTrackingConfigIgnoringLocalAppType

data class GitHubTrackedItemsImportApplyResult(
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val invalidCount: Int = 0,
    val duplicateCount: Int = 0,
    val mergedItems: List<GitHubTrackedApp> = emptyList(),
    val touchedTrackIds: Set<String> = emptySet(),
    val refreshTrackIds: Set<String> = emptySet(),
) {
    val hasChanges: Boolean get() = addedCount > 0 || updatedCount > 0
}

object GitHubTrackedItemsImportApplier {
    fun apply(
        payload: GitHubTrackedItemsImportPayload,
        onRefreshNeeded: () -> Unit,
        existingItems: List<GitHubTrackedApp> = GitHubTrackStore.load(),
    ): GitHubTrackedItemsImportApplyResult {
        if (payload.items.isEmpty()) {
            return GitHubTrackedItemsImportApplyResult(
                invalidCount = payload.invalidCount,
                duplicateCount = payload.duplicateCount,
                mergedItems = existingItems,
            )
        }
        val nowMillis = System.currentTimeMillis()
        val mergedItems = existingItems.toMutableList()
        val indexById = mergedItems.withIndex().associate { it.value.id to it.index }.toMutableMap()
        val trackedAddedAt = GitHubTrackStore.loadTrackedAddedAtById().toMutableMap()
        val trackedModifiedAt = GitHubTrackStore.loadTrackedModifiedAtById().toMutableMap()
        val (checkCache, refreshTimestamp) = GitHubTrackStore.loadCheckCache()
        val nextCheckCache = checkCache.toMutableMap()
        val changedIds = linkedSetOf<String>()
        val touchedIds = linkedSetOf<String>()
        var added = 0
        var updated = 0
        var unchanged = 0

        payload.items.forEach { item ->
            val existingIndex = indexById[item.id]
            when {
                existingIndex == null -> {
                    mergedItems += item
                    indexById[item.id] = mergedItems.lastIndex
                    trackedAddedAt.putIfAbsent(item.id, nowMillis)
                    trackedModifiedAt[item.id] = nowMillis
                    touchedIds += item.id
                    changedIds += item.id
                    added += 1
                }

                else -> {
                    val existingItem = mergedItems[existingIndex]
                    val mergedItem = item.withTrackedLocalAppTypeFallback(existingItem)
                    if (existingItem == mergedItem) {
                        unchanged += 1
                    } else {
                        mergedItems[existingIndex] = mergedItem
                        trackedAddedAt.putIfAbsent(item.id, nowMillis)
                        trackedModifiedAt[item.id] = nowMillis
                        touchedIds += item.id
                        if (!existingItem.hasSameGitHubTrackingConfigIgnoringLocalAppType(mergedItem)) {
                            nextCheckCache.remove(item.id)
                            changedIds += item.id
                        }
                        updated += 1
                    }
                }
            }
        }

        if (added > 0 || updated > 0) {
            GitHubTrackStore.save(mergedItems)
            GitHubTrackStore.saveTrackedAddedAtById(trackedAddedAt)
            GitHubTrackStore.saveTrackedModifiedAtById(trackedModifiedAt)
            if (nextCheckCache.size != checkCache.size) {
                GitHubTrackStore.saveCheckCache(nextCheckCache, refreshTimestamp)
            }
            GitHubReleaseAssetCacheStore.clearAll()
            GitHubStarImportApkVerificationCacheStore.clearAll()
            changedIds.forEach { id ->
                GitHubTrackStoreSignals.requestTrackRefresh(
                    trackId = id,
                    notifyChangeSignal = false,
                )
            }
            GitHubTrackStoreSignals.notifyChanged()
            onRefreshNeeded()
        }

        return GitHubTrackedItemsImportApplyResult(
            addedCount = added,
            updatedCount = updated,
            unchangedCount = unchanged,
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount,
            mergedItems = mergedItems,
            touchedTrackIds = touchedIds,
            refreshTrackIds = changedIds,
        )
    }

    private fun GitHubTrackedApp.withTrackedLocalAppTypeFallback(
        existingItem: GitHubTrackedApp,
    ): GitHubTrackedApp {
        return if (localAppType == GitHubTrackedLocalAppType.Unknown) {
            copy(localAppType = existingItem.localAppType)
        } else {
            this
        }
    }
}
