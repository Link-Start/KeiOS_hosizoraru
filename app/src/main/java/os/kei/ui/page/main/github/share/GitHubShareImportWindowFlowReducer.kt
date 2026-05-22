@file:Suppress("ktlint:standard:filename")

package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

internal data class GitHubShareImportWindowFlowSnapshot(
    val pendingPreview: GitHubShareImportPreview? = null,
    val resolving: Boolean = false,
    val phase: GitHubShareImportPhase = GitHubShareImportPhase.Idle,
    val incomingResolveRunning: Boolean = false,
    val pendingTrack: GitHubPendingShareImportTrackRecord? = null,
    val attachCandidate: GitHubPendingShareImportAttachCandidate? = null,
    val managedInstallProgress: GitHubShareImportManagedInstallProgress? = null,
    val attachDuplicateExists: Boolean = false,
    val attachSubmitting: Boolean = false,
    val attachSubmittingAndOpen: Boolean = false,
    val restoringActiveFlow: Boolean = true,
    val notificationOnlyIncomingResolve: Boolean = false,
)

internal fun GitHubShareImportWindowFlowSnapshot.pendingArmedSheetVisible(showPendingArmedSheet: Boolean): Boolean =
    showPendingArmedSheet &&
        pendingTrack != null &&
        pendingPreview == null &&
        !resolving &&
        attachCandidate == null

internal fun GitHubShareImportWindowFlowSnapshot.reduceCoordinatorResult(
    result: ShareImportCoordinatorResult,
): GitHubShareImportWindowFlowSnapshot =
    when (result) {
        ShareImportCoordinatorResult.None -> {
            copy(phase = result.toShareImportPhase())
        }

        is ShareImportCoordinatorResult.AssetReady -> {
            copy(
                pendingPreview = result.preview,
                pendingTrack = null,
                attachCandidate = null,
                managedInstallProgress = null,
                phase = result.toShareImportPhase(),
            )
        }

        is ShareImportCoordinatorResult.Pending -> {
            copy(
                pendingTrack = result.pending,
                pendingPreview = null,
                attachCandidate = null,
                managedInstallProgress = null,
                phase = result.toShareImportPhase(),
            )
        }

        is ShareImportCoordinatorResult.Detected -> {
            copy(
                pendingTrack = null,
                pendingPreview = null,
                attachCandidate = result.candidate,
                managedInstallProgress = null,
                phase = result.toShareImportPhase(),
            )
        }

        is ShareImportCoordinatorResult.Added,
        is ShareImportCoordinatorResult.AlreadyTracked,
        is ShareImportCoordinatorResult.Cancelled,
        -> {
            copy(
                pendingTrack = null,
                pendingPreview = null,
                attachCandidate = null,
                managedInstallProgress = null,
                phase = result.toShareImportPhase(),
            )
        }

        is ShareImportCoordinatorResult.Failed -> {
            copy(phase = result.toShareImportPhase())
        }
    }

internal fun GitHubShareImportWindowFlowSnapshot.reduceDeliveryResult(
    delivery: ShareImportDeliveryCoordinatorResult,
    preview: GitHubShareImportPreview,
    selectedAssetSizeBytes: Long,
): GitHubShareImportWindowFlowSnapshot =
    when (delivery) {
        ShareImportDeliveryCoordinatorResult.Cancelled -> {
            copy(
                managedInstallProgress = null,
                phase = GitHubShareImportPhase.Idle,
            )
        }

        is ShareImportDeliveryCoordinatorResult.Failed -> {
            copy(
                managedInstallProgress = null,
                phase = GitHubShareImportPhase.Failed,
            )
        }

        is ShareImportDeliveryCoordinatorResult.InstallReady -> {
            copy(
                managedInstallProgress =
                    GitHubShareImportManagedInstallProgress(
                        phase = GitHubShareImportPhase.InstallReady,
                        assetName = delivery.assetName,
                        targetDisplayName = preview.targetDisplayName,
                        progressPercent = 100,
                        totalBytes = selectedAssetSizeBytes,
                    ),
                phase = GitHubShareImportPhase.InstallReady,
            )
        }

        is ShareImportDeliveryCoordinatorResult.InstallDetected -> {
            copy(
                pendingTrack = null,
                attachCandidate = delivery.candidate,
                pendingPreview = null,
                managedInstallProgress = null,
                phase = GitHubShareImportPhase.InstallDetected,
            )
        }

        is ShareImportDeliveryCoordinatorResult.WaitingInstall -> {
            copy(
                pendingTrack = delivery.pending,
                attachCandidate = null,
                pendingPreview = null,
                managedInstallProgress = null,
                phase = GitHubShareImportPhase.WaitingInstall,
            )
        }
    }
