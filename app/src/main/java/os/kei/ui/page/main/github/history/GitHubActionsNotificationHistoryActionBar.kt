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
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.widget.chrome.LiquidToolbar
import os.kei.ui.page.main.widget.chrome.LiquidToolbarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidToolbarAction
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
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
    historyMode: GitHubHistoryMode,
    filterMode: GitHubActionsHistoryFilterMode,
    refreshFilterMode: GitHubRefreshHistoryFilterMode,
    trackChangeFilterMode: GitHubTrackChangeHistoryFilterMode,
    appInstallFilterMode: GitHubAppInstallHistoryFilterMode,
    sortMode: GitHubActionsHistorySortMode,
    refreshSortMode: GitHubRefreshHistorySortMode,
    trackChangeSortMode: GitHubTrackChangeHistorySortMode,
    appInstallSortMode: GitHubAppInstallHistorySortMode,
    sortDirection: GitHubActionsHistorySortDirection,
    onRefresh: () -> Unit,
    onShowActionMenuPopupChange: (Boolean) -> Unit,
    onFilterModeChange: (GitHubActionsHistoryFilterMode) -> Unit,
    onRefreshFilterModeChange: (GitHubRefreshHistoryFilterMode) -> Unit,
    onTrackChangeFilterModeChange: (GitHubTrackChangeHistoryFilterMode) -> Unit,
    onAppInstallFilterModeChange: (GitHubAppInstallHistoryFilterMode) -> Unit,
    onSortModeChange: (GitHubActionsHistorySortMode) -> Unit,
    onRefreshSortModeChange: (GitHubRefreshHistorySortMode) -> Unit,
    onTrackChangeSortModeChange: (GitHubTrackChangeHistorySortMode) -> Unit,
    onAppInstallSortModeChange: (GitHubAppInstallHistorySortMode) -> Unit,
    onSortDirectionChange: (GitHubActionsHistorySortDirection) -> Unit,
    onCleanupAgeSelect: (GitHubActionsHistoryCleanupAge) -> Unit,
    onExportRefreshHistory: () -> Unit,
) {
    val refreshIcon = appLucideRefreshIcon()
    val moreIcon = appLucideMoreIcon()
    val filterIcon = appLucideFilterIcon()
    val sortIcon = appLucideSortIcon()
    val timeIcon = appLucideTimeIcon()
    val exportIcon = appLucideDownloadIcon()
    val cleanupIcon = appLucideTrashIcon()
    val chevronRightIcon = appLucideChevronRightIcon()
    val refreshLabel = stringResource(R.string.common_refresh)
    val moreLabel = stringResource(R.string.github_actions_history_action_more)
    val filterLabel = stringResource(R.string.github_actions_history_action_filter)
    val sortLabel = stringResource(R.string.github_actions_history_action_sort)
    val sortDirectionLabel = stringResource(R.string.github_actions_history_action_sort_direction)
    val cleanupLabel = stringResource(R.string.github_actions_history_action_cleanup)
    val cleanupSubtitle = stringResource(R.string.github_actions_history_cleanup_subtitle)
    val exportRefreshLabel = stringResource(R.string.github_history_action_export_refresh)
    val exportRefreshSubtitle = stringResource(R.string.github_history_action_export_refresh_summary)
    val filterModes = GitHubActionsHistoryFilterMode.entries
    val filterLabels = filterModes.map { mode -> stringResource(mode.labelRes) }
    val refreshFilterModes = GitHubRefreshHistoryFilterMode.entries
    val refreshFilterLabels = refreshFilterModes.map { mode -> stringResource(mode.labelRes) }
    val trackChangeFilterModes = GitHubTrackChangeHistoryFilterMode.entries
    val trackChangeFilterLabels = trackChangeFilterModes.map { mode -> stringResource(mode.labelRes) }
    val appInstallFilterModes = GitHubAppInstallHistoryFilterMode.entries
    val appInstallFilterLabels = appInstallFilterModes.map { mode -> stringResource(mode.labelRes) }
    val sortModes = GitHubActionsHistorySortMode.entries
    val sortLabels = sortModes.map { mode -> stringResource(mode.labelRes) }
    val refreshSortModes = GitHubRefreshHistorySortMode.entries
    val refreshSortLabels = refreshSortModes.map { mode -> stringResource(mode.labelRes) }
    val trackChangeSortModes = GitHubTrackChangeHistorySortMode.entries
    val trackChangeSortLabels = trackChangeSortModes.map { mode -> stringResource(mode.labelRes) }
    val appInstallSortModes = GitHubAppInstallHistorySortMode.entries
    val appInstallSortLabels = appInstallSortModes.map { mode -> stringResource(mode.labelRes) }
    val sortDirections = GitHubActionsHistorySortDirection.entries
    val sortDirectionLabels = sortDirections.map { direction -> stringResource(direction.labelRes) }
    val cleanupAges = GitHubActionsHistoryCleanupAge.entries
    val cleanupAgeLabels = cleanupAges.map { age -> stringResource(age.labelRes) }
    val selectedFilterLabel =
        when (historyMode) {
            GitHubHistoryMode.Refresh -> {
                refreshFilterLabels.getOrElse(refreshFilterModes.indexOf(refreshFilterMode)) {
                    stringResource(refreshFilterMode.labelRes)
                }
            }

            GitHubHistoryMode.Actions -> {
                filterLabels.getOrElse(filterModes.indexOf(filterMode)) {
                    stringResource(filterMode.labelRes)
                }
            }

            GitHubHistoryMode.Tracking -> {
                trackChangeFilterLabels.getOrElse(trackChangeFilterModes.indexOf(trackChangeFilterMode)) {
                    stringResource(trackChangeFilterMode.labelRes)
                }
            }

            GitHubHistoryMode.Apps -> {
                appInstallFilterLabels.getOrElse(appInstallFilterModes.indexOf(appInstallFilterMode)) {
                    stringResource(appInstallFilterMode.labelRes)
                }
            }
        }
    val selectedSortLabel =
        when (historyMode) {
            GitHubHistoryMode.Refresh -> {
                refreshSortLabels.getOrElse(refreshSortModes.indexOf(refreshSortMode)) {
                    stringResource(refreshSortMode.labelRes)
                }
            }

            GitHubHistoryMode.Actions -> {
                sortLabels.getOrElse(sortModes.indexOf(sortMode)) {
                    stringResource(sortMode.labelRes)
                }
            }

            GitHubHistoryMode.Tracking -> {
                trackChangeSortLabels.getOrElse(trackChangeSortModes.indexOf(trackChangeSortMode)) {
                    stringResource(trackChangeSortMode.labelRes)
                }
            }

            GitHubHistoryMode.Apps -> {
                appInstallSortLabels.getOrElse(appInstallSortModes.indexOf(appInstallSortMode)) {
                    stringResource(appInstallSortMode.labelRes)
                }
            }
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
                LiquidToolbarAction(
                    icon = refreshIcon,
                    contentDescription = refreshLabel,
                    enabled = !loading,
                    onClick = {
                        onShowActionMenuPopupChange(false)
                        onRefresh()
                    },
                ),
                LiquidToolbarAction(
                    icon = moreIcon,
                    contentDescription = moreLabel,
                    onClick = { onShowActionMenuPopupChange(!showActionMenuPopup) },
                    // Was `selectedIndex = if (showActionMenuPopup) 1 else 0`. The More action is
                    // on for as long as its menu is open, which is what a toolbar item's selected
                    // appearance is for.
                    active = showActionMenuPopup,
                ),
            )
        }

    Box {
        LiquidToolbar(
            backdrop = backdrop,
            actions = actionItems,
        )

        LiquidToolbarPopupAnchors(itemCount = 2) { slotIndex, popupAnchorBounds ->
            if (slotIndex == 1) {
                SnapshotWindowListPopup(
                    show = showActionMenuPopup,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    anchorBoundsProvider = popupAnchorBounds,
                    placement = SnapshotPopupPlacement.ButtonEnd,
                    onDismissRequest = { onShowActionMenuPopupChange(false) },
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
                                        when (historyMode) {
                                            GitHubHistoryMode.Refresh -> {
                                                refreshFilterModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = refreshFilterLabels[index],
                                                        selected = refreshFilterMode == mode,
                                                        leadingIcon = filterIcon,
                                                        onClick = { onRefreshFilterModeChange(mode) },
                                                    )
                                                }
                                            }

                                            GitHubHistoryMode.Actions -> {
                                                filterModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = filterLabels[index],
                                                        selected = filterMode == mode,
                                                        leadingIcon = filterIcon,
                                                        onClick = { onFilterModeChange(mode) },
                                                    )
                                                }
                                            }

                                            GitHubHistoryMode.Tracking -> {
                                                trackChangeFilterModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = trackChangeFilterLabels[index],
                                                        selected = trackChangeFilterMode == mode,
                                                        leadingIcon = filterIcon,
                                                        onClick = { onTrackChangeFilterModeChange(mode) },
                                                    )
                                                }
                                            }

                                            GitHubHistoryMode.Apps -> {
                                                appInstallFilterModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = appInstallFilterLabels[index],
                                                        selected = appInstallFilterMode == mode,
                                                        leadingIcon = filterIcon,
                                                        onClick = { onAppInstallFilterModeChange(mode) },
                                                    )
                                                }
                                            }
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
                                        when (historyMode) {
                                            GitHubHistoryMode.Refresh -> {
                                                refreshSortModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = refreshSortLabels[index],
                                                        selected = refreshSortMode == mode,
                                                        leadingIcon = sortIcon,
                                                        onClick = { onRefreshSortModeChange(mode) },
                                                    )
                                                }
                                            }

                                            GitHubHistoryMode.Actions -> {
                                                sortModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = sortLabels[index],
                                                        selected = sortMode == mode,
                                                        leadingIcon = sortIcon,
                                                        onClick = { onSortModeChange(mode) },
                                                    )
                                                }
                                            }

                                            GitHubHistoryMode.Tracking -> {
                                                trackChangeSortModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = trackChangeSortLabels[index],
                                                        selected = trackChangeSortMode == mode,
                                                        leadingIcon = sortIcon,
                                                        onClick = { onTrackChangeSortModeChange(mode) },
                                                    )
                                                }
                                            }

                                            GitHubHistoryMode.Apps -> {
                                                appInstallSortModes.mapIndexed { index, mode ->
                                                    LiquidGlassActionMenuSingleChoiceRow(
                                                        id = mode.name,
                                                        text = appInstallSortLabels[index],
                                                        selected = appInstallSortMode == mode,
                                                        leadingIcon = sortIcon,
                                                        onClick = { onAppInstallSortModeChange(mode) },
                                                    )
                                                }
                                            }
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
                                LiquidGlassActionMenuActionRow(
                                    id = "export_refresh",
                                    text = exportRefreshLabel,
                                    subtitle = exportRefreshSubtitle,
                                    leadingIcon = exportIcon,
                                    enabled = hasRecords && !loading && historyMode == GitHubHistoryMode.Refresh,
                                    onClick = {
                                        onShowActionMenuPopupChange(false)
                                        onExportRefreshHistory()
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
