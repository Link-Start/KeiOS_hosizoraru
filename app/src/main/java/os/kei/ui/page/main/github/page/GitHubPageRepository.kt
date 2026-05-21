package os.kei.ui.page.main.github.page

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanResult
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerDerivedState
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerInput
import os.kei.ui.page.main.github.picker.filterAndSortGitHubTrackAppCandidates
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.GitHubShareImportResult

@Immutable
internal data class GitHubTrackEditorDraft(
    val sourceMode: GitHubTrackedSourceMode,
    val repoUrl: String,
    val packageName: String,
    val preferPreRelease: Boolean,
    val alwaysShowLatestReleaseDownloadButton: Boolean,
    val checkActionsUpdates: Boolean,
    val updateIntervalMode: GitHubTrackedUpdateIntervalMode,
    val actionsUpdateIntervalMode: GitHubTrackedActionsUpdateIntervalMode,
    val preciseApkVersionMode: GitHubTrackedPreciseApkVersionMode,
    val appList: List<InstalledAppItem>
)

internal sealed interface GitHubTrackEditorResult {
    data class Ready(val item: GitHubTrackedApp) : GitHubTrackEditorResult
    data object InvalidRepository : GitHubTrackEditorResult
    data object InvalidPackageName : GitHubTrackEditorResult
}

@Immutable
internal data class GitHubOnlineShareTargetInput(
    val shouldResolve: Boolean,
    val appList: List<InstalledAppItem>
)

@Immutable
internal data class GitHubActiveShareImportFlow(
    val preview: GitHubShareImportPreview?,
    val pendingTrack: GitHubPendingShareImportTrack?,
    val attachCandidate: GitHubPendingShareImportAttachCandidate?,
    val result: GitHubShareImportResult?
)

internal class GitHubPageRepository(
    ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
) {
    private val contentStateDeriver = GitHubPageContentStateDeriver(defaultDispatcher)
    private val trackRepository = GitHubPageTrackRepository(ioDispatcher)
    private val installedAppRepository = GitHubPageInstalledAppRepository(
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher
    )
    private val refreshRepository = GitHubPageRefreshRepository(ioDispatcher)
    private val transferRepository = GitHubPageTransferRepository(
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher
    )
    private val discoveryRepository = GitHubPageDiscoveryRepository(
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher
    )
    private val assetBridge = GitHubPageAssetBridge(ioDispatcher)

    suspend fun buildContentState(input: GitHubPageContentInput): GitHubPageContentDerivedState {
        return contentStateDeriver.build(input)
    }

    suspend fun buildAppPickerState(input: GitHubTrackAppPickerInput): GitHubTrackAppPickerDerivedState {
        return withContext(defaultDispatcher) {
            GitHubTrackAppPickerDerivedState(
                filteredApps =
                    filterAndSortGitHubTrackAppCandidates(
                        apps = input.appList,
                        query = input.query,
                        includeUserApps = input.includeUserApps,
                        includeSystemApps = input.includeSystemApps,
                        includeTrackedApps = input.includeTrackedApps,
                        trackedPackageNames = input.trackedPackageNames,
                        pinnedPackageNames = input.pinnedPackageNames,
                        sortMode = input.sortMode,
                        sortDirection = input.sortDirection,
                    ),
                deriving = false,
                input = input,
            )
        }
    }

    suspend fun queryOnlineShareTargets(
        context: Context,
        input: GitHubOnlineShareTargetInput
    ): List<OnlineShareTargetOption> {
        return installedAppRepository.queryOnlineShareTargets(context, input)
    }

    suspend fun queryDownloaders(context: Context): List<DownloaderOption> {
        return installedAppRepository.queryDownloaders(context)
    }

    suspend fun loadTrackSnapshot(): GitHubTrackSnapshot =
        trackRepository.loadTrackSnapshot()

    suspend fun loadLookupConfig(): GitHubLookupConfig =
        trackRepository.loadLookupConfig()

    suspend fun saveLookupConfig(config: GitHubLookupConfig) =
        trackRepository.saveLookupConfig(config)

    suspend fun loadRefreshIntervalHours(): Int =
        trackRepository.loadRefreshIntervalHours()

    suspend fun saveRefreshIntervalHours(hours: Int) =
        trackRepository.saveRefreshIntervalHours(hours)

    suspend fun saveTrackedItems(
        context: Context,
        items: List<GitHubTrackedApp>,
        trackedFirstInstallAtByPackage: Map<String, Long>,
        trackedAddedAtById: Map<String, Long>,
        trackedModifiedAtById: Map<String, Long>,
        refreshTrackIds: Set<String> = emptySet(),
        emitStoreSignal: Boolean = true
    ) = trackRepository.saveTrackedItems(
        context = context,
        items = items,
        trackedFirstInstallAtByPackage = trackedFirstInstallAtByPackage,
        trackedAddedAtById = trackedAddedAtById,
        trackedModifiedAtById = trackedModifiedAtById,
        refreshTrackIds = refreshTrackIds,
        emitStoreSignal = emitStoreSignal
    )

    suspend fun saveCheckCache(
        states: Map<String, GitHubCheckCacheEntry>,
        refreshTimestamp: Long
    ) = trackRepository.saveCheckCache(states, refreshTimestamp)

    suspend fun clearCheckCache() =
        trackRepository.clearCheckCache()

    suspend fun clearPendingShareImportTrack() =
        trackRepository.clearPendingShareImportTrack()

    suspend fun clearActiveShareImportFlow() =
        trackRepository.clearActiveShareImportFlow()

    suspend fun clearShareImportResult() =
        trackRepository.clearShareImportResult()

    suspend fun saveShareImportResult(result: GitHubShareImportResult) =
        trackRepository.saveShareImportResult(result)

    suspend fun loadActiveShareImportFlow(): GitHubActiveShareImportFlow =
        trackRepository.loadActiveShareImportFlow()

    fun scheduleGitHubRefresh(context: Context) =
        trackRepository.scheduleGitHubRefresh(context)

    fun currentTrackStoreSignalVersion(): Long =
        trackRepository.currentTrackStoreSignalVersion()

    fun trackStoreSignalVersions(): StateFlow<Long> =
        trackRepository.trackStoreSignalVersions()

    fun buildAppListPermissionIntent(context: Context): Intent? =
        installedAppRepository.buildAppListPermissionIntent(context)

    suspend fun consumeTrackRefreshRequests(validTrackIds: Set<String>): Set<String> =
        trackRepository.consumeTrackRefreshRequests(validTrackIds)

    suspend fun queryInstalledLaunchableApps(
        context: Context,
        forceRefresh: Boolean,
        includeSystemApps: Boolean = true,
        pinnedSystemPackageNames: Set<String> = emptySet()
    ): List<InstalledAppItem> = installedAppRepository.queryInstalledLaunchableApps(
        context = context,
        forceRefresh = forceRefresh,
        includeSystemApps = includeSystemApps,
        pinnedSystemPackageNames = pinnedSystemPackageNames
    )

    suspend fun preloadAppIcons(
        context: Context,
        packageNames: List<String>
    ) = installedAppRepository.preloadAppIcons(context, packageNames)

    suspend fun localVersionInfoOrNull(
        context: Context,
        packageName: String
    ) = installedAppRepository.localVersionInfoOrNull(context, packageName)

    suspend fun evaluateTrackedApp(
        context: Context,
        item: GitHubTrackedApp,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): VersionCheckUi = refreshRepository.evaluateTrackedApp(
        context = context,
        item = item,
        profilePurposeOverride = profilePurposeOverride,
        forceRefresh = forceRefresh
    )

    suspend fun notifyRefreshProgress(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) = refreshRepository.notifyRefreshProgress(
        context = context,
        current = current,
        total = total,
        preReleaseUpdateCount = preReleaseUpdateCount,
        updatableCount = updatableCount,
        failedCount = failedCount
    )

    suspend fun notifyRefreshCompleted(
        context: Context,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) = refreshRepository.notifyRefreshCompleted(
        context = context,
        total = total,
        preReleaseUpdateCount = preReleaseUpdateCount,
        updatableCount = updatableCount,
        failedCount = failedCount
    )

    suspend fun notifyRefreshCancelled(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) = refreshRepository.notifyRefreshCancelled(
        context = context,
        current = current,
        total = total,
        preReleaseUpdateCount = preReleaseUpdateCount,
        updatableCount = updatableCount,
        failedCount = failedCount
    )

    fun cancelRefreshNotification(context: Context) =
        refreshRepository.cancelRefreshNotification(context)

    suspend fun clearReleaseStrategyCaches() =
        refreshRepository.clearReleaseStrategyCaches()

    suspend fun clearAllAssetCache() =
        assetBridge.clearAllAssetCache()

    suspend fun parseTrackedItemsImport(raw: String): GitHubTrackedItemsImportPayload =
        transferRepository.parseTrackedItemsImport(raw)

    suspend fun buildTrackedItemsImportPreview(
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): GitHubTrackImportPreview = transferRepository.buildTrackedItemsImportPreview(
        payload = payload,
        existingItems = existingItems
    )

    suspend fun buildStrategyBenchmarkTargets(
        items: List<GitHubTrackedApp>
    ): List<GitHubRepoTarget> =
        discoveryRepository.buildStrategyBenchmarkTargets(items)

    suspend fun runStrategyBenchmark(
        targets: List<GitHubRepoTarget>,
        apiToken: String
    ): GitHubStrategyBenchmarkReport =
        discoveryRepository.runStrategyBenchmark(targets, apiToken)

    suspend fun checkCredential(
        apiToken: String
    ): GitHubStrategyLoadTrace<GitHubApiCredentialStatus> =
        discoveryRepository.checkCredential(apiToken)

    suspend fun buildTrackedItem(draft: GitHubTrackEditorDraft): GitHubTrackEditorResult =
        discoveryRepository.buildTrackedItem(draft)

    suspend fun previewStarredRepositoryImport(
        request: GitHubStarredRepositoryImportRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubStarredRepositoryImportPreview> =
        discoveryRepository.previewStarredRepositoryImport(request, existingItems)

    suspend fun searchRepositoriesForApp(
        request: GitHubAppRepositorySearchRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubAppRepositorySearchResult> =
        discoveryRepository.searchRepositoriesForApp(request, existingItems)

    suspend fun scanPackageNameFromLatestStableApk(
        request: GitHubApkPackageNameScanRequest
    ): Result<GitHubApkPackageNameScanResult> =
        discoveryRepository.scanPackageNameFromLatestStableApk(request)

    suspend fun scanPackageNameFromDirectApk(
        repoUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkPackageNameScanResult> =
        discoveryRepository.scanPackageNameFromDirectApk(repoUrl, lookupConfig)

    suspend fun scanRepositoryFromPackage(
        request: GitHubPackageRepositoryScanRequest
    ): Result<GitHubPackageRepositoryScanResult> =
        discoveryRepository.scanRepositoryFromPackage(request)

    fun buildReleaseUrl(owner: String, repo: String): String =
        assetBridge.buildReleaseUrl(owner, repo)

    fun buildAssetCacheKey(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        hasApiToken: Boolean
    ): String = assetBridge.buildAssetCacheKey(
        owner = owner,
        repo = repo,
        rawTag = rawTag,
        releaseUrl = releaseUrl,
        preferHtml = preferHtml,
        aggressiveFiltering = aggressiveFiltering,
        includeAllAssets = includeAllAssets,
        hasApiToken = hasApiToken
    )

    suspend fun loadAssetBundle(
        cacheKey: String,
        refreshIntervalHours: Int
    ): GitHubReleaseAssetBundle? =
        assetBridge.loadAssetBundle(cacheKey, refreshIntervalHours)

    suspend fun saveAssetBundle(
        cacheKey: String,
        bundle: GitHubReleaseAssetBundle
    ) = assetBridge.saveAssetBundle(cacheKey, bundle)

    suspend fun clearAssetCache(cacheKey: String) =
        assetBridge.clearAssetCache(cacheKey)

    suspend fun clearAssetCaches(cacheKeys: List<String>) =
        assetBridge.clearAssetCaches(cacheKeys)

    suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        apiToken: String
    ): Result<GitHubReleaseAssetBundle> = assetBridge.fetchApkAssets(
        owner = owner,
        repo = repo,
        rawTag = rawTag,
        releaseUrl = releaseUrl,
        preferHtml = preferHtml,
        aggressiveFiltering = aggressiveFiltering,
        includeAllAssets = includeAllAssets,
        apiToken = apiToken
    )

    suspend fun fetchReleaseNotesTargets(
        owner: String,
        repo: String,
        apiToken: String
    ): Result<List<GitHubReleaseNotesTarget>> =
        assetBridge.fetchReleaseNotesTargets(owner, repo, apiToken)

    suspend fun resolvePreferredDownloadUrl(
        asset: GitHubReleaseAssetFile,
        useApiAssetUrl: Boolean,
        apiToken: String
    ): String = assetBridge.resolvePreferredDownloadUrl(asset, useApiAssetUrl, apiToken)

    suspend fun buildTrackedItemsExportJson(
        items: List<GitHubTrackedApp>,
        exportedAtMillis: Long
    ): String = transferRepository.buildTrackedItemsExportJson(items, exportedAtMillis)

    suspend fun writeText(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String
    ) = transferRepository.writeText(contentResolver, uri, content)

    suspend fun readText(
        contentResolver: ContentResolver,
        uri: Uri
    ): String = transferRepository.readText(contentResolver, uri)
}
