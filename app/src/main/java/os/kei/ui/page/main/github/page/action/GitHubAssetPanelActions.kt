package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.localizedGitHubPageErrorMessage
import os.kei.ui.page.main.github.statusActionUrl

internal class GitHubAssetPanelActions(
    private val env: GitHubPageActionEnvironment,
    private val openExternalUrl: (String) -> Unit,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val clock get() = env.clock

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
            openFallbackOrToast(
                item = item,
                itemState = itemState,
                alwaysLatestRelease = alwaysLatestRelease,
                openFallbackTarget = openFallbackTarget,
            )
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
                    errorState[item.id] =
                        localizedGitHubPageErrorMessage(
                            context = context,
                            error = error,
                            fallbackMessage = context.getString(R.string.github_error_load_apk_assets_failed),
                        )
                }
        }
    }

    private fun openFallbackOrToast(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        alwaysLatestRelease: Boolean,
        openFallbackTarget: Boolean,
    ) {
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
