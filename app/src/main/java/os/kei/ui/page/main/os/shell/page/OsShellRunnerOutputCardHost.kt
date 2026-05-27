@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.component.OsShellRunnerOutputCard
import os.kei.ui.page.main.os.shell.state.BindOsShellRunnerAutoScrollEffect
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle
import os.kei.ui.page.main.os.shell.state.toOutputSnapshot

@Composable
internal fun OsShellRunnerOutputCardHost(
    shellRunnerViewModel: OsShellRunnerViewModel,
    textBundle: OsShellRunnerTextBundle,
    outputScrollState: ScrollState,
    outputLazyListState: LazyListState,
    autoScrollOutputEnabled: Boolean,
    onFormatOutput: () -> Unit,
    onCopyOutput: () -> Unit,
    onClearOutput: () -> Unit,
) {
    val rawOutputState by shellRunnerViewModel.outputState.collectAsStateWithLifecycle()
    val outputSnapshot = remember(rawOutputState) { rawOutputState.toOutputSnapshot() }

    BindOsShellRunnerAutoScrollEffect(
        outputText = outputSnapshot.text,
        outputEntriesSize = outputSnapshot.entries.size,
        outputScrollState = outputScrollState,
        outputLazyListState = outputLazyListState,
        enabled = autoScrollOutputEnabled,
    )

    OsShellRunnerOutputCard(
        outputTitle = textBundle.outputTitle,
        outputHint = textBundle.outputHint,
        outputSnapshot = outputSnapshot,
        outputScrollState = outputScrollState,
        outputLazyListState = outputLazyListState,
        formatOutputActionDescription = textBundle.formatOutputActionDescription,
        copyOutputActionDescription = textBundle.copyOutputActionDescription,
        clearOutputActionDescription = textBundle.clearOutputActionDescription,
        onFormatOutput = onFormatOutput,
        onCopyOutput = onCopyOutput,
        onClearOutput = onClearOutput,
    )
}
