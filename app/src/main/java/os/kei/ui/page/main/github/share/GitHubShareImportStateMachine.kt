package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo

internal fun ShareImportCoordinatorResult.toShareImportPhase(): GitHubShareImportPhase =
    when (this) {
        ShareImportCoordinatorResult.None -> GitHubShareImportPhase.Idle
        is ShareImportCoordinatorResult.AssetReady -> GitHubShareImportPhase.AssetReady
        is ShareImportCoordinatorResult.Pending -> GitHubShareImportPhase.WaitingInstall
        is ShareImportCoordinatorResult.Detected -> GitHubShareImportPhase.InstallDetected
        is ShareImportCoordinatorResult.Added -> GitHubShareImportPhase.Added
        is ShareImportCoordinatorResult.AlreadyTracked -> GitHubShareImportPhase.Added
        is ShareImportCoordinatorResult.Failed -> GitHubShareImportPhase.Failed
        is ShareImportCoordinatorResult.Cancelled -> GitHubShareImportPhase.Idle
    }

internal sealed interface GitHubShareImportActivePreviewDeliveryPlan {
    data object MissingPreview : GitHubShareImportActivePreviewDeliveryPlan

    data object InstallActionDisabled : GitHubShareImportActivePreviewDeliveryPlan

    data object MissingSelectedAsset : GitHubShareImportActivePreviewDeliveryPlan

    data class Ready(
        val preview: GitHubShareImportPreview,
        val selectedAsset: GitHubReleaseAssetFile,
    ) : GitHubShareImportActivePreviewDeliveryPlan
}

internal fun resolveActivePreviewDeliveryPlan(preview: GitHubShareImportPreview?): GitHubShareImportActivePreviewDeliveryPlan {
    if (preview == null) return GitHubShareImportActivePreviewDeliveryPlan.MissingPreview
    if (!preview.sendInstallActionEnabled) {
        return GitHubShareImportActivePreviewDeliveryPlan.InstallActionDisabled
    }
    val selectedAsset =
        preview.selectedAssetForSend
            ?: return GitHubShareImportActivePreviewDeliveryPlan.MissingSelectedAsset
    return GitHubShareImportActivePreviewDeliveryPlan.Ready(
        preview = preview,
        selectedAsset = selectedAsset,
    )
}

internal sealed interface GitHubShareImportSelectedAssetDeliveryPlan {
    data object DirectDelivery : GitHubShareImportSelectedAssetDeliveryPlan

    data class LaunchManagedInstall(
        val selectedPreview: GitHubShareImportPreview,
        val progress: GitHubShareImportManagedInstallProgress,
    ) : GitHubShareImportSelectedAssetDeliveryPlan

    data class CommitManagedInstall(
        val progress: GitHubShareImportManagedInstallProgress,
    ) : GitHubShareImportSelectedAssetDeliveryPlan
}

internal fun resolveSelectedAssetDeliveryPlan(
    preview: GitHubShareImportPreview,
    selectedAsset: GitHubReleaseAssetFile,
    appManagedShareInstallEnabled: Boolean,
    currentManagedProgress: GitHubShareImportManagedInstallProgress?,
): GitHubShareImportSelectedAssetDeliveryPlan {
    if (!appManagedShareInstallEnabled) {
        return GitHubShareImportSelectedAssetDeliveryPlan.DirectDelivery
    }
    if (currentManagedProgress?.phase == GitHubShareImportPhase.InstallReady) {
        return GitHubShareImportSelectedAssetDeliveryPlan.CommitManagedInstall(
            progress =
                currentManagedProgress.copy(
                    phase = GitHubShareImportPhase.InstallCommitting,
                    progressPercent = 92,
                ),
        )
    }
    val selectedPreview =
        preview.copy(
            selectedAssetName = selectedAsset.name,
            sendInstallActionEnabled = true,
        )
    return GitHubShareImportSelectedAssetDeliveryPlan.LaunchManagedInstall(
        selectedPreview = selectedPreview,
        progress =
            GitHubShareImportManagedInstallProgress(
                phase = GitHubShareImportPhase.Installing,
                assetName = selectedAsset.name,
                targetDisplayName = selectedPreview.targetDisplayName,
                progressPercent = 0,
                totalBytes = selectedAsset.sizeBytes,
            ),
    )
}

internal fun buildWaitingInstallTrackRecord(
    preview: GitHubShareImportPreview,
    selectedAsset: GitHubReleaseAssetFile,
    scannedManifestInfo: GitHubApkManifestInfo?,
    armedAtMillis: Long,
): GitHubPendingShareImportTrackRecord {
    val scannedPackageName = scannedManifestInfo?.packageName.orEmpty()
    val scannedVersionName = scannedManifestInfo?.versionName.orEmpty()
    return GitHubPendingShareImportTrackRecord(
        projectUrl = preview.projectUrl,
        owner = preview.owner,
        repo = preview.repo,
        releaseTag = preview.releaseTag,
        assetName = selectedAsset.name,
        packageName = scannedPackageName,
        versionName = scannedVersionName,
        targetDisplayName =
            buildShareImportTargetDisplayName(
                repo = preview.repo,
                assetName = selectedAsset.name,
                packageName = scannedPackageName,
            ).ifBlank { preview.targetDisplayName },
        armedAtMillis = armedAtMillis,
    )
}
