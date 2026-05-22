package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubManagedApkInstaller
import os.kei.feature.github.install.GitHubShizukuPackageInstaller
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig

internal object GitHubShareImportManagedInstallCoordinator {
    private var managedApkInstaller: GitHubManagedApkInstaller = GitHubShizukuPackageInstaller()
    private val progressNotifier = GitHubManagedInstallProgressNotifier()
    private val resultApplier = GitHubManagedInstallResultApplier()
    private var manifestInfoScanner:
        suspend (GitHubReleaseAssetFile, GitHubLookupConfig) -> GitHubApkManifestInfo =
        { asset, config ->
            scanShareImportAssetManifestInfo(
                asset = asset,
                lookupConfig = config,
            ).getOrDefault(GitHubApkManifestInfo(assetName = asset.name))
        }
    private var assetUrlResolver:
        suspend (GitHubLookupConfig, GitHubReleaseAssetFile) -> String = { config, asset ->
            resolvePreferredAssetUrl(config, asset)
        }

    fun setInstallerForTesting(installer: GitHubManagedApkInstaller) {
        managedApkInstaller = installer
    }

    fun setSupportForTesting(
        packageNameScanner: suspend (GitHubReleaseAssetFile, GitHubLookupConfig) -> String,
        assetUrlResolver: suspend (GitHubLookupConfig, GitHubReleaseAssetFile) -> String,
    ) {
        this.manifestInfoScanner = { asset, config ->
            GitHubApkManifestInfo(
                assetName = asset.name,
                packageName = packageNameScanner(asset, config).trim(),
            )
        }
        this.assetUrlResolver = assetUrlResolver
    }

    fun resetTestHooks() {
        managedApkInstaller = GitHubShizukuPackageInstaller()
        manifestInfoScanner = { asset, config ->
            scanShareImportAssetManifestInfo(
                asset = asset,
                lookupConfig = config,
            ).getOrDefault(GitHubApkManifestInfo(assetName = asset.name))
        }
        assetUrlResolver = { config, asset ->
            resolvePreferredAssetUrl(config, asset)
        }
    }

    private fun prepareActions(): GitHubManagedInstallPrepareActions =
        GitHubManagedInstallPrepareActions(
            manifestInfoScanner = manifestInfoScanner,
            assetUrlResolver = assetUrlResolver,
        )

    suspend fun start(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult =
        coroutineScope {
            val prepareActions = prepareActions()
            val startState =
                prepareActions.beginStart(
                    context = context,
                    preview = preview,
                    selectedAsset = selectedAsset,
                )
            onProgressUpdate(startState.initialProgress)
            val request =
                prepareActions.prepareRequest(
                    preview = preview,
                    selectedAsset = selectedAsset,
                    lookupConfig = lookupConfig,
                    startState = startState,
                ) ?: return@coroutineScope ShareImportDeliveryCoordinatorResult.Cancelled
            val installResult =
                managedApkInstaller.stage(
                    context = context,
                    request = request,
                ) { progress ->
                    progressNotifier.handleProgress(
                        context = context,
                        request = request,
                        progress = progress,
                        onProgressUpdate = onProgressUpdate,
                    )
                }
            return@coroutineScope resultApplier.applyResult(
                context = context,
                preview = preview,
                request = request,
                result = installResult,
            )
        }

    suspend fun commitActive(
        context: Context,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult {
        val appContext = context.applicationContext
        val commitRequest =
            GitHubManagedInstallCommitActions.buildActiveCommitRequest()
                ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        val result =
            managedApkInstaller.commit(
                context = appContext,
                request = commitRequest.request,
                sessionId = commitRequest.activeRecord.sessionId,
                downloadedBytes = commitRequest.activeRecord.downloadedBytes,
                totalBytes = commitRequest.activeRecord.totalBytes,
                onProgress = { progress ->
                    progressNotifier.handleProgress(
                        context = appContext,
                        request = commitRequest.request,
                        progress = progress,
                        onProgressUpdate = onProgressUpdate,
                    )
                },
            )
        return resultApplier.applyResult(
            context = appContext,
            preview = commitRequest.preview,
            request = commitRequest.request,
            result = result,
        )
    }

    suspend fun cancelActive(context: Context) {
        val activeRecord =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveManagedInstall()
            } ?: return
        if (activeRecord.sessionId > 0) {
            managedApkInstaller.cancel(context.applicationContext, activeRecord.sessionId)
        }
    }
}
