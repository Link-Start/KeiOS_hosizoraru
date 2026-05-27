package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

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
