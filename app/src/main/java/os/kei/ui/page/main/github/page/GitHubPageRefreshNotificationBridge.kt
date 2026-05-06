package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.notification.GitHubRefreshNotificationHelper

internal class GitHubPageRefreshNotificationBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun notifyProgress(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) {
        withContext(ioDispatcher) {
            GitHubRefreshNotificationHelper.notifyProgress(
                context = context,
                current = current,
                total = total,
                preReleaseUpdateCount = preReleaseUpdateCount,
                updatableCount = updatableCount,
                failedCount = failedCount
            )
        }
    }

    suspend fun notifyCompleted(
        context: Context,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) {
        withContext(ioDispatcher) {
            GitHubRefreshNotificationHelper.notifyCompleted(
                context = context,
                total = total,
                preReleaseUpdateCount = preReleaseUpdateCount,
                updatableCount = updatableCount,
                failedCount = failedCount
            )
        }
    }

    suspend fun notifyCancelled(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int
    ) {
        withContext(ioDispatcher) {
            GitHubRefreshNotificationHelper.notifyCancelled(
                context = context,
                current = current,
                total = total,
                preReleaseUpdateCount = preReleaseUpdateCount,
                updatableCount = updatableCount,
                failedCount = failedCount
            )
        }
    }

    fun cancel(context: Context) {
        GitHubRefreshNotificationHelper.cancel(context)
    }
}
