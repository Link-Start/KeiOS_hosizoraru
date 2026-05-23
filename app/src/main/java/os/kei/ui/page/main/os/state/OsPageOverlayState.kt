package os.kei.ui.page.main.os.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.createDefaultShellCommandCardDraft
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.createDefaultActivityShortcutDraft
import os.kei.ui.page.main.os.transfer.OsCardImportPreview

internal enum class OsCardImportTarget {
    Activity,
    Shell,
}

internal data class OsPageOverlayState(
    val activityShortcutDraft: OsGoogleSystemServiceConfig,
    val onActivityShortcutDraftChange: (OsGoogleSystemServiceConfig) -> Unit,
    val showActivityShortcutEditor: Boolean,
    val onShowActivityShortcutEditorChange: (Boolean) -> Unit,
    val activityCardEditMode: OsActivityCardEditMode,
    val onActivityCardEditModeChange: (OsActivityCardEditMode) -> Unit,
    val editingActivityShortcutCardId: String?,
    val onEditingActivityShortcutCardIdChange: (String?) -> Unit,
    val editingActivityShortcutBuiltIn: Boolean,
    val onEditingActivityShortcutBuiltInChange: (Boolean) -> Unit,
    val showCardManager: Boolean,
    val onShowCardManagerChange: (Boolean) -> Unit,
    val showActivityVisibilityManager: Boolean,
    val onShowActivityVisibilityManagerChange: (Boolean) -> Unit,
    val showShellCardVisibilityManager: Boolean,
    val onShowShellCardVisibilityManagerChange: (Boolean) -> Unit,
    val showShellCommandCardEditor: Boolean,
    val onShowShellCommandCardEditorChange: (Boolean) -> Unit,
    val editingShellCommandCardId: String?,
    val onEditingShellCommandCardIdChange: (String?) -> Unit,
    val shellCommandCardDraft: OsShellCommandCard,
    val onShellCommandCardDraftChange: (OsShellCommandCard) -> Unit,
    val showShellCardDeleteConfirm: Boolean,
    val onShowShellCardDeleteConfirmChange: (Boolean) -> Unit,
    val showActivityCardDeleteConfirm: Boolean,
    val onShowActivityCardDeleteConfirmChange: (Boolean) -> Unit,
    val pendingExportContent: String?,
    val onPendingExportContentChange: (String?) -> Unit,
    val pendingImportTarget: OsCardImportTarget?,
    val onPendingImportTargetChange: (OsCardImportTarget?) -> Unit,
    val pendingCardImportPreview: OsCardImportPreview?,
    val onPendingCardImportPreviewChange: (OsCardImportPreview?) -> Unit,
    val cardTransferInProgress: Boolean,
    val onCardTransferInProgressChange: (Boolean) -> Unit,
)

@Composable
internal fun rememberOsPageOverlayState(
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    showCardManager: Boolean,
    onShowCardManagerChange: (Boolean) -> Unit,
    showActivityVisibilityManager: Boolean,
    onShowActivityVisibilityManagerChange: (Boolean) -> Unit,
    showShellCardVisibilityManager: Boolean,
    onShowShellCardVisibilityManagerChange: (Boolean) -> Unit,
    showActivityShortcutEditor: Boolean,
    onShowActivityShortcutEditorChange: (Boolean) -> Unit,
    activityCardEditMode: OsActivityCardEditMode,
    onActivityCardEditModeChange: (OsActivityCardEditMode) -> Unit,
    editingActivityShortcutCardId: String?,
    onEditingActivityShortcutCardIdChange: (String?) -> Unit,
    editingActivityShortcutBuiltIn: Boolean,
    onEditingActivityShortcutBuiltInChange: (Boolean) -> Unit,
    showShellCommandCardEditor: Boolean,
    onShowShellCommandCardEditorChange: (Boolean) -> Unit,
    editingShellCommandCardId: String?,
    onEditingShellCommandCardIdChange: (String?) -> Unit,
    showShellCardDeleteConfirm: Boolean,
    onShowShellCardDeleteConfirmChange: (Boolean) -> Unit,
    showActivityCardDeleteConfirm: Boolean,
    onShowActivityCardDeleteConfirmChange: (Boolean) -> Unit,
    pendingExportContent: String?,
    onPendingExportContentChange: (String?) -> Unit,
    pendingImportTarget: OsCardImportTarget?,
    onPendingImportTargetChange: (OsCardImportTarget?) -> Unit,
    pendingCardImportPreview: OsCardImportPreview?,
    onPendingCardImportPreviewChange: (OsCardImportPreview?) -> Unit,
    cardTransferInProgress: Boolean,
    onCardTransferInProgressChange: (Boolean) -> Unit,
): OsPageOverlayState {
    var activityShortcutDraft by remember {
        mutableStateOf(createDefaultActivityShortcutDraft(googleSystemServiceDefaults))
    }
    var shellCommandCardDraft by remember { mutableStateOf(createDefaultShellCommandCardDraft()) }
    return remember(
        activityShortcutDraft,
        showActivityShortcutEditor,
        onShowActivityShortcutEditorChange,
        activityCardEditMode,
        onActivityCardEditModeChange,
        editingActivityShortcutCardId,
        onEditingActivityShortcutCardIdChange,
        editingActivityShortcutBuiltIn,
        onEditingActivityShortcutBuiltInChange,
        showCardManager,
        onShowCardManagerChange,
        showActivityVisibilityManager,
        onShowActivityVisibilityManagerChange,
        showShellCardVisibilityManager,
        onShowShellCardVisibilityManagerChange,
        showShellCommandCardEditor,
        onShowShellCommandCardEditorChange,
        editingShellCommandCardId,
        onEditingShellCommandCardIdChange,
        shellCommandCardDraft,
        showShellCardDeleteConfirm,
        onShowShellCardDeleteConfirmChange,
        showActivityCardDeleteConfirm,
        onShowActivityCardDeleteConfirmChange,
        pendingExportContent,
        onPendingExportContentChange,
        pendingImportTarget,
        onPendingImportTargetChange,
        pendingCardImportPreview,
        onPendingCardImportPreviewChange,
        cardTransferInProgress,
        onCardTransferInProgressChange,
    ) {
        OsPageOverlayState(
            activityShortcutDraft = activityShortcutDraft,
            onActivityShortcutDraftChange = { activityShortcutDraft = it },
            showActivityShortcutEditor = showActivityShortcutEditor,
            onShowActivityShortcutEditorChange = onShowActivityShortcutEditorChange,
            activityCardEditMode = activityCardEditMode,
            onActivityCardEditModeChange = onActivityCardEditModeChange,
            editingActivityShortcutCardId = editingActivityShortcutCardId,
            onEditingActivityShortcutCardIdChange = onEditingActivityShortcutCardIdChange,
            editingActivityShortcutBuiltIn = editingActivityShortcutBuiltIn,
            onEditingActivityShortcutBuiltInChange = onEditingActivityShortcutBuiltInChange,
            showCardManager = showCardManager,
            onShowCardManagerChange = onShowCardManagerChange,
            showActivityVisibilityManager = showActivityVisibilityManager,
            onShowActivityVisibilityManagerChange = onShowActivityVisibilityManagerChange,
            showShellCardVisibilityManager = showShellCardVisibilityManager,
            onShowShellCardVisibilityManagerChange = onShowShellCardVisibilityManagerChange,
            showShellCommandCardEditor = showShellCommandCardEditor,
            onShowShellCommandCardEditorChange = onShowShellCommandCardEditorChange,
            editingShellCommandCardId = editingShellCommandCardId,
            onEditingShellCommandCardIdChange = onEditingShellCommandCardIdChange,
            shellCommandCardDraft = shellCommandCardDraft,
            onShellCommandCardDraftChange = { shellCommandCardDraft = it },
            showShellCardDeleteConfirm = showShellCardDeleteConfirm,
            onShowShellCardDeleteConfirmChange = onShowShellCardDeleteConfirmChange,
            showActivityCardDeleteConfirm = showActivityCardDeleteConfirm,
            onShowActivityCardDeleteConfirmChange = onShowActivityCardDeleteConfirmChange,
            pendingExportContent = pendingExportContent,
            onPendingExportContentChange = onPendingExportContentChange,
            pendingImportTarget = pendingImportTarget,
            onPendingImportTargetChange = onPendingImportTargetChange,
            pendingCardImportPreview = pendingCardImportPreview,
            onPendingCardImportPreviewChange = onPendingCardImportPreviewChange,
            cardTransferInProgress = cardTransferInProgress,
            onCardTransferInProgressChange = onCardTransferInProgressChange,
        )
    }
}
