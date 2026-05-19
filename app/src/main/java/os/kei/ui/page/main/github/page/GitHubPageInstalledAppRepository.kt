package os.kei.ui.page.main.github.page

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.AppIconCache
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.query.queryDownloaderOptions
import os.kei.ui.page.main.github.query.queryOnlineShareTargetOptions

internal class GitHubPageInstalledAppRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun queryOnlineShareTargets(
        context: Context,
        input: GitHubOnlineShareTargetInput
    ): List<OnlineShareTargetOption> {
        if (!input.shouldResolve) return emptyList()
        return withContext(defaultDispatcher) {
            queryOnlineShareTargetOptions(context, input.appList)
        }
    }

    suspend fun queryDownloaders(context: Context): List<DownloaderOption> {
        return withContext(defaultDispatcher) {
            queryDownloaderOptions(context)
        }
    }

    fun buildAppListPermissionIntent(context: Context): Intent? {
        return GitHubVersionUtils.buildAppListPermissionIntent(context)
    }

    suspend fun queryInstalledLaunchableApps(
        context: Context,
        forceRefresh: Boolean,
        includeSystemApps: Boolean = true,
        pinnedSystemPackageNames: Set<String> = emptySet()
    ): List<InstalledAppItem> {
        return withContext(ioDispatcher) {
            GitHubVersionUtils.queryInstalledLaunchableApps(
                context = context,
                forceRefresh = forceRefresh,
                includeSystemApps = includeSystemApps,
                pinnedSystemPackageNames = pinnedSystemPackageNames
            )
        }
    }

    suspend fun preloadAppIcons(
        context: Context,
        packageNames: List<String>
    ) {
        if (packageNames.isEmpty()) return
        withContext(ioDispatcher) {
            AppIconCache.preload(context, packageNames)
        }
    }

    suspend fun localVersionInfoOrNull(
        context: Context,
        packageName: String
    ): GitHubVersionUtils.LocalVersionInfo? {
        return withContext(ioDispatcher) {
            GitHubVersionUtils.localVersionInfoOrNull(context, packageName)
        }
    }
}
