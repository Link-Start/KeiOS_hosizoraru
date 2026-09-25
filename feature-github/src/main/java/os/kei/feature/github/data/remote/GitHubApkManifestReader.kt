package os.kei.feature.github.data.remote

import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.engine.apk.GitHubApkInspectionEngine
import os.kei.feature.github.engine.apk.GitHubApkReadTarget
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption

class GitHubApkManifestReader(
    zipEntryReader: RemoteZipEntryReader = RemoteZipEntryReader(),
) {
    private val inspectionEngine = GitHubApkInspectionEngine(zipEntryReader)

    suspend fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
    ): Result<GitHubApkManifestInfo> {
        return inspectionEngine.inspect(
            assetName = asset.name,
            targets = resolveReadTargets(asset, lookupConfig),
            nestedArtifactArchive = asset.isPotentialNestedApkArchive(),
        )
    }

    suspend fun readPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
    ): Result<String> {
        return inspectionEngine.readPackageName(resolveReadTargets(asset, lookupConfig))
    }

    suspend fun readNestedApkPackageName(
        asset: GitHubReleaseAssetFile,
        nestedApkEntryName: String,
        lookupConfig: GitHubLookupConfig,
    ): Result<String> {
        return inspectionEngine.readNestedApkPackageName(
            targets = resolveReadTargets(asset, lookupConfig),
            nestedApkEntryName = nestedApkEntryName,
        )
    }

    suspend fun readSelectedNestedApkPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        selectNestedApkEntryNames: (List<String>) -> List<String>,
    ): Result<String> {
        return inspectionEngine.readSelectedNestedApkPackageName(
            targets = resolveReadTargets(asset, lookupConfig),
            selectNestedApkEntryNames = selectNestedApkEntryNames,
        )
    }

    suspend fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
    ): Result<ByteArray> {
        return inspectionEngine.readAndroidManifestBytes(resolveReadTargets(asset, lookupConfig))
    }

    suspend fun listEntryNames(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
    ): Result<List<String>> {
        return inspectionEngine.listEntryNames(resolveReadTargets(asset, lookupConfig))
    }

    private suspend fun resolveReadTargets(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
    ): List<GitHubApkReadTarget> {
        val token = lookupConfig.apiToken.trim()
        val canUseApiAsset =
            lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken &&
                token.isNotBlank() &&
                asset.apiAssetUrl.isNotBlank()
        val apiTarget = if (canUseApiAsset) {
            GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
                asset = asset,
                useApiAssetUrl = true,
                apiToken = token,
            ).getOrNull()?.let { url ->
                GitHubApkReadTarget(
                    url = url,
                    token = token,
                    source = GitHubReleaseAssetFetchSources.API,
                )
            }
        } else {
            null
        }
        val htmlTarget = GitHubApkReadTarget(
            url = asset.downloadUrl,
            token = lookupConfig.apiToken,
            source = GitHubReleaseAssetFetchSources.HTML,
        )
        return buildList {
            if (lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken) {
                apiTarget?.let(::add)
                add(htmlTarget)
            } else {
                add(htmlTarget)
                apiTarget?.let(::add)
            }
        }.distinctBy { target -> target.url }
    }
}
