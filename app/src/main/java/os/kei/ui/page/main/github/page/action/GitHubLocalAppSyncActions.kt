package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.isLocalAppUninstalled
import java.util.Locale

internal class GitHubLocalAppSyncActions(
    private val env: GitHubPageActionEnvironment,
) {
    private val context get() = env.context
    private val state get() = env.state
    private val repository get() = env.repository
    private val appListReloadMutex = Mutex()

    suspend fun reloadApps(
        forceRefresh: Boolean = false,
        includeSystemApps: Boolean = state.lookupConfig.scanSystemAppsByDefault,
    ) {
        appListReloadMutex.withLock {
            state.appListRefreshing = true
            try {
                state.appList =
                    repository.queryInstalledLaunchableApps(
                        context = context,
                        forceRefresh = forceRefresh,
                        includeSystemApps = includeSystemApps,
                        pinnedSystemPackageNames = trackedSystemPackageNames(),
                    )
                syncTrackedLocalAppTypesFromAppList()
                repository.preloadAppIcons(
                    context = context,
                    packageNames = trackedPackageNames(),
                )
                state.appListLoaded = true
            } finally {
                state.appListRefreshing = false
            }
        }
    }

    suspend fun syncLocalAppStateWithInstalledApps(
        forceRefreshApps: Boolean = true,
        onShouldRefreshItem: (GitHubTrackedApp) -> Unit,
    ) {
        if (forceRefreshApps) {
            reloadApps(forceRefresh = true)
        }
        val trackedSnapshot = state.trackedItems.toList()
        if (trackedSnapshot.isEmpty()) return

        val localAppTypeUpdates = mutableMapOf<String, GitHubTrackedLocalAppType>()
        trackedSnapshot.forEach { item ->
            val packageName = item.packageName.trim()
            if (packageName.isBlank()) return@forEach

            val latestLocalVersionInfo =
                runCatching {
                    repository.localVersionInfoOrNull(context, packageName)
                }.getOrNull()
            latestLocalVersionInfo?.let { info ->
                val detectedType = GitHubTrackedLocalAppType.fromSystemFlag(info.isSystemApp)
                if (detectedType != GitHubTrackedLocalAppType.Unknown &&
                    detectedType != item.localAppType
                ) {
                    localAppTypeUpdates[item.id] = detectedType
                }
            }

            val cachedState = state.checkStates[item.id] ?: return@forEach
            if (shouldRefreshForInstalledAppChange(cachedState, latestLocalVersionInfo?.versionCode)) {
                onShouldRefreshItem(item)
            }
        }
        applyTrackedLocalAppTypeUpdates(localAppTypeUpdates)
    }

    private fun trackedSystemPackageNames(): Set<String> =
        state.trackedItems
            .asSequence()
            .filter { it.localAppType == GitHubTrackedLocalAppType.System }
            .mapNotNull { item ->
                item.packageName
                    .trim()
                    .lowercase(Locale.ROOT)
                    .takeIf { packageName -> packageName.isNotBlank() }
            }.toSet()

    private fun trackedPackageNames(): List<String> =
        state.trackedItems
            .map { it.packageName.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun syncTrackedLocalAppTypesFromAppList() {
        if (state.trackedItems.isEmpty() || state.appList.isEmpty()) return
        val detectedTypeByPackage =
            state.appList
                .mapNotNull { app ->
                    val packageName =
                        app.packageName
                            .trim()
                            .lowercase(Locale.ROOT)
                            .takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                    packageName to GitHubTrackedLocalAppType.fromSystemFlag(app.isSystemApp)
                }.toMap()
        val updates =
            state.trackedItems
                .mapNotNull { item ->
                    val detectedType =
                        detectedTypeByPackage[item.packageName.trim().lowercase(Locale.ROOT)]
                            ?: return@mapNotNull null
                    if (item.localAppType == detectedType) {
                        null
                    } else {
                        item.id to detectedType
                    }
                }.toMap()
        applyTrackedLocalAppTypeUpdates(updates)
    }

    private fun applyTrackedLocalAppTypeUpdates(updates: Map<String, GitHubTrackedLocalAppType>) {
        if (updates.isEmpty()) return
        val updatedItems =
            state.trackedItems.map { item ->
                val detectedType =
                    updates[item.id]?.takeIf {
                        it != GitHubTrackedLocalAppType.Unknown && it != item.localAppType
                    } ?: return@map item
                item.copy(localAppType = detectedType)
            }
        if (updatedItems == state.trackedItems.toList()) return
        state.trackedItems.clear()
        state.trackedItems.addAll(updatedItems)
        env.saveTrackedItems(emitStoreSignal = false)
    }

    private fun shouldRefreshForInstalledAppChange(
        cachedState: VersionCheckUi,
        latestVersionCode: Long?,
    ): Boolean {
        val installed = latestVersionCode != null
        val cachedUninstalled = cachedState.isLocalAppUninstalled()
        val cachedVersionCode = cachedState.localVersionCode
        val resolvedVersionCode = latestVersionCode ?: -1L
        return when {
            installed && cachedUninstalled -> true
            !installed && !cachedUninstalled -> true
            installed && cachedVersionCode != resolvedVersionCode -> true
            else -> false
        }
    }
}
