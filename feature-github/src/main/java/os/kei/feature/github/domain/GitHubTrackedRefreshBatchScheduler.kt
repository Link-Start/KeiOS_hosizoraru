package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isGitBackedRepositoryTrack
import os.kei.feature.github.model.isDirectApkTrack

private const val DEFAULT_DIRECT_APK_REFRESH_CONCURRENCY = 2
private const val SMALL_BATCH_REFRESH_CONCURRENCY = 4
private const val MEDIUM_BATCH_REFRESH_CONCURRENCY = 6
private const val LARGE_BATCH_REFRESH_CONCURRENCY = 8
private const val MEDIUM_BATCH_THRESHOLD = 16
private const val LARGE_BATCH_THRESHOLD = 48

data class GitHubTrackedRefreshWorkItem(
    val originalIndex: Int,
    val item: GitHubTrackedApp
)

object GitHubTrackedRefreshBatchScheduler {
    fun buildFairRefreshOrder(
        trackedItems: List<GitHubTrackedApp>
    ): List<GitHubTrackedRefreshWorkItem> {
        if (trackedItems.size <= 1) {
            return trackedItems.mapIndexed { index, item ->
                GitHubTrackedRefreshWorkItem(index, item)
            }
        }
        val githubItems = ArrayDeque<GitHubTrackedRefreshWorkItem>()
        val directApkItems = ArrayDeque<GitHubTrackedRefreshWorkItem>()
        val otherItems = ArrayDeque<GitHubTrackedRefreshWorkItem>()
        trackedItems.forEachIndexed { index, item ->
            val workItem = GitHubTrackedRefreshWorkItem(index, item)
            when {
                item.isGitBackedRepositoryTrack() -> githubItems += workItem
                item.isDirectApkTrack() -> directApkItems += workItem
                else -> otherItems += workItem
            }
        }
        return buildList(trackedItems.size) {
            var preferGitHub = true
            while (githubItems.isNotEmpty() || directApkItems.isNotEmpty()) {
                val next = when {
                    preferGitHub && githubItems.isNotEmpty() -> githubItems.removeFirst()
                    !preferGitHub && directApkItems.isNotEmpty() -> directApkItems.removeFirst()
                    githubItems.isNotEmpty() -> githubItems.removeFirst()
                    else -> directApkItems.removeFirst()
                }
                add(next)
                preferGitHub = !preferGitHub
            }
            while (otherItems.isNotEmpty()) {
                add(otherItems.removeFirst())
            }
        }
    }

    fun directApkConcurrency(maxConcurrency: Int): Int {
        return maxConcurrency
            .coerceAtLeast(1)
            .coerceAtMost(DEFAULT_DIRECT_APK_REFRESH_CONCURRENCY)
    }

    fun refreshConcurrency(itemCount: Int): Int {
        if (itemCount <= 0) return 1
        val target = when {
            itemCount >= LARGE_BATCH_THRESHOLD -> LARGE_BATCH_REFRESH_CONCURRENCY
            itemCount >= MEDIUM_BATCH_THRESHOLD -> MEDIUM_BATCH_REFRESH_CONCURRENCY
            else -> SMALL_BATCH_REFRESH_CONCURRENCY
        }
        return target.coerceAtMost(itemCount)
    }
}
