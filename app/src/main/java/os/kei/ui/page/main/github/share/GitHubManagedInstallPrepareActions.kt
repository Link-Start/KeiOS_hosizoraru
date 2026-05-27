package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallRequestIds
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal class GitHubManagedInstallPrepareActions(
    private val manifestInfoScanner: suspend (GitHubReleaseAssetFile, GitHubLookupConfig) -> GitHubApkManifestInfo,
    private val assetUrlResolver: suspend (GitHubLookupConfig, GitHubReleaseAssetFile) -> String,
) {
    suspend fun beginStart(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
    ): GitHubManagedInstallStartState {
        val requestId = GitHubApkInstallRequestIds.newId(context.packageName)
        val startState =
            buildManagedInstallStartState(
                preview = preview,
                selectedAsset = selectedAsset,
                requestId = requestId,
            )
        withContext(AppDispatchers.githubNetwork) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.saveActiveManagedInstall(
                buildManagedInstallInitialRecord(
                    preview = preview,
                    selectedAsset = selectedAsset,
                    startState = startState,
                ),
            )
        }
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyInstalling(
            context = context,
            owner = preview.owner,
            repo = preview.repo,
            releaseTag = preview.releaseTag,
            assetName = selectedAsset.name,
            progressPercent = 4,
            targetDisplayName = startState.targetDisplayName,
        )
        return startState
    }

    suspend fun prepareRequest(
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        startState: GitHubManagedInstallStartState,
    ): GitHubApkInstallRequest? =
        coroutineScope {
            val scannedManifestInfoDeferred =
                async(AppDispatchers.githubNetwork) {
                    manifestInfoScanner(selectedAsset, lookupConfig)
                }
            val resolvedUrlDeferred =
                async(AppDispatchers.githubNetwork) {
                    assetUrlResolver(lookupConfig, selectedAsset)
                }
            val scannedManifestInfo = scannedManifestInfoDeferred.await()
            if (!activeRequestMatches(startState.requestId)) {
                resolvedUrlDeferred.cancel()
                return@coroutineScope null
            }
            val resolvedDownloadUrl = resolvedUrlDeferred.awaitActive(startState.requestId) ?: return@coroutineScope null
            val request =
                buildManagedInstallRequest(
                    preview = preview,
                    selectedAsset = selectedAsset,
                    lookupConfig = lookupConfig,
                    startState = startState,
                    manifestInfo = scannedManifestInfo,
                    resolvedDownloadUrl = resolvedDownloadUrl,
                )
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.saveActiveManagedInstall(
                    request.toManagedInstallRecord(sessionId = -1),
                )
            }
            GitHubTrackStoreSignals.notifyChanged()
            request
        }

    private suspend fun Deferred<String>.awaitActive(requestId: String): String? {
        if (!activeRequestMatches(requestId)) return null
        val resolvedDownloadUrl = await()
        if (!activeRequestMatches(requestId)) return null
        return resolvedDownloadUrl
    }

    private suspend fun activeRequestMatches(requestId: String): Boolean =
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()?.requestId == requestId
        }
}
