package os.kei.ui.page.main.github.page

import android.content.Intent
import os.kei.R
import os.kei.core.system.AppPackageChangedEvent
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.action.GitHubAssetActions
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment
import os.kei.ui.page.main.github.page.action.GitHubRefreshActions

internal class GitHubPackageChangedActionFacade(
    private val env: GitHubPageActionEnvironment,
    private val refreshActions: GitHubRefreshActions,
    private val assetActions: GitHubAssetActions,
) {
    private val minHandleIntervalMs = 1200L
    private val handledAtByPackage = mutableMapOf<String, Long>()
    private val packageUpdateActions =
        setOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED,
        )

    suspend fun handle(event: AppPackageChangedEvent) {
        val packageName = event.packageName.trim()
        if (packageName.isBlank()) return
        if (event.action !in packageUpdateActions) return
        if (event.replacing && event.action == Intent.ACTION_PACKAGE_REMOVED) return

        val matchedItems = env.state.trackedItems.filter { it.packageName == packageName }
        if (matchedItems.isEmpty()) return

        val lastHandledAt = handledAtByPackage[packageName] ?: 0L
        if ((event.atMillis - lastHandledAt).coerceAtLeast(0L) < minHandleIntervalMs) {
            return
        }
        handledAtByPackage[packageName] = event.atMillis

        refreshActions.reloadApps(forceRefresh = true)
        val uninstallAction =
            event.action == Intent.ACTION_PACKAGE_REMOVED ||
                event.action == Intent.ACTION_PACKAGE_FULLY_REMOVED
        matchedItems.forEach { item ->
            val wasAssetExpanded = env.state.apkAssetExpanded[item.id] == true
            val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
            val previousState = env.state.checkStates[item.id] ?: VersionCheckUi()
            if (uninstallAction) {
                env.state.checkStates[item.id] =
                    previousState.copy(
                        loading = true,
                        localVersion = "",
                        localVersionCode = -1L,
                        message = env.string(R.string.github_msg_checking),
                    )
            }
            assetActions.clearApkAssetCache(item, previousState)
            refreshActions.refreshItem(item = item, showToastOnError = false) { updatedState ->
                if (wasAssetExpanded && canLoadApkAssets(item, updatedState, env.context)) {
                    assetActions.clearApkAssetCache(item, updatedState)
                    assetActions.loadApkAssets(
                        item = item,
                        itemState = updatedState,
                        toggleOnlyWhenCached = false,
                        includeAllAssets = includeAllAssets,
                    )
                } else if (wasAssetExpanded) {
                    assetActions.clearApkAssetUiState(item.id)
                } else {
                    assetActions.clearApkAssetRuntimeState(item.id)
                }
            }
        }
    }
}
