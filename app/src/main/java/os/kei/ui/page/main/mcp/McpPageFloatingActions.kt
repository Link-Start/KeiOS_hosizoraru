@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.os.osLucideRunIcon
import os.kei.ui.page.main.widget.glass.AppFloatingDockAction
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalActionDock
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BoxScope.McpPageFloatingActionDock(
    backdrop: LayerBackdrop,
    uiState: McpServerUiState,
    runtime: MainPageRuntime,
    refreshRunning: Boolean,
    actions: McpPageActions,
) {
    val dockAlignment =
        if (runtime.floatingDockSide == AppFloatingDockSide.Start) {
            Alignment.BottomStart
        } else {
            Alignment.BottomEnd
        }
    val dockStartPadding = if (runtime.floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (runtime.floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val dockBottomState =
        rememberAppFloatingDockBottomState(
            contentBottomPadding = runtime.contentBottomPadding,
            bottomBarVisible = runtime.bottomBarVisible,
            label = "mcp_floating_action_dock_bottom",
        )
    val copyIcon = osLucideCopyIcon()
    val refreshIcon = appLucideRefreshIcon()
    val toggleIcon = if (uiState.running) appLucidePauseIcon() else osLucideRunIcon()
    val moreIcon = appLucideMoreIcon()
    val primaryColor = MiuixTheme.colorScheme.primary
    val errorColor = MiuixTheme.colorScheme.error
    val copyConfigContentDescription = stringResource(R.string.mcp_action_copy_current_config)
    val refreshContentDescription = stringResource(R.string.common_refresh)
    val expandDescription = stringResource(R.string.common_expand)
    val toggleContentDescription =
        if (uiState.running) {
            stringResource(R.string.mcp_action_stop_service)
        } else {
            stringResource(R.string.mcp_action_start_service)
        }
    val dockActions =
        remember(
            copyIcon,
            refreshIcon,
            toggleIcon,
            uiState.running,
            refreshRunning,
            copyConfigContentDescription,
            refreshContentDescription,
            toggleContentDescription,
            primaryColor,
            errorColor,
            actions,
        ) {
            listOf(
                AppFloatingDockAction(
                    icon = copyIcon,
                    contentDescription = copyConfigContentDescription,
                    iconTint = primaryColor,
                    onClick = actions.onCopyCurrentConfig,
                ),
                AppFloatingDockAction(
                    icon = refreshIcon,
                    contentDescription = refreshContentDescription,
                    iconTint = primaryColor,
                    enabled = !refreshRunning,
                    rotating = refreshRunning,
                    onClick = actions.onRefreshNow,
                ),
                AppFloatingDockAction(
                    icon = toggleIcon,
                    contentDescription = toggleContentDescription,
                    iconTint = if (uiState.running) errorColor else primaryColor,
                    onClick = actions.onToggleServer,
                ),
            )
        }
    AppFloatingVerticalActionDock(
        backdrop = backdrop,
        actions = dockActions,
        compact = !runtime.bottomBarVisible,
        compactIcon = moreIcon,
        compactContentDescription = expandDescription,
        onCompactClick = runtime.onShowBottomBar,
        modifier =
            Modifier
                .align(dockAlignment)
                .offset { IntOffset(x = 0, y = -dockBottomState.value.roundToPx()) }
                .padding(
                    start = dockStartPadding,
                    end = dockEndPadding,
                ),
    )
}
