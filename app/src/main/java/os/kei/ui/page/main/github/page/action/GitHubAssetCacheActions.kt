package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.launch
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget

internal class GitHubAssetCacheActions(
    private val env: GitHubPageActionEnvironment
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository

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
}
