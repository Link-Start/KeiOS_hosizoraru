package os.kei.ui.page.main.github.page.action

import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.data.remote.isGitHubActionsApkArtifactArchive
import os.kei.feature.github.install.GitHubPageManagedInstallConfirmRegistry
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.page.GitHubApkInfoDetailRequest
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.page.githubManagedInstallKey
import os.kei.ui.page.main.github.statusActionUrl

internal class GitHubAssetActions(
    private val env: GitHubPageActionEnvironment
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val systemDmOption get() = env.systemDmOption
    private val apkInfoRepository = GitHubApkInfoRepository()
    private val cacheActions = GitHubAssetCacheActions(env)
    private val releaseNotesActions = GitHubReleaseNotesActions(env, apkInfoRepository)
    private val managedInstallRunner = GitHubPageManagedInstallRunner(env, apkInfoRepository)
    private var managedInstallConfirmRegistrationToken: Long? = null

    fun dispose() {
        clearManagedInstallConfirmRegistration()
    }

    fun openExternalUrl(
        url: String,
        failureMessage: String = env.openLinkFailureMessage
    ) {
        if (!SafeExternalIntents.startBrowsableUrl(context, url)) {
            env.toast(failureMessage)
        }
    }

    fun shareApkLink(asset: GitHubReleaseAssetFile) {
        scope.launch {
            shareApkLinkInternal(asset)
        }
    }

    fun openApkInDownloader(item: GitHubTrackedApp, asset: GitHubReleaseAssetFile) {
        scope.launch {
            openApkInDownloaderInternal(asset)
        }
    }

    fun installApkWithKeiOs(item: GitHubTrackedApp, asset: GitHubReleaseAssetFile) {
        scope.launch {
            if (shouldInstallWithKeiOs(asset)) {
                openManagedInstallConfirm(item, asset)
            } else {
                openApkInDownloaderInternal(asset)
            }
        }
    }

    fun confirmManagedInstall() {
        val request = consumeManagedInstallConfirmRequest() ?: run {
            env.toast(R.string.github_page_install_confirm_expired)
            return
        }
        launchManagedInstall(request)
    }

    private fun launchManagedInstall(request: GitHubManagedInstallConfirmRequest) {
        scope.launch {
            managedInstallRunner.install(request.item, request.asset)
        }
    }

    fun dismissManagedInstallConfirm() {
        val request = state.managedInstallConfirmRequest
        state.managedInstallConfirmRequest = null
        clearManagedInstallConfirmRegistration()
        if (request != null &&
            state.managedInstallLoading[request.item.githubManagedInstallKey(request.asset)] != true
        ) {
            GitHubShareImportNotificationHelper.cancel(context)
        }
    }

    fun openApkInfo(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean = false
    ) {
        state.apkInfoDetailRequest = GitHubApkInfoDetailRequest(
            item = item,
            asset = asset
        )
        loadApkInfo(asset = asset, forceRefresh = forceRefresh)
    }

    fun openManagedInstallConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile
    ) {
        state.apkInfoDetailRequest = null
        state.managedInstallConfirmRequest = GitHubManagedInstallConfirmRequest(
            item = item,
            asset = asset
        )
        registerManagedInstallConfirmAction()
        notifyManagedInstallConfirm(item, asset, state.apkInfoResults[asset.githubApkInfoKey()])
        loadApkInfo(asset = asset, forceRefresh = false)
    }

    private fun consumeManagedInstallConfirmRequest(): GitHubManagedInstallConfirmRequest? {
        val request = state.managedInstallConfirmRequest ?: return null
        state.managedInstallConfirmRequest = null
        clearManagedInstallConfirmRegistration()
        return request
    }

    private fun registerManagedInstallConfirmAction() {
        clearManagedInstallConfirmRegistration()
        managedInstallConfirmRegistrationToken =
            GitHubPageManagedInstallConfirmRegistry.register {
                withContext(Dispatchers.Main.immediate) {
                    if (!scope.coroutineContext.isActive) return@withContext false
                    val request = consumeManagedInstallConfirmRequest()
                        ?: return@withContext false
                    launchManagedInstall(request)
                    true
                }
            }
    }

    private fun clearManagedInstallConfirmRegistration() {
        managedInstallConfirmRegistrationToken?.let { token ->
            GitHubPageManagedInstallConfirmRegistry.clear(token)
        }
        managedInstallConfirmRegistrationToken = null
    }

    private fun loadApkInfo(
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean
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
                ?.let { request -> notifyManagedInstallConfirm(request.item, asset, cachedInfo) }
            return
        }
        if (state.apkInfoLoading[key] == true) return
        state.apkInfoLoading[key] = true
        state.apkInfoErrors.remove(key)
        scope.launch {
            val result = withContext(AppDispatchers.githubNetwork) {
                apkInfoRepository.inspect(
                    asset = asset,
                    lookupConfig = state.lookupConfig,
                    forceRefresh = forceRefresh
                )
            }
            state.apkInfoLoading[key] = false
            result.onSuccess { info ->
                state.apkInfoResults[key] = info
                state.apkInfoInstalledResults[key] = loadInstalledPackageInfo(info.packageName)
                state.managedInstallConfirmRequest
                    ?.takeIf { it.asset.githubApkInfoKey() == key }
                    ?.let { request -> notifyManagedInstallConfirm(request.item, asset, info) }
            }.onFailure { error ->
                state.apkInfoErrors[key] = error.message
                    ?: context.getString(R.string.github_apk_info_error_failed)
            }
        }
    }

    private fun notifyManagedInstallConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        info: GitHubApkManifestInfo?
    ) {
        GitHubShareImportNotificationHelper.notifyPageInstallConfirm(
            context = context,
            owner = item.owner,
            repo = item.repo,
            releaseTag = "latest",
            assetName = asset.name,
            appLabel = info?.appLabel.orEmpty().ifBlank { item.appLabel },
            packageName = info?.packageName.orEmpty().ifBlank { item.packageName },
            versionName = info?.versionName.orEmpty(),
            targetDisplayName = item.appLabel.ifBlank { assetDisplayName(asset.name) },
            confirmActionEnabled = info != null || asset.isGitHubActionsApkArtifactArchive()
        )
    }

    private fun shouldInstallWithKeiOs(asset: GitHubReleaseAssetFile): Boolean {
        return state.lookupConfig.appManagedShareInstallEnabled &&
                asset.name.endsWith(".apk", ignoreCase = true)
    }

    private fun loadInstalledPackageInfo(packageName: String): GitHubInstalledPackageInfo? {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return null
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(
                normalizedPackageName,
                PackageManager.PackageInfoFlags.of(0)
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
            apkSizeBytes = applicationInfo.installedApkSizeBytes()
        )
    }

    suspend fun sendAssetToConfiguredChannel(asset: GitHubReleaseAssetFile): Boolean {
        return if (state.lookupConfig.onlineShareTargetPackage.isNotBlank()) {
            shareApkLinkInternal(asset)
        } else {
            openApkInDownloaderInternal(asset)
        }
    }

    fun clearApkAssetUiState(itemId: String) {
        cacheActions.clearApkAssetUiState(itemId)
    }

    fun clearApkAssetRuntimeState(itemId: String) {
        cacheActions.clearApkAssetRuntimeState(itemId)
    }

    fun clearApkAssetCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = false
    ) {
        cacheActions.clearApkAssetCache(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    suspend fun clearApkAssetCacheNow(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = false
    ) {
        cacheActions.clearApkAssetCacheNow(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    suspend fun clearApkAssetCachesForTargetsNow(
        targets: List<Pair<GitHubTrackedApp, VersionCheckUi>>,
        allowLatestReleaseFallback: Boolean = false
    ) {
        cacheActions.clearApkAssetCachesForTargetsNow(
            targets = targets,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    fun clearApkAssetStateAndCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = true
    ) {
        cacheActions.clearApkAssetStateAndCache(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    suspend fun clearApkAssetStateAndCacheNow(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = true
    ) {
        cacheActions.clearApkAssetStateAndCacheNow(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    suspend fun clearAllApkAssetStateAndCacheNow() {
        cacheActions.clearAllApkAssetStateAndCacheNow()
    }

    suspend fun clearApkAssetStatesAndCachesNow(
        targets: List<Pair<GitHubTrackedApp, VersionCheckUi>>,
        clearItemIds: Set<String> = targets.map { (item, _) -> item.id }.toSet(),
        allowLatestReleaseFallback: Boolean = true
    ) {
        cacheActions.clearApkAssetStatesAndCachesNow(
            targets = targets,
            clearItemIds = clearItemIds,
            allowLatestReleaseFallback = allowLatestReleaseFallback
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
        bypassPersistedCache: Boolean = false
    ) {
        val alwaysLatestRelease = item.alwaysShowLatestReleaseDownloadButton
        val target = itemState.apkAssetTarget(
            owner = item.owner,
            repo = item.repo,
            context = context,
            alwaysLatestRelease = alwaysLatestRelease,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
        if (target == null) {
            if (!openFallbackTarget) {
                env.toast(R.string.github_toast_no_apk_to_load)
                return
            }
            val fallbackUrl = if (alwaysLatestRelease) {
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
            val assetCacheKey = repository.buildAssetCacheKey(
                owner = item.owner,
                repo = item.repo,
                rawTag = target.rawTag,
                releaseUrl = target.releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                includeAllAssets = includeAllAssets,
                hasApiToken = lookupConfig.apiToken.isNotBlank()
            )
            val persistedBundle = if (bypassPersistedCache) {
                null
            } else {
                repository.loadAssetBundle(
                    cacheKey = assetCacheKey,
                    refreshIntervalHours = refreshIntervalHours
                )
            }
            if (
                persistedBundle != null &&
                state.matchesAssetSourceSignature(persistedBundle, lookupConfig) &&
                (!requireReleaseNotesBody || persistedBundle.releaseNotesBody.isNotBlank())
            ) {
                loadingState[item.id] = false
                state.apkAssetBundles[item.id] = persistedBundle
                state.apkAssetBundleLoadedAtMs[item.id] = System.currentTimeMillis()
                if (expandPanelOnLoad) {
                    state.apkAssetErrors[item.id] = buildEmptyAssetMessage(
                        label = target.label,
                        includeAllAssets = includeAllAssets,
                        assetCount = persistedBundle.assets.size
                    )
                }
                return@launch
            } else if (persistedBundle != null) {
                repository.clearAssetCache(assetCacheKey)
            }
            val result = repository.fetchApkAssets(
                owner = item.owner,
                repo = item.repo,
                rawTag = target.rawTag,
                releaseUrl = target.releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                includeAllAssets = includeAllAssets,
                apiToken = lookupConfig.apiToken
            )
            loadingState[item.id] = false
            result.onSuccess { bundle ->
                val persistedBundle = bundle.copy(
                    sourceConfigSignature = state.buildAssetSourceSignature(lookupConfig)
                )
                state.apkAssetBundles[item.id] = persistedBundle
                state.apkAssetBundleLoadedAtMs[item.id] = System.currentTimeMillis()
                scope.launch {
                    repository.saveAssetBundle(
                        cacheKey = assetCacheKey,
                        bundle = persistedBundle
                    )
                }
                if (expandPanelOnLoad) {
                    state.apkAssetErrors[item.id] = buildEmptyAssetMessage(
                        label = target.label,
                        includeAllAssets = includeAllAssets,
                        assetCount = persistedBundle.assets.size
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
        clearCache: Boolean
    ) {
        releaseNotesActions.loadReleaseNotes(
            item = item,
            itemState = itemState,
            clearCache = clearCache
        )
    }

    fun loadReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        forceRefresh: Boolean = false
    ) {
        releaseNotesActions.loadReleaseNotesTargets(
            item = item,
            itemState = itemState,
            forceRefresh = forceRefresh
        )
    }

    fun selectReleaseNotesTarget(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget
    ) {
        releaseNotesActions.selectReleaseNotesTarget(item = item, target = target)
    }

    private fun isRuntimeCacheFresh(
        loadedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val loadedAt = loadedAtMs ?: return false
        if (loadedAt <= 0L) return false
        val intervalMs = state.refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        return (nowMs - loadedAt).coerceAtLeast(0L) < intervalMs
    }

    private suspend fun resolvePreferredAssetUrl(asset: GitHubReleaseAssetFile): String {
        val token = state.lookupConfig.apiToken.trim()
        val preferApiAsset =
            state.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken
        return repository.resolvePreferredDownloadUrl(
            asset = asset,
            useApiAssetUrl = preferApiAsset,
            apiToken = token
        )
    }

    private suspend fun shareApkLinkInternal(asset: GitHubReleaseAssetFile): Boolean {
        val resolvedUrl = SafeExternalIntents.httpsExternalUrlOrNull(resolvePreferredAssetUrl(asset))
            ?: run {
                env.toast(R.string.github_toast_share_link_failed)
                return false
            }
        val onlineSharePackage = state.lookupConfig.onlineShareTargetPackage.trim()
        val intent = SafeExternalIntents.textShareIntent(
            text = resolvedUrl,
            subject = asset.name,
            targetPackage = onlineSharePackage,
            extras = if (onlineSharePackage.isNotBlank()) {
                mapOf(
                    "channel" to "Online",
                    "extra_channel" to "Online",
                    "online_channel" to true
                )
            } else {
                emptyMap()
            }
        )
        return runCatching {
            if (onlineSharePackage.isNotBlank()) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.github_share_apk_link_title))
                )
            }
            true
        }.getOrElse {
            env.toast(R.string.github_toast_share_link_failed)
            false
        }
    }

    private suspend fun openApkInDownloaderInternal(asset: GitHubReleaseAssetFile): Boolean {
        val resolvedUrl = SafeExternalIntents.httpsExternalUrlOrNull(resolvePreferredAssetUrl(asset))
            ?: run {
                env.toast(R.string.github_toast_open_downloader_failed)
                return false
            }
        val preferredPackage = state.lookupConfig.preferredDownloaderPackage.trim()
        return runCatching {
            when (preferredPackage) {
                systemDmOption.packageName -> {
                    enqueueWithSystemDownloadManager(resolvedUrl, asset.name)
                    env.toast(R.string.github_toast_downloader_system_builtin)
                }
                "" -> {
                    require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl))
                    env.toast(R.string.github_toast_downloader_system_default)
                }
                else -> {
                    require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl, preferredPackage))
                    env.toast(R.string.github_toast_downloader_selected)
                }
            }
            true
        }.recoverCatching {
            if (preferredPackage.isNotBlank() && preferredPackage != systemDmOption.packageName) {
                require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl))
                env.toast(R.string.github_toast_downloader_fallback_system)
                true
            } else {
                throw it
            }
        }.getOrElse {
            env.toast(R.string.github_toast_open_downloader_failed)
            false
        }
    }

    private fun enqueueWithSystemDownloadManager(url: String, fileName: String) {
        AppPrivateDownloadManager.enqueueHttpsDownload(
            context = context,
            url = url,
            fileName = fileName
        )
    }

    private fun buildEmptyAssetMessage(
        label: String,
        includeAllAssets: Boolean,
        assetCount: Int
    ): String {
        if (assetCount > 0) return ""
        return if (includeAllAssets) {
            context.getString(
                R.string.github_msg_assets_no_downloadable_except_source,
                label
            )
        } else {
            context.getString(
                R.string.github_msg_assets_no_downloadable,
                label
            )
        }
    }
}
