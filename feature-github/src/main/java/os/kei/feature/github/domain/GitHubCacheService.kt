package os.kei.feature.github.domain

import os.kei.feature.github.data.local.AppIconCache
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry

data class GitHubCacheSummaryData(
    val trackedCount: Int,
    val checkCacheCount: Int,
    val releaseAssetCacheCount: Int,
    val cacheBytes: Long,
    val configBytes: Long,
    val diskBytes: Long,
    val iconMemoryBytes: Long,
    val lastRefreshMs: Long,
    val refreshIntervalHours: Int,
)

data class GitHubAppIconCacheSummaryData(
    val memoryBytes: Long,
    val updatedAtMs: Long,
    val iconCount: Int,
)

object GitHubCacheService {
    fun clearGitHubCaches() {
        clearGitHubCheckCaches()
        AppIconCache.clear()
    }

    fun clearGitHubCheckCaches() {
        GitHubReleaseStrategyRegistry.clearAllCaches()
        GitHubTrackStore.clearCheckCache()
        GitHubTrackStoreSignals.notifyChanged()
        GitHubReleaseAssetCacheStore.clearAll()
    }

    fun clearGitHubMcpCaches() {
        clearGitHubCheckCaches()
        GitHubStarImportApkVerificationCacheStore.clearAll()
    }

    fun clearAppIconCache() {
        AppIconCache.clear()
    }

    fun loadGitHubSummaryData(): GitHubCacheSummaryData {
        val snapshot = GitHubTrackStore.loadSnapshot()
        return GitHubCacheSummaryData(
            trackedCount = snapshot.items.size,
            checkCacheCount = snapshot.checkCache.size,
            releaseAssetCacheCount = GitHubReleaseAssetCacheStore.cachedEntryCount(),
            cacheBytes = GitHubTrackStore.cacheBytesEstimated(),
            configBytes = GitHubTrackStore.configBytesEstimated(),
            diskBytes = GitHubTrackStore.actualDataBytes(),
            iconMemoryBytes = AppIconCache.estimatedMemoryBytes(),
            lastRefreshMs = snapshot.lastRefreshMs,
            refreshIntervalHours = snapshot.refreshIntervalHours,
        )
    }

    fun loadAppIconSummaryData(): GitHubAppIconCacheSummaryData =
        GitHubAppIconCacheSummaryData(
            memoryBytes = AppIconCache.estimatedMemoryBytes(),
            updatedAtMs = AppIconCache.lastUpdatedAtMs(),
            iconCount = AppIconCache.size(),
        )
}
