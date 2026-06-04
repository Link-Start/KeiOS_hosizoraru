@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.history

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSingleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSubmenuRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ActionsHistoryActionMenuCompactMinWidth = 236.dp
private val ActionsHistoryActionMenuPreferredMinWidth = 252.dp
private val ActionsHistoryActionMenuPreferredMaxWidth = 288.dp
private val ActionsHistoryActionMenuHorizontalMargin = 92.dp

@Composable
internal fun GitHubActionsNotificationHistoryActionBar(
    backdrop: Backdrop,
    loading: Boolean,
    hasRecords: Boolean,
    showActionMenuPopup: Boolean,
    filterMode: GitHubActionsHistoryFilterMode,
    sortMode: GitHubActionsHistorySortMode,
    sortDirection: GitHubActionsHistorySortDirection,
    onRefresh: () -> Unit,
    onShowActionMenuPopupChange: (Boolean) -> Unit,
    onFilterModeChange: (GitHubActionsHistoryFilterMode) -> Unit,
    onSortModeChange: (GitHubActionsHistorySortMode) -> Unit,
    onSortDirectionChange: (GitHubActionsHistorySortDirection) -> Unit,
    onCleanupAgeSelect: (GitHubActionsHistoryCleanupAge) -> Unit,
) {
    val refreshIcon = appLucideRefreshIcon()
    val moreIcon = appLucideMoreIcon()
    val filterIcon = appLucideFilterIcon()
    val sortIcon = appLucideSortIcon()
    val timeIcon = appLucideTimeIcon()
    val cleanupIcon = appLucideTrashIcon()
    val chevronRightIcon = appLucideChevronRightIcon()
    val refreshLabel = stringResource(R.string.common_refresh)
    val moreLabel = stringResource(R.string.github_actions_history_action_more)
    val filterLabel = stringResource(R.string.github_actions_history_action_filter)
    val sortLabel = stringResource(R.string.github_actions_history_action_sort)
    val sortDirectionLabel = stringResource(R.string.github_actions_history_action_sort_direction)
    val cleanupLabel = stringResource(R.string.github_actions_history_action_cleanup)
    val cleanupSubtitle = stringResource(R.string.github_actions_history_cleanup_subtitle)
    val filterModes = GitHubActionsHistoryFilterMode.entries
    val filterLabels = filterModes.map { mode -> stringResource(mode.labelRes) }
    val sortModes = GitHubActionsHistorySortMode.entries
    val sortLabels = sortModes.map { mode -> stringResource(mode.labelRes) }
    val sortDirections = GitHubActionsHistorySortDirection.entries
    val sortDirectionLabels = sortDirections.map { direction -> stringResource(direction.labelRes) }
    val cleanupAges = GitHubActionsHistoryCleanupAge.entries
    val cleanupAgeLabels = cleanupAges.map { age -> stringResource(age.labelRes) }
    val selectedFilterLabel =
        filterLabels.getOrElse(filterModes.indexOf(filterMode)) {
            stringResource(filterMode.labelRes)
        }
    val selectedSortLabel =
        sortLabels.getOrElse(sortModes.indexOf(sortMode)) {
            stringResource(sortMode.labelRes)
        }
    val selectedDirectionLabel =
        sortDirectionLabels.getOrElse(sortDirections.indexOf(sortDirection)) {
            stringResource(sortDirection.labelRes)
        }
    val screenWidth = appWindowWidthDp()
    val actionMenuMaxWidth =
        (screenWidth - ActionsHistoryActionMenuHorizontalMargin)
            .coerceIn(
                ActionsHistoryActionMenuCompactMinWidth,
                ActionsHistoryActionMenuPreferredMaxWidth,
            )
    val actionMenuMinWidth = minOf(ActionsHistoryActionMenuPreferredMinWidth, actionMenuMaxWidth)
    val actionItems =
        remember(
            refreshIcon,
            moreIcon,
            refreshLabel,
            moreLabel,
            loading,
            showActionMenuPopup,
            onRefresh,
            onShowActionMenuPopupChange,
        ) {
            listOf(
                LiquidActionItem(
                    icon = refreshIcon,
                    contentDescription = refreshLabel,
                    enabled = !loading,
                    onClick = {
                        onShowActionMenuPopupChange(false)
                        onRefresh()
                    },
                ),
                LiquidActionItem(
                    icon = moreIcon,
                    contentDescription = moreLabel,
                    onClick = { onShowActionMenuPopupChange(!showActionMenuPopup) },
                ),
            )
        }

    Box {
        LiquidActionBar(
            backdrop = backdrop,
            layeredStyleEnabled = true,
            items = actionItems,
            selectedIndex = if (showActionMenuPopup) 1 else 0,
        )

        LiquidActionBarPopupAnchors(itemCount = 2) { slotIndex, popupAnchorBounds ->
            if (slotIndex == 1 && showActionMenuPopup) {
                SnapshotWindowListPopup(
                    show = showActionMenuPopup,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    anchorBounds = popupAnchorBounds,
                    placement = SnapshotPopupPlacement.ButtonEnd,
                    onDismissRequest = { onShowActionMenuPopupChange(false) },
                    enableWindowDim = false,
                    maxWidth = actionMenuMaxWidth,
                ) {
                    LiquidGlassActionMenu(
                        backdrop = backdrop,
                        accentColor = MiuixTheme.colorScheme.onBackground,
                        minWidth = actionMenuMinWidth,
                        maxWidth = actionMenuMaxWidth,
                        items =
                            listOf(
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "filter",
                                    text = filterLabel,
                                    subtitle = selectedFilterLabel,
                                    leadingIcon = filterIcon,
                                    trailingIcon = chevronRightIcon,
                                    enabled = hasRecords,
                                    submenuItems =
                                        filterModes.mapIndexed { index, mode ->
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = mode.name,
                                                text = filterLabels[index],
                                                selected = filterMode == mode,
                                                leadingIcon = filterIcon,
                                                onClick = { onFilterModeChange(mode) },
                                            )
                                        },
                                ),
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "sort",
                                    text = sortLabel,
                                    subtitle = selectedSortLabel,
                                    leadingIcon = sortIcon,
                                    trailingIcon = chevronRightIcon,
                                    enabled = hasRecords,
                                    submenuItems =
                                        sortModes.mapIndexed { index, mode ->
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = mode.name,
                                                text = sortLabels[index],
                                                selected = sortMode == mode,
                                                leadingIcon = sortIcon,
                                                onClick = { onSortModeChange(mode) },
                                            )
                                        },
                                ),
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "sort_direction",
                                    text = sortDirectionLabel,
                                    subtitle = selectedDirectionLabel,
                                    leadingIcon = sortIcon,
                                    trailingIcon = chevronRightIcon,
                                    enabled = hasRecords,
                                    submenuItems =
                                        sortDirections.mapIndexed { index, direction ->
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = direction.name,
                                                text = sortDirectionLabels[index],
                                                selected = sortDirection == direction,
                                                leadingIcon = sortIcon,
                                                onClick = { onSortDirectionChange(direction) },
                                            )
                                        },
                                ),
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "cleanup",
                                    text = cleanupLabel,
                                    subtitle = cleanupSubtitle,
                                    leadingIcon = cleanupIcon,
                                    trailingIcon = chevronRightIcon,
                                    enabled = hasRecords && !loading,
                                    submenuItems =
                                        cleanupAges.mapIndexed { index, age ->
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = age.name,
                                                text = cleanupAgeLabels[index],
                                                selected = false,
                                                leadingIcon = timeIcon,
                                                onClick = { onCleanupAgeSelect(age) },
                                            )
                                        },
                                ),
                            ),
                        onDismissRequest = { onShowActionMenuPopupChange(false) },
                    )
                }
            }
        }
    }
}
