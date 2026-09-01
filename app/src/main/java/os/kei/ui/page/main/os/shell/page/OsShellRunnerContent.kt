@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.shell.component.OsShellRunnerInputCard
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageBackdrop
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnPanes
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageContentMaxWidthFor
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import os.kei.ui.page.main.widget.chrome.LiquidToolbar
import os.kei.ui.page.main.widget.chrome.LiquidToolbarAction
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun OsShellRunnerContent(
    textBundle: OsShellRunnerTextBundle,
    scrollBehavior: ScrollBehavior,
    topBarBackdrop: AppPageBackdrop,
    pageListState: LazyListState,
    actionItems: List<LiquidToolbarAction>,
    commandInput: String,
    runningCommand: Boolean,
    startupFocusRequestToken: Int,
    outputContent: @Composable (modifier: Modifier, fillAvailableHeight: Boolean) -> Unit,
    onRequestClose: () -> Unit,
    onCommandInputChange: (String) -> Unit,
    onRunCommand: () -> Unit,
    onStopCommand: () -> Unit,
    onOpenSaveCommandSheet: () -> Unit,
) {
    // This page is exactly two things, and on a panel wide enough for both it is the one page where the
    // split needs no lane rule: the command goes left, what it printed goes right. Stacked, the pair used
    // barely half the width and half the height of a tablet and left the rest empty.
    val columnCount = appPageColumnCount()
    val twoPanes = columnCount >= 2
    AppPageScaffold(
        title = textBundle.shellPageTitle,
        largeTitle = textBundle.shellPageTitle,
        modifier = Modifier.pageRootTestTag(KeiOsTestTags.OsShellRunnerPageRoot),
        scrollBehavior = scrollBehavior,
        titleBackdrop = topBarBackdrop,
        contentMaxWidth = appPageContentMaxWidthFor(columnCount),
        reserveTopEndActionSpace = true,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onRequestClose,
                backdrop = topBarBackdrop,
            )
        },
        actions = {
            LiquidToolbar(
                backdrop = topBarBackdrop,
                actions = actionItems,
            )
        },
    ) { innerPadding ->
        if (twoPanes) {
            AppPageTwoColumnPanes(
                innerPadding = innerPadding,
                // No nested-scroll connection on purpose: neither pane scrolls the page, and letting the
                // output's own scroll collapse the top bar would resize the panes measured against it.
                modifier =
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(topBarBackdrop.producer),
                primary = {
                    OsShellRunnerInputCard(
                        modifier = Modifier.fillMaxSize(),
                        fillAvailableHeight = true,
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
                },
                secondary = {
                    outputContent(Modifier.fillMaxSize(), true)
                },
            )
        } else {
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = pageListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .layerBackdrop(topBarBackdrop.producer),
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
                    outputContent(Modifier, false)
                }
            }
        }
    }
}
