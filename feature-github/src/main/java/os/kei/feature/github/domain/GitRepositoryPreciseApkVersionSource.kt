package os.kei.feature.github.domain

import okhttp3.OkHttpClient
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitRepositoryTrackIdentity

internal class GitRepositoryPreciseApkVersionSource(
    identity: GitRepositoryTrackIdentity,
    client: OkHttpClient? = null,
    gitLabApiBaseUrl: String = "https://${identity.host}/api/v4",
    giteeApiBaseUrl: String = "https://gitee.com/api/v5",
    giteaApiBaseUrl: String = "https://${identity.host}/api/v1",
    private val apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository(),
) : GitHubPreciseApkVersionSource {
    private val releaseAssetSource =
        if (client == null) {
            GitRepositoryReleaseAssetSource(
                identity = identity,
                gitLabApiBaseUrl = gitLabApiBaseUrl,
                giteeApiBaseUrl = giteeApiBaseUrl,
                giteaApiBaseUrl = giteaApiBaseUrl
            )
        } else {
            GitRepositoryReleaseAssetSource(
                identity = identity,
                client = client,
                gitLabApiBaseUrl = gitLabApiBaseUrl,
                giteeApiBaseUrl = giteeApiBaseUrl,
                giteaApiBaseUrl = giteaApiBaseUrl
            )
        }

    override suspend fun loadReleaseAssetBundle(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle> =
        releaseAssetSource.loadReleaseAssetBundle(
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            lookupConfig = lookupConfig,
            includeAllAssets = false
        )

    override suspend fun inspectApk(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> {
        return apkInfoRepository.inspect(asset = asset, lookupConfig = lookupConfig)
    }
}
