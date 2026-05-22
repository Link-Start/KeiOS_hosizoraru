@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.shell.OsShellBehaviorSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellOutputSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellRunnerChromePrefs
import os.kei.ui.page.main.os.shell.OsShellRunnerOutputSaveMode
import os.kei.ui.page.main.os.shell.OsShellRunnerSettings
import os.kei.ui.page.main.os.shell.OsShellRunnerStartupBehavior
import os.kei.ui.page.main.os.shell.ShellOutputDisplayEntry
import os.kei.ui.page.main.os.shell.component.OsShellRunnerSaveSheet
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun OsShellRunnerSheets(
    textBundle: OsShellRunnerTextBundle,
    showSaveSheet: Boolean,
    showBehaviorSettingsSheet: Boolean,
    showOutputSettingsSheet: Boolean,
    showDangerousCommandConfirm: Boolean,
    commandInput: String,
    latestOutputEntry: ShellOutputDisplayEntry?,
    saveTitleInput: String,
    onSaveTitleInputChange: (String) -> Unit,
    saveSubtitleInput: String,
    onSaveSubtitleInputChange: (String) -> Unit,
    saveInitialSubtitleInput: String,
    shellCommandAccentColor: Color,
    shellSuccessAccentColor: Color,
    shellStoppedAccentColor: Color,
    settings: OsShellRunnerSettings,
    chromePrefs: OsShellRunnerChromePrefs,
    dangerousCommandPreview: String,
    onDismissSaveSheet: () -> Unit,
    onSaveSheetDismissFinished: () -> Unit,
    onConfirmSave: () -> Unit,
    onDismissBehaviorSettings: () -> Unit,
    onPersistInputEnabledChange: (Boolean) -> Unit,
    onTimeoutSecondsChange: (Int) -> Unit,
    onDangerousCommandConfirmChange: (Boolean) -> Unit,
    onCompletionToastChange: (Boolean) -> Unit,
    onStartupBehaviorChange: (OsShellRunnerStartupBehavior) -> Unit,
    onExitCleanupModeChange: (os.kei.ui.page.main.os.shell.OsShellRunnerExitCleanupMode) -> Unit,
    onDismissOutputSettings: () -> Unit,
    onPersistOutputEnabledChange: (Boolean) -> Unit,
    onAutoFormatOutputChange: (Boolean) -> Unit,
    onAutoScrollOutputChange: (Boolean) -> Unit,
    onOutputLimitCharsChange: (Int) -> Unit,
    onOutputSaveModeChange: (OsShellRunnerOutputSaveMode) -> Unit,
    onCopyModeChange: (os.kei.ui.page.main.os.shell.OsShellRunnerCopyMode) -> Unit,
    onDismissDangerousCommand: () -> Unit,
    onConfirmDangerousCommand: () -> Unit,
) {
    OsShellRunnerSaveSheet(
        show = showSaveSheet,
        title = textBundle.saveSheetTitle,
        commandInput = commandInput,
        latestOutputEntry = latestOutputEntry,
        saveSheetCommandLabel = textBundle.saveSheetCommandLabel,
        saveSheetFieldTitle = textBundle.saveSheetFieldTitle,
        saveSheetFieldSubtitle = textBundle.saveSheetFieldSubtitle,
        saveSheetTitleHint = textBundle.saveSheetTitleHint,
        saveSheetSubtitleHint = textBundle.saveSheetSubtitleHint,
        saveSheetTimePlaceholder = textBundle.saveSheetTimePlaceholder,
        saveTitleInput = saveTitleInput,
        onSaveTitleInputChange = onSaveTitleInputChange,
        saveSubtitleInput = saveSubtitleInput,
        onSaveSubtitleInputChange = onSaveSubtitleInputChange,
        hasUnsavedChanges =
            saveTitleInput.trim().isNotBlank() ||
                saveSubtitleInput.trim() != saveInitialSubtitleInput.trim(),
        shellCommandAccentColor = shellCommandAccentColor,
        shellSuccessAccentColor = shellSuccessAccentColor,
        shellStoppedAccentColor = shellStoppedAccentColor,
        onDismissRequest = onDismissSaveSheet,
        onDismissFinished = onSaveSheetDismissFinished,
        onConfirm = onConfirmSave,
    )

    CompositionLocalProvider(LocalLiquidControlsEnabled provides chromePrefs.liquidSwitchEnabled) {
        OsShellBehaviorSettingsSheet(
            show = showBehaviorSettingsSheet,
            onDismissRequest = onDismissBehaviorSettings,
            settings = settings,
            onPersistInputEnabledChange = onPersistInputEnabledChange,
            onTimeoutSecondsChange = onTimeoutSecondsChange,
            onDangerousCommandConfirmChange = onDangerousCommandConfirmChange,
            onCompletionToastChange = onCompletionToastChange,
            onStartupBehaviorChange = onStartupBehaviorChange,
            onExitCleanupModeChange = onExitCleanupModeChange,
        )
        OsShellOutputSettingsSheet(
            show = showOutputSettingsSheet,
            onDismissRequest = onDismissOutputSettings,
            settings = settings,
            onPersistOutputEnabledChange = onPersistOutputEnabledChange,
            onAutoFormatOutputChange = onAutoFormatOutputChange,
            onAutoScrollOutputChange = onAutoScrollOutputChange,
            onOutputLimitCharsChange = onOutputLimitCharsChange,
            onOutputSaveModeChange = onOutputSaveModeChange,
            onCopyModeChange = onCopyModeChange,
        )
    }

    OsShellDangerousCommandConfirmDialog(
        show = showDangerousCommandConfirm,
        title = textBundle.dangerousCommandDialogTitle,
        summary =
            stringResource(
                R.string.os_shell_dangerous_command_dialog_summary,
                dangerousCommandPreview.ifBlank { "-" },
            ),
        confirmText = textBundle.dangerousCommandConfirmText,
        onDismissRequest = onDismissDangerousCommand,
        onConfirm = onConfirmDangerousCommand,
    )
}

@Composable
private fun OsShellDangerousCommandConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLiquidDialogActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
            )
            AppLiquidDialogActionButton(
                modifier = Modifier.weight(1f),
                text = confirmText,
                containerColor = MiuixTheme.colorScheme.error,
                variant = GlassVariant.SheetDangerAction,
                onClick = onConfirm,
            )
        }
    }
}
