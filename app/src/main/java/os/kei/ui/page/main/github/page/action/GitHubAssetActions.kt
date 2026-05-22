package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.launch
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubApkInfoDetailRequest
import os.kei.ui.page.main.github.page.githubApkInfoKey

internal class GitHubAssetActions(
    private val env: GitHubPageActionEnvironment,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val apkInfoRepository = GitHubApkInfoRepository()
    private val cacheActions = GitHubAssetCacheActions(env)
    private val transferActions = GitHubAssetTransferActions(env)
    private val releaseNotesActions = GitHubReleaseNotesActions(env, apkInfoRepository)
    private val managedInstallRunner = GitHubPageManagedInstallRunner(env, apkInfoRepository)
    private val managedInstallConfirmActions =
        GitHubManagedInstallConfirmActions(env, managedInstallRunner)
    private val apkInfoActions =
        GitHubApkInfoActions(
            env = env,
            apkInfoRepository = apkInfoRepository,
            managedInstallConfirmActions = managedInstallConfirmActions,
        )
    private val assetPanelActions =
        GitHubAssetPanelActions(
            env = env,
            openExternalUrl = ::openExternalUrl,
        )

    fun dispose() {
        managedInstallConfirmActions.dispose()
    }

    fun openExternalUrl(
        url: String,
        failureMessage: String = env.openLinkFailureMessage,
    ) {
        if (!SafeExternalIntents.startBrowsableUrl(context, url)) {
            env.toast(failureMessage)
        }
    }

    fun shareApkLink(asset: GitHubReleaseAssetFile) {
        scope.launch {
            transferActions.shareApkLink(asset)
        }
    }

    fun openApkInDownloader(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) {
        scope.launch {
            transferActions.openApkInDownloader(asset)
        }
    }

    fun installApkWithKeiOs(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) {
        managedInstallConfirmActions.installOrFallback(
            item = item,
            asset = asset,
            onOpenConfirm = ::openManagedInstallConfirm,
            onFallbackDownload = transferActions::openApkInDownloader,
        )
    }

    fun confirmManagedInstall() {
        managedInstallConfirmActions.confirmManagedInstall()
    }

    fun dismissManagedInstallConfirm() {
        managedInstallConfirmActions.dismissManagedInstallConfirm()
    }

    fun openApkInfo(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean = false,
    ) {
        state.apkInfoDetailRequest =
            GitHubApkInfoDetailRequest(
                item = item,
                asset = asset,
            )
        apkInfoActions.loadApkInfo(asset = asset, forceRefresh = forceRefresh)
    }

    fun openManagedInstallConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) {
        managedInstallConfirmActions.openConfirm(
            item = item,
            asset = asset,
            manifestInfo = state.apkInfoResults[asset.githubApkInfoKey()],
            onLoadApkInfo = { apkInfoActions.loadApkInfo(asset = it, forceRefresh = false) },
        )
    }

    suspend fun sendAssetToConfiguredChannel(asset: GitHubReleaseAssetFile): Boolean = transferActions.sendAssetToConfiguredChannel(asset)

    fun clearApkAssetUiState(itemId: String) {
        cacheActions.clearApkAssetUiState(itemId)
    }

    fun clearApkAssetRuntimeState(itemId: String) {
        cacheActions.clearApkAssetRuntimeState(itemId)
    }

    fun clearApkAssetCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = false,
    ) {
        cacheActions.clearApkAssetCache(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
        )
    }

    suspend fun clearApkAssetCacheNow(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = false,
    ) {
        cacheActions.clearApkAssetCacheNow(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
        )
    }

    suspend fun clearApkAssetCachesForTargetsNow(
        targets: List<Pair<GitHubTrackedApp, VersionCheckUi>>,
        allowLatestReleaseFallback: Boolean = false,
    ) {
        cacheActions.clearApkAssetCachesForTargetsNow(
            targets = targets,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
        )
    }

    fun clearApkAssetStateAndCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = true,
    ) {
        cacheActions.clearApkAssetStateAndCache(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
        )
    }

    suspend fun clearApkAssetStateAndCacheNow(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = true,
    ) {
        cacheActions.clearApkAssetStateAndCacheNow(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
        )
    }

    suspend fun clearAllApkAssetStateAndCacheNow() {
        cacheActions.clearAllApkAssetStateAndCacheNow()
    }

    suspend fun clearApkAssetStatesAndCachesNow(
        targets: List<Pair<GitHubTrackedApp, VersionCheckUi>>,
        clearItemIds: Set<String> = targets.map { (item, _) -> item.id }.toSet(),
        allowLatestReleaseFallback: Boolean = true,
    ) {
        cacheActions.clearApkAssetStatesAndCachesNow(
            targets = targets,
            clearItemIds = clearItemIds,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
        )
    }

    fun loadApkAssets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        toggleOnlyWhenCached: Boolean = true,
        includeAllAssets: Boolean = false,
        allowLatestReleaseFallback: Boolean = false,
        expandPanelOnLoad: Boolean = true,
        openFallbackTarget: Boolean = true,
        showAssetPanelLoading: Boolean = true,
        requireReleaseNotesBody: Boolean = false,
        bypassPersistedCache: Boolean = false,
    ) {
        assetPanelActions.loadApkAssets(
            item = item,
            itemState = itemState,
            toggleOnlyWhenCached = toggleOnlyWhenCached,
            includeAllAssets = includeAllAssets,
            allowLatestReleaseFallback = allowLatestReleaseFallback,
            expandPanelOnLoad = expandPanelOnLoad,
            openFallbackTarget = openFallbackTarget,
            showAssetPanelLoading = showAssetPanelLoading,
            requireReleaseNotesBody = requireReleaseNotesBody,
            bypassPersistedCache = bypassPersistedCache,
        )
    }

    fun loadReleaseNotes(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        clearCache: Boolean,
    ) {
        releaseNotesActions.loadReleaseNotes(
            item = item,
            itemState = itemState,
            clearCache = clearCache,
        )
    }

    fun loadReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        forceRefresh: Boolean = false,
    ) {
        releaseNotesActions.loadReleaseNotesTargets(
            item = item,
            itemState = itemState,
            forceRefresh = forceRefresh,
        )
    }

    fun selectReleaseNotesTarget(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
    ) {
        releaseNotesActions.selectReleaseNotesTarget(item = item, target = target)
    }
}
