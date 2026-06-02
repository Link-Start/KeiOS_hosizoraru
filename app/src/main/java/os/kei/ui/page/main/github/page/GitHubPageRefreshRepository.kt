package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.state.toUi

internal class GitHubPageRefreshRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork
) {
    private val notificationBridge = GitHubPageRefreshNotificationBridge(ioDispatcher)

    suspend fun evaluateTrackedApp(
        context: Context,
        item: GitHubTrackedApp,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): VersionCheckUi {
        return withContext(ioDispatcher) {
            GitHubReleaseCheckService.evaluateTrackedApp(
                context = context,
                item = item,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh
            ).toUi()
        }
    }

    suspend fun notifyRefreshProgress(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ): Boolean {
        return notificationBridge.notifyProgress(
            context = context,
            current = current,
            total = total,
            preReleaseUpdateCount = preReleaseUpdateCount,
            updatableCount = updatableCount,
            failedCount = failedCount
        )
    }

    suspend fun notifyRefreshCompleted(
        context: Context,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ): Boolean {
        return notificationBridge.notifyCompleted(
            context = context,
            total = total,
            preReleaseUpdateCount = preReleaseUpdateCount,
            updatableCount = updatableCount,
            failedCount = failedCount
        )
    }

    suspend fun notifyRefreshCancelled(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ): Boolean {
        return notificationBridge.notifyCancelled(
            context = context,
            current = current,
            total = total,
            preReleaseUpdateCount = preReleaseUpdateCount,
            updatableCount = updatableCount,
            failedCount = failedCount
        )
    }

    fun cancelRefreshNotification(context: Context) {
        notificationBridge.cancel(context)
    }

    suspend fun clearReleaseStrategyCaches() {
        withContext(ioDispatcher) {
            GitHubReleaseStrategyRegistry.clearAllCaches()
        }
    }
}
