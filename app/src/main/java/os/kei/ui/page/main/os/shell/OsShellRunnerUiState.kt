package os.kei.ui.page.main.os.shell

import androidx.compose.runtime.Immutable

@Immutable
internal data class OsShellRunnerUiState(
    val persistentState: OsShellRunnerPersistentState = OsShellRunnerPersistentState(),
    val chromePrefs: OsShellRunnerChromePrefs = OsShellRunnerChromePrefs(),
    val commandExecutionState: OsShellRunnerCommandExecutionState = OsShellRunnerCommandExecutionState(),
    val pageChromeState: OsShellRunnerPageChromeState = OsShellRunnerPageChromeState(),
)

@Immutable
internal data class OsShellRunnerPageChromeState(
    val startupFocusRequestToken: Int = 0,
    val startupFocusApplied: Boolean = false,
    val showSaveSheet: Boolean = false,
    val showBehaviorSettingsSheet: Boolean = false,
    val showOutputSettingsSheet: Boolean = false,
    val showDangerousCommandConfirm: Boolean = false,
    val pendingDangerousCommand: String = "",
    val saveTitleInput: String = "",
    val saveSubtitleInput: String = "",
    val saveInitialSubtitleInput: String = "",
)
