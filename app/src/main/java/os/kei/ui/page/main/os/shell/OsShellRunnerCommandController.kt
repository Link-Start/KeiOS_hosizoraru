package os.kei.ui.page.main.os.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OsShellRunnerCommandController(
    private val scope: CoroutineScope,
    private val repository: OsShellRunnerRepository,
    private val events: MutableSharedFlow<OsShellRunnerEvent>,
) {
    private var commandJob: Job? = null
    private var suppressStopOutputAppend = false
    private val mutableExecutionState = MutableStateFlow(OsShellRunnerCommandExecutionState())
    private val mutableSaveState = MutableStateFlow(OsShellRunnerCommandSaveState())

    val executionState: StateFlow<OsShellRunnerCommandExecutionState> =
        mutableExecutionState.asStateFlow()
    val saveState: StateFlow<OsShellRunnerCommandSaveState> =
        mutableSaveState.asStateFlow()

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
        if (mutableExecutionState.value.runningCommand) return
        commandJob =
            scope.launch {
                mutableExecutionState.update { it.copy(runningCommand = true) }
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
                        events.emit(OsShellRunnerEvent.LiquidToast(commandCompletedText))
                    }
                } catch (_: CancellationException) {
                    handleCommandCancellation(
                        command = command,
                        commandStoppedText = commandStoppedText,
                        outputResultLabel = outputResultLabel,
                        outputTimeLabel = outputTimeLabel,
                        completionToast = completionToast,
                    )
                } finally {
                    mutableExecutionState.update { it.copy(runningCommand = false) }
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

    fun requestSaveCommandSheet(
        command: String,
        commandSaveEmptyToast: String,
    ) {
        scope.launch {
            val normalizedCommand = command.trim()
            if (normalizedCommand.isBlank()) {
                events.emit(OsShellRunnerEvent.Toast(commandSaveEmptyToast))
                return@launch
            }
            val suggestedSubtitle = repository.latestShellCardSubtitle(normalizedCommand)
            events.emit(OsShellRunnerEvent.OpenSaveCommandSheet(suggestedSubtitle))
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
        if (mutableSaveState.value.savingCommandCard) return
        scope.launch {
            val normalizedCommand = command.trim()
            if (normalizedCommand.isBlank()) {
                events.emit(OsShellRunnerEvent.Toast(commandSaveEmptyToast))
                return@launch
            }
            val normalizedTitle = title.trim()
            if (normalizedTitle.isBlank()) {
                events.emit(OsShellRunnerEvent.Toast(saveSheetTitleRequiredToast))
                return@launch
            }
            mutableSaveState.update { it.copy(savingCommandCard = true) }
            try {
                val saved =
                    repository.createShellCommandCard(
                        command = normalizedCommand,
                        title = normalizedTitle,
                        subtitle = subtitle.trim(),
                        runOutput = runOutput,
                    )
                if (saved) {
                    events.emit(OsShellRunnerEvent.CloseSaveCommandSheet)
                    events.emit(OsShellRunnerEvent.LiquidToast(commandSavedToast))
                }
            } finally {
                mutableSaveState.update { it.copy(savingCommandCard = false) }
            }
        }
    }

    fun cancel() {
        commandJob?.cancel()
        commandJob = null
        suppressStopOutputAppend = false
    }

    private suspend fun handleCommandCancellation(
        command: String,
        commandStoppedText: String,
        outputResultLabel: String,
        outputTimeLabel: String,
        completionToast: Boolean,
    ) {
        if (suppressStopOutputAppend) {
            suppressStopOutputAppend = false
            return
        }
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
            events.emit(OsShellRunnerEvent.LiquidToast(commandStoppedText))
        }
    }
}
