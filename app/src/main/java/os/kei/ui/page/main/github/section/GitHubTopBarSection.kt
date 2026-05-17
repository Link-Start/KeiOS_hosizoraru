package os.kei.ui.page.main.github.section

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.RefreshIntervalOption
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.os.appLucideUploadIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuQuickAction
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSingleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSubmenuRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GitHubActionMenuCompactMinWidth = 244.dp
private val GitHubActionMenuPreferredMinWidth = 256.dp
private val GitHubActionMenuPreferredMaxWidth = 272.dp
private val GitHubActionMenuHorizontalMargin = 92.dp

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
    sortDirection: GitHubSortDirection,
    trackedFilterMode: GitHubTrackedFilterMode,
    refreshIntervalHours: Int,
    showActionMenuPopup: Boolean,
    tracksExporting: Boolean,
    tracksImporting: Boolean,
    onOpenStrategySheet: () -> Unit,
    onOpenCheckLogicSheet: () -> Unit,
    onShowActionMenuPopupChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubSortMode) -> Unit,
    onSortDirectionChange: (GitHubSortDirection) -> Unit,
    onTrackedFilterModeChange: (GitHubTrackedFilterMode) -> Unit,
    onRefreshIntervalHoursChange: (Int) -> Unit,
    onExportTrackedItems: () -> Unit,
    onImportTrackedItems: () -> Unit,
    onOpenStarImport: () -> Unit,
    onActionBarInteractingChanged: (Boolean) -> Unit
) {
    val editStrategyIcon = appLucideEditIcon()
    val checkLogicIcon = appLucideConfigIcon()
    val exportTracksIcon = appLucideDownloadIcon()
    val importTracksIcon = appLucideUploadIcon()
    val importStarsIcon = appLucideHeartIcon()
    val sortIcon = appLucideSortIcon()
    val filterIcon = appLucideFilterIcon()
    val intervalIcon = appLucideTimeIcon()
    val moreIcon = appLucideMoreIcon()
    val chevronRightIcon = appLucideChevronRightIcon()
    val editStrategyContentDescription = stringResource(R.string.github_topbar_cd_edit_strategy)
    val checkLogicContentDescription = stringResource(R.string.github_topbar_cd_check_logic)
    val sortContentDescription = stringResource(R.string.github_topbar_cd_sort)
    val sortDirectionLabel = stringResource(R.string.github_topbar_cd_sort_direction)
    val filterLabel = stringResource(R.string.github_topbar_cd_filter)
    val refreshIntervalLabel = stringResource(R.string.github_check_sheet_label_refresh_interval)
    val moreContentDescription = stringResource(R.string.github_item_cd_more_actions)
    val exportTracksLabel = if (tracksExporting) {
        stringResource(R.string.github_check_sheet_action_exporting)
    } else {
        stringResource(R.string.github_check_sheet_action_export_tracks)
    }
    val importTracksLabel = if (tracksImporting) {
        stringResource(R.string.github_check_sheet_action_importing)
    } else {
        stringResource(R.string.github_check_sheet_action_import_tracks)
    }
    val importStarsLabel = stringResource(R.string.github_check_sheet_action_import_stars)
    val transferActionEnabled = !tracksExporting && !tracksImporting
    val screenWidth = appWindowWidthDp()
    val actionMenuMaxWidth = (screenWidth - GitHubActionMenuHorizontalMargin)
        .coerceIn(GitHubActionMenuCompactMinWidth, GitHubActionMenuPreferredMaxWidth)
    val actionMenuMinWidth = minOf(GitHubActionMenuPreferredMinWidth, actionMenuMaxWidth)
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
                onClick = { onShowActionMenuPopupChange(!showActionMenuPopup) },
                testTag = KeiOsTestTags.GitHubImportMenuButton
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
                        maxWidth = actionMenuMaxWidth
                    ) {
                        val modes = GitHubSortMode.entries
                        val sortLabels = modes.map { mode -> stringResource(mode.labelRes) }
                        val selectedSortLabel = sortLabels.getOrElse(modes.indexOf(sortMode)) {
                            stringResource(sortMode.labelRes)
                        }
                        val directions = GitHubSortDirection.entries
                        val directionLabels = directions.map { direction ->
                            stringResource(direction.labelRes)
                        }
                        val selectedDirectionLabel = directionLabels.getOrElse(
                            directions.indexOf(sortDirection)
                        ) {
                            stringResource(sortDirection.labelRes)
                        }
                        val filterModes = GitHubTrackedFilterMode.entries
                        val filterLabels = filterModes.map { mode -> stringResource(mode.labelRes) }
                        val selectedFilterLabel =
                            filterLabels.getOrElse(filterModes.indexOf(trackedFilterMode)) {
                                stringResource(trackedFilterMode.labelRes)
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
                            accentColor = MiuixTheme.colorScheme.onBackground,
                            minWidth = actionMenuMinWidth,
                            maxWidth = actionMenuMaxWidth,
                            quickActions = listOf(
                                LiquidGlassActionMenuQuickAction(
                                    id = "export_tracks",
                                    icon = exportTracksIcon,
                                    label = exportTracksLabel,
                                    enabled = transferActionEnabled,
                                    onClick = onExportTrackedItems
                                ),
                                LiquidGlassActionMenuQuickAction(
                                    id = "import_tracks",
                                    icon = importTracksIcon,
                                    label = importTracksLabel,
                                    enabled = transferActionEnabled,
                                    testTag = KeiOsTestTags.GitHubImportTracks,
                                    onClick = onImportTrackedItems
                                ),
                                LiquidGlassActionMenuQuickAction(
                                    id = "import_stars",
                                    icon = importStarsIcon,
                                    label = importStarsLabel,
                                    enabled = transferActionEnabled,
                                    testTag = KeiOsTestTags.GitHubImportStars,
                                    onClick = onOpenStarImport
                                )
                            ),
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
                                    id = "sort_direction",
                                    text = sortDirectionLabel,
                                    subtitle = selectedDirectionLabel,
                                    leadingIcon = sortIcon,
                                    trailingIcon = chevronRightIcon,
                                    submenuItems = directions.mapIndexed { index, direction ->
                                        LiquidGlassActionMenuSingleChoiceRow(
                                            id = direction.name,
                                            text = directionLabels[index],
                                            selected = sortDirection == direction,
                                            leadingIcon = sortIcon,
                                            onClick = { onSortDirectionChange(direction) }
                                        )
                                    }
                                ),
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "filter",
                                    text = filterLabel,
                                    subtitle = selectedFilterLabel,
                                    leadingIcon = filterIcon,
                                    trailingIcon = chevronRightIcon,
                                    submenuItems = filterModes.mapIndexed { index, mode ->
                                        LiquidGlassActionMenuSingleChoiceRow(
                                            id = mode.name,
                                            text = filterLabels[index],
                                            selected = trackedFilterMode == mode,
                                            leadingIcon = filterIcon,
                                            onClick = { onTrackedFilterModeChange(mode) }
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
