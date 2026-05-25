@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.state

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

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
