package os.kei.ui.page.main.github.page.action

import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.page.GitHubApkInfoDetailRequest
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.statusActionUrl

internal class GitHubAssetActions(
    private val env: GitHubPageActionEnvironment,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val clock get() = env.clock
    private val apkInfoRepository = GitHubApkInfoRepository()
    private val cacheActions = GitHubAssetCacheActions(env)
    private val transferActions = GitHubAssetTransferActions(env)
    private val releaseNotesActions = GitHubReleaseNotesActions(env, apkInfoRepository)
    private val managedInstallRunner = GitHubPageManagedInstallRunner(env, apkInfoRepository)
    private val managedInstallConfirmActions =
        GitHubManagedInstallConfirmActions(env, managedInstallRunner)

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
        loadApkInfo(asset = asset, forceRefresh = forceRefresh)
    }

    fun openManagedInstallConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) {
        managedInstallConfirmActions.openConfirm(
            item = item,
            asset = asset,
            manifestInfo = state.apkInfoResults[asset.githubApkInfoKey()],
            onLoadApkInfo = { loadApkInfo(asset = it, forceRefresh = false) },
        )
    }

    private fun loadApkInfo(
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean,
    ) {
        val key = asset.githubApkInfoKey()
        if (forceRefresh) {
            state.apkInfoResults.remove(key)
            state.apkInfoInstalledResults.remove(key)
            state.apkInfoErrors.remove(key)
        }
        state.apkInfoResults[key]?.let { cachedInfo ->
            if (!state.apkInfoInstalledResults.containsKey(key)) {
                state.apkInfoInstalledResults[key] =
                    loadInstalledPackageInfo(cachedInfo.packageName)
            }
            state.managedInstallConfirmRequest
                ?.takeIf { it.asset.githubApkInfoKey() == key }
                ?.let { request -> managedInstallConfirmActions.notifyConfirm(request.item, asset, cachedInfo) }
            return
        }
        if (state.apkInfoLoading[key] == true) return
        state.apkInfoLoading[key] = true
        state.apkInfoErrors.remove(key)
        scope.launch {
            val result =
                withContext(AppDispatchers.githubNetwork) {
                    apkInfoRepository.inspect(
                        asset = asset,
                        lookupConfig = state.lookupConfig,
                        forceRefresh = forceRefresh,
                    )
                }
            state.apkInfoLoading[key] = false
            result
                .onSuccess { info ->
                    state.apkInfoResults[key] = info
                    state.apkInfoInstalledResults[key] = loadInstalledPackageInfo(info.packageName)
                    state.managedInstallConfirmRequest
                        ?.takeIf { it.asset.githubApkInfoKey() == key }
                        ?.let { request -> managedInstallConfirmActions.notifyConfirm(request.item, asset, info) }
                }.onFailure { error ->
                    state.apkInfoErrors[key] = error.message
                        ?: context.getString(R.string.github_apk_info_error_failed)
                }
        }
    }

    private fun loadInstalledPackageInfo(packageName: String): GitHubInstalledPackageInfo? {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return null
        val packageInfo =
            runCatching {
                context.packageManager.getPackageInfo(
                    normalizedPackageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            }.getOrNull() ?: return null
        val applicationInfo = packageInfo.applicationInfo
        return GitHubInstalledPackageInfo(
            packageName = normalizedPackageName,
            appLabel = applicationInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
            versionName = packageInfo.versionName?.trim().orEmpty(),
            versionCode = packageInfo.longVersionCode,
            minSdk = applicationInfo?.minSdkVersion ?: -1,
            targetSdk = applicationInfo?.targetSdkVersion ?: -1,
            apkSizeBytes = applicationInfo.installedApkSizeBytes(),
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
        val alwaysLatestRelease = item.alwaysShowLatestReleaseDownloadButton
        val target =
            itemState.apkAssetTarget(
                owner = item.owner,
                repo = item.repo,
                context = context,
                alwaysLatestRelease = alwaysLatestRelease,
                allowLatestReleaseFallback = allowLatestReleaseFallback,
            )
        if (target == null) {
            if (!openFallbackTarget) {
                env.toast(R.string.github_toast_no_apk_to_load)
                return
            }
            val fallbackUrl =
                if (alwaysLatestRelease) {
                    repository.buildReleaseUrl(item.owner, item.repo)
                } else {
                    itemState.statusActionUrl(item.owner, item.repo)
                }
            if (fallbackUrl.isNotBlank()) {
                openExternalUrl(fallbackUrl)
            } else {
                env.toast(R.string.github_toast_no_apk_to_load)
            }
            return
        }

        state.apkAssetIncludeAll[item.id] = includeAllAssets
        val lookupConfig = state.lookupConfig.forTrackedItem(item)
        val loadingState =
            if (showAssetPanelLoading) state.apkAssetLoading else state.releaseNotesLoading
        val errorState =
            if (showAssetPanelLoading) state.apkAssetErrors else state.releaseNotesErrors

        val cachedBundle = state.apkAssetBundles[item.id]
        if (
            toggleOnlyWhenCached &&
            cachedBundle != null &&
            isRuntimeCacheFresh(state.apkAssetBundleLoadedAtMs[item.id]) &&
            state.matchesAssetSourceSignature(cachedBundle, lookupConfig) &&
            cachedBundle.tagName.equals(target.rawTag, ignoreCase = true) &&
            cachedBundle.showingAllAssets == includeAllAssets &&
            (!requireReleaseNotesBody || cachedBundle.releaseNotesBody.isNotBlank())
        ) {
            if (expandPanelOnLoad) {
                state.apkAssetExpanded[item.id] = !(state.apkAssetExpanded[item.id] ?: false)
            }
            loadingState[item.id] = false
            errorState.remove(item.id)
            return
        }

        if (expandPanelOnLoad) {
            state.apkAssetExpanded[item.id] = true
        }
        loadingState[item.id] = true
        errorState.remove(item.id)
        scope.launch {
            val preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
            val refreshIntervalHours = repository.loadRefreshIntervalHours()
            val assetCacheKey =
                repository.buildAssetCacheKey(
                    owner = item.owner,
                    repo = item.repo,
                    rawTag = target.rawTag,
                    releaseUrl = target.releaseUrl,
                    preferHtml = preferHtml,
                    aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                    includeAllAssets = includeAllAssets,
                    hasApiToken = lookupConfig.apiToken.isNotBlank(),
                )
            val persistedBundle =
                if (bypassPersistedCache) {
                    null
                } else {
                    repository.loadAssetBundle(
                        cacheKey = assetCacheKey,
                        refreshIntervalHours = refreshIntervalHours,
                    )
                }
            if (
                persistedBundle != null &&
                state.matchesAssetSourceSignature(persistedBundle, lookupConfig) &&
                (!requireReleaseNotesBody || persistedBundle.releaseNotesBody.isNotBlank())
            ) {
                loadingState[item.id] = false
                state.apkAssetBundles[item.id] = persistedBundle
                state.apkAssetBundleLoadedAtMs[item.id] = clock.nowMs()
                if (expandPanelOnLoad) {
                    state.apkAssetErrors[item.id] =
                        buildEmptyAssetMessage(
                            label = target.label,
                            includeAllAssets = includeAllAssets,
                            assetCount = persistedBundle.assets.size,
                        )
                }
                return@launch
            } else if (persistedBundle != null) {
                repository.clearAssetCache(assetCacheKey)
            }
            val result =
                repository.fetchApkAssets(
                    owner = item.owner,
                    repo = item.repo,
                    rawTag = target.rawTag,
                    releaseUrl = target.releaseUrl,
                    preferHtml = preferHtml,
                    aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                    includeAllAssets = includeAllAssets,
                    apiToken = lookupConfig.apiToken,
                )
            loadingState[item.id] = false
            result
                .onSuccess { bundle ->
                    val persistedBundle =
                        bundle.copy(
                            sourceConfigSignature = state.buildAssetSourceSignature(lookupConfig),
                        )
                    state.apkAssetBundles[item.id] = persistedBundle
                    state.apkAssetBundleLoadedAtMs[item.id] = clock.nowMs()
                    scope.launch {
                        repository.saveAssetBundle(
                            cacheKey = assetCacheKey,
                            bundle = persistedBundle,
                        )
                    }
                    if (expandPanelOnLoad) {
                        state.apkAssetErrors[item.id] =
                            buildEmptyAssetMessage(
                                label = target.label,
                                includeAllAssets = includeAllAssets,
                                assetCount = persistedBundle.assets.size,
                            )
                    }
                }.onFailure { error ->
                    errorState[item.id] = error.message
                        ?: context.getString(R.string.github_error_load_apk_assets_failed)
                }
        }
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

    private fun isRuntimeCacheFresh(
        loadedAtMs: Long?,
        nowMs: Long = clock.nowMs(),
    ): Boolean {
        val loadedAt = loadedAtMs ?: return false
        if (loadedAt <= 0L) return false
        val intervalMs = state.refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        return (nowMs - loadedAt).coerceAtLeast(0L) < intervalMs
    }

    private fun buildEmptyAssetMessage(
        label: String,
        includeAllAssets: Boolean,
        assetCount: Int,
    ): String {
        if (assetCount > 0) return ""
        return if (includeAllAssets) {
            context.getString(
                R.string.github_msg_assets_no_downloadable_except_source,
                label,
            )
        } else {
            context.getString(
                R.string.github_msg_assets_no_downloadable,
                label,
            )
        }
    }
}
