@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.shell.ShellOutputDisplayEntry
import os.kei.ui.page.main.os.shell.component.OsShellRunnerInputCard
import os.kei.ui.page.main.os.shell.component.OsShellRunnerOutputCard
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@androidx.compose.runtime.Composable
internal fun OsShellRunnerContent(
    textBundle: OsShellRunnerTextBundle,
    scrollBehavior: ScrollBehavior,
    topBarBackdrop: LayerBackdrop,
    pageListState: LazyListState,
    liquidActionBarLayeredStyleEnabled: Boolean,
    actionItems: List<LiquidActionItem>,
    commandInput: String,
    runningCommand: Boolean,
    startupFocusRequestToken: Int,
    outputText: String,
    outputEntries: List<ShellOutputDisplayEntry>,
    outputScrollState: ScrollState,
    onRequestClose: () -> Unit,
    onCommandInputChange: (String) -> Unit,
    onRunCommand: () -> Unit,
    onStopCommand: () -> Unit,
    onOpenSaveCommandSheet: () -> Unit,
    onFormatOutput: () -> Unit,
    onCopyOutput: () -> Unit,
    onClearOutput: () -> Unit,
) {
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
                onClick = onRequestClose,
                backdrop = topBarBackdrop,
                layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
            )
        },
        actions = {
            LiquidActionBar(
                backdrop = topBarBackdrop,
                layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                items = actionItems,
            )
        },
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = pageListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .layerBackdrop(topBarBackdrop),
            sectionSpacing = AppChromeTokens.pageSectionGap,
        ) {
            item(key = "shell_input_card", contentType = "shell_input_card") {
                OsShellRunnerInputCard(
                    inputTitle = textBundle.inputTitle,
                    inputHint = textBundle.inputHint,
                    commandInput = commandInput,
                    onCommandInputChange = onCommandInputChange,
                    runningCommand = runningCommand,
                    runActionDescription = textBundle.runActionDescription,
                    stopActionDescription = textBundle.stopActionDescription,
                    saveCommandActionDescription = textBundle.saveCommandActionDescription,
                    focusRequestToken = startupFocusRequestToken,
                    onRunCommand = onRunCommand,
                    onStopCommand = onStopCommand,
                    onOpenSaveCommandSheet = onOpenSaveCommandSheet,
                )
            }
            item(key = "shell_output_card", contentType = "shell_output_card") {
                OsShellRunnerOutputCard(
                    outputTitle = textBundle.outputTitle,
                    outputHint = textBundle.outputHint,
                    outputText = outputText,
                    outputEntries = outputEntries,
                    outputScrollState = outputScrollState,
                    formatOutputActionDescription = textBundle.formatOutputActionDescription,
                    copyOutputActionDescription = textBundle.copyOutputActionDescription,
                    clearOutputActionDescription = textBundle.clearOutputActionDescription,
                    onFormatOutput = onFormatOutput,
                    onCopyOutput = onCopyOutput,
                    onClearOutput = onClearOutput,
                )
            }
        }
    }
}
