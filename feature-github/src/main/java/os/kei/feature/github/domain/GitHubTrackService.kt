package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.feature.github.data.local.GitHubPendingShareImportAttachCandidateRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportPreviewRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubShareImportResultRecord
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp

data class GitHubActiveShareImportFlowRecords(
    val preview: GitHubPendingShareImportPreviewRecord?,
    val pendingTrack: GitHubPendingShareImportTrackRecord?,
    val attachCandidate: GitHubPendingShareImportAttachCandidateRecord?,
    val result: GitHubShareImportResultRecord?,
)

data class GitHubBackgroundScheduleSnapshot(
    val trackSnapshot: GitHubTrackSnapshot,
    val actionsRecommendedRunsByTrackId: Map<String, GitHubActionsRecommendedRunSnapshot>,
)

class GitHubTrackService(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
) {
    suspend fun loadTrackSnapshot(): GitHubTrackSnapshot =
        withContext(ioDispatcher) {
            GitHubTrackStore.loadSnapshot()
        }

    fun loadTrackSnapshotBlocking(): GitHubTrackSnapshot =
        GitHubTrackStore.loadSnapshot()

    suspend fun loadBackgroundScheduleSnapshot(): GitHubBackgroundScheduleSnapshot =
        withContext(ioDispatcher) {
            GitHubBackgroundScheduleSnapshot(
                trackSnapshot = GitHubTrackStore.loadSnapshot(),
                actionsRecommendedRunsByTrackId = GitHubActionsRecommendedRunStore.loadAll(),
            )
        }

    fun loadBackgroundScheduleSnapshotBlocking(): GitHubBackgroundScheduleSnapshot =
        GitHubBackgroundScheduleSnapshot(
            trackSnapshot = GitHubTrackStore.loadSnapshot(),
            actionsRecommendedRunsByTrackId = GitHubActionsRecommendedRunStore.loadAll(),
        )

    suspend fun loadLookupConfig(): GitHubLookupConfig =
        withContext(ioDispatcher) {
            GitHubTrackStore.loadLookupConfig()
        }

    fun loadLookupConfigBlocking(): GitHubLookupConfig =
        GitHubTrackStore.loadLookupConfig()

    suspend fun loadApiToken(): String =
        withContext(ioDispatcher) {
            GitHubTrackStore.loadLookupConfig().apiToken
        }

    suspend fun saveLookupConfig(config: GitHubLookupConfig) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveLookupConfig(config)
        }
    }

    suspend fun loadRefreshIntervalHours(): Int =
        withContext(ioDispatcher) {
            GitHubTrackStore.loadRefreshIntervalHours()
        }

    suspend fun loadAppPickerPreferences(): GitHubAppPickerPreferences =
        withContext(ioDispatcher) {
            GitHubTrackStore.loadAppPickerPreferences()
        }

    suspend fun saveAppPickerPreferences(preferences: GitHubAppPickerPreferences) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveAppPickerPreferences(preferences)
        }
    }

    suspend fun saveRefreshIntervalHours(hours: Int) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveRefreshIntervalHours(hours)
        }
    }

    suspend fun saveTrackedItems(
        items: List<GitHubTrackedApp>,
        trackedFirstInstallAtByPackage: Map<String, Long>,
        trackedAddedAtById: Map<String, Long>,
        trackedModifiedAtById: Map<String, Long>,
        refreshTrackIds: Set<String> = emptySet(),
        emitStoreSignal: Boolean = true,
    ) {
        withContext(ioDispatcher) {
            GitHubTrackStore.save(items)
            GitHubTrackStore.saveTrackedFirstInstallAtByPackage(trackedFirstInstallAtByPackage)
            GitHubTrackStore.saveTrackedAddedAtById(trackedAddedAtById)
            GitHubTrackStore.saveTrackedModifiedAtById(trackedModifiedAtById)
            refreshTrackIds.forEach { trackId ->
                GitHubTrackStoreSignals.requestTrackRefresh(
                    trackId = trackId,
                    notifyChangeSignal = false,
                )
            }
            if (emitStoreSignal || refreshTrackIds.isNotEmpty()) {
                GitHubTrackStoreSignals.notifyChanged()
            }
        }
    }

    suspend fun saveCheckCache(
        states: Map<String, GitHubCheckCacheEntry>,
        refreshTimestamp: Long,
    ) {
        withContext(ioDispatcher) {
            GitHubTrackStore.saveCheckCache(states, refreshTimestamp)
            GitHubTrackStoreSignals.notifyChanged(refreshTimestamp)
        }
    }

    suspend fun clearCheckCache() {
        withContext(ioDispatcher) {
            GitHubTrackStore.clearCheckCache()
        }
    }

    fun clearCheckCacheBlocking(notifyChange: Boolean = false) {
        GitHubTrackStore.clearCheckCache()
        if (notifyChange) {
            GitHubTrackStoreSignals.notifyChanged()
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

    suspend fun saveShareImportResult(record: GitHubShareImportResultRecord) {
        withContext(ioDispatcher) {
            GitHubShareImportFlowStore.saveActiveResult(record)
            GitHubTrackStoreSignals.notifyChanged()
        }
    }

    suspend fun loadActiveShareImportFlow(): GitHubActiveShareImportFlowRecords =
        withContext(ioDispatcher) {
            GitHubActiveShareImportFlowRecords(
                preview = GitHubShareImportFlowStore.loadActivePreview(),
                pendingTrack = GitHubTrackStore.loadPendingShareImportTrack(),
                attachCandidate = GitHubShareImportFlowStore.loadActiveAttachCandidate(),
                result = GitHubShareImportFlowStore.loadActiveResult(),
            )
        }

    fun currentTrackStoreSignalVersion(): Long =
        GitHubTrackStoreSignals.version.value

    fun trackStoreSignalVersions(): StateFlow<Long> =
        GitHubTrackStoreSignals.version

    suspend fun consumeTrackRefreshRequests(validTrackIds: Set<String>): Set<String> =
        withContext(ioDispatcher) {
            GitHubTrackStoreSignals.consumeTrackRefreshRequests(validTrackIds)
        }
}
