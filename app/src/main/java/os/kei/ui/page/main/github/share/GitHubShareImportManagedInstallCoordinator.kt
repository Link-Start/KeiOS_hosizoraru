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
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal object GitHubShareImportManagedInstallCoordinator {
    private var managedApkInstaller: GitHubManagedApkInstaller = GitHubShizukuPackageInstaller()
    private var manifestInfoScanner:
            suspend (GitHubReleaseAssetFile, GitHubLookupConfig) -> GitHubApkManifestInfo =
        { asset, config ->
            scanShareImportAssetManifestInfo(
                asset = asset,
                lookupConfig = config
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
        assetUrlResolver: suspend (GitHubLookupConfig, GitHubReleaseAssetFile) -> String
    ) {
        this.manifestInfoScanner = { asset, config ->
            GitHubApkManifestInfo(
                assetName = asset.name,
                packageName = packageNameScanner(asset, config).trim()
            )
        }
        this.assetUrlResolver = assetUrlResolver
    }

    fun resetTestHooks() {
        managedApkInstaller = GitHubShizukuPackageInstaller()
        manifestInfoScanner = { asset, config ->
            scanShareImportAssetManifestInfo(
                asset = asset,
                lookupConfig = config
            ).getOrDefault(GitHubApkManifestInfo(assetName = asset.name))
        }
        assetUrlResolver = { config, asset ->
            resolvePreferredAssetUrl(config, asset)
        }
    }

    suspend fun start(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {}
    ): ShareImportDeliveryCoordinatorResult = coroutineScope {
        val targetDisplayName = preview.targetDisplayName.ifBlank {
            buildShareImportTargetDisplayName(
                repo = preview.repo,
                assetName = selectedAsset.name
            )
        }
        val initialProgress = GitHubShareImportManagedInstallProgress(
            phase = GitHubShareImportPhase.Installing,
            assetName = selectedAsset.name,
            targetDisplayName = targetDisplayName,
            progressPercent = 0,
            totalBytes = selectedAsset.sizeBytes
        )
        val requestId = GitHubApkInstallRequestIds.newId(context.packageName)
        withContext(Dispatchers.IO) {
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
                    totalBytes = initialProgress.totalBytes
                )
            )
        }
        GitHubTrackStoreSignals.notifyChanged()
        onProgressUpdate(initialProgress)
        GitHubShareImportNotificationHelper.notifyInstalling(
            context = context,
            owner = preview.owner,
            repo = preview.repo,
            releaseTag = preview.releaseTag,
            assetName = selectedAsset.name,
            progressPercent = 4,
            targetDisplayName = targetDisplayName
        )
        val scannedManifestInfoDeferred = async(Dispatchers.IO) {
            manifestInfoScanner(selectedAsset, lookupConfig)
        }
        val resolvedUrlDeferred = async(Dispatchers.IO) {
            assetUrlResolver(lookupConfig, selectedAsset)
        }
        val scannedManifestInfo = scannedManifestInfoDeferred.await()
        val scannedPackageName = scannedManifestInfo.packageName.trim()
        val scannedAppLabel = scannedManifestInfo.appLabel.trim()
        val scannedVersionName = scannedManifestInfo.versionName.trim()
        val scannedVersionCode = scannedManifestInfo.versionCode.trim()
        val scannedMinSdk = scannedManifestInfo.minSdk.trim()
        val scannedTargetSdk = scannedManifestInfo.targetSdk.trim()
        val scannedNativeAbis = scannedManifestInfo.nativeAbis
            .map { abi -> abi.trim() }
            .filter { abi -> abi.isNotBlank() }
        val activeManagedInstall = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        }
        if (activeManagedInstall?.requestId != requestId) {
            resolvedUrlDeferred.cancel()
            return@coroutineScope ShareImportDeliveryCoordinatorResult.Cancelled
        }
        val resolvedDownloadUrl = resolvedUrlDeferred.await()
        val activeAfterResolve = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        }
        if (activeAfterResolve?.requestId != requestId) {
            return@coroutineScope ShareImportDeliveryCoordinatorResult.Cancelled
        }
        val request = GitHubApkInstallRequest(
            owner = preview.owner,
            repo = preview.repo,
            releaseTag = preview.releaseTag,
            projectUrl = preview.projectUrl,
            asset = selectedAsset,
            lookupConfig = lookupConfig,
            targetDisplayName = targetDisplayName,
            scannedAppLabel = scannedAppLabel,
            scannedPackageName = scannedPackageName,
            scannedVersionName = scannedVersionName,
            scannedVersionCode = scannedVersionCode,
            scannedMinSdk = scannedMinSdk,
            scannedTargetSdk = scannedTargetSdk,
            scannedNativeAbis = scannedNativeAbis,
            resolvedDownloadUrl = resolvedDownloadUrl,
            requestId = requestId
        )
        withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.saveActiveManagedInstall(
                request.toManagedInstallRecord(sessionId = -1)
            )
        }
        GitHubTrackStoreSignals.notifyChanged()

        val installResult = managedApkInstaller.stage(
            context = context,
            request = request
        ) { progress ->
            handleProgress(
                context = context,
                request = request,
                progress = progress,
                onProgressUpdate = onProgressUpdate
            )
        }
        return@coroutineScope applyResult(
            context = context,
            preview = preview,
            request = request,
            result = installResult
        )
    }

    suspend fun commitActive(
        context: Context,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {}
    ): ShareImportDeliveryCoordinatorResult {
        val appContext = context.applicationContext
        val activeRecord = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        } ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        if (activeRecord.sessionId <= 0) {
            return ShareImportDeliveryCoordinatorResult.Cancelled
        }
        val preview = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActivePreview()?.toShareImportPreview()
        } ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        val asset = preview.assets.firstOrNull { asset ->
            asset.name.equals(activeRecord.assetName, ignoreCase = true)
        } ?: preview.selectedAssetForSend ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        val lookupConfig = withContext(Dispatchers.IO) {
            GitHubTrackStore.loadLookupConfig()
        }
        val request = GitHubApkInstallRequest(
            owner = activeRecord.owner,
            repo = activeRecord.repo,
            releaseTag = activeRecord.releaseTag,
            projectUrl = activeRecord.projectUrl,
            asset = asset,
            lookupConfig = lookupConfig,
            targetDisplayName = activeRecord.targetDisplayName.ifBlank { preview.targetDisplayName },
            scannedAppLabel = activeRecord.appLabel,
            scannedPackageName = activeRecord.packageName,
            scannedVersionName = activeRecord.versionName,
            scannedVersionCode = activeRecord.versionCode,
            scannedMinSdk = activeRecord.minSdk,
            scannedTargetSdk = activeRecord.targetSdk,
            scannedNativeAbis = activeRecord.nativeAbis,
            requestId = activeRecord.requestId,
            startedAtMillis = activeRecord.startedAtMillis
        )
        val result = managedApkInstaller.commit(
            context = appContext,
            request = request,
            sessionId = activeRecord.sessionId,
            downloadedBytes = activeRecord.downloadedBytes,
            totalBytes = activeRecord.totalBytes,
            onProgress = { progress ->
                handleProgress(
                    context = appContext,
                    request = request,
                    progress = progress,
                    onProgressUpdate = onProgressUpdate
                )
            }
        )
        return applyResult(
            context = appContext,
            preview = preview,
            request = request,
            result = result
        )
    }

    suspend fun cancelActive(context: Context) {
        val activeRecord = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        } ?: return
        if (activeRecord.sessionId > 0) {
            managedApkInstaller.cancel(context.applicationContext, activeRecord.sessionId)
        }
    }

    private suspend fun handleProgress(
        context: Context,
        request: GitHubApkInstallRequest,
        progress: GitHubApkInstallProgress,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit
    ) {
        val activeRecord = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        }
        if (activeRecord?.requestId != request.requestId) {
            throw CancellationException("share import managed install cancelled")
        }
        val uiProgress = progress.toShareImportManagedInstallProgress(request)
        val nextRecord = activeRecord.copy(
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
            totalBytes = uiProgress.totalBytes
        )
        if (nextRecord != activeRecord) {
            withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.saveActiveManagedInstall(nextRecord)
            }
            GitHubTrackStoreSignals.notifyChanged()
        }
        onProgressUpdate(uiProgress)
        when (progress.stage) {
            GitHubApkInstallStage.Preparing,
            GitHubApkInstallStage.Staging -> {
                GitHubShareImportNotificationHelper.notifyInstalling(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    packageName = request.scannedPackageName,
                    versionName = request.scannedVersionName,
                    targetDisplayName = request.targetDisplayName
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
                    packageName = request.scannedPackageName,
                    versionName = request.scannedVersionName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Committing -> {
                GitHubShareImportNotificationHelper.notifyInstallCommitting(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    packageName = request.scannedPackageName,
                    versionName = request.scannedVersionName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.ReadyToCommit -> {
                GitHubShareImportNotificationHelper.notifyInstallReady(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    packageName = request.scannedPackageName,
                    versionName = request.scannedVersionName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Succeeded,
            GitHubApkInstallStage.Failed,
            GitHubApkInstallStage.Cancelled -> Unit
        }
    }

    private fun GitHubApkInstallProgress.toShareImportManagedInstallProgress(
        request: GitHubApkInstallRequest
    ): GitHubShareImportManagedInstallProgress {
        val phase = when (stage) {
            GitHubApkInstallStage.Downloading -> GitHubShareImportPhase.InstallDownloading
            GitHubApkInstallStage.ReadyToCommit -> GitHubShareImportPhase.InstallReady
            GitHubApkInstallStage.Committing -> GitHubShareImportPhase.InstallCommitting
            GitHubApkInstallStage.Preparing,
            GitHubApkInstallStage.Staging -> GitHubShareImportPhase.Installing

            GitHubApkInstallStage.Succeeded,
            GitHubApkInstallStage.Failed,
            GitHubApkInstallStage.Cancelled -> GitHubShareImportPhase.Idle
        }
        return GitHubShareImportManagedInstallProgress(
            phase = phase,
            assetName = request.asset.name,
            appLabel = request.scannedAppLabel,
            packageName = request.scannedPackageName,
            versionName = request.scannedVersionName,
            versionCode = request.scannedVersionCode,
            minSdk = request.scannedMinSdk,
            targetSdk = request.scannedTargetSdk,
            nativeAbis = request.scannedNativeAbis,
            targetDisplayName = request.targetDisplayName,
            progressPercent = boundedProgressPercent,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )
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
            is GitHubApkInstallResult.Staged -> {
                applyStaged(
                    context = context,
                    request = request,
                    result = result
                )
            }

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

    private suspend fun applyStaged(
        context: Context,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult.Staged
    ): ShareImportDeliveryCoordinatorResult {
        val activeRecord = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveManagedInstall()
        }
        if (activeRecord?.requestId != request.requestId) {
            return ShareImportDeliveryCoordinatorResult.Cancelled
        }
        val nextRecord = activeRecord.copy(
            sessionId = result.sessionId,
            appLabel = request.scannedAppLabel.ifBlank { activeRecord.appLabel },
            packageName = result.packageName.ifBlank { activeRecord.packageName },
            versionName = request.scannedVersionName.ifBlank { activeRecord.versionName },
            versionCode = request.scannedVersionCode.ifBlank { activeRecord.versionCode },
            minSdk = request.scannedMinSdk.ifBlank { activeRecord.minSdk },
            targetSdk = request.scannedTargetSdk.ifBlank { activeRecord.targetSdk },
            nativeAbis = request.scannedNativeAbis.ifEmpty { activeRecord.nativeAbis },
            progressPhase = GitHubShareImportPhase.InstallReady.name,
            progressPercent = 100,
            downloadedBytes = result.downloadedBytes,
            totalBytes = result.totalBytes
        )
        withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.saveActiveManagedInstall(nextRecord)
        }
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyInstallReady(
            context = context,
            owner = request.owner,
            repo = request.repo,
            releaseTag = request.releaseTag,
            assetName = request.asset.name,
            packageName = nextRecord.packageName,
            versionName = nextRecord.versionName,
            targetDisplayName = request.targetDisplayName
        )
        return ShareImportDeliveryCoordinatorResult.InstallReady(
            requestId = request.requestId,
            assetName = request.asset.name
        )
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
                .ifBlank { request.scannedAppLabel }
                .ifBlank { request.targetDisplayName }
                .ifBlank { packageName },
            versionName = snapshot?.versionName
                .orEmpty()
                .ifBlank { request.scannedVersionName },
            versionCode = snapshot?.versionCode
                .orEmpty()
                .ifBlank { request.scannedVersionCode },
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
            versionName = candidate.versionName,
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
            appLabel = scannedAppLabel,
            packageName = scannedPackageName,
            versionName = scannedVersionName,
            versionCode = scannedVersionCode,
            minSdk = scannedMinSdk,
            targetSdk = scannedTargetSdk,
            nativeAbis = scannedNativeAbis,
            targetDisplayName = targetDisplayName,
            sessionId = sessionId,
            progressPhase = GitHubShareImportPhase.Installing.name,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = asset.sizeBytes,
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
