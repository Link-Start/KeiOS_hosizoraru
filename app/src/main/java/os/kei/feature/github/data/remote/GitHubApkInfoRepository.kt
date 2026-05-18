package os.kei.feature.github.data.remote

import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.GitHubSingleFlight
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.githubAssetSourceSignature
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

internal class GitHubApkInfoRepository(
    private val manifestReader: GitHubApkManifestReader = GitHubApkManifestReader()
) {
    suspend fun inspect(
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
        return inFlightInspectCache.run(cacheKey) {
            val result = runCatching {
                manifestReader.inspect(asset = asset, lookupConfig = lookupConfig)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Result.failure(error)
            }
            result.getOrNull()?.let { info ->
                if (completedInspectCache.size >= MAX_COMPLETED_INSPECT_CACHE_SIZE) {
                    completedInspectCache.clear()
                }
                completedInspectCache[cacheKey] = info
            }
            result
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
            asset.digest.trim(),
            asset.sizeBytes.toString(),
            (asset.updatedAtMillis ?: -1L).toString(),
            lookupConfig.githubAssetSourceSignature(),
            lookupConfig.selectedStrategy.storageId,
            lookupConfig.apiToken.trim()
                .takeIf { it.isNotBlank() }
                ?.let { token -> "token:${token.hashCode()}" }
                ?: "guest"
        ).joinToString("|")
    }

    private companion object {
        const val MAX_COMPLETED_INSPECT_CACHE_SIZE = 128
        val completedInspectCache = ConcurrentHashMap<String, GitHubApkManifestInfo>()
        val inFlightInspectCache = GitHubSingleFlight<String, GitHubApkManifestInfo>()
    }
}
