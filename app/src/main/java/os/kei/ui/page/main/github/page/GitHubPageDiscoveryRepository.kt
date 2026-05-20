package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubDirectApkDirectoryIndexResolver
import os.kei.feature.github.data.remote.GitHubDirectApkJsonFallbackResolver
import os.kei.feature.github.data.remote.GitHubDirectApkVersionedDirectoryResolver
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubApkPackageNameScanner
import os.kei.feature.github.domain.GitHubDirectApkReleaseCheckSource
import os.kei.feature.github.domain.GitHubPackageRepositoryResolver
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.domain.GitHubStrategyBenchmarkService
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanResult
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.parseGithubOwnerRepoStrict

internal class GitHubPageDiscoveryRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$""")

    suspend fun buildStrategyBenchmarkTargets(
        items: List<GitHubTrackedApp>
    ): List<GitHubRepoTarget> {
        return withContext(defaultDispatcher) {
            GitHubStrategyBenchmarkService.buildTargets(items)
        }
    }

    suspend fun runStrategyBenchmark(
        targets: List<GitHubRepoTarget>,
        apiToken: String
    ): GitHubStrategyBenchmarkReport {
        return withContext(ioDispatcher) {
            GitHubStrategyBenchmarkService.compareTargets(
                targets = targets,
                apiToken = apiToken
            )
        }
    }

    suspend fun checkCredential(
        apiToken: String
    ): GitHubStrategyLoadTrace<GitHubApiCredentialStatus> {
        return withContext(ioDispatcher) {
            GitHubApiTokenReleaseStrategy(apiToken).checkCredentialTrace()
        }
    }

    suspend fun buildTrackedItem(draft: GitHubTrackEditorDraft): GitHubTrackEditorResult {
        return withContext(defaultDispatcher) {
            val sourceIdentity = when (draft.sourceMode) {
                GitHubTrackedSourceMode.GitHubRepository -> {
                    val parsed = parseGithubOwnerRepoStrict(draft.repoUrl)
                        ?: return@withContext GitHubTrackEditorResult.InvalidRepository
                    GitHubTrackEditorSourceIdentity(
                        owner = parsed.first,
                        repo = parsed.second,
                        fallbackLabel = "${parsed.first}/${parsed.second}"
                    )
                }

                GitHubTrackedSourceMode.DirectApk -> {
                    val identity = buildDirectApkTrackIdentity(draft.repoUrl)
                        ?: return@withContext GitHubTrackEditorResult.InvalidRepository
                    GitHubTrackEditorSourceIdentity(
                        owner = identity.owner,
                        repo = identity.repo,
                        fallbackLabel = identity.displayName
                    )
                }
            }
            val resolvedPackageName = draft.packageName.trim()
            if (resolvedPackageName.isNotBlank() && !packageNamePattern.matches(resolvedPackageName)) {
                return@withContext GitHubTrackEditorResult.InvalidPackageName
            }
            val matchedInstalledApp = resolvedPackageName
                .takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    draft.appList.firstOrNull { item ->
                        item.packageName.equals(packageName, ignoreCase = true)
                    }
                }
            val resolvedAppLabel = when {
                matchedInstalledApp != null -> matchedInstalledApp.label
                resolvedPackageName.isNotBlank() -> resolvedPackageName
                else -> sourceIdentity.fallbackLabel
            }
            GitHubTrackEditorResult.Ready(
                GitHubTrackedApp(
                    repoUrl = draft.repoUrl.trim(),
                    owner = sourceIdentity.owner,
                    repo = sourceIdentity.repo,
                    packageName = resolvedPackageName,
                    appLabel = resolvedAppLabel,
                    sourceMode = draft.sourceMode,
                    preferPreRelease = draft.preferPreRelease,
                    alwaysShowLatestReleaseDownloadButton = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository ->
                            draft.alwaysShowLatestReleaseDownloadButton

                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    updateIntervalMode = draft.updateIntervalMode,
                    checkActionsUpdates = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository -> draft.checkActionsUpdates
                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    actionsUpdateIntervalMode = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository -> draft.actionsUpdateIntervalMode
                        GitHubTrackedSourceMode.DirectApk ->
                            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
                    },
                    preciseApkVersionMode = draft.preciseApkVersionMode,
                    localAppType = GitHubTrackedLocalAppType.fromSystemFlag(
                        matchedInstalledApp?.isSystemApp
                    )
                )
            )
        }
    }

    suspend fun previewStarredRepositoryImport(
        request: GitHubStarredRepositoryImportRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubStarredRepositoryImportPreview> {
        return GitHubRepositoryDiscoveryService(
            source = GitHubRepositoryDiscoveryRepository(apiToken = request.apiToken),
            ioDispatcher = ioDispatcher
        ).previewStarredRepositoryImport(
            request = request,
            existingItems = existingItems
        )
    }

    suspend fun searchRepositoriesForApp(
        request: GitHubAppRepositorySearchRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubAppRepositorySearchResult> {
        return GitHubRepositoryDiscoveryService(
            source = GitHubRepositoryDiscoveryRepository(apiToken = request.apiToken),
            ioDispatcher = ioDispatcher
        ).searchRepositoriesForApp(
            request = request,
            existingItems = existingItems
        )
    }

    suspend fun scanPackageNameFromLatestStableApk(
        request: GitHubApkPackageNameScanRequest
    ): Result<GitHubApkPackageNameScanResult> {
        return withContext(ioDispatcher) {
            GitHubApkPackageNameScanner(
                GitHubApkPackageNameScanRepository()
            ).scan(request)
        }
    }

    suspend fun scanPackageNameFromDirectApk(
        repoUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkPackageNameScanResult> {
        return withContext(ioDispatcher) {
            runCatching {
                val identity = buildDirectApkTrackIdentity(repoUrl)
                    ?: error("invalid direct APK URL")
                val item = GitHubTrackedApp(
                    repoUrl = identity.url,
                    owner = identity.owner,
                    repo = identity.repo,
                    packageName = "",
                    appLabel = identity.displayName,
                    sourceMode = GitHubTrackedSourceMode.DirectApk
                )
                val asset = GitHubDirectApkReleaseCheckSource.buildDirectApkAsset(item)
                    ?: error("invalid direct APK URL")
                val jsonFallbackResolver = GitHubDirectApkJsonFallbackResolver()
                val jsonResolution = if (identity.url.endsWith(".json", ignoreCase = true)) {
                    jsonFallbackResolver.resolve(identity.url).getOrNull()
                } else {
                    null
                }
                val versionedDirectoryResolution = GitHubDirectApkVersionedDirectoryResolver()
                    .resolve(identity.url)
                    .getOrNull()
                val directoryIndexResolution = if (versionedDirectoryResolution == null) {
                    GitHubDirectApkDirectoryIndexResolver()
                        .resolve(identity.url)
                        .getOrNull()
                } else {
                    null
                }
                val companionJsonResolution = if (
                    jsonResolution == null &&
                    versionedDirectoryResolution == null &&
                    directoryIndexResolution == null
                ) {
                    jsonFallbackResolver.resolve(identity.url).getOrNull()
                } else {
                    null
                }
                val scanAsset = jsonResolution?.toAsset()
                    ?: versionedDirectoryResolution?.toAsset(asset.name)
                    ?: directoryIndexResolution?.toAsset(asset.name)
                    ?: companionJsonResolution?.toAsset()
                    ?: asset
                val manifest = GitHubApkInfoRepository().inspect(
                    asset = scanAsset,
                    lookupConfig = lookupConfig.copy(
                        selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
                        apiToken = "",
                        checkAllTrackedPreReleases = false,
                        preciseApkVersionEnabled = true
                    ),
                    forceRefresh = true
                ).getOrThrow()
                GitHubApkPackageNameScanResult(
                    owner = identity.owner,
                    repo = identity.repo,
                    releaseTag = manifest.versionName
                        .ifBlank { jsonResolution?.versionName.orEmpty() }
                        .ifBlank { companionJsonResolution?.versionName.orEmpty() }
                        .ifBlank { versionedDirectoryResolution?.version.orEmpty() }
                        .ifBlank { directoryIndexResolution?.version.orEmpty() }
                        .ifBlank { manifest.versionCode },
                    releaseUrl = identity.url,
                    assetName = manifest.assetName.ifBlank { scanAsset.name.ifBlank { identity.assetName } },
                    packageName = manifest.packageName
                )
            }
        }
    }

    suspend fun scanRepositoryFromPackage(
        request: GitHubPackageRepositoryScanRequest
    ): Result<GitHubPackageRepositoryScanResult> {
        return withContext(ioDispatcher) {
            GitHubPackageRepositoryResolver(
                discoverySource = GitHubRepositoryDiscoveryRepository(
                    apiToken = request.lookupConfig.apiToken
                ),
                packageNameScanner = GitHubApkPackageNameScanner(
                    GitHubApkPackageNameScanRepository()
                ),
                ioDispatcher = ioDispatcher
            ).scanRepositoriesForPackage(request)
        }
    }
}

private data class GitHubTrackEditorSourceIdentity(
    val owner: String,
    val repo: String,
    val fallbackLabel: String
)
