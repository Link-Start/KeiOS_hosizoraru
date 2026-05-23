@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.state

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager

private const val SHELL_PERSIST_DEBOUNCE_MS = 220L

@Composable
@OptIn(FlowPreview::class)
internal fun BindOsShellRunnerPersistEffects(
    persistInputEnabled: Boolean,
    persistOutputEnabled: Boolean,
    commandInput: String,
    outputText: String,
    onPersistInput: (String) -> Unit,
    onPersistOutput: (String) -> Unit,
) {
    BindOsShellRunnerInputPersistEffect(
        persistInputEnabled = persistInputEnabled,
        commandInput = commandInput,
        onPersistInput = onPersistInput,
    )
    BindOsShellRunnerOutputPersistEffect(
        persistOutputEnabled = persistOutputEnabled,
        outputText = outputText,
        onPersistOutput = onPersistOutput,
    )
}

@Composable
@OptIn(FlowPreview::class)
internal fun BindOsShellRunnerInputPersistEffect(
    persistInputEnabled: Boolean,
    commandInput: String,
    onPersistInput: (String) -> Unit,
) {
    val currentCommandInput = rememberUpdatedState(commandInput)
    val currentPersistInput = rememberUpdatedState(onPersistInput)
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(persistInputEnabled, snapshotFlowManager) {
        if (!persistInputEnabled) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow { currentCommandInput.value }
            .distinctUntilChanged()
            .debounce(SHELL_PERSIST_DEBOUNCE_MS)
            .collectLatest { input ->
                currentPersistInput.value(input)
            }
    }
}

@Composable
@OptIn(FlowPreview::class)
internal fun BindOsShellRunnerOutputPersistEffect(
    persistOutputEnabled: Boolean,
    outputText: String,
    onPersistOutput: (String) -> Unit,
) {
    val currentOutputText = rememberUpdatedState(outputText)
    val currentPersistOutput = rememberUpdatedState(onPersistOutput)
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(persistOutputEnabled, snapshotFlowManager) {
        if (!persistOutputEnabled) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow { currentOutputText.value }
            .distinctUntilChanged()
            .debounce(SHELL_PERSIST_DEBOUNCE_MS)
            .collectLatest { output ->
                currentPersistOutput.value(output)
            }
    }
}

@Composable
internal fun BindOsShellRunnerAutoScrollEffect(
    outputText: String,
    outputScrollState: ScrollState,
    enabled: Boolean,
) {
    LaunchedEffect(outputText, enabled) {
        if (enabled && outputText.isNotBlank()) {
            outputScrollState.scrollTo(outputScrollState.maxValue)
        }
    }
}
