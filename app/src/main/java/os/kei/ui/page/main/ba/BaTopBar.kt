package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BaCalendarRefreshIntervalOption
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownSingleChoiceItem
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun BaTopBar(
    topBarColor: Color,
    scrollBehavior: ScrollBehavior?,
) {
    AppTopBarSection(
        title = "",
        largeTitle = stringResource(R.string.ba_topbar_title),
        scrollBehavior = scrollBehavior,
        color = topBarColor,
    )
}

@Composable
internal fun BaTopBarActions(
    backdrop: LayerBackdrop,
    liquidActionBarLayeredStyleEnabled: Boolean,
    showCalendarIntervalPopup: Boolean,
    calendarRefreshIntervalHours: Int,
    onShowSettings: () -> Unit,
    onShowCalendarIntervalPopupChange: (Boolean) -> Unit,
    onCalendarRefreshIntervalSelected: (Int) -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
) {
    val editIcon = appLucideEditIcon()
    val refreshIntervalIcon = appLucideTimeIcon()
    val editContentDescription = stringResource(R.string.ba_cd_edit)
    val refreshIntervalContentDescription = stringResource(R.string.ba_cd_refresh_interval)
    val actionItems = remember(
        editContentDescription,
        refreshIntervalContentDescription,
        showCalendarIntervalPopup,
        onShowSettings,
        onShowCalendarIntervalPopupChange,
    ) {
        listOf(
            LiquidActionItem(
                icon = editIcon,
                contentDescription = editContentDescription,
                onClick = onShowSettings,
            ),
            LiquidActionItem(
                icon = refreshIntervalIcon,
                contentDescription = refreshIntervalContentDescription,
                onClick = { onShowCalendarIntervalPopupChange(!showCalendarIntervalPopup) },
            ),
        )
    }

    Box {
        LiquidActionBar(
            backdrop = backdrop,
            layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
            items = actionItems,
            onInteractionChanged = onInteractionChanged,
        )

        LiquidActionBarPopupAnchors(itemCount = 2) { slotIndex, popupAnchorBounds ->
            if (slotIndex == 1 && showCalendarIntervalPopup) {
                SnapshotWindowListPopup(
                    show = showCalendarIntervalPopup,
                    alignment = PopupPositionProvider.Align.BottomStart,
                    anchorBounds = popupAnchorBounds,
                    placement = SnapshotPopupPlacement.ActionBarCenter,
                    onDismissRequest = { onShowCalendarIntervalPopupChange(false) },
                    enableWindowDim = false,
                ) {
                    val options = BaCalendarRefreshIntervalOption.entries
                    val selected = BaCalendarRefreshIntervalOption.fromHours(
                        calendarRefreshIntervalHours,
                    )
                    LiquidGlassDropdownColumn(
                        backdrop = backdrop,
                        initialScrollItemIndex = options.indexOf(selected)
                    ) {
                        options.forEachIndexed { index, option ->
                            LiquidGlassDropdownSingleChoiceItem(
                                text = stringResource(option.labelRes),
                                optionSize = options.size,
                                isSelected = selected == option,
                                index = index,
                                onSelectedIndexChange = { selectedIndex ->
                                    onCalendarRefreshIntervalSelected(options[selectedIndex].hours)
                                    onShowCalendarIntervalPopupChange(false)
                                },
                                leadingIcon = refreshIntervalIcon
                            )
                        }
                    }
                }
            }
        }
    }
}
