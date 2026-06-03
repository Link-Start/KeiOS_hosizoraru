package os.kei.ui.page.main.os.shell.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.shell.OsShellRunnerCommandExecutionState
import os.kei.ui.page.main.os.shell.OsShellRunnerCommandExecutor
import os.kei.ui.page.main.os.shell.OsShellRunnerExitCleanupMode
import os.kei.ui.page.main.os.shell.OsShellRunnerOutputSaveMode
import os.kei.ui.page.main.os.shell.OsShellRunnerPersistentUiState
import os.kei.ui.page.main.os.shell.OsShellRunnerStartupBehavior
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.state.OsShellRunnerOutputSnapshot
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle

@Stable
internal class OsShellRunnerPageActions(
    val stopCommand: (Boolean) -> Unit,
    val executeCommand: (String) -> Unit,
    val runCommand: () -> Unit,
    val openSaveCommandSheet: () -> Unit,
    val saveCommandToCard: () -> Unit,
    val copyOutput: () -> Unit,
    val formatOutput: () -> Unit,
    val clearOutput: (Boolean) -> Unit,
    val clearAllContent: () -> Unit,
    val applyCloseCleanup: () -> Unit,
    val requestClose: () -> Unit,
    val updateCommandInput: (String) -> Unit,
    val updatePersistInput: (Boolean) -> Unit,
    val updatePersistOutput: (Boolean) -> Unit,
    val updateTimeoutSeconds: (Int) -> Unit,
    val updateDangerousCommandConfirm: (Boolean) -> Unit,
    val updateCompletionToast: (Boolean) -> Unit,
    val updateStartupBehavior: (OsShellRunnerStartupBehavior) -> Unit,
    val updateExitCleanupMode: (OsShellRunnerExitCleanupMode) -> Unit,
    val updateAutoFormatOutput: (Boolean) -> Unit,
    val updateAutoScrollOutput: (Boolean) -> Unit,
    val updateOutputLimitChars: (Int) -> Unit,
    val updateOutputSaveMode: (OsShellRunnerOutputSaveMode) -> Unit,
    val updateCopyMode: (os.kei.ui.page.main.os.shell.OsShellRunnerCopyMode) -> Unit,
    val confirmDangerousCommand: () -> Unit,
)

@Composable
internal fun rememberOsShellRunnerPageActions(
    context: Context,
    shellRunnerViewModel: OsShellRunnerViewModel,
    pageState: OsShellRunnerPageStateHolder,
    persistentState: OsShellRunnerPersistentUiState,
    commandExecutionState: OsShellRunnerCommandExecutionState,
    currentOutputSnapshot: () -> OsShellRunnerOutputSnapshot,
    textBundle: OsShellRunnerTextBundle,
    canRunShellCommand: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRunShellCommand: OsShellRunnerCommandExecutor,
    onClose: () -> Unit,
): OsShellRunnerPageActions =
    rememberOsShellRunnerPageActionsInternal(
        context = context,
        shellRunnerViewModel = shellRunnerViewModel,
        pageState = pageState,
        latestPersistentState = rememberUpdatedState(persistentState),
        latestCommandExecutionState = rememberUpdatedState(commandExecutionState),
        latestOutputSnapshotProvider = rememberUpdatedState(currentOutputSnapshot),
        textBundle = textBundle,
        latestCanRunShellCommand = rememberUpdatedState(canRunShellCommand),
        onRequestShizukuPermission = onRequestShizukuPermission,
        onRunShellCommand = onRunShellCommand,
        onClose = onClose,
    )

@Composable
private fun rememberOsShellRunnerPageActionsInternal(
    context: Context,
    shellRunnerViewModel: OsShellRunnerViewModel,
    pageState: OsShellRunnerPageStateHolder,
    latestPersistentState: androidx.compose.runtime.State<OsShellRunnerPersistentUiState>,
    latestCommandExecutionState: androidx.compose.runtime.State<OsShellRunnerCommandExecutionState>,
    latestOutputSnapshotProvider: androidx.compose.runtime.State<() -> OsShellRunnerOutputSnapshot>,
    textBundle: OsShellRunnerTextBundle,
    latestCanRunShellCommand: androidx.compose.runtime.State<Boolean>,
    onRequestShizukuPermission: () -> Unit,
    onRunShellCommand: OsShellRunnerCommandExecutor,
    onClose: () -> Unit,
): OsShellRunnerPageActions =
    remember(
        context,
        shellRunnerViewModel,
        pageState,
        latestPersistentState,
        latestCommandExecutionState,
        latestOutputSnapshotProvider,
        textBundle,
        latestCanRunShellCommand,
        onRequestShizukuPermission,
        onRunShellCommand,
        onClose,
    ) {
        lateinit var actions: OsShellRunnerPageActions

        val stopCommand: (Boolean) -> Unit = { showStoppedOutput ->
            shellRunnerViewModel.stopShellCommand(showStoppedOutput)
        }
        val executeCommand: (String) -> Unit = { command ->
            val state = latestPersistentState.value
            val settings = state.settings
            if (!latestCommandExecutionState.value.runningCommand) {
                shellRunnerViewModel.runShellCommand(
                    command = command,
                    timeoutMs = settings.commandTimeoutSeconds.coerceAtLeast(5) * 1_000L,
                    commandStoppedText = textBundle.commandStoppedText,
                    commandCompletedText = textBundle.commandCompletedToast,
                    noOutputText = textBundle.noOutputText,
                    outputResultLabel = textBundle.outputResultLabel,
                    outputTimeLabel = textBundle.outputTimeLabel,
                    completionToast = settings.completionToast,
                    onRunShellCommand = onRunShellCommand,
                )
            }
        }
        val clearOutput: (Boolean) -> Unit = { showToast ->
            shellRunnerViewModel.clearOutput()
            if (showToast) {
                context.showLiquidToastOnly(textBundle.outputClearedToast)
            }
        }
        actions =
            OsShellRunnerPageActions(
                stopCommand = stopCommand,
                executeCommand = executeCommand,
                runCommand = {
                    if (!latestCommandExecutionState.value.runningCommand) {
                        val state = latestPersistentState.value
                        val settings = state.settings
                        val commandInput = state.commandInput
                        val command = commandInput.trim()
                        when {
                            command.isBlank() -> {
                                shellRunnerViewModel.replaceOutputMessage(textBundle.emptyCommandText)
                                context.showToast(textBundle.emptyCommandText)
                            }

                            !latestCanRunShellCommand.value -> {
                                onRequestShizukuPermission()
                                shellRunnerViewModel.replaceOutputMessage(textBundle.missingPermissionText)
                                context.showToast(textBundle.missingPermissionText)
                            }

                            settings.dangerousCommandConfirm && isPotentiallyDangerousShellCommand(command) -> {
                                pageState.openDangerousCommandConfirm(command)
                            }

                            else -> {
                                executeCommand(command)
                            }
                        }
                    }
                },
                openSaveCommandSheet = {
                    val commandInput = latestPersistentState.value.commandInput
                    shellRunnerViewModel.requestSaveCommandSheet(
                        command = commandInput,
                        commandSaveEmptyToast = textBundle.commandSaveEmptyToast,
                    )
                },
                saveCommandToCard = {
                    val state = latestPersistentState.value
                    val outputSnapshot = latestOutputSnapshotProvider.value()
                    shellRunnerViewModel.saveShellCommandCard(
                        command = state.commandInput,
                        title = pageState.saveTitleInput,
                        subtitle = pageState.saveSubtitleInput,
                        runOutput = outputSnapshot.latestRunResultOutput,
                        commandSaveEmptyToast = textBundle.commandSaveEmptyToast,
                        saveSheetTitleRequiredToast = textBundle.saveSheetTitleRequiredToast,
                        commandSavedToast = textBundle.commandSavedToast,
                    )
                },
                copyOutput = {
                    val state = latestPersistentState.value
                    val outputSnapshot = latestOutputSnapshotProvider.value()
                    val output =
                        resolveShellOutputCopyText(
                            settings = state.settings,
                            latestOutputEntry = outputSnapshot.latestEntry,
                            latestRunResultOutput = outputSnapshot.latestRunResultOutput,
                            outputText = outputSnapshot.text,
                        )
                    if (output.isBlank()) {
                        context.showToast(textBundle.outputCopyEmptyToast)
                    } else {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", output))
                            context.showLiquidToastOnly(textBundle.outputCopiedToast)
                        }
                    }
                },
                formatOutput = {
                    val outputText = latestOutputSnapshotProvider.value().text
                    val output = outputText.trim()
                    if (output.isBlank()) {
                        context.showToast(textBundle.outputFormatEmptyToast)
                    } else {
                        shellRunnerViewModel.formatOutput(
                            commandStoppedText = textBundle.commandStoppedText,
                            outputResultLabel = textBundle.outputResultLabel,
                            outputTimeLabel = textBundle.outputTimeLabel,
                            formattedToast = textBundle.outputFormattedToast,
                        )
                    }
                },
                clearOutput = clearOutput,
                clearAllContent = {
                    stopCommand(false)
                    shellRunnerViewModel.updateCommandInput("")
                    clearOutput(false)
                    context.showLiquidToastOnly(textBundle.clearAllToast)
                },
                applyCloseCleanup = {
                    if (pageState.consumeCloseCleanupRequest()) {
                        val settings = latestPersistentState.value.settings
                        stopCommand(false)
                        when (settings.exitCleanupMode) {
                            OsShellRunnerExitCleanupMode.KeepAll -> {
                                Unit
                            }

                            OsShellRunnerExitCleanupMode.ClearInput -> {
                                shellRunnerViewModel.updateCommandInput("")
                                shellRunnerViewModel.clearSavedInput()
                            }

                            OsShellRunnerExitCleanupMode.ClearOutput -> {
                                clearOutput(false)
                                shellRunnerViewModel.clearSavedOutput()
                            }
                        }
                    }
                },
                requestClose = {
                    actions.applyCloseCleanup()
                    onClose()
                },
                updateCommandInput = shellRunnerViewModel::updateCommandInput,
                updatePersistInput = shellRunnerViewModel::updatePersistInput,
                updatePersistOutput = shellRunnerViewModel::updatePersistOutput,
                updateTimeoutSeconds = shellRunnerViewModel::updateTimeoutSeconds,
                updateDangerousCommandConfirm = shellRunnerViewModel::updateDangerousCommandConfirm,
                updateCompletionToast = shellRunnerViewModel::updateCompletionToast,
                updateStartupBehavior = { behavior ->
                    shellRunnerViewModel.updateStartupBehavior(behavior)
                    if (behavior == OsShellRunnerStartupBehavior.FocusInput) {
                        pageState.requestStartupFocus()
                    }
                },
                updateExitCleanupMode = shellRunnerViewModel::updateExitCleanupMode,
                updateAutoFormatOutput = shellRunnerViewModel::updateAutoFormatOutput,
                updateAutoScrollOutput = shellRunnerViewModel::updateAutoScrollOutput,
                updateOutputLimitChars = { limit ->
                    shellRunnerViewModel.updateOutputLimitChars(
                        limit = limit,
                        commandStoppedText = textBundle.commandStoppedText,
                        outputResultLabel = textBundle.outputResultLabel,
                        outputTimeLabel = textBundle.outputTimeLabel,
                    )
                },
                updateOutputSaveMode = { mode ->
                    shellRunnerViewModel.updateOutputSaveMode(
                        mode = mode,
                        commandStoppedText = textBundle.commandStoppedText,
                        outputResultLabel = textBundle.outputResultLabel,
                        outputTimeLabel = textBundle.outputTimeLabel,
                    )
                },
                updateCopyMode = shellRunnerViewModel::updateCopyMode,
                confirmDangerousCommand = {
                    val command = pageState.pendingDangerousCommand.trim()
                    pageState.dismissDangerousCommandConfirm()
                    if (command.isNotBlank()) {
                        executeCommand(command)
                    }
                },
            )
        actions
    }
