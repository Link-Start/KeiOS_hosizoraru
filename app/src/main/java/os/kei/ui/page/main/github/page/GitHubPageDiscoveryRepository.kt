package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryFacade
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchResult
import os.kei.feature.github.model.GitHubLookupConfig
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
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.parseGithubOwnerRepoStrict

internal class GitHubPageDiscoveryRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
) {
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$""")
    private val discoveryFacade =
        GitHubRepositoryDiscoveryFacade(
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher,
        )

    suspend fun buildStrategyBenchmarkTargets(
        items: List<GitHubTrackedApp>
    ): List<GitHubRepoTarget> = discoveryFacade.buildStrategyBenchmarkTargets(items)

    suspend fun runStrategyBenchmark(
        targets: List<GitHubRepoTarget>,
        apiToken: String
    ): GitHubStrategyBenchmarkReport =
        discoveryFacade.runStrategyBenchmark(
            targets = targets,
            apiToken = apiToken,
        )

    suspend fun checkCredential(
        apiToken: String
    ): GitHubStrategyLoadTrace<GitHubApiCredentialStatus> = discoveryFacade.checkCredential(apiToken)

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

                GitHubTrackedSourceMode.GitRepository -> {
                    val identity = buildGitRepositoryTrackIdentity(draft.repoUrl)
                        ?: return@withContext GitHubTrackEditorResult.InvalidRepository
                    GitHubTrackEditorSourceIdentity(
                        owner = identity.owner,
                        repo = identity.repo,
                        fallbackLabel = identity.displayName
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

                        GitHubTrackedSourceMode.GitRepository -> false
                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    updateIntervalMode = draft.updateIntervalMode,
                    checkActionsUpdates = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository -> draft.checkActionsUpdates
                        GitHubTrackedSourceMode.GitRepository -> false
                        GitHubTrackedSourceMode.DirectApk -> false
                    },
                    actionsUpdateIntervalMode = when (draft.sourceMode) {
                        GitHubTrackedSourceMode.GitHubRepository -> draft.actionsUpdateIntervalMode
                        GitHubTrackedSourceMode.GitRepository ->
                            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
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
    ): Result<GitHubStarredRepositoryImportPreview> =
        discoveryFacade.previewStarredRepositoryImport(
            request = request,
            existingItems = existingItems,
        )

    suspend fun searchRepositoriesForApp(
        request: GitHubAppRepositorySearchRequest,
        existingItems: List<GitHubTrackedApp>
    ): Result<GitHubAppRepositorySearchResult> =
        discoveryFacade.searchRepositoriesForApp(
            request = request,
            existingItems = existingItems,
        )

    suspend fun scanPackageNameFromLatestStableApk(
        request: GitHubApkPackageNameScanRequest
    ): Result<GitHubApkPackageNameScanResult> =
        discoveryFacade.scanPackageNameFromLatestStableApk(request)

    suspend fun scanPackageNameFromDirectApk(
        repoUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkPackageNameScanResult> =
        discoveryFacade.scanPackageNameFromDirectApk(
            repoUrl = repoUrl,
            lookupConfig = lookupConfig,
        )

    suspend fun scanRepositoryFromPackage(
        request: GitHubPackageRepositoryScanRequest
    ): Result<GitHubPackageRepositoryScanResult> =
        discoveryFacade.scanRepositoryFromPackage(request)
}

private data class GitHubTrackEditorSourceIdentity(
    val owner: String,
    val repo: String,
    val fallbackLabel: String
)
