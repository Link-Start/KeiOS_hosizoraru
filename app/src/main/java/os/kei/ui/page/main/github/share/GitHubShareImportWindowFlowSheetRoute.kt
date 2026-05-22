@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable

@Composable
internal fun GitHubShareImportWindowFlowSheetRoute(
    snapshot: GitHubShareImportWindowFlowSnapshot,
    sheetActions: GitHubShareImportWindowFlowSheetActions,
    pendingArmedSheetVisible: Boolean,
    onMinimizeActiveFlow: (() -> Unit)?,
    onClosePendingArmedSheet: (() -> Unit)?,
) {
    GitHubShareImportWindowSheetHost(
        snapshot = snapshot,
        pendingArmedSheetVisible = pendingArmedSheetVisible,
        onMinimizeActiveFlow = { onMinimizeActiveFlow?.invoke() },
        onCancelPreview = sheetActions::cancelPreview,
        onConfirmImport = sheetActions::confirmImport,
        onClosePendingArmedSheet = {
            onClosePendingArmedSheet?.invoke()
        },
        onCancelPending = sheetActions::cancelPending,
        onCancelAttach = sheetActions::cancelAttach,
        onConfirmAttach = { sheetActions.confirmAttach(openGitHubAfterAttach = false) },
        onConfirmAttachAndOpenGitHub = { sheetActions.confirmAttach(openGitHubAfterAttach = true) },
    )
}
