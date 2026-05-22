package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallRequestIds
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal data class GitHubManagedInstallStartState(
    val requestId: String,
    val targetDisplayName: String,
    val initialProgress: GitHubShareImportManagedInstallProgress,
)

internal class GitHubManagedInstallPrepareActions(
    private val manifestInfoScanner: suspend (GitHubReleaseAssetFile, GitHubLookupConfig) -> GitHubApkManifestInfo,
    private val assetUrlResolver: suspend (GitHubLookupConfig, GitHubReleaseAssetFile) -> String,
) {
    suspend fun beginStart(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
    ): GitHubManagedInstallStartState {
        val targetDisplayName =
            preview.targetDisplayName.ifBlank {
                buildShareImportTargetDisplayName(
                    repo = preview.repo,
                    assetName = selectedAsset.name,
                )
            }
        val initialProgress =
            GitHubShareImportManagedInstallProgress(
                phase = GitHubShareImportPhase.Installing,
                assetName = selectedAsset.name,
                targetDisplayName = targetDisplayName,
                progressPercent = 0,
                totalBytes = selectedAsset.sizeBytes,
            )
        val requestId = GitHubApkInstallRequestIds.newId(context.packageName)
        withContext(AppDispatchers.githubNetwork) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.saveActiveManagedInstall(
                GitHubPendingShareImportManagedInstallRecord(
                    requestId = requestId,
                    projectUrl = preview.projectUrl,
                    owner = preview.owner,
                    repo = preview.repo,
                    releaseTag = preview.releaseTag,
                    assetName = selectedAsset.name,
                    targetDisplayName = targetDisplayName,
                    progressPhase = initialProgress.phase.name,
                    progressPercent = initialProgress.boundedProgressPercent,
                    downloadedBytes = initialProgress.downloadedBytes,
                    totalBytes = initialProgress.totalBytes,
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
            targetDisplayName = targetDisplayName,
        )
        return GitHubManagedInstallStartState(
            requestId = requestId,
            targetDisplayName = targetDisplayName,
            initialProgress = initialProgress,
        )
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
                GitHubApkInstallRequest(
                    owner = preview.owner,
                    repo = preview.repo,
                    releaseTag = preview.releaseTag,
                    projectUrl = preview.projectUrl,
                    asset = selectedAsset,
                    lookupConfig = lookupConfig,
                    targetDisplayName = startState.targetDisplayName,
                    scannedAppLabel = scannedManifestInfo.appLabel.trim(),
                    scannedPackageName = scannedManifestInfo.packageName.trim(),
                    scannedVersionName = scannedManifestInfo.versionName.trim(),
                    scannedVersionCode = scannedManifestInfo.versionCode.trim(),
                    scannedMinSdk = scannedManifestInfo.minSdk.trim(),
                    scannedTargetSdk = scannedManifestInfo.targetSdk.trim(),
                    scannedNativeAbis = scannedManifestInfo.normalizedNativeAbis(),
                    resolvedDownloadUrl = resolvedDownloadUrl,
                    requestId = startState.requestId,
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

private fun GitHubApkManifestInfo.normalizedNativeAbis(): List<String> =
    nativeAbis
        .map { abi -> abi.trim() }
        .filter { abi -> abi.isNotBlank() }
