package os.kei.ui.page.main.os.shell

import androidx.compose.runtime.Immutable

@Immutable
internal data class OsShellRunnerCommandExecutionState(
    val runningCommand: Boolean = false,
)

@Immutable
internal data class OsShellRunnerCommandSaveState(
    val savingCommandCard: Boolean = false,
)

internal typealias OsShellRunnerCommandExecutor = suspend (String, Long, suspend (String) -> Unit) -> String?

internal sealed interface OsShellRunnerEvent {
    data class Toast(
        val message: String,
    ) : OsShellRunnerEvent

    data class LiquidToast(
        val message: String,
    ) : OsShellRunnerEvent

    data class OpenSaveCommandSheet(
        val suggestedSubtitle: String,
    ) : OsShellRunnerEvent

    data object CloseSaveCommandSheet : OsShellRunnerEvent
}
