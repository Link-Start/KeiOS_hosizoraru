package os.kei.ui.page.main.os.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
internal fun rememberOsPageOverlayState(googleSystemServiceDefaults: OsGoogleSystemServiceConfig): OsPageOverlayState {
    var activityShortcutDraft by remember {
        mutableStateOf(createDefaultActivityShortcutDraft(googleSystemServiceDefaults))
    }
    var showActivityShortcutEditor by rememberSaveable { mutableStateOf(false) }
    var activityCardEditMode by rememberSaveable { mutableStateOf(OsActivityCardEditMode.Edit) }
    var editingActivityShortcutCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingActivityShortcutBuiltIn by rememberSaveable { mutableStateOf(false) }
    var showCardManager by rememberSaveable { mutableStateOf(false) }
    var showActivityVisibilityManager by rememberSaveable { mutableStateOf(false) }
    var showShellCardVisibilityManager by rememberSaveable { mutableStateOf(false) }
    var showShellCommandCardEditor by rememberSaveable { mutableStateOf(false) }
    var editingShellCommandCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var shellCommandCardDraft by remember { mutableStateOf(createDefaultShellCommandCardDraft()) }
    var showShellCardDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showActivityCardDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingImportTarget by remember { mutableStateOf<OsCardImportTarget?>(null) }
    var pendingCardImportPreview by remember { mutableStateOf<OsCardImportPreview?>(null) }
    var cardTransferInProgress by remember { mutableStateOf(false) }

    return remember(
        activityShortcutDraft,
        showActivityShortcutEditor,
        activityCardEditMode,
        editingActivityShortcutCardId,
        editingActivityShortcutBuiltIn,
        showCardManager,
        showActivityVisibilityManager,
        showShellCardVisibilityManager,
        showShellCommandCardEditor,
        editingShellCommandCardId,
        shellCommandCardDraft,
        showShellCardDeleteConfirm,
        showActivityCardDeleteConfirm,
        pendingExportContent,
        pendingImportTarget,
        pendingCardImportPreview,
        cardTransferInProgress,
    ) {
        OsPageOverlayState(
            activityShortcutDraft = activityShortcutDraft,
            onActivityShortcutDraftChange = { activityShortcutDraft = it },
            showActivityShortcutEditor = showActivityShortcutEditor,
            onShowActivityShortcutEditorChange = { showActivityShortcutEditor = it },
            activityCardEditMode = activityCardEditMode,
            onActivityCardEditModeChange = { activityCardEditMode = it },
            editingActivityShortcutCardId = editingActivityShortcutCardId,
            onEditingActivityShortcutCardIdChange = { editingActivityShortcutCardId = it },
            editingActivityShortcutBuiltIn = editingActivityShortcutBuiltIn,
            onEditingActivityShortcutBuiltInChange = { editingActivityShortcutBuiltIn = it },
            showCardManager = showCardManager,
            onShowCardManagerChange = { showCardManager = it },
            showActivityVisibilityManager = showActivityVisibilityManager,
            onShowActivityVisibilityManagerChange = { showActivityVisibilityManager = it },
            showShellCardVisibilityManager = showShellCardVisibilityManager,
            onShowShellCardVisibilityManagerChange = { showShellCardVisibilityManager = it },
            showShellCommandCardEditor = showShellCommandCardEditor,
            onShowShellCommandCardEditorChange = { showShellCommandCardEditor = it },
            editingShellCommandCardId = editingShellCommandCardId,
            onEditingShellCommandCardIdChange = { editingShellCommandCardId = it },
            shellCommandCardDraft = shellCommandCardDraft,
            onShellCommandCardDraftChange = { shellCommandCardDraft = it },
            showShellCardDeleteConfirm = showShellCardDeleteConfirm,
            onShowShellCardDeleteConfirmChange = { showShellCardDeleteConfirm = it },
            showActivityCardDeleteConfirm = showActivityCardDeleteConfirm,
            onShowActivityCardDeleteConfirmChange = { showActivityCardDeleteConfirm = it },
            pendingExportContent = pendingExportContent,
            onPendingExportContentChange = { pendingExportContent = it },
            pendingImportTarget = pendingImportTarget,
            onPendingImportTargetChange = { pendingImportTarget = it },
            pendingCardImportPreview = pendingCardImportPreview,
            onPendingCardImportPreviewChange = { pendingCardImportPreview = it },
            cardTransferInProgress = cardTransferInProgress,
            onCardTransferInProgressChange = { cardTransferInProgress = it },
        )
    }
}
