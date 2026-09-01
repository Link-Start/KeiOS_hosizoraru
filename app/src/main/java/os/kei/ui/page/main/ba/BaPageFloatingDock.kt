@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.widget.chrome.appFloatingDockSidePadding
import os.kei.ui.page.main.os.appLucideCalendarIcon
import os.kei.ui.page.main.os.appLucideLibraryIcon
import os.kei.ui.page.main.os.appLucideListChecksIcon
import os.kei.ui.page.main.os.appLucideMailIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.widget.glass.AppFloatingDockAction
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalActionDock
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BoxScope.BaPageFloatingDock(
    backdrop: Backdrop?,
    runtime: MainPageRuntime,
    unreadCounts: BaCalendarPoolUnreadCounts = BaCalendarPoolUnreadCounts(),
    onOpenCalendar: () -> Unit,
    onOpenPool: () -> Unit,
    onOpenGuideCatalog: () -> Unit,
    onOpenDailyDone: () -> Unit,
) {
    val dockAlignment =
        if (runtime.floatingDockSide == AppFloatingDockSide.Start) {
            Alignment.BottomStart
        } else {
            Alignment.BottomEnd
        }
    val dockStartPadding = appFloatingDockSidePadding(runtime.floatingDockSide == AppFloatingDockSide.Start)
    val dockEndPadding = appFloatingDockSidePadding(runtime.floatingDockSide == AppFloatingDockSide.End)
    val floatingDockBottomState =
        rememberAppFloatingDockBottomState(
            contentBottomPadding = runtime.contentBottomPadding,
            bottomBarVisible = runtime.bottomBarVisible,
            label = "ba_floating_action_dock_bottom",
        )
    val calendarIcon = appLucideCalendarIcon()
    val poolIcon = appLucideMailIcon()
    val catalogIcon = appLucideLibraryIcon()
    // A checklist rather than the quick-settings tile's own mark, which was tried first and rejected on
    // the device. That mark is `ic_ba_schale` plus a tick — and the same emblem is the BA tab icon in the
    // bottom bar directly under this dock, where the tick is far too small to separate them: the button
    // read as "BA", not as "dailies". The tick carries the tile fine in the shade, with no emblem beside
    // it; it does not carry it here.
    val dailyDoneIcon = appLucideListChecksIcon()
    val moreIcon = appLucideMoreIcon()
    val calendarDescription = stringResource(R.string.ba_calendar_cd_open_activity)
    val poolDescription = stringResource(R.string.ba_pool_cd_open_activity)
    val catalogDescription = stringResource(R.string.ba_overview_cd_open_catalog)
    val dailyDoneDescription = stringResource(R.string.ba_daily_done_cd_open_sheet)
    val expandDescription = stringResource(R.string.common_expand)
    val primaryIconTint = MiuixTheme.colorScheme.primary
    val currentOnOpenCalendar = rememberUpdatedState(onOpenCalendar)
    val currentOnOpenPool = rememberUpdatedState(onOpenPool)
    val currentOnOpenGuideCatalog = rememberUpdatedState(onOpenGuideCatalog)
    val currentOnOpenDailyDone = rememberUpdatedState(onOpenDailyDone)
    val openCalendarClick = remember { { currentOnOpenCalendar.value() } }
    val openPoolClick = remember { { currentOnOpenPool.value() } }
    val openGuideCatalogClick = remember { { currentOnOpenGuideCatalog.value() } }
    val openDailyDoneClick = remember { { currentOnOpenDailyDone.value() } }
    val calendarBadgeLabel = baCalendarPoolDockBadgeLabel(unreadCounts.calendarCount)
    val poolBadgeLabel = baCalendarPoolDockBadgeLabel(unreadCounts.poolCount)
    // The collapsed badge's *label* is now derived by the dock from the actions it hides, which
    // reproduces this total without the call site being able to get it wrong. Kept only to phrase the
    // tooltip, which still needs the exact figure.
    val calendarBadgeTooltip =
        calendarBadgeLabel?.let {
            stringResource(R.string.ba_calendar_unread_badge_tooltip, unreadCounts.calendarCount)
        }
    val poolBadgeTooltip =
        poolBadgeLabel?.let {
            stringResource(R.string.ba_pool_unread_badge_tooltip, unreadCounts.poolCount)
        }
    val compactBadgeTooltip =
        if (unreadCounts.totalCount > 0) {
            stringResource(R.string.ba_calendar_pool_unread_badge_tooltip, unreadCounts.totalCount)
        } else {
            null
        }
    val actions =
        remember(
            calendarIcon,
            calendarDescription,
            calendarBadgeLabel,
            calendarBadgeTooltip,
            openCalendarClick,
            poolIcon,
            poolDescription,
            poolBadgeLabel,
            poolBadgeTooltip,
            openPoolClick,
            catalogIcon,
            catalogDescription,
            openGuideCatalogClick,
            dailyDoneIcon,
            dailyDoneDescription,
            openDailyDoneClick,
            primaryIconTint,
        ) {
            listOf(
                AppFloatingDockAction(
                    icon = calendarIcon,
                    contentDescription = calendarDescription,
                    iconTint = primaryIconTint,
                    testTag = KeiOsTestTags.BaDockOpenCalendar,
                    badgeLabel = calendarBadgeLabel,
                    tooltipText = calendarBadgeTooltip,
                    onClick = openCalendarClick,
                ),
                AppFloatingDockAction(
                    icon = poolIcon,
                    contentDescription = poolDescription,
                    iconTint = primaryIconTint,
                    testTag = KeiOsTestTags.BaDockOpenPool,
                    badgeLabel = poolBadgeLabel,
                    tooltipText = poolBadgeTooltip,
                    onClick = openPoolClick,
                ),
                AppFloatingDockAction(
                    icon = catalogIcon,
                    contentDescription = catalogDescription,
                    iconTint = primaryIconTint,
                    testTag = KeiOsTestTags.BaDockOpenGuideCatalog,
                    onClick = openGuideCatalogClick,
                ),
                // Last, so it sits where the collapsed dock's single button was: the three above are
                // routes, this one is the only action, and it is the one worth reaching by thumb.
                AppFloatingDockAction(
                    icon = dailyDoneIcon,
                    contentDescription = dailyDoneDescription,
                    iconTint = primaryIconTint,
                    testTag = KeiOsTestTags.BaDockDailyDone,
                    onClick = openDailyDoneClick,
                ),
            )
        }

    AppFloatingVerticalActionDock(
        backdrop = backdrop,
        actions = actions,
        compact = !runtime.bottomBarVisible,
        compactIcon = moreIcon,
        compactContentDescription = expandDescription,
        compactTooltipText = compactBadgeTooltip,
        onCompactClick = runtime.onShowBottomBar,
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

private const val BaCalendarPoolDockBadgeMaxCount = 99

private fun baCalendarPoolDockBadgeLabel(count: Int): String? =
    when {
        count <= 0 -> null
        count > BaCalendarPoolDockBadgeMaxCount -> "$BaCalendarPoolDockBadgeMaxCount+"
        else -> count.toString()
    }

/**
 * Stable handles for the two routes these actions push. The benchmark cannot match on the content
 * descriptions, which are localised.
 */
