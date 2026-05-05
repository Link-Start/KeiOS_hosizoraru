package os.kei.ui.page.main.github.importer

import android.content.Context
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubRepositoryImportCandidate

internal suspend fun applyStarImport(
    context: Context,
    candidates: List<GitHubRepositoryImportCandidate>
): Int {
    if (candidates.isEmpty()) return 0
    val selectedItems = candidates.map { it.trackedApp }
    val existing = GitHubTrackStore.load()
    val merged = existing.toMutableList()
    val indexById = merged.withIndex().associate { it.value.id to it.index }.toMutableMap()
    var changedCount = 0
    selectedItems.forEach { item ->
        val existingIndex = indexById[item.id]
        if (existingIndex == null) {
            merged += item
            indexById[item.id] = merged.lastIndex
            changedCount += 1
        } else if (merged[existingIndex] != item) {
            merged[existingIndex] = item
            changedCount += 1
        }
    }
    if (changedCount == 0) return 0
    GitHubTrackStore.save(merged)
    selectedItems.forEach { item ->
        GitHubTrackStoreSignals.requestTrackRefresh(
            trackId = item.id,
            notifyChangeSignal = false
        )
    }
    GitHubTrackStoreSignals.notifyChanged()
    AppBackgroundScheduler.scheduleGitHubRefresh(context)
    return changedCount
}
