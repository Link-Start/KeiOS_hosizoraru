package os.kei.feature.github.data.remote

import os.kei.feature.github.domain.GitHubScanReleaseTarget
import os.kei.feature.github.domain.scanPreferHtmlAssets
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot

interface GitHubPackageScanReleaseSource {
    suspend fun loadSnapshot(owner: String, repo: String, config: GitHubLookupConfig): Result<GitHubRepositoryReleaseSnapshot>
    suspend fun fetchLatestStableApkAssets(owner: String, repo: String, config: GitHubLookupConfig): Result<GitHubReleaseAssetBundle>
    suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        release: GitHubScanReleaseTarget,
        config: GitHubLookupConfig,
    ): Result<GitHubReleaseAssetBundle>
}

internal object RemoteGitHubPackageScanReleaseSource : GitHubPackageScanReleaseSource {
    override suspend fun loadSnapshot(owner: String, repo: String, config: GitHubLookupConfig) = when (config.selectedStrategy) {
        GitHubLookupStrategyOption.AtomFeed -> GitHubAtomReleaseStrategy.loadSnapshot(owner, repo)
        GitHubLookupStrategyOption.GitHubApiToken -> GitHubApiTokenReleaseStrategy(config.apiToken).loadSnapshot(owner, repo)
    }

    override suspend fun fetchLatestStableApkAssets(owner: String, repo: String, config: GitHubLookupConfig) =
        GitHubReleaseAssetRepository.fetchLatestStableApkAssets(
            owner = owner,
            repo = repo,
            aggressiveFiltering = config.aggressiveApkFiltering,
            apiToken = config.apiToken,
        )

    override suspend fun fetchApkAssets(owner: String, repo: String, release: GitHubScanReleaseTarget, config: GitHubLookupConfig) =
        GitHubReleaseAssetRepository.fetchApkAssets(
            owner = owner,
            repo = repo,
            rawTag = release.tag,
            releaseUrl = release.releaseUrl.ifBlank { GitHubVersionUtils.buildReleaseTagUrl(owner, repo, release.tag) },
            preferHtml = config.scanPreferHtmlAssets,
            aggressiveFiltering = config.aggressiveApkFiltering,
            includeAllAssets = false,
            apiToken = config.apiToken,
        )
}
