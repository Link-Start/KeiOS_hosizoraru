package os.kei.feature.github.domain

import android.content.Context
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.StarImportApplyResult

private const val STAR_IMPORT_IMMEDIATE_REFRESH_REQUEST_LIMIT = 8

internal object GitHubStarImportApplier {
    suspend fun apply(
        context: Context,
        candidates: List<GitHubRepositoryImportCandidate>
    ): StarImportApplyResult {
        if (candidates.isEmpty()) return StarImportApplyResult()
        val merge = GitHubStarImportMerger.merge(
            existingItems = GitHubTrackStore.load(),
            importedItems = candidates.map { it.trackedApp }
        )
        val result = merge.result
        if (!result.hasChanges) return result
        GitHubTrackStore.save(merge.items)
        updateTrackedAddedAt(result)
        result.affectedTrackIds.take(STAR_IMPORT_IMMEDIATE_REFRESH_REQUEST_LIMIT)
            .forEach { trackId ->
                GitHubTrackStoreSignals.requestTrackRefresh(
                    trackId = trackId,
                    notifyChangeSignal = false
                )
            }
        GitHubTrackStoreSignals.notifyChanged()
        AppBackgroundScheduler.scheduleGitHubRefresh(context)
        return result
    }

    private fun updateTrackedAddedAt(result: StarImportApplyResult) {
        val nowMillis = System.currentTimeMillis()
        val addedAt = GitHubTrackStore.loadTrackedAddedAtById().toMutableMap()
        result.removedTrackIds.forEach(addedAt::remove)
        result.affectedTrackIds.forEach { trackId ->
            addedAt.putIfAbsent(trackId, nowMillis)
        }
        GitHubTrackStore.saveTrackedAddedAtById(addedAt)
    }
}
