package os.kei.feature.github.domain

import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.githubAssetSourceSignature

internal data class GitHubPreciseApkVersionRequest(
    val owner: String,
    val repo: String,
    val release: GitHubReleaseVersionSignals,
    val packageName: String,
    val lookupConfig: GitHubLookupConfig
)

internal interface GitHubPreciseApkVersionSource {
    suspend fun loadReleaseAssetBundle(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle>

    suspend fun inspectApk(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo>
}

internal class GitHubPreciseApkVersionResolver(
    private val source: GitHubPreciseApkVersionSource = DefaultGitHubPreciseApkVersionSource()
) {
    suspend fun resolve(request: GitHubPreciseApkVersionRequest): Result<GitHubRemoteApkVersionInfo> =
        runCatching {
            val rawTag = request.release.rawTag.trim().ifBlank {
                GitHubReleaseAssetRepository.parseReleaseTagFromUrl(request.release.link)
            }
            check(rawTag.isNotBlank()) { "Release tag is required for precise APK version" }
            val releaseUrl = request.release.link.trim().ifBlank {
                GitHubVersionUtils.buildReleaseTagUrl(request.owner, request.repo, rawTag)
            }
            val bundle = source.loadReleaseAssetBundle(
                owner = request.owner,
                repo = request.repo,
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                lookupConfig = request.lookupConfig
            ).getOrThrow()
            val apkAssets = bundle.assets
                .filter { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
                .take(MAX_APK_INSPECT_CANDIDATES)
            check(apkAssets.isNotEmpty()) { "Release contains no APK asset" }

            val inspected = GitHubExecution.mapOrderedBounded(
                items = apkAssets,
                maxConcurrency = MAX_PARALLEL_APK_INSPECTS
            ) { asset ->
                asset to source.inspectApk(asset = asset, lookupConfig = request.lookupConfig)
            }
            val requestedPackageName = request.packageName.trim()
            val firstSuccess = inspected.firstNotNullOfOrNull { (asset, result) ->
                result.getOrNull()?.takeIf { it.hasRemoteVersion() }?.let { info -> asset to info }
            }
            val matchedSuccess = inspected.firstNotNullOfOrNull { (asset, result) ->
                result.getOrNull()
                    ?.takeIf { info ->
                        info.hasRemoteVersion() &&
                                requestedPackageName.isNotBlank() &&
                                info.packageName.equals(requestedPackageName, ignoreCase = true)
                    }
                    ?.let { info -> asset to info }
            }
            val selected = matchedSuccess ?: firstSuccess
            if (selected == null) {
                throw inspected.firstNotNullOfOrNull { (_, result) -> result.exceptionOrNull() }
                    ?: IllegalStateException("No APK manifest could be inspected")
            }
            val (asset, info) = selected

            GitHubRemoteApkVersionInfo(
                releaseName = bundle.releaseName.ifBlank { request.release.rawName },
                releaseTag = bundle.tagName.ifBlank { rawTag },
                releaseUrl = bundle.htmlUrl.ifBlank { releaseUrl },
                assetName = asset.name,
                packageName = info.packageName,
                versionName = info.versionName,
                versionCode = info.versionCode,
                fetchSource = info.fetchSource.ifBlank { bundle.fetchSource }
            )
        }

    private fun GitHubApkManifestInfo.hasRemoteVersion(): Boolean {
        return versionName.isNotBlank() || versionCode.isNotBlank()
    }

    private companion object {
        const val MAX_APK_INSPECT_CANDIDATES = 12
        const val MAX_PARALLEL_APK_INSPECTS = 4
    }
}

private class DefaultGitHubPreciseApkVersionSource(
    private val apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository()
) : GitHubPreciseApkVersionSource {
    override suspend fun loadReleaseAssetBundle(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle> = runCatching {
        val preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
        val cacheKey = GitHubReleaseAssetCacheStore.buildCacheKey(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
            includeAllAssets = false,
            hasApiToken = lookupConfig.apiToken.isNotBlank()
        )
        val sourceSignature = lookupConfig.githubAssetSourceSignature()
        val refreshIntervalHours = GitHubTrackStore.loadRefreshIntervalHours()
        val cached = GitHubReleaseAssetCacheStore.load(
            cacheKey = cacheKey,
            refreshIntervalHours = refreshIntervalHours
        )
        if (cached != null) {
            val signed = cached.copy(sourceConfigSignature = sourceSignature)
            if (cached.sourceConfigSignature != sourceSignature) {
                GitHubReleaseAssetCacheStore.save(cacheKey = cacheKey, bundle = signed)
            }
            return@runCatching signed
        }

        GitHubReleaseAssetRepository.fetchApkAssetsAsync(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
            includeAllAssets = false,
            apiToken = lookupConfig.apiToken
        ).getOrThrow().copy(
            sourceConfigSignature = sourceSignature
        ).also { bundle ->
            GitHubReleaseAssetCacheStore.save(cacheKey = cacheKey, bundle = bundle)
        }
    }

    override suspend fun inspectApk(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> {
        return apkInfoRepository.inspectAsync(asset = asset, lookupConfig = lookupConfig)
    }
}
