package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.domain.GitHubTrackService
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
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubLocal
) {
    private val trackService = GitHubTrackService(ioDispatcher)

    suspend fun loadTrackSnapshot(): GitHubTrackSnapshot = trackService.loadTrackSnapshot()

    suspend fun loadLookupConfig(): GitHubLookupConfig = trackService.loadLookupConfig()

    suspend fun saveLookupConfig(config: GitHubLookupConfig) = trackService.saveLookupConfig(config)

    suspend fun loadRefreshIntervalHours(): Int = trackService.loadRefreshIntervalHours()

    suspend fun loadAppPickerPreferences(): GitHubAppPickerPreferences = trackService.loadAppPickerPreferences()

    suspend fun saveAppPickerPreferences(preferences: GitHubAppPickerPreferences) =
        trackService.saveAppPickerPreferences(preferences)

    suspend fun saveRefreshIntervalHours(hours: Int) = trackService.saveRefreshIntervalHours(hours)

    suspend fun saveTrackedItems(
        context: Context,
        items: List<GitHubTrackedApp>,
        trackedFirstInstallAtByPackage: Map<String, Long>,
        trackedAddedAtById: Map<String, Long>,
        trackedModifiedAtById: Map<String, Long>,
        refreshTrackIds: Set<String> = emptySet(),
        emitStoreSignal: Boolean = true
    ) {
        trackService.saveTrackedItems(
            items = items,
            trackedFirstInstallAtByPackage = trackedFirstInstallAtByPackage,
            trackedAddedAtById = trackedAddedAtById,
            trackedModifiedAtById = trackedModifiedAtById,
            refreshTrackIds = refreshTrackIds,
            emitStoreSignal = emitStoreSignal,
        )
        if (emitStoreSignal || refreshTrackIds.isNotEmpty()) {
            AppBackgroundScheduler.scheduleGitHubRefresh(context)
        }
    }

    suspend fun saveCheckCache(
        states: Map<String, GitHubCheckCacheEntry>,
        refreshTimestamp: Long
    ) = trackService.saveCheckCache(states, refreshTimestamp)

    suspend fun clearCheckCache() = trackService.clearCheckCache()

    suspend fun clearPendingShareImportTrack() = trackService.clearPendingShareImportTrack()

    suspend fun clearActiveShareImportFlow() = trackService.clearActiveShareImportFlow()

    suspend fun clearShareImportResult() = trackService.clearShareImportResult()

    suspend fun saveShareImportResult(result: GitHubShareImportResult) =
        trackService.saveShareImportResult(result.toRecord())

    suspend fun loadActiveShareImportFlow(): GitHubActiveShareImportFlow {
        val records = trackService.loadActiveShareImportFlow()
        return GitHubActiveShareImportFlow(
            preview = records.preview?.toShareImportPreview(),
            pendingTrack = records.pendingTrack?.toShareImportTrack(),
            attachCandidate = records.attachCandidate?.toShareImportAttachCandidate(),
            result = records.result?.toShareImportResult(),
        )
    }

    fun scheduleGitHubRefresh(context: Context) {
        AppBackgroundScheduler.scheduleGitHubRefresh(context)
    }

    fun currentTrackStoreSignalVersion(): Long = trackService.currentTrackStoreSignalVersion()

    fun trackStoreSignalVersions(): StateFlow<Long> = trackService.trackStoreSignalVersions()

    suspend fun consumeTrackRefreshRequests(validTrackIds: Set<String>): Set<String> =
        trackService.consumeTrackRefreshRequests(validTrackIds)
}
