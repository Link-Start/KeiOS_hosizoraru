@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

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
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.os.appLucideCalendarIcon
import os.kei.ui.page.main.os.appLucideLibraryIcon
import os.kei.ui.page.main.os.appLucideMailIcon
import os.kei.ui.page.main.widget.glass.AppFloatingDockAction
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalActionDock
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BoxScope.BaPageFloatingDock(
    backdrop: Backdrop?,
    runtime: MainPageRuntime,
    onOpenCalendar: () -> Unit,
    onOpenPool: () -> Unit,
    onOpenGuideCatalog: () -> Unit,
) {
    val dockAlignment =
        if (runtime.floatingDockSide == AppFloatingDockSide.Start) {
            Alignment.BottomStart
        } else {
            Alignment.BottomEnd
        }
    val dockStartPadding = if (runtime.floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (runtime.floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val floatingDockBottomState =
        rememberAppFloatingDockBottomState(
            contentBottomPadding = runtime.contentBottomPadding,
            bottomBarVisible = runtime.bottomBarVisible,
            label = "ba_floating_action_dock_bottom",
        )
    val calendarIcon = appLucideCalendarIcon()
    val poolIcon = appLucideMailIcon()
    val catalogIcon = appLucideLibraryIcon()
    val calendarDescription = stringResource(R.string.ba_calendar_cd_open_activity)
    val poolDescription = stringResource(R.string.ba_pool_cd_open_activity)
    val catalogDescription = stringResource(R.string.ba_overview_cd_open_catalog)
    val primaryIconTint = MiuixTheme.colorScheme.primary
    val actions =
        remember(
            calendarIcon,
            calendarDescription,
            onOpenCalendar,
            poolIcon,
            poolDescription,
            onOpenPool,
            catalogIcon,
            catalogDescription,
            onOpenGuideCatalog,
            primaryIconTint,
        ) {
            listOf(
                AppFloatingDockAction(
                    icon = calendarIcon,
                    contentDescription = calendarDescription,
                    iconTint = primaryIconTint,
                    onClick = onOpenCalendar,
                ),
                AppFloatingDockAction(
                    icon = poolIcon,
                    contentDescription = poolDescription,
                    iconTint = primaryIconTint,
                    onClick = onOpenPool,
                ),
                AppFloatingDockAction(
                    icon = catalogIcon,
                    contentDescription = catalogDescription,
                    iconTint = primaryIconTint,
                    onClick = onOpenGuideCatalog,
                ),
            )
        }

    AppFloatingVerticalActionDock(
        backdrop = backdrop,
        actions = actions,
        modifier =
            Modifier
                .align(dockAlignment)
                .offset { IntOffset(x = 0, y = -floatingDockBottomState.value.roundToPx()) }
                .padding(
                    start = dockStartPadding,
                    end = dockEndPadding,
                ),
    )
}
