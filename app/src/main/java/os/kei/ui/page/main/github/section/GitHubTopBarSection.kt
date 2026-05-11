package os.kei.ui.page.main.github.section

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.RefreshIntervalOption
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSingleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSubmenuRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun GitHubTopBarSection(
    topBarColor: Color,
    scrollBehavior: ScrollBehavior,
    titleBackdrop: LayerBackdrop? = null,
) {
    AppTopBarSection(
        title = "",
        largeTitle = stringResource(R.string.github_page_title),
        scrollBehavior = scrollBehavior,
        color = topBarColor,
        titleBackdrop = titleBackdrop,
        titleEndReserve = AppChromeTokens.topBarTitleActionReserve,
    ) {}
}

@Composable
internal fun GitHubTopBarActions(
    backdrop: LayerBackdrop,
    liquidActionBarLayeredStyleEnabled: Boolean,
    sortMode: GitHubSortMode,
    refreshIntervalHours: Int,
    showActionMenuPopup: Boolean,
    onOpenStrategySheet: () -> Unit,
    onOpenCheckLogicSheet: () -> Unit,
    onShowActionMenuPopupChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubSortMode) -> Unit,
    onRefreshIntervalHoursChange: (Int) -> Unit,
    onActionBarInteractingChanged: (Boolean) -> Unit
) {
    val editStrategyIcon = appLucideEditIcon()
    val checkLogicIcon = appLucideConfigIcon()
    val sortIcon = appLucideSortIcon()
    val intervalIcon = appLucideTimeIcon()
    val moreIcon = appLucideMoreIcon()
    val chevronRightIcon = appLucideChevronRightIcon()
    val editStrategyContentDescription = stringResource(R.string.github_topbar_cd_edit_strategy)
    val checkLogicContentDescription = stringResource(R.string.github_topbar_cd_check_logic)
    val sortContentDescription = stringResource(R.string.github_topbar_cd_sort)
    val refreshIntervalLabel = stringResource(R.string.github_check_sheet_label_refresh_interval)
    val moreContentDescription = stringResource(R.string.github_item_cd_more_actions)
    val actionItems = remember(
        editStrategyIcon,
        checkLogicIcon,
        moreIcon,
        editStrategyContentDescription,
        checkLogicContentDescription,
        moreContentDescription,
        showActionMenuPopup,
        onOpenStrategySheet,
        onOpenCheckLogicSheet,
        onShowActionMenuPopupChange,
    ) {
        listOf(
            LiquidActionItem(
                icon = editStrategyIcon,
                contentDescription = editStrategyContentDescription,
                onClick = {
                    onShowActionMenuPopupChange(false)
                    onOpenStrategySheet()
                }
            ),
            LiquidActionItem(
                icon = checkLogicIcon,
                contentDescription = checkLogicContentDescription,
                onClick = {
                    onShowActionMenuPopupChange(false)
                    onOpenCheckLogicSheet()
                }
            ),
            LiquidActionItem(
                icon = moreIcon,
                contentDescription = moreContentDescription,
                onClick = { onShowActionMenuPopupChange(!showActionMenuPopup) }
            )
        )
    }
    Box {
        LiquidActionBar(
            backdrop = backdrop,
            layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
            items = actionItems,
            onInteractionChanged = onActionBarInteractingChanged
        )

        LiquidActionBarPopupAnchors(itemCount = 3) { slotIndex, popupAnchorBounds ->
            when (slotIndex) {
                2 -> if (showActionMenuPopup) {
                    SnapshotWindowListPopup(
                        show = showActionMenuPopup,
                        alignment = PopupPositionProvider.Align.BottomEnd,
                        anchorBounds = popupAnchorBounds,
                        placement = SnapshotPopupPlacement.ButtonEnd,
                        onDismissRequest = { onShowActionMenuPopupChange(false) },
                        enableWindowDim = false,
                        maxWidth = 340.dp
                    ) {
                        val modes = GitHubSortMode.entries
                        val sortLabels = modes.map { mode -> stringResource(mode.labelRes) }
                        val selectedSortLabel = sortLabels.getOrElse(modes.indexOf(sortMode)) {
                            stringResource(sortMode.labelRes)
                        }
                        val refreshIntervalOptions = RefreshIntervalOption.entries
                        val refreshIntervalLabels = refreshIntervalOptions.map { option ->
                            stringResource(option.labelRes)
                        }
                        val selectedRefreshInterval =
                            RefreshIntervalOption.fromHours(refreshIntervalHours)
                        val selectedRefreshIntervalLabel = refreshIntervalLabels.getOrElse(
                            refreshIntervalOptions.indexOf(selectedRefreshInterval)
                        ) {
                            stringResource(selectedRefreshInterval.labelRes)
                        }
                        LiquidGlassActionMenu(
                            backdrop = backdrop,
                            items = listOf(
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "sort",
                                    text = sortContentDescription,
                                    subtitle = selectedSortLabel,
                                    leadingIcon = sortIcon,
                                    trailingIcon = chevronRightIcon,
                                    submenuItems = modes.mapIndexed { index, mode ->
                                        LiquidGlassActionMenuSingleChoiceRow(
                                            id = mode.name,
                                            text = sortLabels[index],
                                            selected = sortMode == mode,
                                            leadingIcon = sortIcon,
                                            onClick = { onSortModeChange(mode) }
                                        )
                                    }
                                ),
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "refresh_interval",
                                    text = refreshIntervalLabel,
                                    subtitle = selectedRefreshIntervalLabel,
                                    leadingIcon = intervalIcon,
                                    trailingIcon = chevronRightIcon,
                                    submenuItems = refreshIntervalOptions.mapIndexed { index, option ->
                                        LiquidGlassActionMenuSingleChoiceRow(
                                            id = option.name,
                                            text = refreshIntervalLabels[index],
                                            selected = selectedRefreshInterval == option,
                                            leadingIcon = intervalIcon,
                                            onClick = { onRefreshIntervalHoursChange(option.hours) }
                                        )
                                    }
                                )
                            ),
                            onDismissRequest = { onShowActionMenuPopupChange(false) }
                        )
                    }
                }
            }
        }
    }
}
