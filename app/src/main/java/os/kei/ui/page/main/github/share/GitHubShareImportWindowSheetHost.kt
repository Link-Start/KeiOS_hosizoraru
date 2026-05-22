@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

@androidx.compose.runtime.Composable
internal fun GitHubShareImportWindowSheetHost(
    snapshot: GitHubShareImportWindowFlowSnapshot,
    pendingArmedSheetVisible: Boolean,
    onMinimizeActiveFlow: () -> Unit,
    onCancelPreview: () -> Unit,
    onConfirmImport: (GitHubReleaseAssetFile) -> Unit,
    onClosePendingArmedSheet: () -> Unit,
    onCancelPending: () -> Unit,
    onCancelAttach: () -> Unit,
    onConfirmAttach: () -> Unit,
    onConfirmAttachAndOpenGitHub: () -> Unit,
) {
    GitHubShareImportSheet(
        preview = snapshot.pendingPreview,
        resolving = snapshot.resolving && !snapshot.notificationOnlyIncomingResolve,
        phase = snapshot.phase,
        managedInstallProgress = snapshot.managedInstallProgress,
        onDismissRequest = {
            if (!snapshot.resolving && snapshot.pendingPreview != null) {
                onMinimizeActiveFlow()
            }
        },
        onCancel = onCancelPreview,
        onConfirmImport = onConfirmImport,
    )
    GitHubShareImportPendingSheet(
        pending =
            if (pendingArmedSheetVisible) {
                snapshot.pendingTrack
            } else {
                null
            },
        onDismissRequest = {},
        onClose = onClosePendingArmedSheet,
        onCancel = onCancelPending,
    )
    GitHubShareImportAttachConfirmSheet(
        candidate = snapshot.attachCandidate,
        duplicateExists = snapshot.attachDuplicateExists,
        submitting = snapshot.attachSubmitting,
        submittingAndOpen = snapshot.attachSubmittingAndOpen,
        allowDismiss = !snapshot.attachSubmitting,
        onDismissRequest = {
            if (!snapshot.attachSubmitting && snapshot.attachCandidate != null) {
                onMinimizeActiveFlow()
            }
        },
        onCancel = {
            if (!snapshot.attachSubmitting) {
                onCancelAttach()
            }
        },
        onConfirm = {
            if (!snapshot.attachSubmitting && snapshot.attachCandidate != null) {
                onConfirmAttach()
            }
        },
        onConfirmAndOpenGitHub = {
            if (!snapshot.attachSubmitting && snapshot.attachCandidate != null) {
                onConfirmAttachAndOpenGitHub()
            }
        },
    )
}
