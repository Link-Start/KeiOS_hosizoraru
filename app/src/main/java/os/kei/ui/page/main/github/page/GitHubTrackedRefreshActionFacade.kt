package os.kei.ui.page.main.github.page

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.action.GitHubAssetActions
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment
import os.kei.ui.page.main.github.page.action.GitHubRefreshActions

internal class GitHubTrackedRefreshActionFacade(
    private val env: GitHubPageActionEnvironment,
    private val refreshActions: GitHubRefreshActions,
    private val assetActions: GitHubAssetActions,
) {
    private companion object {
        const val RETRY_FAILED_TRACKED_PARALLELISM = 3
    }

    fun refreshAllTracked(
        showToast: Boolean = true,
        forceRefresh: Boolean = true,
    ) {
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            refreshActions.refreshAllTracked(
                showToast = showToast,
                forceRefresh = forceRefresh,
            ) {
                reloadExpandedAssetPanelsAfterRefresh()
            }
        }
    }

    fun refreshVisibleTracked(
        items: List<GitHubTrackedApp>,
        showToast: Boolean = true,
        forceRefresh: Boolean = true,
    ) {
        if (items.isEmpty()) {
            refreshActions.refreshTrackedBatch(
                targets = emptyList(),
                showToast = showToast,
                forceRefresh = forceRefresh,
            )
            return
        }
        val targetIds = items.mapTo(LinkedHashSet()) { it.id }
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            refreshActions.refreshTrackedBatch(
                targets = items,
                showToast = showToast,
                forceRefresh = forceRefresh,
            ) {
                reloadExpandedAssetPanelsAfterRefresh(targetIds = targetIds)
            }
        }
    }

    fun refreshTrackedItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = true,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = true,
    ) {
        env.scope.launch {
            refreshTrackedItemNow(
                item = item,
                showToastOnError = showToastOnError,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
                reloadAppsBeforeRefresh = true,
            )
        }
    }

    suspend fun refreshTrackedItemNow(
        item: GitHubTrackedApp,
        showToastOnError: Boolean,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        reloadAppsBeforeRefresh: Boolean,
    ) {
        if (env.state.trackedItems.none { it.id == item.id }) return
        if (env.state.itemRefreshLoading[item.id] == true) return
        if (env.state.checkStates[item.id]?.loading == true) return
        env.state.itemRefreshLoading[item.id] = true
        try {
            if (reloadAppsBeforeRefresh) {
                refreshActions.reloadApps(forceRefresh = true)
            }
            val wasAssetExpanded = env.state.apkAssetExpanded[item.id] == true
            val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
            val previousState = env.state.checkStates[item.id] ?: VersionCheckUi()
            assetActions.clearApkAssetCache(item, previousState)
            refreshActions.refreshItemNow(
                item = item,
                showToastOnError = showToastOnError,
                keepCurrentVisualWhileRefreshing = true,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
            ) { updatedState ->
                if (wasAssetExpanded && canLoadApkAssets(item, updatedState, env.context)) {
                    assetActions.clearApkAssetCache(item, updatedState)
                    assetActions.loadApkAssets(
                        item = item,
                        itemState = updatedState,
                        toggleOnlyWhenCached = false,
                        includeAllAssets = includeAllAssets,
                        allowLatestReleaseFallback = updatedState.isLocalAppUninstalled(),
                    )
                } else if (wasAssetExpanded) {
                    assetActions.clearApkAssetUiState(item.id)
                } else {
                    assetActions.clearApkAssetRuntimeState(item.id)
                }
                env.state.requestTrackCardFocus(item.id)
            }
        } finally {
            env.state.itemRefreshLoading.remove(item.id)
        }
    }

    fun refreshFailedTrackedItems(showToast: Boolean = true) {
        val failedItems =
            env.state.trackedItems.filter { item ->
                env.state.checkStates[item.id]?.failed == true
            }
        if (failedItems.isEmpty()) {
            if (showToast) {
                env.toast(R.string.github_toast_no_checkable_item)
            }
            return
        }
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            coroutineScope {
                failedItems.chunked(RETRY_FAILED_TRACKED_PARALLELISM).forEach { chunk ->
                    chunk
                        .map { item ->
                            launch {
                                if (env.state.trackedItems.any { it.id == item.id }) {
                                    refreshTrackedItemNow(
                                        item = item,
                                        showToastOnError = showToast,
                                        reloadAppsBeforeRefresh = false,
                                    )
                                }
                            }
                        }.joinAll()
                }
            }
        }
    }

    private fun reloadExpandedAssetPanelsAfterRefresh(targetIds: Set<String>? = null) {
        val expandedItemIds =
            env.state.apkAssetExpanded
                .filterValues { it }
                .keys
                .filterTo(HashSet()) { targetIds == null || it in targetIds }
        if (expandedItemIds.isEmpty()) return

        env.state.trackedItems.forEach { item ->
            if (item.id !in expandedItemIds) return@forEach
            val itemState = env.state.checkStates[item.id] ?: return@forEach
            val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
            if (canLoadApkAssets(item, itemState, env.context)) {
                assetActions.clearApkAssetCache(item, itemState)
                assetActions.loadApkAssets(
                    item = item,
                    itemState = itemState,
                    toggleOnlyWhenCached = false,
                    includeAllAssets = includeAllAssets,
                    allowLatestReleaseFallback = itemState.isLocalAppUninstalled(),
                )
            } else {
                assetActions.clearApkAssetUiState(item.id)
            }
        }
    }
}
