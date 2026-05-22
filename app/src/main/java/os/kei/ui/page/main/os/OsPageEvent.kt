package os.kei.ui.page.main.os

internal sealed interface OsPageEvent {
    data class LaunchExportDocument(
        val fileName: String,
        val content: String,
    ) : OsPageEvent

    data class ExportFailed(
        val error: Throwable,
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
}
