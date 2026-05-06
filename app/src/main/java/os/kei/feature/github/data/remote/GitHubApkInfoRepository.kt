package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

internal class GitHubApkInfoRepository(
    private val manifestReader: GitHubApkManifestReader = GitHubApkManifestReader()
) {
    fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        forceRefresh: Boolean = false
    ): Result<GitHubApkManifestInfo> {
        val cacheKey = buildInspectCacheKey(asset, lookupConfig)
        if (forceRefresh) {
            completedInspectCache.remove(cacheKey)
        }
        completedInspectCache[cacheKey]?.let { cached ->
            return Result.success(cached)
        }
        val newFuture = CompletableFuture<Result<GitHubApkManifestInfo>>()
        val activeFuture = inFlightInspectCache.putIfAbsent(cacheKey, newFuture)
        if (activeFuture != null) {
            return activeFuture.awaitInspectResult()
        }
        val result = runCatching {
            manifestReader.inspect(asset = asset, lookupConfig = lookupConfig)
        }.getOrElse { error ->
            Result.failure(error)
        }
        try {
            result.getOrNull()?.let { info ->
                if (completedInspectCache.size >= MAX_COMPLETED_INSPECT_CACHE_SIZE) {
                    completedInspectCache.clear()
                }
                completedInspectCache[cacheKey] = info
            }
            newFuture.complete(result)
        } finally {
            inFlightInspectCache.remove(cacheKey, newFuture)
        }
        return result
    }

    private fun CompletableFuture<Result<GitHubApkManifestInfo>>.awaitInspectResult(): Result<GitHubApkManifestInfo> {
        return try {
            get()
        } catch (error: ExecutionException) {
            Result.failure(error.cause ?: error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(error)
        }
    }

    private fun buildInspectCacheKey(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): String {
        return listOf(
            asset.name.trim(),
            asset.downloadUrl.trim(),
            asset.apiAssetUrl.trim(),
            lookupConfig.selectedStrategy.storageId,
            lookupConfig.apiToken.trim().hashCode().toString()
        ).joinToString("|")
    }

    private companion object {
        const val MAX_COMPLETED_INSPECT_CACHE_SIZE = 128
        val completedInspectCache = ConcurrentHashMap<String, GitHubApkManifestInfo>()
        val inFlightInspectCache =
            ConcurrentHashMap<String, CompletableFuture<Result<GitHubApkManifestInfo>>>()
    }
}
