package os.kei.ui.page.main.github.share

import androidx.annotation.StringRes
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

internal sealed interface ShareImportDeliveryCoordinatorResult {
    data class InstallReady(
        val requestId: String,
        val assetName: String,
    ) : ShareImportDeliveryCoordinatorResult

    data class WaitingInstall(
        val pending: GitHubPendingShareImportTrackRecord,
        val toastResId: Int,
        val assetName: String,
    ) : ShareImportDeliveryCoordinatorResult

    data class InstallDetected(
        val candidate: GitHubPendingShareImportAttachCandidate,
        val assetName: String,
    ) : ShareImportDeliveryCoordinatorResult

    data class Failed(
        val toastResId: Int,
        val toastMessage: String = "",
    ) : ShareImportDeliveryCoordinatorResult

    data object Cancelled : ShareImportDeliveryCoordinatorResult
}

internal sealed interface ShareImportCoordinatorResult {
    data object None : ShareImportCoordinatorResult

    data class AssetReady(
        val preview: GitHubShareImportPreview,
        val sendInstallActionEnabled: Boolean,
    ) : ShareImportCoordinatorResult

    data class Pending(
        val pending: GitHubPendingShareImportTrackRecord,
    ) : ShareImportCoordinatorResult

    data class Detected(
        val candidate: GitHubPendingShareImportAttachCandidate,
    ) : ShareImportCoordinatorResult

    data class Added(
        val candidate: GitHubPendingShareImportAttachCandidate,
        val appLabel: String,
    ) : ShareImportCoordinatorResult

    data class AlreadyTracked(
        val candidate: GitHubPendingShareImportAttachCandidate,
    ) : ShareImportCoordinatorResult

    data class Failed(
        val message: String,
    ) : ShareImportCoordinatorResult

    data class Cancelled(
        val result: GitHubShareImportResult?,
    ) : ShareImportCoordinatorResult
}

internal data class ShareImportIncomingCoordinatorResult(
    val coordinatorResult: ShareImportCoordinatorResult,
    val notificationFirst: Boolean,
    val sendInstallActionEnabled: Boolean = false,
    @param:StringRes val toastResId: Int? = null,
    val toastMessage: String = "",
)
