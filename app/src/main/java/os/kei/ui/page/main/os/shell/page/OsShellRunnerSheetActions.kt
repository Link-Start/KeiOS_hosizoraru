package os.kei.ui.page.main.os.shell.page

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntRect
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
    val onTimeoutDropdownExpandedChange: (Boolean) -> Unit,
    val onTimeoutDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    val onDangerousCommandConfirmChange: (Boolean) -> Unit,
    val onCompletionToastChange: (Boolean) -> Unit,
    val onStartupBehaviorChange: (OsShellRunnerStartupBehavior) -> Unit,
    val onExitCleanupModeChange: (OsShellRunnerExitCleanupMode) -> Unit,
    val onDismissOutputSettings: () -> Unit,
    val onPersistOutputEnabledChange: (Boolean) -> Unit,
    val onAutoFormatOutputChange: (Boolean) -> Unit,
    val onAutoScrollOutputChange: (Boolean) -> Unit,
    val onOutputLimitCharsChange: (Int) -> Unit,
    val onOutputLimitDropdownExpandedChange: (Boolean) -> Unit,
    val onOutputLimitDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    val onOutputSaveModeChange: (OsShellRunnerOutputSaveMode) -> Unit,
    val onCopyModeChange: (OsShellRunnerCopyMode) -> Unit,
    val onDismissDangerousCommand: () -> Unit,
    val onConfirmDangerousCommand: () -> Unit,
)
