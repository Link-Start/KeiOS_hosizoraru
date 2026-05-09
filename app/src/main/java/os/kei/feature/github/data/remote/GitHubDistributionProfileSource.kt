package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRepositoryDistributionProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState

internal object GitHubDistributionProfileSource {
    suspend fun fetch(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long,
        availability: MutableList<GitHubRepositoryProfileSourceState>
    ): GitHubRepositoryDistributionProfile {
        val stable = request.releaseSnapshot
            ?.latestStable
            ?.takeIf { request.releaseSnapshot.hasStableRelease }
        val rawTag = stable?.rawTag.orEmpty()
        if (rawTag.isBlank()) {
            availability += skipped(
                source = GitHubRepositoryProfileSource.ReleaseAssetsApi,
                fetchedAtMillis = fetchedAtMillis,
                message = "stable release unavailable"
            )
            return buildLocal(request, fetchedAtMillis)
        }
        val bundleResult = GitHubReleaseAssetRepository.fetchApkAssetsAsync(
            owner = request.owner,
            repo = request.repo,
            rawTag = rawTag,
            releaseUrl = stable?.link.orEmpty(),
            preferHtml = request.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed,
            aggressiveFiltering = false,
            includeAllAssets = true,
            apiToken = request.lookupConfig.apiToken
        )
        val bundle = bundleResult.getOrNull()
        if (bundle == null) {
            availability += failed(
                source = GitHubRepositoryProfileSource.ReleaseAssetsApi,
                fetchedAtMillis = fetchedAtMillis,
                error = bundleResult.exceptionOrNull()
                    ?: IllegalStateException("release asset fetch failed")
            )
            return buildLocal(request, fetchedAtMillis)
        }
        val source = when (bundle.fetchSource) {
            GitHubReleaseAssetFetchSources.HTML -> GitHubRepositoryProfileSource.ReleaseAssetsHtml
            else -> GitHubRepositoryProfileSource.ReleaseAssetsApi
        }
        availability += loaded(source, fetchedAtMillis)
        val apkLikeAssets = bundle.assets.filter { it.name.androidInstallableAssetLike() }
        val bundleAssets = bundle.assets.filter { it.name.androidBundleLike() }
        val remoteApk = request.preciseStableApkVersion ?: request.precisePreReleaseApkVersion
        return GitHubRepositoryDistributionProfile(
            latestAssetCount = intField(bundle.assets.size, source, fetchedAtMillis),
            apkLikeAssetCount = intField(apkLikeAssets.size, source, fetchedAtMillis),
            androidBundleAssetCount = intField(bundleAssets.size, source, fetchedAtMillis),
            totalDownloadCount = intField(
                bundle.assets.sumOf { it.downloadCount },
                source,
                fetchedAtMillis
            ),
            assetDigestCount = intField(
                bundle.assets.count { it.digest.isNotBlank() },
                source,
                fetchedAtMillis
            ),
            hasInstallableAndroidAsset = booleanField(
                apkLikeAssets.isNotEmpty(),
                source,
                fetchedAtMillis
            ),
            latestStableApkPackageName = stringField(
                remoteApk?.packageName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionName = stringField(
                remoteApk?.versionName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionCode = longField(
                remoteApk?.versionCodeLong ?: -1L,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            )
        )
    }

    fun buildLocal(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubRepositoryDistributionProfile {
        val remoteApk = request.preciseStableApkVersion ?: request.precisePreReleaseApkVersion
        return GitHubRepositoryDistributionProfile(
            latestStableApkPackageName = stringField(
                remoteApk?.packageName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionName = stringField(
                remoteApk?.versionName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionCode = longField(
                remoteApk?.versionCodeLong ?: -1L,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            )
        )
    }
}
