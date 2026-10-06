@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.emptyFlow
import os.kei.R
import os.kei.ui.page.main.os.shell.OsShellBehaviorSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellOutputSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellRunnerChromePrefs
import os.kei.ui.page.main.os.shell.OsShellRunnerSettings
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.component.OsShellRunnerSaveSheet
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle
import os.kei.ui.page.main.os.shell.state.toOutputSnapshot
import os.kei.ui.page.main.widget.dialog.AppWindowDialogHost
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class OsShellDangerousCommandDialogContent(
    val title: String,
    val summary: String,
    val confirmText: String,
)

@Stable
internal class OsShellDialogExitSnapshot<T : Any>(
    initialValue: T?,
) {
    var retainedValue: T? by mutableStateOf(initialValue)
        private set

    fun resolve(currentValue: T?): T? = currentValue ?: retainedValue

    fun retain(currentValue: T?) {
        if (currentValue != null) {
            retainedValue = currentValue
        }
    }

    fun clear() {
        retainedValue = null
    }
}

@Composable
internal fun <T : Any> rememberOsShellDialogExitSnapshot(currentValue: T?): OsShellDialogExitSnapshot<T> {
    val snapshot = remember { OsShellDialogExitSnapshot(currentValue) }
    SideEffect { snapshot.retain(currentValue) }
    return snapshot
}

@Composable
internal fun OsShellRunnerSheets(
    textBundle: OsShellRunnerTextBundle,
    showSaveSheet: Boolean,
    showBehaviorSettingsSheet: Boolean,
    showOutputSettingsSheet: Boolean,
    showDangerousCommandConfirm: Boolean,
    commandInput: String,
    shellRunnerViewModel: OsShellRunnerViewModel,
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
    outputLimitDropdownExpanded: Boolean,
    dangerousCommandPreview: String,
    actions: OsShellRunnerSheetActions,
) {
    val outputStateFlow =
        remember(shellRunnerViewModel, showSaveSheet) {
            if (showSaveSheet) {
                shellRunnerViewModel.outputState
            } else {
                emptyFlow()
            }
        }
    val rawOutputState by outputStateFlow.collectAsStateWithLifecycle(
        initialValue = shellRunnerViewModel.outputState.value,
    )
    val outputSnapshot = remember(rawOutputState) { rawOutputState.toOutputSnapshot() }

    OsShellRunnerSaveSheet(
        show = showSaveSheet,
        title = textBundle.saveSheetTitle,
        commandInput = commandInput,
        latestOutputEntry = outputSnapshot.latestEntry,
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
            onTimeoutDropdownExpandedChange = actions.onTimeoutDropdownExpandedChange,
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
            onOutputLimitDropdownExpandedChange = actions.onOutputLimitDropdownExpandedChange,
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
internal fun OsShellDangerousCommandConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val currentContent =
        remember(show, title, summary, confirmText) {
            if (show) {
                OsShellDangerousCommandDialogContent(
                    title = title,
                    summary = summary,
                    confirmText = confirmText,
                )
            } else {
                null
            }
        }
    val exitSnapshot = rememberOsShellDialogExitSnapshot(currentContent)
    val renderedContent = exitSnapshot.resolve(currentContent)

    AppWindowDialogHost(
        show = show,
        title = renderedContent?.title,
        summary = renderedContent?.summary,
        onDismissRequest = onDismissRequest,
        onDismissFinished = exitSnapshot::clear,
    ) {
        renderedContent?.let { content ->
            Spacer(modifier = Modifier.height(16.dp))
            OsShellDangerousCommandConfirmActions(
                confirmText = content.confirmText,
                actionsEnabled = show,
                onDismissRequest = onDismissRequest,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
internal fun OsShellDangerousCommandConfirmActions(
    confirmText: String,
    actionsEnabled: Boolean = true,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLiquidDialogActionButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.common_cancel),
            onClick = onDismissRequest,
            enabled = actionsEnabled,
        )
        AppLiquidDialogActionButton(
            modifier = Modifier.weight(1f),
            text = confirmText,
            containerColor = MiuixTheme.colorScheme.error,
            variant = GlassVariant.SheetDangerAction,
            onClick = onConfirm,
            enabled = actionsEnabled,
        )
    }
}
