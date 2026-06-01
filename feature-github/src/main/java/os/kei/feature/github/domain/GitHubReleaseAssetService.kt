package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.data.remote.GitHubVersionUtils

class GitHubReleaseAssetService(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
) {
    fun buildReleaseUrl(owner: String, repo: String): String =
        GitHubVersionUtils.buildReleaseUrl(owner, repo)

    fun buildAssetCacheKey(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        hasApiToken: Boolean,
    ): String =
        GitHubReleaseAssetCacheStore.buildCacheKey(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = aggressiveFiltering,
            includeAllAssets = includeAllAssets,
            hasApiToken = hasApiToken,
        )

    suspend fun loadAssetBundle(
        cacheKey: String,
        refreshIntervalHours: Int,
    ): GitHubReleaseAssetBundle? =
        withContext(ioDispatcher) {
            GitHubReleaseAssetCacheStore.load(
                cacheKey = cacheKey,
                refreshIntervalHours = refreshIntervalHours,
            )
        }

    suspend fun saveAssetBundle(
        cacheKey: String,
        bundle: GitHubReleaseAssetBundle,
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
        apiToken: String,
    ): Result<GitHubReleaseAssetBundle> =
        withContext(ioDispatcher) {
            GitHubReleaseAssetRepository.fetchApkAssets(
                owner = owner,
                repo = repo,
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                preferHtml = preferHtml,
                aggressiveFiltering = aggressiveFiltering,
                includeAllAssets = includeAllAssets,
                apiToken = apiToken,
            )
        }

    suspend fun fetchReleaseNotesTargets(
        owner: String,
        repo: String,
        apiToken: String,
    ): Result<List<GitHubReleaseNotesTarget>> =
        withContext(ioDispatcher) {
            GitHubReleaseAssetRepository.fetchReleaseNotesTargets(
                owner = owner,
                repo = repo,
                apiToken = apiToken,
            )
        }

    suspend fun resolvePreferredDownloadUrl(
        asset: GitHubReleaseAssetFile,
        useApiAssetUrl: Boolean,
        apiToken: String,
    ): String =
        withContext(ioDispatcher) {
            GitHubReleaseAssetRepository
                .resolvePreferredDownloadUrl(
                    asset = asset,
                    useApiAssetUrl = useApiAssetUrl,
                    apiToken = apiToken,
                )
                .getOrElse { asset.downloadUrl }
        }

    suspend fun clearAllAssetCache() {
        withContext(ioDispatcher) {
            GitHubReleaseAssetCacheStore.clearAll()
        }
    }
}
