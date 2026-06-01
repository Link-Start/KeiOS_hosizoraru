package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubDirectApkDirectoryIndexResolver
import os.kei.feature.github.data.remote.GitHubDirectApkJsonFallbackResolver
import os.kei.feature.github.data.remote.GitHubDirectApkVersionedDirectoryResolver
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanResult
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.buildDirectApkTrackIdentity

class GitHubRepositoryDiscoveryFacade(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun buildStrategyBenchmarkTargets(
        items: List<GitHubTrackedApp>,
    ): List<GitHubRepoTarget> =
        withContext(defaultDispatcher) {
            GitHubStrategyBenchmarkService.buildTargets(items)
        }

    suspend fun runStrategyBenchmark(
        targets: List<GitHubRepoTarget>,
        apiToken: String,
    ): GitHubStrategyBenchmarkReport =
        withContext(ioDispatcher) {
            GitHubStrategyBenchmarkService.compareTargets(
                targets = targets,
                apiToken = apiToken,
            )
        }

    suspend fun checkCredential(apiToken: String): GitHubStrategyLoadTrace<GitHubApiCredentialStatus> =
        withContext(ioDispatcher) {
            GitHubApiTokenReleaseStrategy(apiToken).checkCredentialTrace()
        }

    suspend fun searchRepositories(
        query: String,
        apiToken: String,
        limit: Int,
    ): Result<List<GitHubRepositoryCandidate>> =
        withContext(ioDispatcher) {
            GitHubRepositoryDiscoveryRepository(apiToken = apiToken)
                .searchRepositories(query, limit)
        }

    suspend fun previewStarredRepositoryImport(
        request: GitHubStarredRepositoryImportRequest,
        existingItems: List<GitHubTrackedApp>,
    ): Result<GitHubStarredRepositoryImportPreview> =
        GitHubRepositoryDiscoveryService(
            source = GitHubRepositoryDiscoveryRepository(apiToken = request.apiToken),
            ioDispatcher = ioDispatcher,
        ).previewStarredRepositoryImport(
            request = request,
            existingItems = existingItems,
        )

    suspend fun searchRepositoriesForApp(
        request: GitHubAppRepositorySearchRequest,
        existingItems: List<GitHubTrackedApp>,
    ): Result<GitHubAppRepositorySearchResult> =
        GitHubRepositoryDiscoveryService(
            source = GitHubRepositoryDiscoveryRepository(apiToken = request.apiToken),
            ioDispatcher = ioDispatcher,
        ).searchRepositoriesForApp(
            request = request,
            existingItems = existingItems,
        )

    suspend fun scanPackageNameFromLatestStableApk(
        request: GitHubApkPackageNameScanRequest,
    ): Result<GitHubApkPackageNameScanResult> =
        withContext(ioDispatcher) {
            GitHubApkPackageNameScanner(
                GitHubApkPackageNameScanRepository(),
            ).scan(request)
        }

    suspend fun scanPackageNameFromDirectApk(
        repoUrl: String,
        lookupConfig: GitHubLookupConfig,
    ): Result<GitHubApkPackageNameScanResult> =
        withContext(ioDispatcher) {
            runCatching {
                val identity = buildDirectApkTrackIdentity(repoUrl)
                    ?: error("invalid direct APK URL")
                val item =
                    GitHubTrackedApp(
                        repoUrl = identity.url,
                        owner = identity.owner,
                        repo = identity.repo,
                        packageName = "",
                        appLabel = identity.displayName,
                        sourceMode = GitHubTrackedSourceMode.DirectApk,
                    )
                val asset = GitHubDirectApkReleaseCheckSource.buildDirectApkAsset(item)
                    ?: error("invalid direct APK URL")
                val jsonFallbackResolver = GitHubDirectApkJsonFallbackResolver()
                val jsonResolution =
                    if (identity.url.endsWith(".json", ignoreCase = true)) {
                        jsonFallbackResolver.resolve(identity.url).getOrNull()
                    } else {
                        null
                    }
                val versionedDirectoryResolution =
                    GitHubDirectApkVersionedDirectoryResolver()
                        .resolve(identity.url)
                        .getOrNull()
                val directoryIndexResolution =
                    if (versionedDirectoryResolution == null) {
                        GitHubDirectApkDirectoryIndexResolver()
                            .resolve(identity.url)
                            .getOrNull()
                    } else {
                        null
                    }
                val companionJsonResolution =
                    if (
                        jsonResolution == null &&
                        versionedDirectoryResolution == null &&
                        directoryIndexResolution == null
                    ) {
                        jsonFallbackResolver.resolve(identity.url).getOrNull()
                    } else {
                        null
                    }
                val scanAsset =
                    jsonResolution?.toAsset()
                        ?: versionedDirectoryResolution?.toAsset(asset.name)
                        ?: directoryIndexResolution?.toAsset(asset.name)
                        ?: companionJsonResolution?.toAsset()
                        ?: asset
                val manifest =
                    GitHubApkInfoRepository()
                        .inspect(
                            asset = scanAsset,
                            lookupConfig =
                                lookupConfig.copy(
                                    selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
                                    apiToken = "",
                                    checkAllTrackedPreReleases = false,
                                    preciseApkVersionEnabled = true,
                                ),
                            forceRefresh = true,
                        )
                        .getOrThrow()
                GitHubApkPackageNameScanResult(
                    owner = identity.owner,
                    repo = identity.repo,
                    releaseTag =
                        manifest.versionName
                            .ifBlank { jsonResolution?.versionName.orEmpty() }
                            .ifBlank { companionJsonResolution?.versionName.orEmpty() }
                            .ifBlank { versionedDirectoryResolution?.version.orEmpty() }
                            .ifBlank { directoryIndexResolution?.version.orEmpty() }
                            .ifBlank { manifest.versionCode },
                    releaseUrl = identity.url,
                    assetName = manifest.assetName.ifBlank { scanAsset.name.ifBlank { identity.assetName } },
                    packageName = manifest.packageName,
                )
            }
        }

    suspend fun scanRepositoryFromPackage(
        request: GitHubPackageRepositoryScanRequest,
    ): Result<GitHubPackageRepositoryScanResult> =
        withContext(ioDispatcher) {
            GitHubPackageRepositoryResolver(
                discoverySource =
                    GitHubRepositoryDiscoveryRepository(
                        apiToken = request.lookupConfig.apiToken,
                    ),
                packageNameScanner =
                    GitHubApkPackageNameScanner(
                        GitHubApkPackageNameScanRepository(),
                    ),
                ioDispatcher = ioDispatcher,
            ).scanRepositoriesForPackage(request)
        }
}
