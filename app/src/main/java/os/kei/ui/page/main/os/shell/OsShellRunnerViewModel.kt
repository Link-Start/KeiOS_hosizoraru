package os.kei.ui.page.main.os.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.os.shell.state.OsShellRunnerOutputState

internal class OsShellRunnerViewModel : ViewModel() {
    private val repository = OsShellRunnerRepository()
    private var loadJob: Job? = null
    private var commandJob: Job? = null
    private var suppressStopOutputAppend = false
    private val commandExecutionMutableState = MutableStateFlow(OsShellRunnerCommandExecutionState())
    private val commandSaveMutableState = MutableStateFlow(OsShellRunnerCommandSaveState())
    private val eventMutableFlow = MutableSharedFlow<OsShellRunnerEvent>(extraBufferCapacity = 8)

    val commandExecutionState: StateFlow<OsShellRunnerCommandExecutionState> =
        commandExecutionMutableState.asStateFlow()

    val commandSaveState: StateFlow<OsShellRunnerCommandSaveState> =
        commandSaveMutableState.asStateFlow()

    val events: SharedFlow<OsShellRunnerEvent> = eventMutableFlow.asSharedFlow()

    val persistentState: StateFlow<OsShellRunnerPersistentState> =
        repository
            .observePersistentState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = repository.observePersistentState().value,
            )

    val chromePrefs: StateFlow<OsShellRunnerChromePrefs> =
        repository
            .observeChromePrefs()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = repository.observeChromePrefs().value,
            )

    fun loadPersistentState(
        commandStoppedText: String,
        outputResultLabel: String,
        outputTimeLabel: String,
    ) {
        if (loadJob != null) return
        loadJob =
            viewModelScope.launch {
                repository.loadPersistentState(
                    commandStoppedText = commandStoppedText,
                    outputResultLabel = outputResultLabel,
                    outputTimeLabel = outputTimeLabel,
                )
            }
    }

    fun refreshChromePrefs() {
        viewModelScope.launch {
            repository.refreshChromePrefs()
        }
    }

    fun updateCommandInput(value: String) {
        repository.updateCommandInput(value)
    }

    fun updateOutputState(outputState: OsShellRunnerOutputState) {
        repository.updateOutputState(outputState)
    }

    fun replaceOutputMessage(message: String) {
        repository.replaceOutputMessage(message)
    }

    fun clearOutput() {
        repository.clearOutput()
    }

    fun runShellCommand(
        command: String,
        timeoutMs: Long,
        commandStoppedText: String,
        commandCompletedText: String,
        noOutputText: String,
        outputResultLabel: String,
        outputTimeLabel: String,
        completionToast: Boolean,
        onRunShellCommand: suspend (String, Long) -> String?,
    ) {
        if (commandExecutionMutableState.value.runningCommand) return
        commandJob =
            viewModelScope.launch {
                commandExecutionMutableState.update { it.copy(runningCommand = true) }
                try {
                    val output =
                        runCatching { onRunShellCommand(command, timeoutMs) }
                            .getOrElse { throwable ->
                                if (throwable is CancellationException) throw throwable
                                throwable.localizedMessage?.takeIf { it.isNotBlank() }
                                    ?: throwable.javaClass.simpleName
                            }?.takeIf { it.isNotBlank() }
                            ?: noOutputText
                    repository.appendOutput(
                        command = command,
                        result = output,
                        commandStoppedText = commandStoppedText,
                        outputResultLabel = outputResultLabel,
                        outputTimeLabel = outputTimeLabel,
                    )
                    if (completionToast) {
                        eventMutableFlow.emit(OsShellRunnerEvent.LiquidToast(commandCompletedText))
                    }
                } catch (_: CancellationException) {
                    if (suppressStopOutputAppend) {
                        suppressStopOutputAppend = false
                    } else {
                        withContext(NonCancellable) {
                            repository.appendOutput(
                                command = command,
                                result = commandStoppedText,
                                commandStoppedText = commandStoppedText,
                                outputResultLabel = outputResultLabel,
                                outputTimeLabel = outputTimeLabel,
                            )
                        }
                        if (completionToast) {
                            eventMutableFlow.emit(OsShellRunnerEvent.LiquidToast(commandStoppedText))
                        }
                    }
                } finally {
                    commandExecutionMutableState.update { it.copy(runningCommand = false) }
                    commandJob = null
                }
            }
    }

    fun stopShellCommand(showStoppedOutput: Boolean = true) {
        val job = commandJob ?: return
        if (!showStoppedOutput) {
            suppressStopOutputAppend = true
        }
        job.cancel(CancellationException("user-stop"))
    }

    fun formatOutput(
        commandStoppedText: String,
        outputResultLabel: String,
        outputTimeLabel: String,
        formattedToast: String? = null,
    ) {
        viewModelScope.launch {
            repository.formatOutput(
                commandStoppedText = commandStoppedText,
                outputResultLabel = outputResultLabel,
                outputTimeLabel = outputTimeLabel,
            )
            formattedToast
                ?.takeIf { it.isNotBlank() }
                ?.let { eventMutableFlow.emit(OsShellRunnerEvent.LiquidToast(it)) }
        }
    }

    fun updatePersistInput(enabled: Boolean) {
        launchRepositoryUpdate { setPersistInput(enabled) }
    }

    fun updatePersistOutput(enabled: Boolean) {
        launchRepositoryUpdate { setPersistOutput(enabled) }
    }

    fun updateTimeoutSeconds(seconds: Int) {
        launchRepositoryUpdate { setTimeoutSeconds(seconds) }
    }

    fun updateAutoFormatOutput(enabled: Boolean) {
        launchRepositoryUpdate { setAutoFormatOutput(enabled) }
    }

    fun updateAutoScrollOutput(enabled: Boolean) {
        launchRepositoryUpdate { setAutoScrollOutput(enabled) }
    }

    fun updateOutputLimitChars(
        limit: Int,
        commandStoppedText: String,
        outputResultLabel: String,
        outputTimeLabel: String,
    ) {
        launchRepositoryUpdate {
            setOutputLimitChars(
                limit = limit,
                commandStoppedText = commandStoppedText,
                outputResultLabel = outputResultLabel,
                outputTimeLabel = outputTimeLabel,
            )
        }
    }

    fun updateOutputSaveMode(
        mode: OsShellRunnerOutputSaveMode,
        commandStoppedText: String,
        outputResultLabel: String,
        outputTimeLabel: String,
    ) {
        launchRepositoryUpdate {
            setOutputSaveMode(
                mode = mode,
                commandStoppedText = commandStoppedText,
                outputResultLabel = outputResultLabel,
                outputTimeLabel = outputTimeLabel,
            )
        }
    }

    fun updateDangerousCommandConfirm(enabled: Boolean) {
        launchRepositoryUpdate { setDangerousCommandConfirm(enabled) }
    }

    fun updateCompletionToast(enabled: Boolean) {
        launchRepositoryUpdate { setCompletionToast(enabled) }
    }

    fun updateStartupBehavior(behavior: OsShellRunnerStartupBehavior) {
        launchRepositoryUpdate { setStartupBehavior(behavior) }
    }

    fun updateExitCleanupMode(mode: OsShellRunnerExitCleanupMode) {
        launchRepositoryUpdate { setExitCleanupMode(mode) }
    }

    fun updateCopyMode(mode: OsShellRunnerCopyMode) {
        launchRepositoryUpdate { setCopyMode(mode) }
    }

    fun persistInput(value: String) {
        viewModelScope.launch {
            repository.persistInput(value)
        }
    }

    fun persistOutput(value: String) {
        viewModelScope.launch {
            repository.persistOutput(value)
        }
    }

    fun clearSavedInput() {
        viewModelScope.launch {
            repository.clearSavedInput()
        }
    }

    fun clearSavedOutput() {
        viewModelScope.launch {
            repository.clearSavedOutput()
        }
    }

    fun requestSaveCommandSheet(
        command: String,
        commandSaveEmptyToast: String,
    ) {
        viewModelScope.launch {
            val normalizedCommand = command.trim()
            if (normalizedCommand.isBlank()) {
                eventMutableFlow.emit(OsShellRunnerEvent.Toast(commandSaveEmptyToast))
                return@launch
            }
            val suggestedSubtitle = repository.latestShellCardSubtitle(normalizedCommand)
            eventMutableFlow.emit(OsShellRunnerEvent.OpenSaveCommandSheet(suggestedSubtitle))
        }
    }

    fun saveShellCommandCard(
        command: String,
        title: String,
        subtitle: String,
        runOutput: String,
        commandSaveEmptyToast: String,
        saveSheetTitleRequiredToast: String,
        commandSavedToast: String,
    ) {
        if (commandSaveMutableState.value.savingCommandCard) return
        viewModelScope.launch {
            val normalizedCommand = command.trim()
            if (normalizedCommand.isBlank()) {
                eventMutableFlow.emit(OsShellRunnerEvent.Toast(commandSaveEmptyToast))
                return@launch
            }
            val normalizedTitle = title.trim()
            if (normalizedTitle.isBlank()) {
                eventMutableFlow.emit(OsShellRunnerEvent.Toast(saveSheetTitleRequiredToast))
                return@launch
            }
            commandSaveMutableState.update { it.copy(savingCommandCard = true) }
            try {
                val saved =
                    repository.createShellCommandCard(
                        command = normalizedCommand,
                        title = normalizedTitle,
                        subtitle = subtitle.trim(),
                        runOutput = runOutput,
                    )
                if (saved) {
                    eventMutableFlow.emit(OsShellRunnerEvent.CloseSaveCommandSheet)
                    eventMutableFlow.emit(OsShellRunnerEvent.LiquidToast(commandSavedToast))
                }
            } finally {
                commandSaveMutableState.update { it.copy(savingCommandCard = false) }
            }
        }
    }

    private fun launchRepositoryUpdate(update: suspend OsShellRunnerRepository.() -> Unit) {
        viewModelScope.launch {
            repository.update()
        }
    }
}
