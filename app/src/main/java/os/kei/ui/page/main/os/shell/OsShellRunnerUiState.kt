package os.kei.ui.page.main.os.shell

import androidx.compose.runtime.Immutable

@Immutable
internal data class OsShellRunnerUiState(
    val persistentState: OsShellRunnerPersistentState = OsShellRunnerPersistentState(),
    val chromePrefs: OsShellRunnerChromePrefs = OsShellRunnerChromePrefs(),
    val commandExecutionState: OsShellRunnerCommandExecutionState = OsShellRunnerCommandExecutionState(),
)

