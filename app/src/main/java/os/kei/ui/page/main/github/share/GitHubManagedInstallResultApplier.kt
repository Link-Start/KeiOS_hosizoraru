package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal class GitHubManagedInstallResultApplier(
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    suspend fun applyResult(
        context: Context,
        preview: GitHubShareImportPreview,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult,
    ): ShareImportDeliveryCoordinatorResult {
        val activeRecord =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveManagedInstall()
            }
        if (activeRecord?.requestId != request.requestId) {
            return ShareImportDeliveryCoordinatorResult.Cancelled
        }
        return when (result) {
            is GitHubApkInstallResult.Staged -> {
                applyStaged(
                    context = context,
                    request = request,
                    result = result,
                )
            }

            is GitHubApkInstallResult.Cancelled -> {
                withContext(AppDispatchers.githubNetwork) {
                    GitHubShareImportFlowStore.clearActiveManagedInstall()
                }
                GitHubShareImportNotificationHelper.notifyCancelled(context)
                ShareImportDeliveryCoordinatorResult.Cancelled
            }

            is GitHubApkInstallResult.Failed -> {
                withContext(AppDispatchers.githubNetwork) {
                    GitHubShareImportFlowStore.clearActiveManagedInstall()
                }
                val reason = managedInstallFailureMessage(context, result)
                GitHubShareImportNotificationHelper.notifyFailed(context, reason)
                ShareImportDeliveryCoordinatorResult.Failed(
                    toastResId = R.string.github_toast_share_import_failed,
                    toastMessage = reason,
                )
            }

            is GitHubApkInstallResult.Succeeded -> {
                applySuccess(
                    context = context,
                    preview = preview,
                    request = request,
                    result = result,
                )
            }
        }
    }

    private suspend fun applyStaged(
        context: Context,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult.Staged,
    ): ShareImportDeliveryCoordinatorResult {
        val activeRecord =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveManagedInstall()
            }
        if (activeRecord?.requestId != request.requestId) {
            return ShareImportDeliveryCoordinatorResult.Cancelled
        }
        val nextRecord =
            buildStagedManagedInstallRecord(
                activeRecord = activeRecord,
                request = request,
                result = result,
            )
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.saveActiveManagedInstall(nextRecord)
        }
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyInstallReady(
            context = context,
            owner = request.owner,
            repo = request.repo,
            releaseTag = request.releaseTag,
            assetName = request.asset.name,
            appLabel = nextRecord.appLabel,
            packageName = nextRecord.packageName,
            versionName = nextRecord.versionName,
            targetDisplayName = request.targetDisplayName,
        )
        return ShareImportDeliveryCoordinatorResult.InstallReady(
            requestId = request.requestId,
            assetName = request.asset.name,
        )
    }

    private suspend fun applySuccess(
        context: Context,
        preview: GitHubShareImportPreview,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult.Succeeded,
    ): ShareImportDeliveryCoordinatorResult {
        val packageName = result.packageName.trim()
        if (packageName.isBlank()) {
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.clearActiveManagedInstall()
            }
            val reason =
                context.getString(
                    R.string.github_share_import_error_app_managed_package_missing,
                )
            GitHubShareImportNotificationHelper.notifyFailed(context, reason)
            return ShareImportDeliveryCoordinatorResult.Failed(
                toastResId = R.string.github_toast_share_import_failed,
                toastMessage = reason,
            )
        }
        val snapshot = loadInstalledPackageSnapshot(context, packageName)
        val candidate =
            buildManagedInstallAttachCandidate(
                preview = preview,
                request = request,
                result = result,
                snapshot = snapshot,
                eventAction = managedInstallAction(context),
                detectedAtMillis = clock.nowMs(),
            )
        withContext(AppDispatchers.githubNetwork) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.clearActiveManagedInstall()
            GitHubShareImportFlowStore.clearActivePreview()
            GitHubShareImportFlowStore.saveActiveAttachCandidate(
                candidate.toPendingAttachCandidateRecord(),
            )
        }
        GitHubShareImportPendingScheduler.cancel(context)
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyInstallDetected(
            context = context,
            owner = candidate.owner,
            repo = candidate.repo,
            appLabel = candidate.appLabel,
            packageName = candidate.packageName,
            versionName = candidate.versionName,
            targetDisplayName =
                buildShareImportTargetDisplayName(
                    appLabel = candidate.appLabel,
                    repo = candidate.repo,
                    packageName = candidate.packageName,
                ),
        )
        return ShareImportDeliveryCoordinatorResult.InstallDetected(
            candidate = candidate,
            assetName = request.asset.name,
        )
    }
}
