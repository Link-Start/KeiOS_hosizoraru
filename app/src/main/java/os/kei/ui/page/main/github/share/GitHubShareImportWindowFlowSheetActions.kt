package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

internal class GitHubShareImportWindowFlowSheetActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val flowState: GitHubShareImportWindowFlowStateHolder,
    private val installFlowCoordinator: GitHubShareImportInstallFlowCoordinator,
    private val onNavigateToGitHubPage: () -> Unit,
) {
    fun cancelPreview() {
        scope.launch {
            flowState.applyCoordinatorResult(
                GitHubShareImportFlowCoordinator.cancelActiveFlow(context),
            )
        }
        flowState.clearAfterPreviewCancel()
    }

    fun confirmImport(selectedAsset: GitHubReleaseAssetFile) {
        scope.launch {
            confirmGitHubShareImportAsset(selectedAsset)
        }
    }

    fun cancelPending() {
        scope.launch {
            flowState.applyCoordinatorResult(
                GitHubShareImportFlowCoordinator.cancelActiveFlow(context),
            )
            toast(context, R.string.github_toast_share_import_pending_cancelled)
        }
    }

    fun cancelAttach() {
        scope.launch {
            flowState.applyCoordinatorResult(
                GitHubShareImportFlowCoordinator.cancelActiveFlow(context),
            )
        }
    }

    fun confirmAttach(openGitHubAfterAttach: Boolean) {
        flowState.updateAttachSubmitting(
            submitting = true,
            confirmAndOpen = openGitHubAfterAttach,
        )
        flowState.updatePhase(GitHubShareImportPhase.AddingTrack)
        scope.launch {
            try {
                val result =
                    GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(
                        context = context,
                        prefetchLatestCheck = !openGitHubAfterAttach,
                    )
                toastAttachConfirmResult(result)
                flowState.applyCoordinatorResult(result)
                if (
                    openGitHubAfterAttach &&
                    (
                        result is ShareImportCoordinatorResult.Added ||
                            result is ShareImportCoordinatorResult.AlreadyTracked
                    )
                ) {
                    runCatching {
                        onNavigateToGitHubPage()
                    }.onFailure {
                        toast(context, R.string.common_open_link_failed)
                    }
                }
            } finally {
                flowState.updateAttachSubmitting(submitting = false, confirmAndOpen = false)
            }
        }
    }

    private suspend fun confirmGitHubShareImportAsset(selectedAsset: GitHubReleaseAssetFile) {
        val preview = flowState.pendingPreview ?: return
        flowState.updatePhase(GitHubShareImportPhase.Delivering)
        when (
            val delivery =
                installFlowCoordinator.startSelectedAssetDelivery(
                    context = context,
                    preview = preview,
                    selectedAsset = selectedAsset,
                    currentManagedProgress = flowState.managedInstallProgress,
                    onManagedInstallProgress = { progress ->
                        withContext(Dispatchers.Main.immediate) {
                            flowState.updateManagedInstallProgress(progress)
                        }
                    },
                )
        ) {
            is GitHubShareImportSelectedAssetDeliveryResult.LaunchingManagedInstall -> {
                flowState.applyCoordinatorResult(
                    ShareImportCoordinatorResult.AssetReady(
                        preview = delivery.selectedPreview,
                        sendInstallActionEnabled = delivery.selectedPreview.sendInstallActionEnabled,
                    ),
                )
                flowState.updateManagedInstallProgress(delivery.progress)
            }

            is GitHubShareImportSelectedAssetDeliveryResult.CommittingManagedInstall -> {
                flowState.updateManagedInstallProgress(delivery.progress)
            }

            is GitHubShareImportSelectedAssetDeliveryResult.Delivered -> {
                applyGitHubShareImportDelivery(
                    preview = preview,
                    delivery = delivery.result,
                    selectedAssetSizeBytes = selectedAsset.sizeBytes,
                )
            }
        }
    }

    private fun applyGitHubShareImportDelivery(
        preview: GitHubShareImportPreview,
        delivery: ShareImportDeliveryCoordinatorResult,
        selectedAssetSizeBytes: Long,
    ) {
        flowState.applyDeliveryResult(
            delivery = delivery,
            preview = preview,
            selectedAssetSizeBytes = selectedAssetSizeBytes,
        )
        when (delivery) {
            is ShareImportDeliveryCoordinatorResult.Failed -> {
                if (delivery.toastMessage.isBlank()) {
                    toast(context, delivery.toastResId)
                } else {
                    toast(context, delivery.toastResId, delivery.toastMessage)
                }
            }

            is ShareImportDeliveryCoordinatorResult.WaitingInstall -> {
                toast(
                    context,
                    R.string.github_toast_share_import_wait_install,
                    delivery.assetName,
                )
            }

            ShareImportDeliveryCoordinatorResult.Cancelled,
            is ShareImportDeliveryCoordinatorResult.InstallDetected,
            is ShareImportDeliveryCoordinatorResult.InstallReady,
            -> {
                Unit
            }
        }
    }

    private fun toastAttachConfirmResult(result: ShareImportCoordinatorResult) {
        when (result) {
            ShareImportCoordinatorResult.None,
            is ShareImportCoordinatorResult.AssetReady,
            is ShareImportCoordinatorResult.Pending,
            is ShareImportCoordinatorResult.Detected,
            is ShareImportCoordinatorResult.Cancelled,
            -> {
                Unit
            }

            is ShareImportCoordinatorResult.AlreadyTracked -> {
                toast(context, R.string.github_toast_share_import_track_exists)
            }

            is ShareImportCoordinatorResult.Failed -> {
                toast(context, R.string.github_toast_share_import_failed, result.message)
            }

            is ShareImportCoordinatorResult.Added -> {
                toast(context, R.string.github_toast_share_import_track_added, result.appLabel)
            }
        }
    }
}
