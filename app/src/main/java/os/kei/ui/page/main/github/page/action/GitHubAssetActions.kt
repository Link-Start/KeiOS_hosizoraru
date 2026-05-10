package os.kei.ui.page.main.github.page.action

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkInstallDeliveryMode
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.githubTrackedDisplayTitle
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowCoordinator
import os.kei.ui.page.main.github.install.GitHubApkInstallRequestContext
import os.kei.ui.page.main.github.install.GitHubApkInstallSourceKind
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.statusActionUrl
import java.io.File
import java.security.MessageDigest

internal class GitHubAssetActions(
    private val env: GitHubPageActionEnvironment
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val systemDmOption get() = env.systemDmOption
    private val apkInfoRepository = GitHubApkInfoRepository()

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

    fun openApkInDownloader(asset: GitHubReleaseAssetFile) {
        scope.launch {
            openApkInDownloaderInternal(asset)
        }
    }

    fun openTrackedApkInDownloader(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        asset: GitHubReleaseAssetFile
    ) {
        scope.launch {
            openApkInDownloaderInternal(
                asset = asset,
                trackedItem = item,
                trackedItemState = itemState
            )
        }
    }

    fun openApkInfo(
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean = false
    ) {
        val key = asset.githubApkInfoKey()
        state.apkInfoDetailRequest = asset
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
            return
        }
        if (state.apkInfoLoading[key] == true) return
        state.apkInfoLoading[key] = true
        state.apkInfoErrors.remove(key)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apkInfoRepository.inspectAsync(
                    asset = asset,
                    lookupConfig = state.lookupConfig,
                    forceRefresh = forceRefresh
                )
            }
            state.apkInfoLoading[key] = false
            result.onSuccess { info ->
                state.apkInfoResults[key] = info
                state.apkInfoInstalledResults[key] = loadInstalledPackageInfo(info.packageName)
            }.onFailure { error ->
                state.apkInfoErrors[key] = error.message
                    ?: context.getString(R.string.github_apk_info_error_failed)
            }
        }
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
            signatureSha256 = packageInfo.signatureSha256List(),
            sourceSizeBytes = applicationInfo.sourceApkSizeBytes()
        )
    }

    suspend fun sendAssetToConfiguredChannel(asset: GitHubReleaseAssetFile): Boolean {
        if (state.lookupConfig.apkInstallDeliveryMode == GitHubApkInstallDeliveryMode.AppShizuku) {
            return startAppInstallFlow(asset)
        }
        return if (state.lookupConfig.onlineShareTargetPackage.isNotBlank()) {
            shareApkLinkInternal(asset)
        } else {
            openApkInDownloaderInternal(asset)
        }
    }

    fun clearApkAssetUiState(itemId: String) {
        state.clearAssetUiState(itemId)
    }

    fun clearApkAssetRuntimeState(itemId: String) {
        state.clearAssetRuntimeState(itemId)
    }

    fun clearApkAssetCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = false
    ) {
        val target = itemState.apkAssetTarget(
            owner = item.owner,
            repo = item.repo,
            context = context,
            alwaysLatestRelease = item.alwaysShowLatestReleaseDownloadButton,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        ) ?: return
        val preferHtml = state.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
        val hasApiToken = state.lookupConfig.apiToken.isNotBlank()
        val releaseUrl = target.releaseUrl
        val normalizedRawTag = target.rawTag
        val cacheKeyDefault = repository.buildAssetCacheKey(
            owner = item.owner,
            repo = item.repo,
            rawTag = normalizedRawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = state.lookupConfig.aggressiveApkFiltering,
            includeAllAssets = false,
            hasApiToken = hasApiToken
        )
        val cacheKeyAllAssets = repository.buildAssetCacheKey(
            owner = item.owner,
            repo = item.repo,
            rawTag = normalizedRawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = state.lookupConfig.aggressiveApkFiltering,
            includeAllAssets = true,
            hasApiToken = hasApiToken
        )
        scope.launch {
            repository.clearAssetCaches(
                listOf(cacheKeyDefault, cacheKeyAllAssets)
            )
        }
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
        val loadingState =
            if (showAssetPanelLoading) state.apkAssetLoading else state.releaseNotesLoading
        val errorState =
            if (showAssetPanelLoading) state.apkAssetErrors else state.releaseNotesErrors

        val cachedBundle = state.apkAssetBundles[item.id]
        if (
            toggleOnlyWhenCached &&
            cachedBundle != null &&
            state.matchesAssetSourceSignature(cachedBundle) &&
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
            val preferHtml = state.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
            val refreshIntervalHours = repository.loadRefreshIntervalHours()
            val assetCacheKey = repository.buildAssetCacheKey(
                owner = item.owner,
                repo = item.repo,
                rawTag = target.rawTag,
                releaseUrl = target.releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = state.lookupConfig.aggressiveApkFiltering,
                includeAllAssets = includeAllAssets,
                hasApiToken = state.lookupConfig.apiToken.isNotBlank()
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
                state.matchesAssetSourceSignature(persistedBundle) &&
                (!requireReleaseNotesBody || persistedBundle.releaseNotesBody.isNotBlank())
            ) {
                loadingState[item.id] = false
                state.apkAssetBundles[item.id] = persistedBundle
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
                aggressiveFiltering = state.lookupConfig.aggressiveApkFiltering,
                includeAllAssets = includeAllAssets,
                apiToken = state.lookupConfig.apiToken
            )
            loadingState[item.id] = false
            result.onSuccess { bundle ->
                val persistedBundle = bundle.copy(
                    sourceConfigSignature = state.buildAssetSourceSignature()
                )
                state.apkAssetBundles[item.id] = persistedBundle
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
        if (clearCache) {
            clearApkAssetCache(
                item = item,
                itemState = itemState,
                allowLatestReleaseFallback = true
            )
        }
        loadApkAssets(
            item = item,
            itemState = itemState,
            toggleOnlyWhenCached = !clearCache,
            includeAllAssets = true,
            allowLatestReleaseFallback = true,
            expandPanelOnLoad = false,
            openFallbackTarget = false,
            showAssetPanelLoading = false,
            requireReleaseNotesBody = true,
            bypassPersistedCache = clearCache
        )
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

    private suspend fun openApkInDownloaderInternal(
        asset: GitHubReleaseAssetFile,
        trackedItem: GitHubTrackedApp? = null,
        trackedItemState: VersionCheckUi? = null
    ): Boolean {
        if (state.lookupConfig.apkInstallDeliveryMode == GitHubApkInstallDeliveryMode.AppShizuku) {
            return startAppInstallFlow(
                asset = asset,
                trackedItem = trackedItem,
                trackedItemState = trackedItemState
            )
        }
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

    private fun startAppInstallFlow(
        asset: GitHubReleaseAssetFile,
        trackedItem: GitHubTrackedApp? = null,
        trackedItemState: VersionCheckUi? = null
    ): Boolean {
        val apkInfo = state.apkInfoResults[asset.githubApkInfoKey()]
        val trackedTarget = trackedItemState?.let { itemState ->
            trackedItem?.let { item ->
                itemState.apkAssetTarget(
                    owner = item.owner,
                    repo = item.repo,
                    context = context,
                    alwaysLatestRelease = item.alwaysShowLatestReleaseDownloadButton,
                    allowLatestReleaseFallback = true
                )
            }
        }
        val trackedRemoteManifest = trackedItemState?.trackedRemoteManifestInfo(
            asset = asset,
            targetRawTag = trackedTarget?.rawTag.orEmpty()
        )
        val remoteManifestInfo = apkInfo ?: trackedRemoteManifest
        val expectedPackageName = trackedItem?.packageName.orEmpty()
            .ifBlank { remoteManifestInfo?.packageName.orEmpty() }
        val installedInfo = trackedItem
            ?.packageName
            ?.takeIf { it.isNotBlank() }
            ?.let(::loadInstalledPackageInfo)
        state.apkInfoDetailRequest = null
        GitHubApkInstallFlowCoordinator.beginInstallAsset(
            context = context,
            lookupConfig = state.lookupConfig,
            asset = asset,
            request = GitHubApkInstallRequestContext(
                sourceKind = if (trackedItem != null) {
                    GitHubApkInstallSourceKind.TrackedReleaseAsset
                } else {
                    GitHubApkInstallSourceKind.ReleaseAsset
                },
                owner = trackedItem?.owner.orEmpty(),
                repo = trackedItem?.repo.orEmpty(),
                releaseTag = trackedTarget?.rawTag.orEmpty(),
                sourceLabel = trackedItem
                    ?.githubTrackedDisplayTitle(trackedItemState)
                    .orEmpty()
                    .ifBlank { asset.name },
                expectedPackageName = expectedPackageName,
                externalFileName = asset.name,
                remoteManifestInfo = remoteManifestInfo
            ),
            initialInstalledInfo = installedInfo
        )
        return true
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

private fun VersionCheckUi.trackedRemoteManifestInfo(
    asset: GitHubReleaseAssetFile,
    targetRawTag: String
): GitHubApkManifestInfo? {
    val candidates = listOfNotNull(latestStableApkVersion, latestPreApkVersion)
    val byTag = targetRawTag
        .takeIf { it.isNotBlank() }
        ?.let { tag ->
            candidates.firstOrNull { info -> info.releaseTag.equals(tag, ignoreCase = true) }
        }
    val byAsset = candidates.firstOrNull { info ->
        info.assetName.equals(asset.name, ignoreCase = true)
    }
    val preferred = when {
        latestPreRawTag.isNotBlank() &&
                latestPreRawTag.equals(targetRawTag, ignoreCase = true) -> latestPreApkVersion

        latestStableRawTag.isNotBlank() &&
                latestStableRawTag.equals(targetRawTag, ignoreCase = true) -> latestStableApkVersion

        recommendsPreRelease || hasPreReleaseUpdate -> latestPreApkVersion
        else -> latestStableApkVersion
    }
    return (byTag ?: byAsset ?: preferred)
        ?.takeIf { info ->
            info.packageName.isNotBlank() ||
                    info.versionName.isNotBlank() ||
                    info.versionCode.isNotBlank()
        }
        ?.toManifestInfo(asset.name)
}

private fun GitHubRemoteApkVersionInfo.toManifestInfo(
    fallbackAssetName: String
): GitHubApkManifestInfo {
    return GitHubApkManifestInfo(
        assetName = assetName.ifBlank { fallbackAssetName },
        fetchSource = fetchSource,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode
    )
}

private fun PackageInfo.signatureSha256List(): List<String> {
    val signing = signingInfo ?: return emptyList()
    val signatures = if (signing.hasMultipleSigners()) {
        signing.apkContentsSigners
    } else {
        signing.signingCertificateHistory
    } ?: return emptyList()
    return signatures
        .map { signature -> signature.toByteArray().sha256Hex() }
        .distinct()
}

private fun android.content.pm.ApplicationInfo?.sourceApkSizeBytes(): Long {
    if (this == null) return 0L
    val files = buildList {
        sourceDir?.let { add(it) }
        splitSourceDirs.orEmpty().forEach(::add)
    }
    return files.sumOf { path -> File(path).takeIf { it.isFile }?.length() ?: 0L }
}

private fun ByteArray.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
