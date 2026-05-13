package os.kei.ui.page.main.github.page

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.AppIconCache
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.domain.GitHubApkPackageNameScanner
import os.kei.feature.github.domain.GitHubDirectApkReleaseCheckSource
import os.kei.feature.github.domain.GitHubPackageRepositoryResolver
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.domain.GitHubStrategyBenchmarkService
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanResult
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.parseGithubOwnerRepoStrict
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.query.queryDownloaderOptions
import os.kei.ui.page.main.github.query.queryOnlineShareTargetOptions
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.github.share.toRecord
import os.kei.ui.page.main.github.share.toShareImportAttachCandidate
import os.kei.ui.page.main.github.share.toShareImportPreview
import os.kei.ui.page.main.github.share.toShareImportResult
import os.kei.ui.page.main.github.share.toShareImportTrack
import os.kei.ui.page.main.github.state.toUi

@Immutable
internal data class GitHubTrackEditorDraft(
    val sourceMode: GitHubTrackedSourceMode,
    val repoUrl: String,
    val packageName: String,
    val preferPreRelease: Boolean,
    val alwaysShowLatestReleaseDownloadButton: Boolean,
    val checkActionsUpdates: Boolean,
    val preciseApkVersionMode: GitHubTrackedPreciseApkVersionMode,
    val appList: List<InstalledAppItem>
)

internal sealed interface GitHubTrackEditorResult {
    data class Ready(val item: GitHubTrackedApp) : GitHubTrackEditorResult
    data object InvalidRepository : GitHubTrackEditorResult
    data object InvalidPackageName : GitHubTrackEditorResult
}

private data class GitHubTrackEditorSourceIdentity(
    val owner: String,
    val repo: String,
    val fallbackLabel: String
)

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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$""")
    private val contentStateDeriver = GitHubPageContentStateDeriver(defaultDispatcher)
    private val assetBridge = GitHubPageAssetBridge(ioDispatcher)
    private val notificationBridge = GitHubPageRefreshNotificationBridge(ioDispatcher)

    suspend fun buildContentState(input: GitHubPageContentInput): GitHubPageContentDerivedState {
        return contentStateDeriver.build(input)
    }

    suspend fun queryOnlineShareTargets(
        context: Context,
        input: GitHubOnlineShareTargetInput
    ): List<OnlineShareTargetOption> {
        if (!input.shouldResolve) return emptyList()
        return withContext(defaultDispatcher) {
            queryOnlineShareTargetOptions(context, input.appList)
        }
    }

    suspend fun queryDownloaders(context: Context): List<DownloaderOption> {
        return withContext(defaultDispatcher) {
            queryDownloaderOptions(context)
        }
    }

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
        refreshTrackIds: Set<String> = emptySet()
    ) {
        withContext(ioDispatcher) {
            GitHubTrackStore.save(items)
            GitHubTrackStore.saveTrackedFirstInstallAtByPackage(trackedFirstInstallAtByPackage)
            GitHubTrackStore.saveTrackedAddedAtById(trackedAddedAtById)
            refreshTrackIds.forEach { trackId ->
                GitHubTrackStoreSignals.requestTrackRefresh(
                    trackId = trackId,
                    notifyChangeSignal = false
                )
            }
            GitHubTrackStoreSignals.notifyChanged()
        }
        AppBackgroundScheduler.scheduleGitHubRefresh(context)
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

    fun buildAppListPermissionIntent(context: Context): Intent? {
        return GitHubVersionUtils.buildAppListPermissionIntent(context)
    }

    suspend fun consumeTrackRefreshRequests(validTrackIds: Set<String>): Set<String> {
        return withContext(ioDispatcher) {
            GitHubTrackStoreSignals.consumeTrackRefreshRequests(validTrackIds)
        }
    }

    suspend fun queryInstalledLaunchableApps(
        context: Context,
        forceRefresh: Boolean,
        includeSystemApps: Boolean = true,
        pinnedSystemPackageNames: Set<String> = emptySet()
    ): List<InstalledAppItem> {
        return withContext(ioDispatcher) {
            GitHubVersionUtils.queryInstalledLaunchableApps(
                context = context,
                forceRefresh = forceRefresh,
                includeSystemApps = includeSystemApps,
                pinnedSystemPackageNames = pinnedSystemPackageNames
            )
        }
    }

    suspend fun preloadAppIcons(
        context: Context,
        packageNames: List<String>
    ) {
        if (packageNames.isEmpty()) return
        withContext(ioDispatcher) {
            AppIconCache.preload(context, packageNames)
        }
    }

    suspend fun localVersionInfoOrNull(
        context: Context,
        packageName: String
    ): GitHubVersionUtils.LocalVersionInfo? {
        return withContext(ioDispatcher) {
            GitHubVersionUtils.localVersionInfoOrNull(context, packageName)
        }
    }

    suspend fun evaluateTrackedApp(
        context: Context,
        item: GitHubTrackedApp,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): VersionCheckUi {
        return withContext(ioDispatcher) {
            GitHubReleaseCheckService.evaluateTrackedApp(
                context = context,
                item = item,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh
            ).toUi()
        }
    }

    suspend fun notifyRefreshProgress(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) {
        notificationBridge.notifyProgress(
            context = context,
            current = current,
            total = total,
            preReleaseUpdateCount = preReleaseUpdateCount,
            updatableCount = updatableCount,
            failedCount = failedCount
        )
    }

    suspend fun notifyRefreshCompleted(
        context: Context,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) {
        notificationBridge.notifyCompleted(
            context = context,
            total = total,
            preReleaseUpdateCount = preReleaseUpdateCount,
            updatableCount = updatableCount,
            failedCount = failedCount
        )
    }

    suspend fun notifyRefreshCancelled(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) {
        notificationBridge.notifyCancelled(
            context = context,
            current = current,
            total = total,
            preReleaseUpdateCount = preReleaseUpdateCount,
            updatableCount = updatableCount,
            failedCount = failedCount
        )
    }

    fun cancelRefreshNotification(context: Context) {
        notificationBridge.cancel(context)
    }

    suspend fun clearReleaseStrategyCaches() {
        withContext(ioDispatcher) {
            GitHubReleaseStrategyRegistry.clearAllCaches()
        }
    }

    suspend fun clearAllAssetCache() {
        assetBridge.clearAllAssetCache()
    }

    suspend fun parseTrackedItemsImport(raw: String): GitHubTrackedItemsImportPayload {
        return withContext(defaultDispatcher) {
            GitHubTrackStore.parseTrackedItemsImport(raw)
        }
    }

    suspend fun buildTrackedItemsImportPreview(
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): GitHubTrackImportPreview {
        return withContext(defaultDispatcher) {
            val existingItemsById = existingItems.associateBy { it.id }
            var newCount = 0
            var updatedCount = 0
            var unchangedCount = 0
            payload.items.forEach { item ->
                when (val existingItem = existingItemsById[item.id]) {
                    null -> newCount += 1
                    item -> unchangedCount += 1
                    else -> updatedCount += 1
                }
            }
            val optionCounts = GitHubTrackStore.calculateTrackedItemsOptionCounts(payload.items)
            val sourceCounts = GitHubTrackStore.calculateTrackedItemsSourceCounts(payload.items)
            GitHubTrackImportPreview(
                payload = payload,
                fileItemCount = payload.sourceCount,
                validCount = payload.items.size,
                duplicateCount = payload.duplicateCount,
                invalidCount = payload.invalidCount,
                newCount = newCount,
                updatedCount = updatedCount,
                unchangedCount = unchangedCount,
                mergedCount = existingItems.size + newCount,
                githubRepositoryCount = sourceCounts.githubRepositoryCount,
                directApkCount = sourceCounts.directApkCount,
                preferPreReleaseCount = optionCounts.preferPreReleaseCount,
                latestReleaseDownloadCount = optionCounts.latestReleaseDownloadCount,
                actionsUpdateCount = optionCounts.actionsUpdateCount,
                preciseApkVersionOverrideCount = optionCounts.preciseApkVersionOverrideCount
            )
        }
    }

    suspend fun buildStrategyBenchmarkTargets(
        items: List<GitHubTrackedApp>
    ): List<GitHubRepoTarget> {
        return withContext(defaultDispatcher) {
            GitHubStrategyBenchmarkService.buildTargets(items)
        }
    }

    suspend fun runStrategyBenchmark(
        targets: List<GitHubRepoTarget>,
        apiToken: String
    ): GitHubStrategyBenchmarkReport {
        return withContext(ioDispatcher) {
            GitHubStrategyBenchmarkService.compareTargets(
                targets = targets,
                apiToken = apiToken
            )
        }
    }

    suspend fun checkCredential(
        apiToken: String
    ): GitHubStrategyLoadTrace<GitHubApiCredentialStatus> {
        return withContext(ioDispatcher) {
            GitHubApiTokenReleaseStrategy(apiToken).checkCredentialTrace()
        }
    }

    suspend fun buildTrackedItem(draft: GitHubTrackEditorDraft): GitHubTrackEditorResult {
        return withContext(defaultDispatcher) {
            val sourceIdentity = when (draft.sourceMode) {
                GitHubTrackedSourceMode.GitHubRepository -> {
                    val parsed = parseGithubOwnerRepoStrict(draft.repoUrl)
                        ?: return@withContext GitHubTrackEditorResult.InvalidRepository
                    GitHubTrackEditorSourceIdentity(
                        owner = parsed.first,
                        repo = parsed.second,
                        fallbackLabel = "${parsed.first}/${parsed.second}"
                    )
                }

                GitHubTrackedSourceMode.DirectApk -> {
                    val identity = buildDirectApkTrackIdentity(draft.repoUrl)
                        ?: return@withContext GitHubTrackEditorResult.InvalidRepository
                    GitHubTrackEditorSourceIdentity(
                        owner = identity.owner,
                        repo = identity.repo,
                        fallbackLabel = identity.displayName
                    )
                }
            }
            val resolvedPackageName = draft.packageName.trim()
            if (resolvedPackageName.isNotBlank() && !packageNamePattern.matches(resolvedPackageName)) {
                return@withContext GitHubTrackEditorResult.InvalidPackageName
            }
            val matchedInstalledApp = resolvedPackageName
                .takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    draft.appList.firstOrNull { item ->
                        item.packageName.equals(packageName, ignoreCase = true)
                    }
                }
            val resolvedAppLabel = when {
                matchedInstalledApp != null -> matchedInstalledApp.label
                resolvedPackageName.isNotBlank() -> resolvedPackageName
                else -> sourceIdentity.fallbackLabel
            }
            GitHubTrackEditorResult.Ready(
                GitHubTrackedApp(
                    repoUrl = draft.repoUrl.trim(),
                    owner = sourceIdentity.owner,
                    repo = sourceIdentity.repo,
                    packageName = resolvedPackageName,
                    appLabel = resolvedAppLabel,
                    sourceMode = draft.sourceMode,
                    preferPreRelease = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository -> draft.preferPreRelease
                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    alwaysShowLatestReleaseDownloadButton = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository ->
                            draft.alwaysShowLatestReleaseDownloadButton

                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    checkActionsUpdates = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository -> draft.checkActionsUpdates
                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    preciseApkVersionMode = draft.preciseApkVersionMode,
                    localAppType = GitHubTrackedLocalAppType.fromSystemFlag(
                        matchedInstalledApp?.isSystemApp
                    )
                )
            )
        }
    }

    suspend fun previewStarredRepositoryImport(
        request: GitHubStarredRepositoryImportRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubStarredRepositoryImportPreview> {
        return withContext(ioDispatcher) {
            GitHubRepositoryDiscoveryService(
                GitHubRepositoryDiscoveryRepository(apiToken = request.apiToken)
            ).previewStarredRepositoryImport(
                request = request,
                existingItems = existingItems
            )
        }
    }

    suspend fun searchRepositoriesForApp(
        request: GitHubAppRepositorySearchRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubAppRepositorySearchResult> {
        return withContext(ioDispatcher) {
            GitHubRepositoryDiscoveryService(
                GitHubRepositoryDiscoveryRepository(apiToken = request.apiToken)
            ).searchRepositoriesForApp(
                request = request,
                existingItems = existingItems
            )
        }
    }

    suspend fun scanPackageNameFromLatestStableApk(
        request: GitHubApkPackageNameScanRequest
    ): Result<GitHubApkPackageNameScanResult> {
        return withContext(ioDispatcher) {
            GitHubApkPackageNameScanner(
                GitHubApkPackageNameScanRepository()
            ).scan(request)
        }
    }

    suspend fun scanPackageNameFromDirectApk(
        repoUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkPackageNameScanResult> {
        return withContext(ioDispatcher) {
            runCatching {
                val identity = buildDirectApkTrackIdentity(repoUrl)
                    ?: error("invalid direct APK URL")
                val item = GitHubTrackedApp(
                    repoUrl = identity.url,
                    owner = identity.owner,
                    repo = identity.repo,
                    packageName = "",
                    appLabel = identity.displayName,
                    sourceMode = GitHubTrackedSourceMode.DirectApk
                )
                val asset = GitHubDirectApkReleaseCheckSource.buildDirectApkAsset(item)
                    ?: error("invalid direct APK URL")
                val manifest = GitHubApkInfoRepository().inspectAsync(
                    asset = asset,
                    lookupConfig = lookupConfig.copy(
                        selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
                        apiToken = "",
                        checkAllTrackedPreReleases = false,
                        preciseApkVersionEnabled = true
                    ),
                    forceRefresh = true
                ).getOrThrow()
                GitHubApkPackageNameScanResult(
                    owner = identity.owner,
                    repo = identity.repo,
                    releaseTag = manifest.versionName.ifBlank { manifest.versionCode },
                    releaseUrl = identity.url,
                    assetName = manifest.assetName.ifBlank { identity.assetName },
                    packageName = manifest.packageName
                )
            }
        }
    }

    suspend fun scanRepositoryFromPackage(
        request: GitHubPackageRepositoryScanRequest
    ): Result<GitHubPackageRepositoryScanResult> {
        return withContext(ioDispatcher) {
            GitHubPackageRepositoryResolver(
                discoverySource = GitHubRepositoryDiscoveryRepository(
                    apiToken = request.lookupConfig.apiToken
                ),
                packageNameScanner = GitHubApkPackageNameScanner(
                    GitHubApkPackageNameScanRepository()
                )
            ).scanRepositoriesForPackage(request)
        }
    }

    fun buildReleaseUrl(owner: String, repo: String): String {
        return assetBridge.buildReleaseUrl(owner, repo)
    }

    fun buildAssetCacheKey(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        hasApiToken: Boolean
    ): String {
        return assetBridge.buildAssetCacheKey(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets,
            hasApiToken = hasApiToken
        )
    }

    suspend fun loadAssetBundle(
        cacheKey: String,
        refreshIntervalHours: Int
    ): GitHubReleaseAssetBundle? {
        return assetBridge.loadAssetBundle(cacheKey, refreshIntervalHours)
    }

    suspend fun saveAssetBundle(
        cacheKey: String,
        bundle: GitHubReleaseAssetBundle
    ) {
        assetBridge.saveAssetBundle(cacheKey, bundle)
    }

    suspend fun clearAssetCache(cacheKey: String) {
        assetBridge.clearAssetCache(cacheKey)
    }

    suspend fun clearAssetCaches(cacheKeys: List<String>) {
        assetBridge.clearAssetCaches(cacheKeys)
    }

    suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        apiToken: String
    ): Result<GitHubReleaseAssetBundle> {
        return assetBridge.fetchApkAssets(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets,
            apiToken = apiToken
        )
    }

    suspend fun fetchReleaseNotesTargets(
        owner: String,
        repo: String,
        apiToken: String
    ): Result<List<GitHubReleaseNotesTarget>> {
        return assetBridge.fetchReleaseNotesTargets(
            owner = owner,
            repo = repo,
            apiToken = apiToken
        )
    }

    suspend fun resolvePreferredDownloadUrl(
        asset: GitHubReleaseAssetFile,
        useApiAssetUrl: Boolean,
        apiToken: String
    ): String {
        return assetBridge.resolvePreferredDownloadUrl(asset, useApiAssetUrl, apiToken)
    }

    suspend fun buildTrackedItemsExportJson(
        items: List<GitHubTrackedApp>,
        exportedAtMillis: Long
    ): String {
        return withContext(defaultDispatcher) {
            GitHubTrackStore.buildTrackedItemsExportJson(
                items = items,
                exportedAtMillis = exportedAtMillis
            )
        }
    }

    suspend fun writeText(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String
    ) {
        withContext(ioDispatcher) {
            contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                checkNotNull(writer) { "openOutputStream returned null" }
                writer.write(content)
            }
        }
    }

    suspend fun readText(
        contentResolver: ContentResolver,
        uri: Uri
    ): String {
        return withContext(ioDispatcher) {
            contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                checkNotNull(reader) { "openInputStream returned null" }
                reader.readText()
            }
        }
    }

}
