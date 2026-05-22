@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.ui.page.main.back.BackNavigationSource
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.back.KeiOSBackNavigationHandler
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.osLucideClearAllIcon
import os.kei.ui.page.main.os.shell.OsShellRunnerExitCleanupMode
import os.kei.ui.page.main.os.shell.OsShellRunnerStartupBehavior
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.state.BindOsShellRunnerAutoScrollEffect
import os.kei.ui.page.main.os.shell.state.BindOsShellRunnerPersistEffects
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
    val scope = rememberCoroutineScope()
    val pageListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val textBundle = rememberOsShellRunnerTextBundle()
    val shellRunnerViewModel: OsShellRunnerViewModel = viewModel()
    LaunchedEffect(
        textBundle.commandStoppedText,
        textBundle.outputResultLabel,
        textBundle.outputTimeLabel,
    ) {
        shellRunnerViewModel.loadPersistentState(
            commandStoppedText = textBundle.commandStoppedText,
            outputResultLabel = textBundle.outputResultLabel,
            outputTimeLabel = textBundle.outputTimeLabel,
        )
    }
    LaunchedEffect(shellRunnerViewModel) {
        shellRunnerViewModel.refreshChromePrefs()
    }
    val persistentState by shellRunnerViewModel.persistentState.collectAsStateWithLifecycle()
    val chromePrefs by shellRunnerViewModel.chromePrefs.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val shellCommandAccentColor = if (isDark) Color(0xFF7AB8FF) else Color(0xFF2563EB)
    val shellSuccessAccentColor = if (isDark) Color(0xFF7EE7A8) else Color(0xFF15803D)
    val shellStoppedAccentColor = if (isDark) Color(0xFFFF9E9E) else Color(0xFFDC2626)
    val outputScrollState = rememberScrollState()
    val liquidActionBarLayeredStyleEnabled = chromePrefs.liquidActionBarLayeredStyleEnabled
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    shellRunnerViewModel.refreshChromePrefs()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val topBarBackdrop =
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

    val commandInput = persistentState.commandInput
    val settings = persistentState.settings
    val latestExitCleanupMode = rememberUpdatedState(settings.exitCleanupMode)
    val outputText = persistentState.outputState.outputText
    val outputEntries = persistentState.outputState.outputEntries
    val latestRunResultOutput = persistentState.outputState.latestRunResultOutput
    var startupFocusRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var startupFocusApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(persistentState.loaded) {
        if (
            persistentState.loaded &&
            !startupFocusApplied &&
            settings.startupBehavior == OsShellRunnerStartupBehavior.FocusInput
        ) {
            startupFocusRequestToken += 1
            startupFocusApplied = true
        }
    }

    var runningCommand by remember { mutableStateOf(false) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var suppressStopOutputAppend by remember { mutableStateOf(false) }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    var showBehaviorSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showOutputSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showDangerousCommandConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDangerousCommand by rememberSaveable { mutableStateOf("") }
    var saveTitleInput by rememberSaveable { mutableStateOf("") }
    var saveSubtitleInput by rememberSaveable { mutableStateOf("") }
    var saveInitialSubtitleInput by rememberSaveable { mutableStateOf("") }
    var closeCleanupApplied by remember { mutableStateOf(false) }

    val latestOutputEntry = remember(outputEntries) { outputEntries.lastOrNull() }

    suspend fun appendOutput(
        command: String,
        result: String,
    ) {
        shellRunnerViewModel.appendOutput(
            command = command,
            result = result,
            commandStoppedText = textBundle.commandStoppedText,
            outputResultLabel = textBundle.outputResultLabel,
            outputTimeLabel = textBundle.outputTimeLabel,
        )
    }

    fun stopCommand(showStoppedOutput: Boolean = true) {
        val job = runningJob ?: return
        if (!showStoppedOutput) {
            suppressStopOutputAppend = true
        }
        job.cancel(CancellationException("user-stop"))
    }

    fun executeCommand(command: String) {
        if (runningCommand) return
        val timeoutMs = settings.commandTimeoutSeconds.coerceAtLeast(5) * 1_000L
        val job =
            scope.launch {
                runningCommand = true
                try {
                    val output =
                        runCatching { onRunShellCommand(command, timeoutMs) }
                            .getOrElse { throwable ->
                                if (throwable is CancellationException) throw throwable
                                throwable.localizedMessage?.takeIf { it.isNotBlank() }
                                    ?: throwable.javaClass.simpleName
                            }?.takeIf { it.isNotBlank() }
                            ?: textBundle.noOutputText
                    appendOutput(command, output)
                    if (settings.completionToast) {
                        context.showLiquidToastOnly(textBundle.commandCompletedToast)
                    }
                } catch (_: CancellationException) {
                    if (suppressStopOutputAppend) {
                        suppressStopOutputAppend = false
                    } else {
                        withContext(NonCancellable) {
                            appendOutput(command, textBundle.commandStoppedText)
                        }
                        if (settings.completionToast) {
                            context.showLiquidToastOnly(textBundle.commandStoppedText)
                        }
                    }
                } finally {
                    runningCommand = false
                    runningJob = null
                }
            }
        runningJob = job
    }

    fun runCommand() {
        if (runningCommand) return
        val command = commandInput.trim()
        if (command.isBlank()) {
            shellRunnerViewModel.replaceOutputMessage(textBundle.emptyCommandText)
            context.showToast(textBundle.emptyCommandText)
            return
        }
        if (!canRunShellCommand) {
            onRequestShizukuPermission()
            shellRunnerViewModel.replaceOutputMessage(textBundle.missingPermissionText)
            context.showToast(textBundle.missingPermissionText)
            return
        }
        if (settings.dangerousCommandConfirm && isPotentiallyDangerousShellCommand(command)) {
            pendingDangerousCommand = command
            showDangerousCommandConfirm = true
            return
        }
        executeCommand(command)
    }

    fun openSaveCommandSheet() {
        val command = commandInput.trim()
        if (command.isBlank()) {
            context.showToast(textBundle.commandSaveEmptyToast)
            return
        }
        scope.launch {
            saveTitleInput = ""
            val suggestedSubtitle = shellRunnerViewModel.latestShellCardSubtitle(command)
            saveSubtitleInput = suggestedSubtitle
            saveInitialSubtitleInput = suggestedSubtitle
            showSaveSheet = true
        }
    }

    fun saveCommandToCard() {
        val command = commandInput.trim()
        if (command.isBlank()) {
            context.showToast(textBundle.commandSaveEmptyToast)
            return
        }
        val title = saveTitleInput.trim()
        if (title.isBlank()) {
            context.showToast(textBundle.saveSheetTitleRequiredToast)
            return
        }
        val subtitle = saveSubtitleInput.trim()
        scope.launch {
            val saved =
                shellRunnerViewModel.createShellCommandCard(
                    command = command,
                    title = title,
                    subtitle = subtitle,
                    runOutput = latestRunResultOutput,
                )
            if (saved) {
                showSaveSheet = false
                context.showLiquidToastOnly(textBundle.commandSavedToast)
            }
        }
    }

    fun copyOutput() {
        val output =
            resolveShellOutputCopyText(
                settings = settings,
                latestOutputEntry = latestOutputEntry,
                latestRunResultOutput = latestRunResultOutput,
                outputText = outputText,
            )
        if (output.isBlank()) {
            context.showToast(textBundle.outputCopyEmptyToast)
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", output))
        context.showLiquidToastOnly(textBundle.outputCopiedToast)
    }

    fun formatOutput() {
        val output = outputText.trim()
        if (output.isBlank()) {
            context.showToast(textBundle.outputFormatEmptyToast)
            return
        }
        scope.launch {
            shellRunnerViewModel.formatOutput(
                commandStoppedText = textBundle.commandStoppedText,
                outputResultLabel = textBundle.outputResultLabel,
                outputTimeLabel = textBundle.outputTimeLabel,
            )
            context.showLiquidToastOnly(textBundle.outputFormattedToast)
        }
    }

    fun clearOutput(showToast: Boolean = true) {
        shellRunnerViewModel.clearOutput()
        if (showToast) {
            context.showLiquidToastOnly(textBundle.outputClearedToast)
        }
    }

    fun clearAllContent() {
        stopCommand(showStoppedOutput = false)
        shellRunnerViewModel.updateCommandInput("")
        clearOutput(showToast = false)
        context.showLiquidToastOnly(textBundle.clearAllToast)
    }

    fun applyCloseCleanup() {
        if (closeCleanupApplied) return
        closeCleanupApplied = true
        stopCommand(showStoppedOutput = false)
        when (latestExitCleanupMode.value) {
            OsShellRunnerExitCleanupMode.KeepAll -> {
                Unit
            }

            OsShellRunnerExitCleanupMode.ClearInput -> {
                shellRunnerViewModel.updateCommandInput("")
                shellRunnerViewModel.clearSavedInput()
            }

            OsShellRunnerExitCleanupMode.ClearOutput -> {
                clearOutput(showToast = false)
                shellRunnerViewModel.clearSavedOutput()
            }
        }
    }

    fun requestClose() {
        applyCloseCleanup()
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            applyCloseCleanup()
        }
    }

    OsShellBackHandler(enabled = showSaveSheet) { showSaveSheet = false }
    OsShellBackHandler(enabled = !showSaveSheet && showBehaviorSettingsSheet) {
        showBehaviorSettingsSheet = false
    }
    OsShellBackHandler(enabled = !showSaveSheet && showOutputSettingsSheet) {
        showOutputSettingsSheet = false
    }
    OsShellBackHandler(
        enabled =
            !showSaveSheet &&
                !showBehaviorSettingsSheet &&
                !showOutputSettingsSheet &&
                showDangerousCommandConfirm,
    ) {
        showDangerousCommandConfirm = false
        pendingDangerousCommand = ""
    }
    KeiOSActivityRootBackHandler(
        enabled =
            !showSaveSheet &&
                !showBehaviorSettingsSheet &&
                !showOutputSettingsSheet &&
                !showDangerousCommandConfirm,
        needsInterception = false,
    ) {
        requestClose()
    }
    val clearAllIcon = osLucideClearAllIcon()
    val behaviorSettingsIcon = appLucideConfigIcon()
    val outputSettingsIcon = appLucideNotesIcon()
    val behaviorSettingsDescription = stringResource(R.string.os_shell_action_behavior_settings)
    val outputSettingsDescription = stringResource(R.string.os_shell_action_output_settings)
    val actionItems =
        remember(
            textBundle.clearAllActionDescription,
            behaviorSettingsDescription,
            outputSettingsDescription,
        ) {
            listOf(
                LiquidActionItem(
                    icon = clearAllIcon,
                    contentDescription = textBundle.clearAllActionDescription,
                    onClick = { clearAllContent() },
                ),
                LiquidActionItem(
                    icon = behaviorSettingsIcon,
                    contentDescription = behaviorSettingsDescription,
                    onClick = { showBehaviorSettingsSheet = true },
                ),
                LiquidActionItem(
                    icon = outputSettingsIcon,
                    contentDescription = outputSettingsDescription,
                    onClick = { showOutputSettingsSheet = true },
                ),
            )
        }

    BindOsShellRunnerPersistEffects(
        persistInputEnabled = settings.persistInput,
        persistOutputEnabled = settings.persistOutput,
        commandInput = commandInput,
        outputText = outputText,
        onPersistInput = shellRunnerViewModel::persistInput,
        onPersistOutput = shellRunnerViewModel::persistOutput,
    )
    BindOsShellRunnerAutoScrollEffect(
        outputText = outputText,
        outputScrollState = outputScrollState,
        enabled = settings.autoScrollOutput,
    )

    OsShellRunnerContent(
        textBundle = textBundle,
        scrollBehavior = scrollBehavior,
        topBarBackdrop = topBarBackdrop,
        pageListState = pageListState,
        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
        actionItems = actionItems,
        commandInput = commandInput,
        runningCommand = runningCommand,
        startupFocusRequestToken = startupFocusRequestToken,
        outputText = outputText,
        outputEntries = outputEntries,
        outputScrollState = outputScrollState,
        onRequestClose = { requestClose() },
        onCommandInputChange = shellRunnerViewModel::updateCommandInput,
        onRunCommand = { runCommand() },
        onStopCommand = { stopCommand() },
        onOpenSaveCommandSheet = { openSaveCommandSheet() },
        onFormatOutput = { formatOutput() },
        onCopyOutput = { copyOutput() },
        onClearOutput = { clearOutput() },
    )

    val dangerousCommandPreview =
        remember(pendingDangerousCommand) {
            pendingDangerousCommand.trim().replace('\n', ' ').take(120)
        }
    OsShellRunnerSheets(
        textBundle = textBundle,
        showSaveSheet = showSaveSheet,
        showBehaviorSettingsSheet = showBehaviorSettingsSheet,
        showOutputSettingsSheet = showOutputSettingsSheet,
        showDangerousCommandConfirm = showDangerousCommandConfirm,
        commandInput = commandInput,
        latestOutputEntry = latestOutputEntry,
        saveTitleInput = saveTitleInput,
        onSaveTitleInputChange = { saveTitleInput = it },
        saveSubtitleInput = saveSubtitleInput,
        onSaveSubtitleInputChange = { saveSubtitleInput = it },
        saveInitialSubtitleInput = saveInitialSubtitleInput,
        shellCommandAccentColor = shellCommandAccentColor,
        shellSuccessAccentColor = shellSuccessAccentColor,
        shellStoppedAccentColor = shellStoppedAccentColor,
        settings = settings,
        chromePrefs = chromePrefs,
        dangerousCommandPreview = dangerousCommandPreview,
        onDismissSaveSheet = { showSaveSheet = false },
        onSaveSheetDismissFinished = {
            if (!showSaveSheet) {
                saveTitleInput = ""
                saveSubtitleInput = ""
                saveInitialSubtitleInput = ""
            }
        },
        onConfirmSave = { saveCommandToCard() },
        onDismissBehaviorSettings = { showBehaviorSettingsSheet = false },
        onPersistInputEnabledChange = shellRunnerViewModel::updatePersistInput,
        onTimeoutSecondsChange = shellRunnerViewModel::updateTimeoutSeconds,
        onDangerousCommandConfirmChange = shellRunnerViewModel::updateDangerousCommandConfirm,
        onCompletionToastChange = shellRunnerViewModel::updateCompletionToast,
        onStartupBehaviorChange = { behavior ->
            shellRunnerViewModel.updateStartupBehavior(behavior)
            if (behavior == OsShellRunnerStartupBehavior.FocusInput) {
                startupFocusRequestToken += 1
            }
        },
        onExitCleanupModeChange = shellRunnerViewModel::updateExitCleanupMode,
        onDismissOutputSettings = { showOutputSettingsSheet = false },
        onPersistOutputEnabledChange = shellRunnerViewModel::updatePersistOutput,
        onAutoFormatOutputChange = shellRunnerViewModel::updateAutoFormatOutput,
        onAutoScrollOutputChange = shellRunnerViewModel::updateAutoScrollOutput,
        onOutputLimitCharsChange = { limit ->
            shellRunnerViewModel.updateOutputLimitChars(
                limit = limit,
                commandStoppedText = textBundle.commandStoppedText,
                outputResultLabel = textBundle.outputResultLabel,
                outputTimeLabel = textBundle.outputTimeLabel,
            )
        },
        onOutputSaveModeChange = { mode ->
            shellRunnerViewModel.updateOutputSaveMode(
                mode = mode,
                commandStoppedText = textBundle.commandStoppedText,
                outputResultLabel = textBundle.outputResultLabel,
                outputTimeLabel = textBundle.outputTimeLabel,
            )
        },
        onCopyModeChange = shellRunnerViewModel::updateCopyMode,
        onDismissDangerousCommand = {
            showDangerousCommandConfirm = false
            pendingDangerousCommand = ""
        },
        onConfirmDangerousCommand = {
            val command = pendingDangerousCommand.trim()
            showDangerousCommandConfirm = false
            pendingDangerousCommand = ""
            if (command.isNotBlank()) {
                executeCommand(command)
            }
        },
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
