package os.kei.ui.page.main.github.page.action

import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.page.GitHubApkInfoDetailRequest
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.page.releaseNotesApkVersionKey
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
    private val managedInstallRunner = GitHubPageManagedInstallRunner(env, apkInfoRepository)

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
            if (shouldInstallWithKeiOs(asset)) {
                openManagedInstallConfirm(item, asset)
            } else {
                openApkInDownloaderInternal(asset)
            }
        }
    }

    fun confirmManagedInstall() {
        val request = state.managedInstallConfirmRequest ?: return
        state.managedInstallConfirmRequest = null
        scope.launch {
            managedInstallRunner.install(request.item, request.asset)
        }
    }

    fun dismissManagedInstallConfirm() {
        state.managedInstallConfirmRequest = null
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

    private fun openManagedInstallConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile
    ) {
        state.apkInfoDetailRequest = null
        state.managedInstallConfirmRequest = GitHubManagedInstallConfirmRequest(
            item = item,
            asset = asset
        )
        loadApkInfo(asset = asset, forceRefresh = false)
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
        }.recoverCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(normalizedPackageName, 0)
        }.getOrNull() ?: return null
        val applicationInfo = packageInfo.applicationInfo
        return GitHubInstalledPackageInfo(
            packageName = normalizedPackageName,
            appLabel = applicationInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
            versionName = packageInfo.versionName?.trim().orEmpty(),
            versionCode = packageInfo.longVersionCode,
            minSdk = applicationInfo?.minSdkVersion ?: -1,
            targetSdk = applicationInfo?.targetSdkVersion ?: -1
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
        val cacheKeys = buildApkAssetCacheKeys(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
        if (cacheKeys.isEmpty()) return
        scope.launch {
            repository.clearAssetCaches(cacheKeys)
        }
    }

    suspend fun clearApkAssetCacheNow(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = false
    ) {
        val cacheKeys = buildApkAssetCacheKeys(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
        if (cacheKeys.isEmpty()) return
        repository.clearAssetCaches(cacheKeys)
    }

    suspend fun clearApkAssetCachesForTargetsNow(
        targets: List<Pair<GitHubTrackedApp, VersionCheckUi>>,
        allowLatestReleaseFallback: Boolean = false
    ) {
        val cacheKeys = targets
            .flatMap { (item, itemState) ->
                buildApkAssetCacheKeys(
                    item = item,
                    itemState = itemState,
                    allowLatestReleaseFallback = allowLatestReleaseFallback
                )
            }
            .distinct()
        if (cacheKeys.isEmpty()) return
        repository.clearAssetCaches(cacheKeys)
    }

    fun clearApkAssetStateAndCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean = true
    ) {
        state.clearAssetUiState(item.id)
        clearApkAssetCache(
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
        state.clearAssetUiState(item.id)
        clearApkAssetCacheNow(
            item = item,
            itemState = itemState,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    suspend fun clearAllApkAssetStateAndCacheNow() {
        state.clearAllAssetUiState()
        repository.clearAllAssetCache()
    }

    suspend fun clearApkAssetStatesAndCachesNow(
        targets: List<Pair<GitHubTrackedApp, VersionCheckUi>>,
        clearItemIds: Set<String> = targets.map { (item, _) -> item.id }.toSet(),
        allowLatestReleaseFallback: Boolean = true
    ) {
        clearItemIds.forEach(state::clearAssetUiState)
        clearApkAssetCachesForTargetsNow(
            targets = targets,
            allowLatestReleaseFallback = allowLatestReleaseFallback
        )
    }

    private fun buildApkAssetCacheKeys(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        allowLatestReleaseFallback: Boolean
    ): List<String> {
        val targets = buildList {
            itemState.apkAssetTarget(
                owner = item.owner,
                repo = item.repo,
                context = context,
                alwaysLatestRelease = item.alwaysShowLatestReleaseDownloadButton,
                allowLatestReleaseFallback = false
            )?.let(::add)
            if (allowLatestReleaseFallback) {
                itemState.apkAssetTarget(
                    owner = item.owner,
                    repo = item.repo,
                    context = context,
                    alwaysLatestRelease = item.alwaysShowLatestReleaseDownloadButton,
                    allowLatestReleaseFallback = true
                )?.let(::add)
            }
        }.distinctBy { target ->
            "${target.rawTag.trim()}|${target.releaseUrl.trim()}"
        }
        if (targets.isEmpty()) return emptyList()
        val lookupConfig = state.lookupConfig.forTrackedItem(item)
        val preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
        val hasApiToken = lookupConfig.apiToken.isNotBlank()
        return targets
            .flatMap { target ->
                listOf(false, true).map { includeAllAssets ->
                    repository.buildAssetCacheKey(
                        owner = item.owner,
                        repo = item.repo,
                        rawTag = target.rawTag,
                        releaseUrl = target.releaseUrl,
                        preferHtml = preferHtml,
                        aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                        includeAllAssets = includeAllAssets,
                        hasApiToken = hasApiToken
                    )
                }
            }
            .distinct()
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
        if (clearCache) {
            loadReleaseNotesTargets(
                item = item,
                itemState = itemState,
                forceRefresh = true
            )
            return
        }
        val selectedTarget = state.releaseNotesSelectedTargets[item.id]
        if (selectedTarget == null) {
            loadReleaseNotesTargets(
                item = item,
                itemState = itemState,
                forceRefresh = clearCache
            )
            return
        }
        loadReleaseNotesBundle(
            item = item,
            target = selectedTarget,
            clearCache = clearCache
        )
    }

    fun loadReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        forceRefresh: Boolean = false
    ) {
        val cachedTargets = state.releaseNotesTargets[item.id].orEmpty()
        val cachedSelected = state.releaseNotesSelectedTargets[item.id]
        if (
            !forceRefresh &&
            cachedTargets.isNotEmpty() &&
            cachedSelected != null &&
            isRuntimeCacheFresh(state.releaseNotesTargetsLoadedAtMs[item.id])
        ) {
            loadReleaseNotesBundle(item = item, target = cachedSelected, clearCache = false)
            return
        }
        state.releaseNotesLoading[item.id] = true
        state.releaseNotesErrors.remove(item.id)
        scope.launch {
            val lookupConfig = state.lookupConfig.forTrackedItem(item)
            val remoteTargets = repository.fetchReleaseNotesTargets(
                owner = item.owner,
                repo = item.repo,
                apiToken = lookupConfig.apiToken
            ).getOrElse { error ->
                fallbackReleaseNotesTargets(item, itemState).ifEmpty {
                    state.releaseNotesLoading[item.id] = false
                    state.releaseNotesErrors[item.id] = error.message
                        ?: context.getString(R.string.github_error_load_apk_assets_failed)
                    return@launch
                }
            }
            val selectedTarget = selectRefreshedReleaseNotesTarget(
                previousTarget = cachedSelected,
                targets = remoteTargets,
                preferPreRelease = item.preferPreRelease
            )
            if (selectedTarget == null) {
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesErrors[item.id] =
                    context.getString(R.string.github_release_notes_detail_empty)
                return@launch
            }
            state.releaseNotesTargets[item.id] = remoteTargets
            state.releaseNotesTargetsLoadedAtMs[item.id] = System.currentTimeMillis()
            state.releaseNotesSelectedTargets[item.id] = selectedTarget
            state.releaseNotesBundles.remove(item.id)
            state.releaseNotesBundleLoadedAtMs.remove(item.id)
            if (forceRefresh) {
                state.releaseNotesApkVersions.keys.removeAll { key -> key.startsWith("${item.id}|") }
            }
            loadReleaseNotesBundle(
                item = item,
                target = selectedTarget,
                clearCache = forceRefresh
            )
        }
    }

    fun selectReleaseNotesTarget(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget
    ) {
        state.releaseNotesSelectedTargets[item.id] = target
        state.releaseNotesBundles.remove(item.id)
        state.releaseNotesErrors.remove(item.id)
        loadReleaseNotesBundle(item = item, target = target, clearCache = false)
    }

    private fun loadReleaseNotesBundle(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
        clearCache: Boolean
    ) {
        val lookupConfig = state.lookupConfig.forTrackedItem(item)
        val cachedBundle = state.releaseNotesBundles[item.id]
        if (
            !clearCache &&
            cachedBundle != null &&
            isRuntimeCacheFresh(state.releaseNotesBundleLoadedAtMs[item.id]) &&
            state.matchesAssetSourceSignature(cachedBundle, lookupConfig) &&
            cachedBundle.tagName.equals(target.tagName, ignoreCase = true) &&
            cachedBundle.releaseNotesBody.isNotBlank()
        ) {
            state.releaseNotesLoading[item.id] = false
            state.releaseNotesErrors.remove(item.id)
            resolveReleaseNotesApkVersionIfNeeded(
                item = item,
                target = target,
                bundle = cachedBundle,
                lookupConfig = lookupConfig,
                forceRefresh = false
            )
            return
        }
        state.releaseNotesLoading[item.id] = true
        state.releaseNotesErrors.remove(item.id)
        scope.launch {
            val preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
            val cacheKey = repository.buildAssetCacheKey(
                owner = item.owner,
                repo = item.repo,
                rawTag = target.tagName,
                releaseUrl = target.htmlUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                includeAllAssets = true,
                hasApiToken = lookupConfig.apiToken.isNotBlank()
            )
            if (clearCache) {
                repository.clearAssetCache(cacheKey)
            }
            val refreshIntervalHours = repository.loadRefreshIntervalHours()
            val persistedBundle = if (clearCache) {
                null
            } else {
                repository.loadAssetBundle(cacheKey, refreshIntervalHours)
            }
            if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@launch
            if (
                persistedBundle != null &&
                state.matchesAssetSourceSignature(persistedBundle, lookupConfig) &&
                persistedBundle.releaseNotesBody.isNotBlank()
            ) {
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesBundles[item.id] = persistedBundle
                state.releaseNotesBundleLoadedAtMs[item.id] = System.currentTimeMillis()
                resolveReleaseNotesApkVersionIfNeeded(
                    item = item,
                    target = target,
                    bundle = persistedBundle,
                    lookupConfig = lookupConfig,
                    forceRefresh = false
                )
                return@launch
            } else if (persistedBundle != null) {
                repository.clearAssetCache(cacheKey)
            }
            repository.fetchApkAssets(
                owner = item.owner,
                repo = item.repo,
                rawTag = target.tagName,
                releaseUrl = target.htmlUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                includeAllAssets = true,
                apiToken = lookupConfig.apiToken
            ).onSuccess { bundle ->
                if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@onSuccess
                val persisted = bundle.copy(
                    sourceConfigSignature = state.buildAssetSourceSignature(lookupConfig)
                )
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesBundles[item.id] = persisted
                state.releaseNotesBundleLoadedAtMs[item.id] = System.currentTimeMillis()
                repository.saveAssetBundle(cacheKey, persisted)
                resolveReleaseNotesApkVersionIfNeeded(
                    item = item,
                    target = target,
                    bundle = persisted,
                    lookupConfig = lookupConfig,
                    forceRefresh = true
                )
            }.onFailure { error ->
                if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@onFailure
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesErrors[item.id] = error.message
                    ?: context.getString(R.string.github_error_load_apk_assets_failed)
            }
        }
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

    private fun fallbackReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi
    ): List<GitHubReleaseNotesTarget> {
        return buildList {
            itemState.latestStableRawTag.trim().takeIf { it.isNotBlank() }?.let { tag ->
                add(
                    GitHubReleaseNotesTarget(
                        releaseName = itemState.latestStableName.trim().ifBlank { tag },
                        tagName = tag,
                        htmlUrl = itemState.latestStableUrl.trim().ifBlank {
                            itemState.statusActionUrl(item.owner, item.repo)
                        },
                        prerelease = false,
                        latestInChannel = true,
                        updatedAtMillis = itemState.latestStableUpdatedAtMillis
                            .takeIf { it > 0L }
                    )
                )
            }
            itemState.latestPreRawTag.trim().takeIf { it.isNotBlank() }?.let { tag ->
                add(
                    GitHubReleaseNotesTarget(
                        releaseName = itemState.latestPreName.trim().ifBlank { tag },
                        tagName = tag,
                        htmlUrl = itemState.latestPreUrl.trim().ifBlank {
                            itemState.statusActionUrl(item.owner, item.repo)
                        },
                        prerelease = true,
                        latestInChannel = true,
                        updatedAtMillis = itemState.latestPreUpdatedAtMillis
                            .takeIf { it > 0L }
                    )
                )
            }
        }.distinctBy { it.id.lowercase() }
    }

    private fun resolveReleaseNotesApkVersionIfNeeded(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
        bundle: GitHubReleaseAssetBundle,
        lookupConfig: GitHubLookupConfig,
        forceRefresh: Boolean
    ) {
        if (!lookupConfig.preciseApkVersionEnabled) return
        val key = releaseNotesApkVersionKey(item.id, target)
        if (!forceRefresh && state.releaseNotesApkVersions[key]?.hasVersion() == true) return
        val apkAssets = bundle.assets
            .filter { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
            .take(MAX_RELEASE_NOTES_APK_VERSION_CANDIDATES)
        if (apkAssets.isEmpty()) return
        scope.launch {
            val resolved = resolveReleaseNotesApkVersion(
                item = item,
                target = target,
                bundle = bundle,
                apkAssets = apkAssets,
                lookupConfig = lookupConfig,
                forceRefresh = forceRefresh
            )
            if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@launch
            resolved?.takeIf { it.hasVersion() }?.let { state.releaseNotesApkVersions[key] = it }
        }
    }

    private suspend fun resolveReleaseNotesApkVersion(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
        bundle: GitHubReleaseAssetBundle,
        apkAssets: List<GitHubReleaseAssetFile>,
        lookupConfig: GitHubLookupConfig,
        forceRefresh: Boolean
    ): GitHubRemoteApkVersionInfo? {
        val inspected = GitHubExecution.mapOrderedBounded(
            items = apkAssets,
            maxConcurrency = MAX_RELEASE_NOTES_APK_VERSION_PARALLEL
        ) { asset ->
            asset to apkInfoRepository.inspectAsync(
                asset = asset,
                lookupConfig = lookupConfig,
                forceRefresh = forceRefresh
            )
        }
        val requestedPackageName = item.packageName.trim()
        val matched = inspected.firstNotNullOfOrNull { (asset, result) ->
            result.getOrNull()
                ?.takeIf { info ->
                    info.versionName.isNotBlank() || info.versionCode.isNotBlank()
                }
                ?.takeIf { info ->
                    requestedPackageName.isBlank() ||
                            info.packageName.equals(requestedPackageName, ignoreCase = true)
                }
                ?.let { info -> asset to info }
        }
        val fallback = inspected.firstNotNullOfOrNull { (asset, result) ->
            result.getOrNull()
                ?.takeIf { info ->
                    info.versionName.isNotBlank() || info.versionCode.isNotBlank()
                }
                ?.let { info -> asset to info }
        }
        val (asset, info) = matched ?: fallback ?: return null
        return GitHubRemoteApkVersionInfo(
            releaseName = bundle.releaseName.ifBlank { target.releaseName },
            releaseTag = bundle.tagName.ifBlank { target.tagName },
            releaseUrl = bundle.htmlUrl.ifBlank { target.htmlUrl },
            assetName = asset.name,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            fetchSource = info.fetchSource.ifBlank { bundle.fetchSource }
        )
    }

    private fun selectDefaultReleaseNotesTarget(
        targets: List<GitHubReleaseNotesTarget>,
        preferPreRelease: Boolean
    ): GitHubReleaseNotesTarget? {
        val preferred = if (preferPreRelease) {
            targets.firstOrNull { it.prerelease && it.latestInChannel }
                ?: targets.firstOrNull { it.prerelease }
        } else {
            targets.firstOrNull { !it.prerelease && it.latestInChannel }
                ?: targets.firstOrNull { !it.prerelease }
        }
        return preferred ?: targets.firstOrNull()
    }

    private fun selectRefreshedReleaseNotesTarget(
        previousTarget: GitHubReleaseNotesTarget?,
        targets: List<GitHubReleaseNotesTarget>,
        preferPreRelease: Boolean
    ): GitHubReleaseNotesTarget? {
        previousTarget?.let { previous ->
            targets.firstOrNull { it.id.equals(previous.id, ignoreCase = true) }?.let { return it }
            targets.firstOrNull {
                it.tagName.equals(previous.tagName, ignoreCase = true) &&
                        it.htmlUrl.equals(previous.htmlUrl, ignoreCase = true)
            }?.let { return it }
            targets.firstOrNull {
                it.tagName.equals(previous.tagName, ignoreCase = true)
            }?.let { return it }
        }
        return selectDefaultReleaseNotesTarget(
            targets = targets,
            preferPreRelease = preferPreRelease
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

    private companion object {
        const val MAX_RELEASE_NOTES_APK_VERSION_CANDIDATES = 4
        const val MAX_RELEASE_NOTES_APK_VERSION_PARALLEL = 2
    }
}
