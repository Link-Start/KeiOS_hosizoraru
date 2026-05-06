package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig

internal class GitHubApkInfoRepository(
    private val manifestReader: GitHubApkManifestReader = GitHubApkManifestReader()
) {
    fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> {
        return manifestReader.inspect(asset = asset, lookupConfig = lookupConfig)
    }
}
