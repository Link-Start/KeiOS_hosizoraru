@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.os.osLucideClearIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.os.osLucideFormatIcon
import os.kei.ui.page.main.os.osLucideRunIcon
import os.kei.ui.page.main.os.osLucideSaveIcon
import os.kei.ui.page.main.os.osLucideStopIcon
import os.kei.ui.page.main.os.shell.ShellCommandInputField
import os.kei.ui.page.main.os.shell.ShellOutputLiquidPanel
import os.kei.ui.page.main.os.shell.state.OsShellRunnerOutputSnapshot
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.core.AppCardHeader
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OsShellRunnerInputCard(
    inputTitle: String,
    inputHint: String,
    commandInput: String,
    onCommandInputChange: (String) -> Unit,
    runningCommand: Boolean,
    runActionDescription: String,
    stopActionDescription: String,
    saveCommandActionDescription: String,
    focusRequestToken: Int,
    onRunCommand: () -> Unit,
    onStopCommand: () -> Unit,
    onOpenSaveCommandSheet: () -> Unit,
    /**
     * Stretch the panel to whatever height the card is given, instead of sizing it to its own bounds.
     *
     * The page hands this `true` only in the two-pane shape, where the card fills a column and the panel is
     * the only thing in it that can absorb the slack. Stacked on a phone the card has no height to fill --
     * its parent is a lazy list item -- so the bounded default is the only thing that works there.
     */
    fillAvailableHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = isAppInDarkTheme()
    AppSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        exportBackdropToContent = true,
    ) {
        AppCardHeader(
            title = inputTitle,
            subtitle = "",
            titleAccessory = {
                if (runningCommand) {
                    LiquidCircularProgressBar(
                        progress = { 0.42f },
                        size = 14.dp,
                        strokeWidth = 2.dp,
                        activeColor = MiuixTheme.colorScheme.primary,
                        inactiveColor =
                            MiuixTheme.colorScheme.primary.copy(
                                alpha = if (isDark) 0.28f else 0.22f,
                            ),
                    )
                }
            },
            endActions = {
                AppStandaloneLiquidIconButton(
                    icon = osLucideRunIcon(),
                    contentDescription = runActionDescription,
                    onClick = onRunCommand,
                    iconTint =
                        if (runningCommand) {
                            MiuixTheme.colorScheme.onBackgroundVariant
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                    variant = GlassVariant.Bar,
                )
                AppStandaloneLiquidIconButton(
                    icon = osLucideStopIcon(),
                    contentDescription = stopActionDescription,
                    onClick = onStopCommand,
                    iconTint =
                        if (runningCommand) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onBackgroundVariant
                        },
                    variant = GlassVariant.Bar,
                )
                AppStandaloneLiquidIconButton(
                    icon = osLucideSaveIcon(),
                    contentDescription = saveCommandActionDescription,
                    onClick = onOpenSaveCommandSheet,
                    iconTint = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.Bar,
                )
            },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (fillAvailableHeight) Modifier.weight(1f) else Modifier)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
        ) {
            ShellCommandInputField(
                value = commandInput,
                onValueChange = onCommandInputChange,
                label = inputHint,
                minHeight = 136.dp,
                focusRequestToken = focusRequestToken,
                fillHeight = fillAvailableHeight,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (fillAvailableHeight) Modifier.fillMaxHeight() else Modifier),
            )
        }
    }
}

@Composable
internal fun OsShellRunnerOutputCard(
    outputTitle: String,
    outputHint: String,
    outputSnapshot: OsShellRunnerOutputSnapshot,
    outputScrollState: ScrollState,
    outputLazyListState: LazyListState,
    formatOutputActionDescription: String,
    copyOutputActionDescription: String,
    clearOutputActionDescription: String,
    onFormatOutput: () -> Unit,
    onCopyOutput: () -> Unit,
    onClearOutput: () -> Unit,
    /**
     * Stretch the panel to whatever height the card is given, instead of sizing it to its own bounds.
     *
     * The page hands this `true` only in the two-pane shape, where the card fills a column and the panel is
     * the only thing in it that can absorb the slack. Stacked on a phone the card has no height to fill --
     * its parent is a lazy list item -- so the bounded default is the only thing that works there.
     */
    fillAvailableHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AppSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        exportBackdropToContent = true,
    ) {
        AppCardHeader(
            title = outputTitle,
            subtitle = "",
            endActions = {
                AppStandaloneLiquidIconButton(
                    icon = osLucideFormatIcon(),
                    contentDescription = formatOutputActionDescription,
                    onClick = onFormatOutput,
                    iconTint = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.Bar,
                )
                AppStandaloneLiquidIconButton(
                    icon = osLucideCopyIcon(),
                    contentDescription = copyOutputActionDescription,
                    onClick = onCopyOutput,
                    iconTint = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.Bar,
                )
                AppStandaloneLiquidIconButton(
                    icon = osLucideClearIcon(),
                    contentDescription = clearOutputActionDescription,
                    onClick = onClearOutput,
                    iconTint = MiuixTheme.colorScheme.onBackgroundVariant,
                    variant = GlassVariant.Bar,
                )
            },
        )
        ShellOutputLiquidPanel(
            text = outputSnapshot.text,
            hint = outputHint,
            entries = outputSnapshot.entries,
            scrollState = outputScrollState,
            lazyListState = outputLazyListState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (fillAvailableHeight) {
                            // No animateContentSize: the height is the pane's, not the output's, so there
                            // is nothing to animate and the growing-card animation would only fight the
                            // window whenever the keyboard opens.
                            Modifier.weight(1f)
                        } else {
                            Modifier.animateContentSize().heightIn(min = 160.dp, max = 320.dp)
                        },
                    ).padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
        )
    }
}
