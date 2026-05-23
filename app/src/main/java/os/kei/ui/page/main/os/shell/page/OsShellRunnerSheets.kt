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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.shell.OsShellBehaviorSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellOutputSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellRunnerChromePrefs
import os.kei.ui.page.main.os.shell.OsShellRunnerSettings
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
    timeoutDropdownExpanded: Boolean,
    timeoutDropdownAnchorBounds: IntRect?,
    outputLimitDropdownExpanded: Boolean,
    outputLimitDropdownAnchorBounds: IntRect?,
    dangerousCommandPreview: String,
    actions: OsShellRunnerSheetActions,
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
        onSaveTitleInputChange = actions.onSaveTitleInputChange,
        saveSubtitleInput = saveSubtitleInput,
        onSaveSubtitleInputChange = actions.onSaveSubtitleInputChange,
        hasUnsavedChanges =
            saveTitleInput.trim().isNotBlank() ||
                saveSubtitleInput.trim() != saveInitialSubtitleInput.trim(),
        shellCommandAccentColor = shellCommandAccentColor,
        shellSuccessAccentColor = shellSuccessAccentColor,
        shellStoppedAccentColor = shellStoppedAccentColor,
        onDismissRequest = actions.onDismissSaveSheet,
        onDismissFinished = actions.onSaveSheetDismissFinished,
        onConfirm = actions.onConfirmSave,
    )

    CompositionLocalProvider(LocalLiquidControlsEnabled provides chromePrefs.liquidSwitchEnabled) {
        OsShellBehaviorSettingsSheet(
            show = showBehaviorSettingsSheet,
            onDismissRequest = actions.onDismissBehaviorSettings,
            settings = settings,
            onPersistInputEnabledChange = actions.onPersistInputEnabledChange,
            onTimeoutSecondsChange = actions.onTimeoutSecondsChange,
            timeoutDropdownExpanded = timeoutDropdownExpanded,
            timeoutDropdownAnchorBounds = timeoutDropdownAnchorBounds,
            onTimeoutDropdownExpandedChange = actions.onTimeoutDropdownExpandedChange,
            onTimeoutDropdownAnchorBoundsChange = actions.onTimeoutDropdownAnchorBoundsChange,
            onDangerousCommandConfirmChange = actions.onDangerousCommandConfirmChange,
            onCompletionToastChange = actions.onCompletionToastChange,
            onStartupBehaviorChange = actions.onStartupBehaviorChange,
            onExitCleanupModeChange = actions.onExitCleanupModeChange,
        )
        OsShellOutputSettingsSheet(
            show = showOutputSettingsSheet,
            onDismissRequest = actions.onDismissOutputSettings,
            settings = settings,
            onPersistOutputEnabledChange = actions.onPersistOutputEnabledChange,
            onAutoFormatOutputChange = actions.onAutoFormatOutputChange,
            onAutoScrollOutputChange = actions.onAutoScrollOutputChange,
            onOutputLimitCharsChange = actions.onOutputLimitCharsChange,
            outputLimitDropdownExpanded = outputLimitDropdownExpanded,
            outputLimitDropdownAnchorBounds = outputLimitDropdownAnchorBounds,
            onOutputLimitDropdownExpandedChange = actions.onOutputLimitDropdownExpandedChange,
            onOutputLimitDropdownAnchorBoundsChange = actions.onOutputLimitDropdownAnchorBoundsChange,
            onOutputSaveModeChange = actions.onOutputSaveModeChange,
            onCopyModeChange = actions.onCopyModeChange,
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
        onDismissRequest = actions.onDismissDangerousCommand,
        onConfirm = actions.onConfirmDangerousCommand,
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
