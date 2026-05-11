package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.data.remote.GitHubVersionUtils

internal class GitHubPageAssetBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun buildReleaseUrl(owner: String, repo: String): String {
        return GitHubVersionUtils.buildReleaseUrl(owner, repo)
    }

    fun buildAssetCacheKey(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        hasApiToken: Boolean
    ): String {
        return GitHubReleaseAssetCacheStore.buildCacheKey(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets,
            hasApiToken = hasApiToken
        )
    }

    suspend fun loadAssetBundle(
        cacheKey: String,
        refreshIntervalHours: Int
    ): GitHubReleaseAssetBundle? {
        return withContext(ioDispatcher) {
            GitHubReleaseAssetCacheStore.load(
                cacheKey = cacheKey,
                refreshIntervalHours = refreshIntervalHours
            )
        }
    }

    suspend fun saveAssetBundle(
        cacheKey: String,
        bundle: GitHubReleaseAssetBundle
    ) {
        withContext(ioDispatcher) {
            GitHubReleaseAssetCacheStore.save(cacheKey = cacheKey, bundle = bundle)
        }
    }

    suspend fun clearAssetCache(cacheKey: String) {
        withContext(ioDispatcher) {
            GitHubReleaseAssetCacheStore.clear(cacheKey)
        }
    }

    suspend fun clearAssetCaches(cacheKeys: List<String>) {
        if (cacheKeys.isEmpty()) return
        withContext(ioDispatcher) {
            cacheKeys.forEach(GitHubReleaseAssetCacheStore::clear)
        }
    }

    suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        apiToken: String
    ): Result<GitHubReleaseAssetBundle> {
        return withContext(ioDispatcher) {
            GitHubReleaseAssetRepository.fetchApkAssetsAsync(
                owner = owner,
                repo = repo,
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = aggressiveFiltering,
                includeAllAssets = includeAllAssets,
                apiToken = apiToken
            )
        }
    }

    suspend fun fetchReleaseNotesTargets(
        owner: String,
        repo: String,
        apiToken: String
    ): Result<List<GitHubReleaseNotesTarget>> {
        return withContext(ioDispatcher) {
            GitHubReleaseAssetRepository.fetchReleaseNotesTargetsAsync(
                owner = owner,
                repo = repo,
                apiToken = apiToken
            )
        }
    }

    suspend fun resolvePreferredDownloadUrl(
        asset: GitHubReleaseAssetFile,
        useApiAssetUrl: Boolean,
        apiToken: String
    ): String {
        return withContext(ioDispatcher) {
            GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
                asset = asset,
                useApiAssetUrl = useApiAssetUrl,
                apiToken = apiToken
            ).getOrElse { asset.downloadUrl }
        }
    }

    suspend fun clearAllAssetCache() {
        withContext(ioDispatcher) {
            GitHubReleaseAssetCacheStore.clearAll()
        }
    }
}
