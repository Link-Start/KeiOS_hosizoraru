@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.back.BackNavigationSource
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.back.KeiOSBackNavigationHandler
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.osLucideClearAllIcon
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.state.rememberOsShellRunnerTextBundle
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OsShellRunnerPage(
    canRunShellCommand: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRunShellCommand: suspend (String, Long) -> String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pageListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val textBundle = rememberOsShellRunnerTextBundle()
    val shellRunnerViewModel: OsShellRunnerViewModel = viewModel()
    val uiState by shellRunnerViewModel.uiState.collectAsStateWithLifecycle()
    val persistentState = uiState.persistentState
    val chromePrefs = uiState.chromePrefs
    val pageChromeState = uiState.pageChromeState
    val commandExecutionState = uiState.commandExecutionState
    val pageState =
        rememberOsShellRunnerPageStateHolder(
            chromeState = pageChromeState,
            chromeActions =
                OsShellRunnerPageChromeActions(
                    onRequestStartupFocus = shellRunnerViewModel::requestStartupFocus,
                    onOpenSaveSheet = shellRunnerViewModel::openSaveSheet,
                    onShowSaveSheetChange = shellRunnerViewModel::updateShowSaveSheet,
                    onShowBehaviorSettingsSheetChange = shellRunnerViewModel::updateShowBehaviorSettingsSheet,
                    onShowOutputSettingsSheetChange = shellRunnerViewModel::updateShowOutputSettingsSheet,
                    onSaveTitleInputChange = shellRunnerViewModel::updateSaveTitleInput,
                    onSaveSubtitleInputChange = shellRunnerViewModel::updateSaveSubtitleInput,
                    onResetSaveSheetInputs = shellRunnerViewModel::resetSaveSheetInputs,
                    onOpenDangerousCommandConfirm = shellRunnerViewModel::openDangerousCommandConfirm,
                    onDismissDangerousCommandConfirm = shellRunnerViewModel::dismissDangerousCommandConfirm,
                    onTimeoutDropdownExpandedChange = shellRunnerViewModel::updateTimeoutDropdownExpanded,
                    onTimeoutDropdownAnchorBoundsChange = shellRunnerViewModel::updateTimeoutDropdownAnchorBounds,
                    onOutputLimitDropdownExpandedChange = shellRunnerViewModel::updateOutputLimitDropdownExpanded,
                    onOutputLimitDropdownAnchorBoundsChange = shellRunnerViewModel::updateOutputLimitDropdownAnchorBounds,
                    onConsumeCloseCleanupRequest = shellRunnerViewModel::consumeCloseCleanupRequest,
                ),
        )
    OsShellRunnerRouteEffects(
        context = context,
        shellRunnerViewModel = shellRunnerViewModel,
        pageState = pageState,
        persistentState = persistentState,
        textBundle = textBundle,
    )
    val isDark = isSystemInDarkTheme()
    val shellCommandAccentColor = if (isDark) Color(0xFF7AB8FF) else Color(0xFF2563EB)
    val shellSuccessAccentColor = if (isDark) Color(0xFF7EE7A8) else Color(0xFF15803D)
    val shellStoppedAccentColor = if (isDark) Color(0xFFFF9E9E) else Color(0xFFDC2626)
    val outputScrollState = rememberScrollState()
    val liquidActionBarLayeredStyleEnabled = chromePrefs.liquidActionBarLayeredStyleEnabled

    val surfaceColor = MiuixTheme.colorScheme.surface
    val topBarBackdrop =
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

    val commandInput = persistentState.commandInput
    val settings = persistentState.settings
    val currentOutputSnapshot =
        remember(shellRunnerViewModel) {
            shellRunnerViewModel::currentOutputSnapshot
        }
    val actions =
        rememberOsShellRunnerPageActions(
            context = context,
            shellRunnerViewModel = shellRunnerViewModel,
            pageState = pageState,
            persistentState = persistentState,
            commandExecutionState = commandExecutionState,
            currentOutputSnapshot = currentOutputSnapshot,
            textBundle = textBundle,
            canRunShellCommand = canRunShellCommand,
            onRequestShizukuPermission = onRequestShizukuPermission,
            onRunShellCommand = onRunShellCommand,
            onClose = onClose,
        )
    val latestActions = rememberUpdatedState(actions)

    DisposableEffect(Unit) {
        onDispose {
            latestActions.value.applyCloseCleanup()
        }
    }

    OsShellBackHandler(enabled = pageState.showSaveSheet) { pageState.showSaveSheet = false }
    OsShellBackHandler(enabled = !pageState.showSaveSheet && pageState.showBehaviorSettingsSheet) {
        pageState.showBehaviorSettingsSheet = false
    }
    OsShellBackHandler(enabled = !pageState.showSaveSheet && pageState.showOutputSettingsSheet) {
        pageState.showOutputSettingsSheet = false
    }
    OsShellBackHandler(
        enabled =
            !pageState.showSaveSheet &&
                !pageState.showBehaviorSettingsSheet &&
                !pageState.showOutputSettingsSheet &&
                pageState.showDangerousCommandConfirm,
    ) {
        pageState.dismissDangerousCommandConfirm()
    }
    KeiOSActivityRootBackHandler(
        enabled =
            !pageState.showSaveSheet &&
                !pageState.showBehaviorSettingsSheet &&
                !pageState.showOutputSettingsSheet &&
                !pageState.showDangerousCommandConfirm,
        needsInterception = false,
    ) {
        actions.requestClose()
    }
    val clearAllIcon = osLucideClearAllIcon()
    val behaviorSettingsIcon = appLucideConfigIcon()
    val outputSettingsIcon = appLucideNotesIcon()
    val behaviorSettingsDescription = stringResource(R.string.os_shell_action_behavior_settings)
    val outputSettingsDescription = stringResource(R.string.os_shell_action_output_settings)
    val actionItems =
        remember(
            actions,
            textBundle.clearAllActionDescription,
            behaviorSettingsDescription,
            outputSettingsDescription,
        ) {
            listOf(
                LiquidActionItem(
                    icon = clearAllIcon,
                    contentDescription = textBundle.clearAllActionDescription,
                    onClick = actions.clearAllContent,
                ),
                LiquidActionItem(
                    icon = behaviorSettingsIcon,
                    contentDescription = behaviorSettingsDescription,
                    onClick = { pageState.showBehaviorSettingsSheet = true },
                ),
                LiquidActionItem(
                    icon = outputSettingsIcon,
                    contentDescription = outputSettingsDescription,
                    onClick = { pageState.showOutputSettingsSheet = true },
                ),
            )
        }

    OsShellRunnerContent(
        textBundle = textBundle,
        scrollBehavior = scrollBehavior,
        topBarBackdrop = topBarBackdrop,
        pageListState = pageListState,
        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
        actionItems = actionItems,
        commandInput = commandInput,
        runningCommand = commandExecutionState.runningCommand,
        startupFocusRequestToken = pageState.startupFocusRequestToken,
        outputContent = {
            OsShellRunnerOutputCardHost(
                shellRunnerViewModel = shellRunnerViewModel,
                textBundle = textBundle,
                outputScrollState = outputScrollState,
                autoScrollOutputEnabled = settings.autoScrollOutput,
                onFormatOutput = actions.formatOutput,
                onCopyOutput = actions.copyOutput,
                onClearOutput = { actions.clearOutput(true) },
            )
        },
        onRequestClose = actions.requestClose,
        onCommandInputChange = actions.updateCommandInput,
        onRunCommand = actions.runCommand,
        onStopCommand = { actions.stopCommand(true) },
        onOpenSaveCommandSheet = actions.openSaveCommandSheet,
    )

    val dangerousCommandPreview =
        remember(pageState.pendingDangerousCommand) {
            pageState.pendingDangerousCommand
                .trim()
                .replace('\n', ' ')
                .take(120)
        }
    val sheetActions =
        remember(pageState, actions) {
            OsShellRunnerSheetActions(
                onSaveTitleInputChange = { pageState.saveTitleInput = it },
                onSaveSubtitleInputChange = { pageState.saveSubtitleInput = it },
                onDismissSaveSheet = { pageState.showSaveSheet = false },
                onSaveSheetDismissFinished = {
                    if (!pageState.showSaveSheet) {
                        pageState.resetSaveSheetInputs()
                    }
                },
                onConfirmSave = actions.saveCommandToCard,
                onDismissBehaviorSettings = { pageState.showBehaviorSettingsSheet = false },
                onPersistInputEnabledChange = actions.updatePersistInput,
                onTimeoutSecondsChange = actions.updateTimeoutSeconds,
                onTimeoutDropdownExpandedChange = pageState::updateTimeoutDropdownExpanded,
                onTimeoutDropdownAnchorBoundsChange = pageState::updateTimeoutDropdownAnchorBounds,
                onDangerousCommandConfirmChange = actions.updateDangerousCommandConfirm,
                onCompletionToastChange = actions.updateCompletionToast,
                onStartupBehaviorChange = actions.updateStartupBehavior,
                onExitCleanupModeChange = actions.updateExitCleanupMode,
                onDismissOutputSettings = { pageState.showOutputSettingsSheet = false },
                onPersistOutputEnabledChange = actions.updatePersistOutput,
                onAutoFormatOutputChange = actions.updateAutoFormatOutput,
                onAutoScrollOutputChange = actions.updateAutoScrollOutput,
                onOutputLimitCharsChange = actions.updateOutputLimitChars,
                onOutputLimitDropdownExpandedChange = pageState::updateOutputLimitDropdownExpanded,
                onOutputLimitDropdownAnchorBoundsChange = pageState::updateOutputLimitDropdownAnchorBounds,
                onOutputSaveModeChange = actions.updateOutputSaveMode,
                onCopyModeChange = actions.updateCopyMode,
                onDismissDangerousCommand = pageState::dismissDangerousCommandConfirm,
                onConfirmDangerousCommand = actions.confirmDangerousCommand,
            )
        }
    OsShellRunnerSheets(
        textBundle = textBundle,
        showSaveSheet = pageState.showSaveSheet,
        showBehaviorSettingsSheet = pageState.showBehaviorSettingsSheet,
        showOutputSettingsSheet = pageState.showOutputSettingsSheet,
        showDangerousCommandConfirm = pageState.showDangerousCommandConfirm,
        commandInput = commandInput,
        shellRunnerViewModel = shellRunnerViewModel,
        saveTitleInput = pageState.saveTitleInput,
        onSaveTitleInputChange = { pageState.saveTitleInput = it },
        saveSubtitleInput = pageState.saveSubtitleInput,
        onSaveSubtitleInputChange = { pageState.saveSubtitleInput = it },
        saveInitialSubtitleInput = pageState.saveInitialSubtitleInput,
        shellCommandAccentColor = shellCommandAccentColor,
        shellSuccessAccentColor = shellSuccessAccentColor,
        shellStoppedAccentColor = shellStoppedAccentColor,
        settings = settings,
        chromePrefs = chromePrefs,
        timeoutDropdownExpanded = pageState.timeoutDropdownExpanded,
        timeoutDropdownAnchorBounds = pageState.timeoutDropdownAnchorBounds,
        outputLimitDropdownExpanded = pageState.outputLimitDropdownExpanded,
        outputLimitDropdownAnchorBounds = pageState.outputLimitDropdownAnchorBounds,
        dangerousCommandPreview = dangerousCommandPreview,
        actions = sheetActions,
    )
}

@Composable
private fun OsShellBackHandler(
    enabled: Boolean,
    source: BackNavigationSource = BackNavigationSource.Modal,
    onBack: () -> Unit,
) {
    KeiOSBackNavigationHandler(
        enabled = enabled,
        source = source,
    ) {
        onBack()
    }
}
