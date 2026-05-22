package os.kei.ui.page.main.os

import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.transfer.OsCardImportPreview

internal sealed interface OsPageEvent {
    data class LaunchExportDocument(
        val fileName: String,
        val content: String,
    ) : OsPageEvent

    data class ExportFailed(
        val error: Throwable,
    ) : OsPageEvent

    data object CardExportWritten : OsPageEvent

    data class CardExportWriteFailed(
        val error: Throwable,
    ) : OsPageEvent

    data class CardImportPreviewReady(
        val preview: OsCardImportPreview,
    ) : OsPageEvent

    data class CardImportFailed(
        val error: Throwable,
    ) : OsPageEvent

    data object CardTransferCompleted : OsPageEvent

    data class ActivityCardsImported(
        val result: OsActivityCardImportMergeResult,
    ) : OsPageEvent

    data class ShellCardsImported(
        val result: OsShellCardImportMergeResult,
    ) : OsPageEvent

    data class OperationFailed(
        val error: Throwable,
    ) : OsPageEvent

    data object ShellCommandCardSaved : OsPageEvent

    data class ShellCommandCardDeleted(
        val cardId: String,
    ) : OsPageEvent

    data object ActivityShortcutCardSaved : OsPageEvent

    data class ActivityShortcutCardDeleted(
        val cardId: String,
    ) : OsPageEvent

    data object ShellCommandCardSaveFailed : OsPageEvent

    data object ShellCommandCardCommandRequired : OsPageEvent

    data object ShellCommandCardNoPermission : OsPageEvent

    data object ShellCommandCardRunCompleted : OsPageEvent

    data class ShellCommandCardRunFailed(
        val error: Throwable,
    ) : OsPageEvent

    data class RefreshCompleted(
        val refreshed: Boolean,
    ) : OsPageEvent

    data class LaunchActivityShortcut(
        val config: OsGoogleSystemServiceConfig,
    ) : OsPageEvent

    data object ActivityShortcutInvalidTarget : OsPageEvent

    data class ShowActivityShortcutEditor(
        val request: OsActivityShortcutEditorRequest,
    ) : OsPageEvent

    data class ShowShellCommandCardEditor(
        val card: OsShellCommandCard,
    ) : OsPageEvent
}
