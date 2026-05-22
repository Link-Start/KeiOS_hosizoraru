@file:Suppress("ktlint:standard:filename")

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
    private var currentSnapshot by mutableStateOf(GitHubShareImportWindowFlowSnapshot())

    val snapshot: GitHubShareImportWindowFlowSnapshot
        get() = currentSnapshot

    val pendingPreview: GitHubShareImportPreview?
        get() = snapshot.pendingPreview
    val resolving: Boolean
        get() = snapshot.resolving
    val phase: GitHubShareImportPhase
        get() = snapshot.phase
    val incomingResolveRunning: Boolean
        get() = snapshot.incomingResolveRunning
    val pendingTrack: GitHubPendingShareImportTrackRecord?
        get() = snapshot.pendingTrack
    val attachCandidate: GitHubPendingShareImportAttachCandidate?
        get() = snapshot.attachCandidate
    val managedInstallProgress: GitHubShareImportManagedInstallProgress?
        get() = snapshot.managedInstallProgress
    val attachDuplicateExists: Boolean
        get() = snapshot.attachDuplicateExists
    val attachSubmitting: Boolean
        get() = snapshot.attachSubmitting
    val attachSubmittingAndOpen: Boolean
        get() = snapshot.attachSubmittingAndOpen
    val restoringActiveFlow: Boolean
        get() = snapshot.restoringActiveFlow
    val notificationOnlyIncomingResolve: Boolean
        get() = snapshot.notificationOnlyIncomingResolve

    fun pendingArmedSheetVisible(showPendingArmedSheet: Boolean): Boolean = snapshot.pendingArmedSheetVisible(showPendingArmedSheet)

    fun updateRestoringActiveFlow(value: Boolean) {
        reduce { copy(restoringActiveFlow = value) }
    }

    fun updateResolving(value: Boolean) {
        reduce { copy(resolving = value) }
    }

    fun updateIncomingResolveRunning(value: Boolean) {
        reduce { copy(incomingResolveRunning = value) }
    }

    fun updateNotificationOnlyIncomingResolve(value: Boolean) {
        reduce { copy(notificationOnlyIncomingResolve = value) }
    }

    fun updateAttachDuplicateExists(value: Boolean) {
        reduce { copy(attachDuplicateExists = value) }
    }

    fun updateAttachSubmitting(
        submitting: Boolean,
        confirmAndOpen: Boolean,
    ) {
        reduce {
            copy(
                attachSubmitting = submitting,
                attachSubmittingAndOpen = confirmAndOpen,
            )
        }
    }

    fun updateManagedInstallProgress(progress: GitHubShareImportManagedInstallProgress?) {
        reduce {
            copy(
                managedInstallProgress = progress,
                phase = progress?.phase ?: phase,
            )
        }
    }

    fun updatePhase(value: GitHubShareImportPhase) {
        reduce { copy(phase = value) }
    }

    fun clearActiveFlowForIncomingResolve() {
        reduce {
            copy(
                pendingPreview = null,
                pendingTrack = null,
                attachCandidate = null,
                managedInstallProgress = null,
                attachDuplicateExists = false,
            )
        }
    }

    fun clearAfterPreviewCancel() {
        reduce {
            copy(
                pendingPreview = null,
                managedInstallProgress = null,
                phase = GitHubShareImportPhase.Idle,
            )
        }
    }

    fun applyStoredSnapshot(snapshot: GitHubShareImportStoredFlowSnapshot) {
        reduce {
            copy(
                pendingTrack = snapshot.pendingTrack,
                pendingPreview = snapshot.preview,
                managedInstallProgress = snapshot.managedInstallProgress,
                attachCandidate = snapshot.attachCandidate,
                phase = snapshot.phase,
            )
        }
    }

    fun applyCoordinatorResult(result: ShareImportCoordinatorResult) {
        reduce { reduceCoordinatorResult(result) }
    }

    fun applyDeliveryResult(
        delivery: ShareImportDeliveryCoordinatorResult,
        preview: GitHubShareImportPreview,
        selectedAssetSizeBytes: Long,
    ) {
        reduce {
            reduceDeliveryResult(
                delivery = delivery,
                preview = preview,
                selectedAssetSizeBytes = selectedAssetSizeBytes,
            )
        }
    }

    private fun reduce(reducer: GitHubShareImportWindowFlowSnapshot.() -> GitHubShareImportWindowFlowSnapshot) {
        currentSnapshot = currentSnapshot.reducer()
    }
}

@Composable
internal fun rememberGitHubShareImportWindowFlowStateHolder(): GitHubShareImportWindowFlowStateHolder =
    remember { GitHubShareImportWindowFlowStateHolder() }
