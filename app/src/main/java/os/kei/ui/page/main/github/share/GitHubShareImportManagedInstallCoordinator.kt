package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallFailureReason
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallRequestIds
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.install.GitHubManagedApkInstaller
import os.kei.feature.github.install.GitHubShizukuPackageInstaller
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal object GitHubShareImportManagedInstallCoordinator {
    private var managedApkInstaller: GitHubManagedApkInstaller = GitHubShizukuPackageInstaller()
    private val progressNotifier = GitHubManagedInstallProgressNotifier()
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock
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

    suspend fun start(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        onProgressUpdate: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult =
        coroutineScope {
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
            onProgressUpdate(initialProgress)
            GitHubShareImportNotificationHelper.notifyInstalling(
                context = context,
                owner = preview.owner,
                repo = preview.repo,
                releaseTag = preview.releaseTag,
                assetName = selectedAsset.name,
                progressPercent = 4,
                targetDisplayName = targetDisplayName,
            )
            val scannedManifestInfoDeferred =
                async(AppDispatchers.githubNetwork) {
                    manifestInfoScanner(selectedAsset, lookupConfig)
                }
            val resolvedUrlDeferred =
                async(AppDispatchers.githubNetwork) {
                    assetUrlResolver(lookupConfig, selectedAsset)
                }
            val scannedManifestInfo = scannedManifestInfoDeferred.await()
            val scannedPackageName = scannedManifestInfo.packageName.trim()
            val scannedAppLabel = scannedManifestInfo.appLabel.trim()
            val scannedVersionName = scannedManifestInfo.versionName.trim()
            val scannedVersionCode = scannedManifestInfo.versionCode.trim()
            val scannedMinSdk = scannedManifestInfo.minSdk.trim()
            val scannedTargetSdk = scannedManifestInfo.targetSdk.trim()
            val scannedNativeAbis =
                scannedManifestInfo.nativeAbis
                    .map { abi -> abi.trim() }
                    .filter { abi -> abi.isNotBlank() }
            val activeManagedInstall =
                withContext(AppDispatchers.githubNetwork) {
                    GitHubShareImportFlowStore.loadActiveManagedInstall()
                }
            if (activeManagedInstall?.requestId != requestId) {
                resolvedUrlDeferred.cancel()
                return@coroutineScope ShareImportDeliveryCoordinatorResult.Cancelled
            }
            val resolvedDownloadUrl = resolvedUrlDeferred.await()
            val activeAfterResolve =
                withContext(AppDispatchers.githubNetwork) {
                    GitHubShareImportFlowStore.loadActiveManagedInstall()
                }
            if (activeAfterResolve?.requestId != requestId) {
                return@coroutineScope ShareImportDeliveryCoordinatorResult.Cancelled
            }
            val request =
                GitHubApkInstallRequest(
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
                    requestId = requestId,
                )
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.saveActiveManagedInstall(
                    request.toManagedInstallRecord(sessionId = -1),
                )
            }
            GitHubTrackStoreSignals.notifyChanged()

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
            return@coroutineScope applyResult(
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
        val activeRecord =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveManagedInstall()
            } ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        if (activeRecord.sessionId <= 0) {
            return ShareImportDeliveryCoordinatorResult.Cancelled
        }
        val preview =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActivePreview()?.toShareImportPreview()
            } ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        val asset =
            preview.assets.firstOrNull { asset ->
                asset.name.equals(activeRecord.assetName, ignoreCase = true)
            } ?: preview.selectedAssetForSend ?: return ShareImportDeliveryCoordinatorResult.Cancelled
        val lookupConfig =
            withContext(AppDispatchers.githubNetwork) {
                GitHubTrackStore.loadLookupConfig()
            }
        val request =
            GitHubApkInstallRequest(
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
                startedAtMillis = activeRecord.startedAtMillis,
            )
        val result =
            managedApkInstaller.commit(
                context = appContext,
                request = request,
                sessionId = activeRecord.sessionId,
                downloadedBytes = activeRecord.downloadedBytes,
                totalBytes = activeRecord.totalBytes,
                onProgress = { progress ->
                    progressNotifier.handleProgress(
                        context = appContext,
                        request = request,
                        progress = progress,
                        onProgressUpdate = onProgressUpdate,
                    )
                },
            )
        return applyResult(
            context = appContext,
            preview = preview,
            request = request,
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

    private suspend fun applyResult(
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
            activeRecord.copy(
                sessionId = result.sessionId,
                appLabel =
                    result.appLabel
                        .ifBlank { request.scannedAppLabel }
                        .ifBlank { activeRecord.appLabel },
                packageName = result.packageName.ifBlank { activeRecord.packageName },
                versionName =
                    result.versionName
                        .ifBlank { request.scannedVersionName }
                        .ifBlank { activeRecord.versionName },
                versionCode =
                    result.versionCode
                        .ifBlank { request.scannedVersionCode }
                        .ifBlank { activeRecord.versionCode },
                minSdk =
                    result.minSdk
                        .ifBlank { request.scannedMinSdk }
                        .ifBlank { activeRecord.minSdk },
                targetSdk =
                    result.targetSdk
                        .ifBlank { request.scannedTargetSdk }
                        .ifBlank { activeRecord.targetSdk },
                nativeAbis = request.scannedNativeAbis.ifEmpty { activeRecord.nativeAbis },
                progressPhase = GitHubShareImportPhase.InstallReady.name,
                progressPercent = 100,
                downloadedBytes = result.downloadedBytes,
                totalBytes = result.totalBytes,
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
            GitHubPendingShareImportAttachCandidate(
                projectUrl = preview.projectUrl,
                owner = preview.owner,
                repo = preview.repo,
                packageName = packageName,
                appLabel =
                    snapshot
                        ?.appLabel
                        .orEmpty()
                        .ifBlank { result.appLabel }
                        .ifBlank { request.scannedAppLabel }
                        .ifBlank { request.targetDisplayName }
                        .ifBlank { packageName },
                versionName =
                    snapshot
                        ?.versionName
                        .orEmpty()
                        .ifBlank { request.scannedVersionName },
                versionCode =
                    snapshot
                        ?.versionCode
                        .orEmpty()
                        .ifBlank { request.scannedVersionCode },
                eventAction = managedInstallAction(context),
                detectedAtMillis = clock.nowMs(),
                firstInstallTimeMs = snapshot?.firstInstallTimeMs ?: result.firstInstallTimeMs,
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

    private fun GitHubApkInstallRequest.toManagedInstallRecord(sessionId: Int): GitHubPendingShareImportManagedInstallRecord =
        GitHubPendingShareImportManagedInstallRecord(
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
            startedAtMillis = startedAtMillis,
        )

    private fun managedInstallFailureMessage(
        context: Context,
        result: GitHubApkInstallResult.Failed,
    ): String =
        when (result.reason) {
            GitHubApkInstallFailureReason.ShizukuUnavailable -> {
                context.getString(R.string.github_share_import_error_shizuku_unavailable)
            }

            GitHubApkInstallFailureReason.ShizukuPermissionMissing -> {
                context.getString(R.string.github_share_import_error_shizuku_permission_missing)
            }

            GitHubApkInstallFailureReason.RemoteInstallPermissionMissing -> {
                context.getString(R.string.github_share_import_error_shizuku_install_permission_missing)
            }

            GitHubApkInstallFailureReason.PackageNameMissing -> {
                context.getString(R.string.github_share_import_error_app_managed_package_missing)
            }

            else -> {
                result.message.ifBlank {
                    context.getString(R.string.github_share_import_error_app_managed_install_failed)
                }
            }
        }

    private fun managedInstallAction(context: Context): String = "${context.packageName}.github.share_import.action.MANAGED_INSTALL"
}
