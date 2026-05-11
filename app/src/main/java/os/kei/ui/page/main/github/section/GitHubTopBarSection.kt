package os.kei.ui.page.main.github.section

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.sheet.GitHubCheckSheetCategory
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownSingleChoiceItem
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
    showSortPopup: Boolean,
    showCheckLogicMenuPopup: Boolean,
    deleteInProgress: Boolean,
    onOpenStrategySheet: () -> Unit,
    onOpenCheckLogicSheet: (GitHubCheckSheetCategory) -> Unit,
    onShowCheckLogicMenuPopupChange: (Boolean) -> Unit,
    onShowSortPopupChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubSortMode) -> Unit,
    onRefreshAllTracked: () -> Unit,
    onActionBarInteractingChanged: (Boolean) -> Unit
) {
    val editStrategyIcon = appLucideEditIcon()
    val checkLogicIcon = appLucideConfigIcon()
    val sortIcon = appLucideSortIcon()
    val editStrategyContentDescription = stringResource(R.string.github_topbar_cd_edit_strategy)
    val checkLogicContentDescription = stringResource(R.string.github_topbar_cd_check_logic)
    val sortContentDescription = stringResource(R.string.github_topbar_cd_sort)
    val actionItems = remember(
        editStrategyContentDescription,
        checkLogicContentDescription,
        sortContentDescription,
        showSortPopup,
        showCheckLogicMenuPopup,
        onOpenStrategySheet,
        onShowCheckLogicMenuPopupChange,
        onShowSortPopupChange,
    ) {
        listOf(
            LiquidActionItem(
                icon = editStrategyIcon,
                contentDescription = editStrategyContentDescription,
                onClick = {
                    onShowCheckLogicMenuPopupChange(false)
                    onShowSortPopupChange(false)
                    onOpenStrategySheet()
                }
            ),
            LiquidActionItem(
                icon = checkLogicIcon,
                contentDescription = checkLogicContentDescription,
                onClick = {
                    onShowSortPopupChange(false)
                    onShowCheckLogicMenuPopupChange(!showCheckLogicMenuPopup)
                }
            ),
            LiquidActionItem(
                icon = sortIcon,
                contentDescription = sortContentDescription,
                onClick = {
                    onShowCheckLogicMenuPopupChange(false)
                    onShowSortPopupChange(!showSortPopup)
                }
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
                1 -> if (showCheckLogicMenuPopup) {
                    SnapshotWindowListPopup(
                        show = showCheckLogicMenuPopup,
                        alignment = PopupPositionProvider.Align.BottomStart,
                        anchorBounds = popupAnchorBounds,
                        placement = SnapshotPopupPlacement.ActionBarCenter,
                        onDismissRequest = { onShowCheckLogicMenuPopupChange(false) },
                        enableWindowDim = false
                    ) {
                        val categories = GitHubCheckSheetCategory.entries
                        LiquidGlassDropdownColumn(
                            backdrop = backdrop,
                            initialScrollItemIndex = 0
                        ) {
                            categories.forEachIndexed { index, category ->
                                LiquidGlassDropdownActionItem(
                                    text = stringResource(category.menuLabelRes),
                                    optionSize = categories.size,
                                    index = index,
                                    onClick = {
                                        onShowCheckLogicMenuPopupChange(false)
                                        onOpenCheckLogicSheet(category)
                                    },
                                    leadingIcon = category.icon()
                                )
                            }
                        }
                    }
                }
                2 -> if (showSortPopup) {
                    SnapshotWindowListPopup(
                        show = showSortPopup,
                        alignment = PopupPositionProvider.Align.BottomStart,
                        anchorBounds = popupAnchorBounds,
                        placement = SnapshotPopupPlacement.ActionBarCenter,
                        onDismissRequest = { onShowSortPopupChange(false) },
                        enableWindowDim = false
                    ) {
                        val modes = GitHubSortMode.entries
                        LiquidGlassDropdownColumn(
                            backdrop = backdrop,
                            initialScrollItemIndex = modes.indexOf(sortMode)
                        ) {
                            modes.forEachIndexed { index, mode ->
                                LiquidGlassDropdownSingleChoiceItem(
                                    text = stringResource(mode.labelRes),
                                    optionSize = modes.size,
                                    isSelected = sortMode == mode,
                                    index = index,
                                    onSelectedIndexChange = { selectedIndex ->
                                        onSortModeChange(modes[selectedIndex])
                                        onShowSortPopupChange(false)
                                    },
                                    leadingIcon = sortIcon
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubCheckSheetCategory.icon() = when (this) {
    GitHubCheckSheetCategory.UpdateChecks -> appLucideConfigIcon()
    GitHubCheckSheetCategory.DownloadFlow -> appLucideDownloadIcon()
    GitHubCheckSheetCategory.ShareImport -> appLucideShareIcon()
    GitHubCheckSheetCategory.Insights -> appLucideInfoIcon()
    GitHubCheckSheetCategory.TrackData -> appLucideListIcon()
    GitHubCheckSheetCategory.Notes -> appLucideNotesIcon()
}
