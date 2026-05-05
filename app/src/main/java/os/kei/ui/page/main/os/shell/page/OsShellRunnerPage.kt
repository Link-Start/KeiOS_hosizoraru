package os.kei.ui.page.main.os.shell.page

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.osLucideClearAllIcon
import os.kei.ui.page.main.os.shell.OsShellBehaviorSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellOutputSettingsSheet
import os.kei.ui.page.main.os.shell.OsShellRunnerCopyMode
import os.kei.ui.page.main.os.shell.OsShellRunnerExitCleanupMode
import os.kei.ui.page.main.os.shell.OsShellRunnerStartupBehavior
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.component.OsShellRunnerInputCard
import os.kei.ui.page.main.os.shell.component.OsShellRunnerOutputCard
import os.kei.ui.page.main.os.shell.component.OsShellRunnerSaveSheet
import os.kei.ui.page.main.os.shell.state.BindOsShellRunnerAutoScrollEffect
import os.kei.ui.page.main.os.shell.state.BindOsShellRunnerPersistEffects
import os.kei.ui.page.main.os.shell.state.rememberOsShellRunnerTextBundle
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val dangerousShellPatterns = listOf(
    Regex("""(^|\s)rm(\s+-[^\n]*)?\s+/(?!sdcard)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)pm\s+uninstall(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)settings\s+put\s+global(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)settings\s+delete\s+(system|secure|global)(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)setprop(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)reboot(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)am\s+force-stop(\s|$)""", RegexOption.IGNORE_CASE)
)

private fun isPotentiallyDangerousShellCommand(command: String): Boolean {
    val normalized = command.trim()
    if (normalized.isBlank()) return false
    return dangerousShellPatterns.any { regex -> regex.containsMatchIn(normalized) }
}

@Composable
fun OsShellRunnerPage(
    canRunShellCommand: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRunShellCommand: suspend (String, Long) -> String?,
    onClose: () -> Unit
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
        textBundle.outputTimeLabel
    ) {
        shellRunnerViewModel.loadPersistentState(
            commandStoppedText = textBundle.commandStoppedText,
            outputResultLabel = textBundle.outputResultLabel,
            outputTimeLabel = textBundle.outputTimeLabel
        )
    }
    LaunchedEffect(shellRunnerViewModel) {
        shellRunnerViewModel.refreshChromePrefs()
    }
    val persistentState by shellRunnerViewModel.persistentState.collectAsState()
    val chromePrefs by shellRunnerViewModel.chromePrefs.collectAsState()
    val isDark = isSystemInDarkTheme()
    val shellCommandAccentColor = if (isDark) Color(0xFF7AB8FF) else Color(0xFF2563EB)
    val shellSuccessAccentColor = if (isDark) Color(0xFF7EE7A8) else Color(0xFF15803D)
    val shellStoppedAccentColor = if (isDark) Color(0xFFFF9E9E) else Color(0xFFDC2626)
    val outputScrollState = rememberScrollState()
    val liquidActionBarLayeredStyleEnabled = chromePrefs.liquidActionBarLayeredStyleEnabled
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shellRunnerViewModel.refreshChromePrefs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val topBarBackdrop = rememberLayerBackdrop {
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
    var closeCleanupApplied by remember { mutableStateOf(false) }

    val latestOutputEntry = remember(outputEntries) { outputEntries.lastOrNull() }

    suspend fun appendOutput(command: String, result: String) {
        shellRunnerViewModel.appendOutput(
            command = command,
            result = result,
            commandStoppedText = textBundle.commandStoppedText,
            outputResultLabel = textBundle.outputResultLabel,
            outputTimeLabel = textBundle.outputTimeLabel
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
        val job = scope.launch {
            runningCommand = true
            try {
                val output = runCatching { onRunShellCommand(command, timeoutMs) }
                    .getOrElse { throwable ->
                        if (throwable is CancellationException) throw throwable
                        throwable.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: throwable.javaClass.simpleName
                    }
                    ?.takeIf { it.isNotBlank() }
                    ?: textBundle.noOutputText
                appendOutput(command, output)
                if (settings.completionToast) {
                    Toast.makeText(context, textBundle.commandCompletedToast, Toast.LENGTH_SHORT).show()
                }
            } catch (_: CancellationException) {
                if (suppressStopOutputAppend) {
                    suppressStopOutputAppend = false
                } else {
                    withContext(NonCancellable) {
                        appendOutput(command, textBundle.commandStoppedText)
                    }
                    if (settings.completionToast) {
                        Toast.makeText(context, textBundle.commandStoppedText, Toast.LENGTH_SHORT).show()
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
            return
        }
        if (!canRunShellCommand) {
            onRequestShizukuPermission()
            shellRunnerViewModel.replaceOutputMessage(textBundle.missingPermissionText)
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
            Toast.makeText(context, textBundle.commandSaveEmptyToast, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            saveTitleInput = ""
            saveSubtitleInput = shellRunnerViewModel.latestShellCardSubtitle(command)
            showSaveSheet = true
        }
    }

    fun saveCommandToCard() {
        val command = commandInput.trim()
        if (command.isBlank()) {
            Toast.makeText(context, textBundle.commandSaveEmptyToast, Toast.LENGTH_SHORT).show()
            return
        }
        val title = saveTitleInput.trim()
        if (title.isBlank()) {
            Toast.makeText(context, textBundle.saveSheetTitleRequiredToast, Toast.LENGTH_SHORT).show()
            return
        }
        val subtitle = saveSubtitleInput.trim()
        scope.launch {
            val saved = shellRunnerViewModel.createShellCommandCard(
                command = command,
                title = title,
                subtitle = subtitle,
                runOutput = latestRunResultOutput
            )
            if (saved) {
                showSaveSheet = false
                Toast.makeText(context, textBundle.commandSavedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyOutput() {
        val preferred = when (settings.copyMode) {
            OsShellRunnerCopyMode.FullHistory -> outputText.trim()
            OsShellRunnerCopyMode.LatestResult -> latestOutputEntry?.result.orEmpty().trim()
        }
        val output = preferred.ifBlank {
            if (settings.copyMode == OsShellRunnerCopyMode.LatestResult) {
                latestRunResultOutput.trim().ifBlank { outputText.trim() }
            } else {
                outputText.trim()
            }
        }
        if (output.isBlank()) {
            Toast.makeText(context, textBundle.outputCopyEmptyToast, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", output))
        Toast.makeText(context, textBundle.outputCopiedToast, Toast.LENGTH_SHORT).show()
    }

    fun formatOutput() {
        val output = outputText.trim()
        if (output.isBlank()) {
            Toast.makeText(context, textBundle.outputFormatEmptyToast, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            shellRunnerViewModel.formatOutput(
                commandStoppedText = textBundle.commandStoppedText,
                outputResultLabel = textBundle.outputResultLabel,
                outputTimeLabel = textBundle.outputTimeLabel
            )
            Toast.makeText(context, textBundle.outputFormattedToast, Toast.LENGTH_SHORT).show()
        }
    }

    fun clearOutput() {
        shellRunnerViewModel.clearOutput()
    }

    fun clearAllContent() {
        stopCommand(showStoppedOutput = false)
        shellRunnerViewModel.updateCommandInput("")
        clearOutput()
        Toast.makeText(context, textBundle.clearAllToast, Toast.LENGTH_SHORT).show()
    }

    fun applyCloseCleanup() {
        if (closeCleanupApplied) return
        closeCleanupApplied = true
        stopCommand(showStoppedOutput = false)
        when (latestExitCleanupMode.value) {
            OsShellRunnerExitCleanupMode.KeepAll -> Unit
            OsShellRunnerExitCleanupMode.ClearInput -> {
                shellRunnerViewModel.updateCommandInput("")
                shellRunnerViewModel.clearSavedInput()
            }
            OsShellRunnerExitCleanupMode.ClearOutput -> {
                clearOutput()
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
        enabled = !showSaveSheet &&
                !showBehaviorSettingsSheet &&
                !showOutputSettingsSheet &&
                showDangerousCommandConfirm
    ) {
        showDangerousCommandConfirm = false
        pendingDangerousCommand = ""
    }
    val clearAllIcon = osLucideClearAllIcon()
    val behaviorSettingsIcon = appLucideConfigIcon()
    val outputSettingsIcon = appLucideNotesIcon()
    val behaviorSettingsDescription = stringResource(R.string.os_shell_action_behavior_settings)
    val outputSettingsDescription = stringResource(R.string.os_shell_action_output_settings)
    val actionItems = remember(
        textBundle.clearAllActionDescription,
        behaviorSettingsDescription,
        outputSettingsDescription
    ) {
        listOf(
            LiquidActionItem(
                icon = clearAllIcon,
                contentDescription = textBundle.clearAllActionDescription,
                onClick = { clearAllContent() }
            ),
            LiquidActionItem(
                icon = behaviorSettingsIcon,
                contentDescription = behaviorSettingsDescription,
                onClick = { showBehaviorSettingsSheet = true }
            ),
            LiquidActionItem(
                icon = outputSettingsIcon,
                contentDescription = outputSettingsDescription,
                onClick = { showOutputSettingsSheet = true }
            )
        )
    }

    BindOsShellRunnerPersistEffects(
        persistInputEnabled = settings.persistInput,
        persistOutputEnabled = settings.persistOutput,
        commandInput = commandInput,
        outputText = outputText,
        onPersistInput = shellRunnerViewModel::persistInput,
        onPersistOutput = shellRunnerViewModel::persistOutput
    )
    BindOsShellRunnerAutoScrollEffect(
        outputText = outputText,
        outputScrollState = outputScrollState,
        enabled = settings.autoScrollOutput
    )

    AppPageScaffold(
        title = textBundle.shellPageTitle,
        largeTitle = textBundle.shellPageTitle,
        scrollBehavior = scrollBehavior,
        titleBackdrop = topBarBackdrop,
        reserveTopEndActionSpace = true,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = { requestClose() },
                backdrop = topBarBackdrop,
                layeredStyleEnabled = liquidActionBarLayeredStyleEnabled
            )
        },
        actions = {
            LiquidActionBar(
                backdrop = topBarBackdrop,
                layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                items = actionItems
            )
        }
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = pageListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(topBarBackdrop),
            sectionSpacing = AppChromeTokens.pageSectionGap
        ) {
            item(key = "shell_input_card") {
                OsShellRunnerInputCard(
                    inputTitle = textBundle.inputTitle,
                    inputHint = textBundle.inputHint,
                    commandInput = commandInput,
                    onCommandInputChange = shellRunnerViewModel::updateCommandInput,
                    runningCommand = runningCommand,
                    runActionDescription = textBundle.runActionDescription,
                    stopActionDescription = textBundle.stopActionDescription,
                    saveCommandActionDescription = textBundle.saveCommandActionDescription,
                    focusRequestToken = startupFocusRequestToken,
                    onRunCommand = { runCommand() },
                    onStopCommand = { stopCommand() },
                    onOpenSaveCommandSheet = { openSaveCommandSheet() }
                )
            }
            item(key = "shell_output_card") {
                OsShellRunnerOutputCard(
                    outputTitle = textBundle.outputTitle,
                    outputHint = textBundle.outputHint,
                    outputText = outputText,
                    outputEntries = outputEntries,
                    outputScrollState = outputScrollState,
                    formatOutputActionDescription = textBundle.formatOutputActionDescription,
                    copyOutputActionDescription = textBundle.copyOutputActionDescription,
                    clearOutputActionDescription = textBundle.clearOutputActionDescription,
                    onFormatOutput = { formatOutput() },
                    onCopyOutput = { copyOutput() },
                    onClearOutput = { clearOutput() }
                )
            }
        }
    }

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
        onSaveTitleInputChange = { saveTitleInput = it },
        saveSubtitleInput = saveSubtitleInput,
        onSaveSubtitleInputChange = { saveSubtitleInput = it },
        shellCommandAccentColor = shellCommandAccentColor,
        shellSuccessAccentColor = shellSuccessAccentColor,
        shellStoppedAccentColor = shellStoppedAccentColor,
        onDismissRequest = { showSaveSheet = false },
        onConfirm = { saveCommandToCard() }
    )

    CompositionLocalProvider(LocalLiquidControlsEnabled provides chromePrefs.liquidSwitchEnabled) {
        OsShellBehaviorSettingsSheet(
            show = showBehaviorSettingsSheet,
            onDismissRequest = { showBehaviorSettingsSheet = false },
            settings = settings,
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
        )
        OsShellOutputSettingsSheet(
            show = showOutputSettingsSheet,
            onDismissRequest = { showOutputSettingsSheet = false },
            settings = settings,
            onPersistOutputEnabledChange = shellRunnerViewModel::updatePersistOutput,
            onAutoFormatOutputChange = shellRunnerViewModel::updateAutoFormatOutput,
            onAutoScrollOutputChange = shellRunnerViewModel::updateAutoScrollOutput,
            onOutputLimitCharsChange = { limit ->
                shellRunnerViewModel.updateOutputLimitChars(
                    limit = limit,
                    commandStoppedText = textBundle.commandStoppedText,
                    outputResultLabel = textBundle.outputResultLabel,
                    outputTimeLabel = textBundle.outputTimeLabel
                )
            },
            onOutputSaveModeChange = { mode ->
                shellRunnerViewModel.updateOutputSaveMode(
                    mode = mode,
                    commandStoppedText = textBundle.commandStoppedText,
                    outputResultLabel = textBundle.outputResultLabel,
                    outputTimeLabel = textBundle.outputTimeLabel
                )
            },
            onCopyModeChange = shellRunnerViewModel::updateCopyMode,
        )
    }

    val dangerousCommandPreview = remember(pendingDangerousCommand) {
        pendingDangerousCommand.trim().replace('\n', ' ').take(120)
    }
    OsShellDangerousCommandConfirmDialog(
        show = showDangerousCommandConfirm,
        title = textBundle.dangerousCommandDialogTitle,
        summary = stringResource(
            R.string.os_shell_dangerous_command_dialog_summary,
            dangerousCommandPreview.ifBlank { "-" }
        ),
        confirmText = textBundle.dangerousCommandConfirmText,
        onDismissRequest = {
            showDangerousCommandConfirm = false
            pendingDangerousCommand = ""
        },
        onConfirm = {
            val command = pendingDangerousCommand.trim()
            showDangerousCommandConfirm = false
            pendingDangerousCommand = ""
            if (command.isNotBlank()) {
                executeCommand(command)
            }
        }
    )
}

@Composable
private fun OsShellBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    BackHandler(enabled = enabled) {
        onBack()
    }
}

@Composable
private fun OsShellDangerousCommandConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidDialogActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest
            )
            AppLiquidDialogActionButton(
                modifier = Modifier.weight(1f),
                text = confirmText,
                containerColor = MiuixTheme.colorScheme.error,
                textColor = MiuixTheme.colorScheme.onError,
                variant = GlassVariant.SheetDangerAction,
                onClick = onConfirm
            )
        }
    }
}
