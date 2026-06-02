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
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitRepositoryTrackIdentity

class GitHubReleaseAssetService(
    private val networkDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val localDispatcher: CoroutineDispatcher = AppDispatchers.githubLocal,
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
        withContext(localDispatcher) {
            GitHubReleaseAssetCacheStore.load(
                cacheKey = cacheKey,
                refreshIntervalHours = refreshIntervalHours,
            )
        }

    suspend fun saveAssetBundle(
        cacheKey: String,
        bundle: GitHubReleaseAssetBundle,
    ) {
        withContext(localDispatcher) {
            GitHubReleaseAssetCacheStore.save(cacheKey = cacheKey, bundle = bundle)
        }
    }

    suspend fun clearAssetCache(cacheKey: String) {
        withContext(localDispatcher) {
            GitHubReleaseAssetCacheStore.clear(cacheKey)
        }
    }

    suspend fun clearAssetCaches(cacheKeys: List<String>) {
        if (cacheKeys.isEmpty()) return
        withContext(localDispatcher) {
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
        withContext(networkDispatcher) {
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
        withContext(networkDispatcher) {
            GitHubReleaseAssetRepository.fetchReleaseNotesTargets(
                owner = owner,
                repo = repo,
                apiToken = apiToken,
            )
        }

    fun buildGitRepositoryAssetCacheKey(
        identity: GitRepositoryTrackIdentity,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): String =
        GitRepositoryReleaseAssetSource(identity = identity).buildAssetCacheKey(
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            lookupConfig = lookupConfig,
            includeAllAssets = includeAllAssets,
        )

    suspend fun fetchGitRepositoryReleaseNotesTargets(
        identity: GitRepositoryTrackIdentity,
    ): Result<List<GitHubReleaseNotesTarget>> =
        withContext(networkDispatcher) {
            GitRepositoryReleaseAssetSource(identity = identity).fetchReleaseNotesTargets()
        }

    suspend fun fetchGitRepositoryReleaseAssetBundle(
        identity: GitRepositoryTrackIdentity,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig,
        includeAllAssets: Boolean,
    ): Result<GitHubReleaseAssetBundle> =
        withContext(networkDispatcher) {
            GitRepositoryReleaseAssetSource(identity = identity).loadReleaseAssetBundle(
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                lookupConfig = lookupConfig,
                includeAllAssets = includeAllAssets,
            )
        }

    suspend fun resolvePreferredDownloadUrl(
        asset: GitHubReleaseAssetFile,
        useApiAssetUrl: Boolean,
        apiToken: String,
    ): String =
        withContext(networkDispatcher) {
            GitHubReleaseAssetRepository
                .resolvePreferredDownloadUrl(
                    asset = asset,
                    useApiAssetUrl = useApiAssetUrl,
                    apiToken = apiToken,
                )
                .getOrElse { asset.downloadUrl }
        }

    suspend fun clearAllAssetCache() {
        withContext(localDispatcher) {
            GitHubReleaseAssetCacheStore.clearAll()
        }
    }
}
