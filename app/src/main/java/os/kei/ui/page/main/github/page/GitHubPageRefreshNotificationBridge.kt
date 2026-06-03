package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.log.AppLogger
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import os.kei.feature.github.notification.GitHubRefreshNotificationHelper

internal class GitHubPageRefreshNotificationBridge(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork
) {
    suspend fun notifyProgress(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int,
        sessionId: Long,
        scope: GitHubRefreshScope,
        source: GitHubRefreshSource,
        totalTrackedCount: Int
    ): Boolean {
        return withContext(ioDispatcher) {
            runCatching {
                GitHubRefreshNotificationHelper.notifyProgress(
                    context = context,
                    current = current,
                    total = total,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    updatableCount = updatableCount,
                    failedCount = failedCount,
                    sessionId = sessionId,
                    scope = scope,
                    source = source,
                    totalTrackedCount = totalTrackedCount
                )
            }.getOrElse { error ->
                AppLogger.w(TAG, "github refresh progress notification failed", error)
                false
            }
        }
    }

    suspend fun notifyCompleted(
        context: Context,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int,
        sessionId: Long,
        scope: GitHubRefreshScope,
        source: GitHubRefreshSource,
        totalTrackedCount: Int
    ): Boolean {
        return withContext(ioDispatcher) {
            val posted =
                runCatching {
                    GitHubRefreshNotificationHelper.notifyCompleted(
                        context = context,
                        total = total,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        updatableCount = updatableCount,
                        failedCount = failedCount,
                        sessionId = sessionId,
                        scope = scope,
                        source = source,
                        totalTrackedCount = totalTrackedCount
                    )
                }.getOrElse { error ->
                    AppLogger.w(TAG, "github refresh completed notification failed", error)
                    false
                }
            if (!posted) {
                runCatching { GitHubRefreshNotificationHelper.cancel(context) }
                    .onFailure { error ->
                        AppLogger.w(TAG, "github refresh stale notification cancel failed", error)
                    }
            }
            posted
        }
    }

    suspend fun notifyCancelled(
        context: Context,
        current: Int,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int,
        sessionId: Long,
        scope: GitHubRefreshScope,
        source: GitHubRefreshSource,
        totalTrackedCount: Int
    ): Boolean {
        return withContext(ioDispatcher) {
            val posted =
                runCatching {
                    GitHubRefreshNotificationHelper.notifyCancelled(
                        context = context,
                        current = current,
                        total = total,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        updatableCount = updatableCount,
                        failedCount = failedCount,
                        sessionId = sessionId,
                        scope = scope,
                        source = source,
                        totalTrackedCount = totalTrackedCount
                    )
                }.getOrElse { error ->
                    AppLogger.w(TAG, "github refresh cancelled notification failed", error)
                    false
                }
            if (!posted) {
                runCatching { GitHubRefreshNotificationHelper.cancel(context) }
                    .onFailure { error ->
                        AppLogger.w(TAG, "github refresh cancelled notification cleanup failed", error)
                    }
            }
            posted
        }
    }

    fun cancel(context: Context) {
        runCatching {
            GitHubRefreshNotificationHelper.cancel(context)
        }.onFailure { error ->
            AppLogger.w(TAG, "github refresh notification cancel failed", error)
        }
    }

    private companion object {
        const val TAG = "GitHubRefreshNotifyBridge"
    }
}
