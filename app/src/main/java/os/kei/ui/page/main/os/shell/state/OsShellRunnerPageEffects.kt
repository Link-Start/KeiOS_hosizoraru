@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.state

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun BindOsShellRunnerAutoScrollEffect(
    outputText: String,
    outputEntriesSize: Int,
    outputScrollState: ScrollState,
    outputLazyListState: LazyListState,
    enabled: Boolean,
) {
    LaunchedEffect(outputText, outputEntriesSize, enabled) {
        if (enabled && outputEntriesSize > 0) {
            outputLazyListState.scrollToItem(outputEntriesSize - 1)
        } else if (enabled && outputText.isNotBlank()) {
            outputScrollState.scrollTo(outputScrollState.maxValue)
        }
    }
}
