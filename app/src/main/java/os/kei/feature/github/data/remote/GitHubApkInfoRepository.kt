package os.kei.feature.github.data.remote

import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption

internal class GitHubApkInfoRepository(
    private val zipEntryReader: RemoteZipEntryReader = RemoteZipEntryReader()
) {
    fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> = runCatching {
        val manifestBytes = readAndroidManifestBytes(asset, lookupConfig).getOrThrow()
        val entryNames = listEntryNames(asset, lookupConfig).getOrDefault(emptyList())
        AndroidBinaryXmlPackageNameParser.parseManifestInfo(manifestBytes).getOrThrow().copy(
            assetName = asset.name,
            nativeAbis = entryNames.extractNativeAbis()
        )
    }

    private fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray> {
        return readWithApiFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readEntry(
                url = url,
                entryName = ANDROID_MANIFEST_ENTRY,
                apiToken = token
            )
        }
    }

    private fun listEntryNames(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<List<String>> {
        return readWithApiFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.listEntryNames(url = url, apiToken = token)
        }
    }

    private fun <T> readWithApiFallback(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        read: (String, String) -> Result<T>
    ): Result<T> {
        val primary = read(asset.downloadUrl, lookupConfig.apiToken)
        if (primary.isSuccess || lookupConfig.selectedStrategy != GitHubLookupStrategyOption.GitHubApiToken) {
            return primary
        }
        val token = lookupConfig.apiToken.trim()
        if (token.isBlank() || asset.apiAssetUrl.isBlank()) return primary
        return GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
            asset = asset,
            useApiAssetUrl = true,
            apiToken = token
        ).mapCatching { apiDownloadUrl ->
            read(apiDownloadUrl, token).getOrThrow()
        }.recoverCatching {
            primary.getOrThrow()
        }
    }

    private fun List<String>.extractNativeAbis(): List<String> {
        return asSequence()
            .mapNotNull { entry ->
                val parts = entry.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && parts.last().endsWith(".so")) {
                    parts[1]
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
            .toList()
    }

    companion object {
        private const val ANDROID_MANIFEST_ENTRY = "AndroidManifest.xml"
    }
}
