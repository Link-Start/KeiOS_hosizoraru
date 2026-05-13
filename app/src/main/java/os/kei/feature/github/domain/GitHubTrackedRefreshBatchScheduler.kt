package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack

private const val DEFAULT_DIRECT_APK_REFRESH_CONCURRENCY = 2

internal data class GitHubTrackedRefreshWorkItem(
    val originalIndex: Int,
    val item: GitHubTrackedApp
)

internal object GitHubTrackedRefreshBatchScheduler {
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
                item.isGitHubRepositoryTrack() -> githubItems += workItem
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
}
