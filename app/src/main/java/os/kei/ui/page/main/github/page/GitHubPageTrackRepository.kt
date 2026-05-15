package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.github.share.toRecord
import os.kei.ui.page.main.github.share.toShareImportAttachCandidate
import os.kei.ui.page.main.github.share.toShareImportPreview
import os.kei.ui.page.main.github.share.toShareImportResult
import os.kei.ui.page.main.github.share.toShareImportTrack

internal class GitHubPageTrackRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun loadTrackSnapshot(): GitHubTrackSnapshot {
        return withContext(ioDispatcher) {
            GitHubTrackStore.loadSnapshot()
        }
    }

    suspend fun loadLookupConfig(): GitHubLookupConfig {
        return withContext(ioDispatcher) {
            GitHubTrackStore.loadLookupConfig()
        }
    }

    suspend fun saveLookupConfig(config: GitHubLookupConfig) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveLookupConfig(config)
        }
    }

    suspend fun loadRefreshIntervalHours(): Int {
        return withContext(ioDispatcher) {
            GitHubTrackStore.loadRefreshIntervalHours()
        }
    }

    suspend fun saveRefreshIntervalHours(hours: Int) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveRefreshIntervalHours(hours)
        }
    }

    suspend fun saveTrackedItems(
        context: Context,
        items: List<GitHubTrackedApp>,
        trackedFirstInstallAtByPackage: Map<String, Long>,
        trackedAddedAtById: Map<String, Long>,
        trackedModifiedAtById: Map<String, Long>,
        refreshTrackIds: Set<String> = emptySet(),
        emitStoreSignal: Boolean = true
    ) {
        withContext(ioDispatcher) {
            GitHubTrackStore.save(items)
            GitHubTrackStore.saveTrackedFirstInstallAtByPackage(trackedFirstInstallAtByPackage)
            GitHubTrackStore.saveTrackedAddedAtById(trackedAddedAtById)
            GitHubTrackStore.saveTrackedModifiedAtById(trackedModifiedAtById)
            refreshTrackIds.forEach { trackId ->
                GitHubTrackStoreSignals.requestTrackRefresh(
                    trackId = trackId,
                    notifyChangeSignal = false
                )
            }
            if (emitStoreSignal || refreshTrackIds.isNotEmpty()) {
                GitHubTrackStoreSignals.notifyChanged()
            }
        }
        if (emitStoreSignal || refreshTrackIds.isNotEmpty()) {
            AppBackgroundScheduler.scheduleGitHubRefresh(context)
        }
    }

    suspend fun saveCheckCache(
        states: Map<String, GitHubCheckCacheEntry>,
        refreshTimestamp: Long
    ) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveCheckCache(states, refreshTimestamp)
        }
    }

    suspend fun clearCheckCache() {
        withContext(ioDispatcher) {
            GitHubTrackStore.clearCheckCache()
        }
    }

    suspend fun clearPendingShareImportTrack() {
        withContext(ioDispatcher) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubTrackStoreSignals.notifyChanged()
        }
    }

    suspend fun clearActiveShareImportFlow() {
        withContext(ioDispatcher) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubTrackStoreSignals.notifyChanged()
        }
    }

    suspend fun clearShareImportResult() {
        withContext(ioDispatcher) {
            GitHubShareImportFlowStore.clearActiveResult()
            GitHubTrackStoreSignals.notifyChanged()
        }
    }

    suspend fun saveShareImportResult(result: GitHubShareImportResult) {
        withContext(ioDispatcher) {
            GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
            GitHubTrackStoreSignals.notifyChanged()
        }
    }

    suspend fun loadActiveShareImportFlow(): GitHubActiveShareImportFlow {
        return withContext(ioDispatcher) {
            GitHubActiveShareImportFlow(
                preview = GitHubShareImportFlowStore
                    .loadActivePreview()
                    ?.toShareImportPreview(),
                pendingTrack = GitHubTrackStore
                    .loadPendingShareImportTrack()
                    ?.toShareImportTrack(),
                attachCandidate = GitHubShareImportFlowStore
                    .loadActiveAttachCandidate()
                    ?.toShareImportAttachCandidate(),
                result = GitHubShareImportFlowStore
                    .loadActiveResult()
                    ?.toShareImportResult()
            )
        }
    }

    fun scheduleGitHubRefresh(context: Context) {
        AppBackgroundScheduler.scheduleGitHubRefresh(context)
    }

    fun currentTrackStoreSignalVersion(): Long {
        return GitHubTrackStoreSignals.version.value
    }

    fun trackStoreSignalVersions(): StateFlow<Long> {
        return GitHubTrackStoreSignals.version
    }

    suspend fun consumeTrackRefreshRequests(validTrackIds: Set<String>): Set<String> {
        return withContext(ioDispatcher) {
            GitHubTrackStoreSignals.consumeTrackRefreshRequests(validTrackIds)
        }
    }
}
