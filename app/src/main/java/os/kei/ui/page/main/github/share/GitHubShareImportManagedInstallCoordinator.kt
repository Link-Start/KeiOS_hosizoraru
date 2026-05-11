package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallFailureReason
import os.kei.feature.github.install.GitHubApkInstallProgress
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallRequestIds
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.install.GitHubApkInstallStage
import os.kei.feature.github.install.GitHubManagedApkInstaller
import os.kei.feature.github.install.GitHubShizukuPackageInstaller
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal object GitHubShareImportManagedInstallCoordinator {
    private var managedApkInstaller: GitHubManagedApkInstaller = GitHubShizukuPackageInstaller()
    private var packageNameScanner:
            suspend (GitHubReleaseAssetFile, GitHubLookupConfig) -> String = { asset, config ->
        scanShareImportAssetPackageName(
            asset = asset,
            lookupConfig = config
        ).getOrDefault("")
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
        assetUrlResolver: suspend (GitHubLookupConfig, GitHubReleaseAssetFile) -> String
    ) {
        this.packageNameScanner = packageNameScanner
        this.assetUrlResolver = assetUrlResolver
    }

    fun resetTestHooks() {
        managedApkInstaller = GitHubShizukuPackageInstaller()
        packageNameScanner = { asset, config ->
            scanShareImportAssetPackageName(
                asset = asset,
                lookupConfig = config
            ).getOrDefault("")
        }
        assetUrlResolver = { config, asset ->
            resolvePreferredAssetUrl(config, asset)
        }
    }

    suspend fun start(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): ShareImportDeliveryCoordinatorResult = coroutineScope {
        val targetDisplayName = preview.targetDisplayName.ifBlank {
            buildShareImportTargetDisplayName(
                repo = preview.repo,
                assetName = selectedAsset.name
            )
        }
        GitHubShareImportNotificationHelper.notifyInstallDownloading(
            context = context,
            owner = preview.owner,
            repo = preview.repo,
            assetName = selectedAsset.name,
            progressPercent = 4,
            downloadedBytes = 0L,
            totalBytes = selectedAsset.sizeBytes,
            targetDisplayName = targetDisplayName
        )
        val scannedPackageNameDeferred = async(Dispatchers.IO) {
            packageNameScanner(selectedAsset, lookupConfig)
        }
        val resolvedUrlDeferred = async(Dispatchers.IO) {
            assetUrlResolver(lookupConfig, selectedAsset)
        }
        val scannedPackageName = scannedPackageNameDeferred.await()
        val request = GitHubApkInstallRequest(
            owner = preview.owner,
            repo = preview.repo,
            releaseTag = preview.releaseTag,
            projectUrl = preview.projectUrl,
            asset = selectedAsset,
            lookupConfig = lookupConfig,
            targetDisplayName = targetDisplayName,
            scannedPackageName = scannedPackageName,
            resolvedDownloadUrl = resolvedUrlDeferred.await(),
            requestId = GitHubApkInstallRequestIds.newId(context.packageName)
        )
        withContext(Dispatchers.IO) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.saveActiveManagedInstall(
                request.toManagedInstallRecord(sessionId = -1)
            )
        }
        GitHubTrackStoreSignals.notifyChanged()

        val installResult = managedApkInstaller.install(
            context = context,
            request = request
        ) { progress ->
            handleProgress(
                context = context,
                request = request,
                progress = progress
            )
        }
        return@coroutineScope applyResult(
            context = context,
            preview = preview,
            request = request,
            result = installResult
        )
    }

    private suspend fun handleProgress(
        context: Context,
        request: GitHubApkInstallRequest,
        progress: GitHubApkInstallProgress
    ) {
        val activeRecord = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        }
        if (activeRecord?.requestId != request.requestId) {
            throw CancellationException("share import managed install cancelled")
        }
        if (progress.sessionId > 0 && activeRecord.sessionId != progress.sessionId) {
            withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.saveActiveManagedInstall(
                    activeRecord.copy(sessionId = progress.sessionId)
                )
            }
        }
        when (progress.stage) {
            GitHubApkInstallStage.Preparing,
            GitHubApkInstallStage.Staging -> {
                GitHubShareImportNotificationHelper.notifyInstalling(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    packageName = request.scannedPackageName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Downloading -> {
                GitHubShareImportNotificationHelper.notifyInstallDownloading(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    packageName = request.scannedPackageName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Committing -> {
                GitHubShareImportNotificationHelper.notifyInstallCommitting(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    assetName = request.asset.name,
                    packageName = request.scannedPackageName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Succeeded,
            GitHubApkInstallStage.Failed,
            GitHubApkInstallStage.Cancelled -> Unit
        }
    }

    private suspend fun applyResult(
        context: Context,
        preview: GitHubShareImportPreview,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult
    ): ShareImportDeliveryCoordinatorResult {
        val activeRecord = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        }
        if (activeRecord?.requestId != request.requestId) {
            return ShareImportDeliveryCoordinatorResult.Cancelled
        }
        return when (result) {
            is GitHubApkInstallResult.Cancelled -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveManagedInstall()
                }
                GitHubShareImportNotificationHelper.notifyCancelled(context)
                ShareImportDeliveryCoordinatorResult.Cancelled
            }

            is GitHubApkInstallResult.Failed -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveManagedInstall()
                }
                val reason = managedInstallFailureMessage(context, result)
                GitHubShareImportNotificationHelper.notifyFailed(context, reason)
                ShareImportDeliveryCoordinatorResult.Failed(
                    toastResId = R.string.github_toast_share_import_failed,
                    toastMessage = reason
                )
            }

            is GitHubApkInstallResult.Succeeded -> {
                applySuccess(
                    context = context,
                    preview = preview,
                    request = request,
                    result = result
                )
            }
        }
    }

    private suspend fun applySuccess(
        context: Context,
        preview: GitHubShareImportPreview,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult.Succeeded
    ): ShareImportDeliveryCoordinatorResult {
        val packageName = result.packageName.trim()
        if (packageName.isBlank()) {
            withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.clearActiveManagedInstall()
            }
            val reason = context.getString(
                R.string.github_share_import_error_app_managed_package_missing
            )
            GitHubShareImportNotificationHelper.notifyFailed(context, reason)
            return ShareImportDeliveryCoordinatorResult.Failed(
                toastResId = R.string.github_toast_share_import_failed,
                toastMessage = reason
            )
        }
        val snapshot = loadInstalledPackageSnapshot(context, packageName)
        val candidate = GitHubPendingShareImportAttachCandidate(
            projectUrl = preview.projectUrl,
            owner = preview.owner,
            repo = preview.repo,
            packageName = packageName,
            appLabel = snapshot?.appLabel
                .orEmpty()
                .ifBlank { result.appLabel }
                .ifBlank { request.targetDisplayName }
                .ifBlank { packageName },
            eventAction = managedInstallAction(context),
            detectedAtMillis = System.currentTimeMillis(),
            firstInstallTimeMs = snapshot?.firstInstallTimeMs ?: result.firstInstallTimeMs
        )
        withContext(Dispatchers.IO) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.clearActiveManagedInstall()
            GitHubShareImportFlowStore.clearActivePreview()
            GitHubShareImportFlowStore.saveActiveAttachCandidate(
                candidate.toPendingAttachCandidateRecord()
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
            targetDisplayName = buildShareImportTargetDisplayName(
                appLabel = candidate.appLabel,
                repo = candidate.repo,
                packageName = candidate.packageName
            )
        )
        return ShareImportDeliveryCoordinatorResult.InstallDetected(
            candidate = candidate,
            assetName = request.asset.name
        )
    }

    private fun GitHubApkInstallRequest.toManagedInstallRecord(
        sessionId: Int
    ): GitHubPendingShareImportManagedInstallRecord {
        return GitHubPendingShareImportManagedInstallRecord(
            requestId = requestId,
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            releaseTag = releaseTag,
            assetName = asset.name,
            packageName = scannedPackageName,
            targetDisplayName = targetDisplayName,
            sessionId = sessionId,
            startedAtMillis = startedAtMillis
        )
    }

    private fun managedInstallFailureMessage(
        context: Context,
        result: GitHubApkInstallResult.Failed
    ): String {
        return when (result.reason) {
            GitHubApkInstallFailureReason.ShizukuUnavailable ->
                context.getString(R.string.github_share_import_error_shizuku_unavailable)

            GitHubApkInstallFailureReason.ShizukuPermissionMissing ->
                context.getString(R.string.github_share_import_error_shizuku_permission_missing)

            GitHubApkInstallFailureReason.RemoteInstallPermissionMissing ->
                context.getString(R.string.github_share_import_error_shizuku_install_permission_missing)

            GitHubApkInstallFailureReason.PackageNameMissing ->
                context.getString(R.string.github_share_import_error_app_managed_package_missing)

            else -> result.message.ifBlank {
                context.getString(R.string.github_share_import_error_app_managed_install_failed)
            }
        }
    }

    private fun managedInstallAction(context: Context): String {
        return "${context.packageName}.github.share_import.action.MANAGED_INSTALL"
    }
}
