package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

@Stable
internal class GitHubShareImportWindowFlowStateHolder {
    var pendingPreview by mutableStateOf<GitHubShareImportPreview?>(null)
        private set
    var resolving by mutableStateOf(false)
        private set
    var phase by mutableStateOf(GitHubShareImportPhase.Idle)
        private set
    var incomingResolveRunning by mutableStateOf(false)
        private set
    var pendingTrack by mutableStateOf<GitHubPendingShareImportTrackRecord?>(null)
        private set
    var attachCandidate by mutableStateOf<GitHubPendingShareImportAttachCandidate?>(null)
        private set
    var managedInstallProgress by mutableStateOf<GitHubShareImportManagedInstallProgress?>(null)
        private set
    var attachDuplicateExists by mutableStateOf(false)
        private set
    var attachSubmitting by mutableStateOf(false)
        private set
    var attachSubmittingAndOpen by mutableStateOf(false)
        private set
    var restoringActiveFlow by mutableStateOf(true)
        private set
    var notificationOnlyIncomingResolve by mutableStateOf(false)
        private set

    val snapshot: GitHubShareImportWindowFlowSnapshot
        get() =
            GitHubShareImportWindowFlowSnapshot(
                pendingPreview = pendingPreview,
                resolving = resolving,
                phase = phase,
                incomingResolveRunning = incomingResolveRunning,
                pendingTrack = pendingTrack,
                attachCandidate = attachCandidate,
                managedInstallProgress = managedInstallProgress,
                attachDuplicateExists = attachDuplicateExists,
                attachSubmitting = attachSubmitting,
                attachSubmittingAndOpen = attachSubmittingAndOpen,
                restoringActiveFlow = restoringActiveFlow,
                notificationOnlyIncomingResolve = notificationOnlyIncomingResolve,
            )

    fun pendingArmedSheetVisible(showPendingArmedSheet: Boolean): Boolean =
        showPendingArmedSheet &&
            pendingTrack != null &&
            pendingPreview == null &&
            !resolving &&
            attachCandidate == null

    fun updateRestoringActiveFlow(value: Boolean) {
        restoringActiveFlow = value
    }

    fun updateResolving(value: Boolean) {
        resolving = value
    }

    fun updateIncomingResolveRunning(value: Boolean) {
        incomingResolveRunning = value
    }

    fun updateNotificationOnlyIncomingResolve(value: Boolean) {
        notificationOnlyIncomingResolve = value
    }

    fun updateAttachDuplicateExists(value: Boolean) {
        attachDuplicateExists = value
    }

    fun updateAttachSubmitting(
        submitting: Boolean,
        confirmAndOpen: Boolean,
    ) {
        attachSubmitting = submitting
        attachSubmittingAndOpen = confirmAndOpen
    }

    fun updateManagedInstallProgress(progress: GitHubShareImportManagedInstallProgress?) {
        managedInstallProgress = progress
        progress?.let { phase = it.phase }
    }

    fun updatePhase(value: GitHubShareImportPhase) {
        phase = value
    }

    fun clearActiveFlowForIncomingResolve() {
        pendingPreview = null
        pendingTrack = null
        attachCandidate = null
        managedInstallProgress = null
        attachDuplicateExists = false
    }

    fun clearAfterPreviewCancel() {
        pendingPreview = null
        managedInstallProgress = null
        phase = GitHubShareImportPhase.Idle
    }

    fun applyStoredSnapshot(snapshot: GitHubShareImportStoredFlowSnapshot) {
        pendingTrack = snapshot.pendingTrack
        pendingPreview = snapshot.preview
        managedInstallProgress = snapshot.managedInstallProgress
        attachCandidate = snapshot.attachCandidate
        phase = snapshot.phase
    }

    fun applyCoordinatorResult(result: ShareImportCoordinatorResult) {
        phase = result.toShareImportPhase()
        when (result) {
            ShareImportCoordinatorResult.None -> {
                Unit
            }

            is ShareImportCoordinatorResult.AssetReady -> {
                pendingPreview = result.preview
                pendingTrack = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Pending -> {
                pendingTrack = result.pending
                pendingPreview = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Detected -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = result.candidate
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Added,
            is ShareImportCoordinatorResult.AlreadyTracked,
            is ShareImportCoordinatorResult.Cancelled,
            -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Failed -> {
                Unit
            }
        }
    }

    fun applyDeliveryResult(
        delivery: ShareImportDeliveryCoordinatorResult,
        preview: GitHubShareImportPreview,
        selectedAssetSizeBytes: Long,
    ) {
        managedInstallProgress = null
        when (delivery) {
            ShareImportDeliveryCoordinatorResult.Cancelled -> {
                phase = GitHubShareImportPhase.Idle
            }

            is ShareImportDeliveryCoordinatorResult.Failed -> {
                phase = GitHubShareImportPhase.Failed
            }

            is ShareImportDeliveryCoordinatorResult.InstallReady -> {
                phase = GitHubShareImportPhase.InstallReady
                managedInstallProgress =
                    GitHubShareImportManagedInstallProgress(
                        phase = GitHubShareImportPhase.InstallReady,
                        assetName = delivery.assetName,
                        targetDisplayName = preview.targetDisplayName,
                        progressPercent = 100,
                        totalBytes = selectedAssetSizeBytes,
                    )
            }

            is ShareImportDeliveryCoordinatorResult.InstallDetected -> {
                pendingTrack = null
                attachCandidate = delivery.candidate
                pendingPreview = null
                phase = GitHubShareImportPhase.InstallDetected
            }

            is ShareImportDeliveryCoordinatorResult.WaitingInstall -> {
                pendingTrack = delivery.pending
                attachCandidate = null
                pendingPreview = null
                phase = GitHubShareImportPhase.WaitingInstall
            }
        }
    }
}

internal data class GitHubShareImportWindowFlowSnapshot(
    val pendingPreview: GitHubShareImportPreview?,
    val resolving: Boolean,
    val phase: GitHubShareImportPhase,
    val incomingResolveRunning: Boolean,
    val pendingTrack: GitHubPendingShareImportTrackRecord?,
    val attachCandidate: GitHubPendingShareImportAttachCandidate?,
    val managedInstallProgress: GitHubShareImportManagedInstallProgress?,
    val attachDuplicateExists: Boolean,
    val attachSubmitting: Boolean,
    val attachSubmittingAndOpen: Boolean,
    val restoringActiveFlow: Boolean,
    val notificationOnlyIncomingResolve: Boolean,
)

@Composable
internal fun rememberGitHubShareImportWindowFlowStateHolder(): GitHubShareImportWindowFlowStateHolder =
    remember { GitHubShareImportWindowFlowStateHolder() }
