package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.install.GitHubApkInstallProgress
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallStage
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal class GitHubManagedInstallProgressNotifier {
    suspend fun handleProgress(
        context: Context,
        request: GitHubApkInstallRequest,
        progress: GitHubApkInstallProgress,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit,
    ) {
        val activeRecord =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveManagedInstall()
            }
        if (activeRecord?.requestId != request.requestId) {
            throw CancellationException("share import managed install cancelled")
        }
        val uiProgress = progress.toShareImportManagedInstallProgress(request)
        val nextRecord =
            activeRecord.copy(
                sessionId = if (progress.sessionId > 0) progress.sessionId else activeRecord.sessionId,
                appLabel = uiProgress.appLabel.ifBlank { activeRecord.appLabel },
                packageName = uiProgress.packageName.ifBlank { activeRecord.packageName },
                versionName = uiProgress.versionName.ifBlank { activeRecord.versionName },
                versionCode = uiProgress.versionCode.ifBlank { activeRecord.versionCode },
                minSdk = uiProgress.minSdk.ifBlank { activeRecord.minSdk },
                targetSdk = uiProgress.targetSdk.ifBlank { activeRecord.targetSdk },
                nativeAbis = uiProgress.nativeAbis.ifEmpty { activeRecord.nativeAbis },
                targetDisplayName = uiProgress.targetDisplayName.ifBlank { activeRecord.targetDisplayName },
                progressPhase = uiProgress.phase.name,
                progressPercent = uiProgress.boundedProgressPercent,
                downloadedBytes = uiProgress.downloadedBytes,
                totalBytes = uiProgress.totalBytes,
            )
        if (nextRecord != activeRecord) {
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.saveActiveManagedInstall(nextRecord)
            }
            GitHubTrackStoreSignals.notifyChanged()
        }
        onProgressUpdate(uiProgress)
        notifyProgress(context, request, progress, uiProgress)
    }

    private fun notifyProgress(
        context: Context,
        request: GitHubApkInstallRequest,
        progress: GitHubApkInstallProgress,
        uiProgress: GitHubShareImportManagedInstallProgress,
    ) {
        when (progress.stage) {
            GitHubApkInstallStage.Preparing,
            GitHubApkInstallStage.Staging,
            -> {
                GitHubShareImportNotificationHelper.notifyInstalling(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    appLabel = uiProgress.appLabel,
                    packageName = uiProgress.packageName,
                    versionName = uiProgress.versionName,
                    targetDisplayName = request.targetDisplayName,
                )
            }

            GitHubApkInstallStage.Downloading -> {
                GitHubShareImportNotificationHelper.notifyInstallDownloading(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    appLabel = uiProgress.appLabel,
                    packageName = uiProgress.packageName,
                    versionName = uiProgress.versionName,
                    targetDisplayName = request.targetDisplayName,
                )
            }

            GitHubApkInstallStage.Committing -> {
                GitHubShareImportNotificationHelper.notifyInstallCommitting(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    appLabel = uiProgress.appLabel,
                    packageName = uiProgress.packageName,
                    versionName = uiProgress.versionName,
                    targetDisplayName = request.targetDisplayName,
                )
            }

            GitHubApkInstallStage.ReadyToCommit -> {
                GitHubShareImportNotificationHelper.notifyInstallReady(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    appLabel = uiProgress.appLabel,
                    packageName = uiProgress.packageName,
                    versionName = uiProgress.versionName,
                    targetDisplayName = request.targetDisplayName,
                )
            }

            GitHubApkInstallStage.Succeeded,
            GitHubApkInstallStage.Failed,
            GitHubApkInstallStage.Cancelled,
            -> {
                Unit
            }
        }
    }

    private fun GitHubApkInstallProgress.toShareImportManagedInstallProgress(
        request: GitHubApkInstallRequest,
    ): GitHubShareImportManagedInstallProgress {
        val phase =
            when (stage) {
                GitHubApkInstallStage.Downloading -> GitHubShareImportPhase.InstallDownloading

                GitHubApkInstallStage.ReadyToCommit -> GitHubShareImportPhase.InstallReady

                GitHubApkInstallStage.Committing -> GitHubShareImportPhase.InstallCommitting

                GitHubApkInstallStage.Preparing,
                GitHubApkInstallStage.Staging,
                -> GitHubShareImportPhase.Installing

                GitHubApkInstallStage.Succeeded,
                GitHubApkInstallStage.Failed,
                GitHubApkInstallStage.Cancelled,
                -> GitHubShareImportPhase.Idle
            }
        return GitHubShareImportManagedInstallProgress(
            phase = phase,
            assetName = request.asset.name,
            appLabel = appLabel.ifBlank { request.scannedAppLabel },
            packageName = packageName.ifBlank { request.scannedPackageName },
            versionName = versionName.ifBlank { request.scannedVersionName },
            versionCode = versionCode.ifBlank { request.scannedVersionCode },
            minSdk = minSdk.ifBlank { request.scannedMinSdk },
            targetSdk = targetSdk.ifBlank { request.scannedTargetSdk },
            nativeAbis = request.scannedNativeAbis,
            targetDisplayName = request.targetDisplayName,
            progressPercent = boundedProgressPercent,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
        )
    }
}
