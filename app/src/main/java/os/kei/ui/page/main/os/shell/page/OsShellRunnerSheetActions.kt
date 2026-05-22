package os.kei.ui.page.main.os.shell.page

import androidx.compose.runtime.Stable
import os.kei.ui.page.main.os.shell.OsShellRunnerCopyMode
import os.kei.ui.page.main.os.shell.OsShellRunnerExitCleanupMode
import os.kei.ui.page.main.os.shell.OsShellRunnerOutputSaveMode
import os.kei.ui.page.main.os.shell.OsShellRunnerStartupBehavior

@Stable
internal class OsShellRunnerSheetActions(
    val onSaveTitleInputChange: (String) -> Unit,
    val onSaveSubtitleInputChange: (String) -> Unit,
    val onDismissSaveSheet: () -> Unit,
    val onSaveSheetDismissFinished: () -> Unit,
    val onConfirmSave: () -> Unit,
    val onDismissBehaviorSettings: () -> Unit,
    val onPersistInputEnabledChange: (Boolean) -> Unit,
    val onTimeoutSecondsChange: (Int) -> Unit,
    val onDangerousCommandConfirmChange: (Boolean) -> Unit,
    val onCompletionToastChange: (Boolean) -> Unit,
    val onStartupBehaviorChange: (OsShellRunnerStartupBehavior) -> Unit,
    val onExitCleanupModeChange: (OsShellRunnerExitCleanupMode) -> Unit,
    val onDismissOutputSettings: () -> Unit,
    val onPersistOutputEnabledChange: (Boolean) -> Unit,
    val onAutoFormatOutputChange: (Boolean) -> Unit,
    val onAutoScrollOutputChange: (Boolean) -> Unit,
    val onOutputLimitCharsChange: (Int) -> Unit,
    val onOutputSaveModeChange: (OsShellRunnerOutputSaveMode) -> Unit,
    val onCopyModeChange: (OsShellRunnerCopyMode) -> Unit,
    val onDismissDangerousCommand: () -> Unit,
    val onConfirmDangerousCommand: () -> Unit,
)
