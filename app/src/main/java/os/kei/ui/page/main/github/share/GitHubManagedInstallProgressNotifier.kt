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
        val uiProgress = buildManagedInstallProgress(request, progress)
        val nextRecord = mergeManagedInstallProgressRecord(activeRecord, request, progress)
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
}
