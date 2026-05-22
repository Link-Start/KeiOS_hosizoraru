package os.kei.ui.page.main.os.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.transfer.OsCardImportPreview
import os.kei.ui.page.main.os.transfer.localizedOsCardImportMessage

@Immutable
internal data class OsPageCardTransferEventActions(
    val onExportWritten: () -> Unit,
    val onExportWriteFailed: (Throwable) -> Unit,
    val onImportPreviewReady: (OsCardImportPreview) -> Unit,
    val onImportFailed: (Throwable) -> Unit,
    val onTransferCompleted: () -> Unit,
    val onActivityCardsImported: (OsActivityCardImportMergeResult) -> Unit,
    val onShellCardsImported: (OsShellCardImportMergeResult) -> Unit,
)

@Composable
internal fun rememberOsPageCardTransferEventActions(
    context: Context,
    overlayState: OsPageOverlayState,
    activityCardExpanded: SnapshotStateMap<String, Boolean>,
    shellCommandCardExpanded: SnapshotStateMap<String, Boolean>,
    textBundle: OsPageTextBundle,
): OsPageCardTransferEventActions =
    remember(
        context,
        overlayState,
        activityCardExpanded,
        shellCommandCardExpanded,
        textBundle,
    ) {
        OsPageCardTransferEventActions(
            onExportWritten = {
                context.showToast(textBundle.exportSuccessText)
            },
            onExportWriteFailed = { error ->
                context.showToast(
                    context.getString(
                        R.string.common_export_failed_with_reason,
                        error.javaClass.simpleName,
                    ),
                )
            },
            onImportPreviewReady = { preview ->
                overlayState.onPendingCardImportPreviewChange(preview)
            },
            onImportFailed = { error ->
                context.showToast(
                    String.format(
                        textBundle.cardImportFailedWithReason,
                        error.localizedOsCardImportMessage(context),
                    ),
                )
            },
            onTransferCompleted = {
                overlayState.onCardTransferInProgressChange(false)
            },
            onActivityCardsImported = { result ->
                val validIds = result.cards.mapTo(mutableSetOf()) { it.id }
                activityCardExpanded.keys.retainAll(validIds)
                if (!validIds.contains(overlayState.editingActivityShortcutCardId.orEmpty())) {
                    overlayState.onShowActivityShortcutEditorChange(false)
                    overlayState.onShowActivityCardDeleteConfirmChange(false)
                    overlayState.onEditingActivityShortcutCardIdChange(null)
                }
                overlayState.onPendingCardImportPreviewChange(null)
                context.showToast(
                    context.getString(
                        R.string.os_activity_card_toast_imported_summary,
                        result.addedCount,
                        result.updatedCount,
                        result.unchangedCount,
                    ),
                )
            },
            onShellCardsImported = { result ->
                val validIds = result.cards.mapTo(mutableSetOf()) { it.id }
                shellCommandCardExpanded.keys.retainAll(validIds)
                if (!validIds.contains(overlayState.editingShellCommandCardId.orEmpty())) {
                    overlayState.onShowShellCommandCardEditorChange(false)
                    overlayState.onShowShellCardDeleteConfirmChange(false)
                    overlayState.onEditingShellCommandCardIdChange(null)
                }
                overlayState.onPendingCardImportPreviewChange(null)
                context.showToast(
                    context.getString(
                        R.string.os_shell_card_toast_imported_summary,
                        result.addedCount,
                        result.updatedCount,
                        result.unchangedCount,
                    ),
                )
            },
        )
    }
