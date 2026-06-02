@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideDatabaseIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.student.catalog.BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MIN_HOURS
import os.kei.ui.page.main.student.catalog.BaGuideCatalogIncrementalRefreshIntervalOptions
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogSortMode
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSingleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSubmenuRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

private val BaCatalogMoreMenuMinWidth = 232.dp
private val BaCatalogMoreMenuMaxWidth = 312.dp
private val BaCatalogMoreMenuMaxHeight = 420.dp

@Composable
internal fun BaGuideCatalogMoreActionPopup(
    show: Boolean,
    anchorBounds: IntRect?,
    backdrop: Backdrop,
    sortMode: BaGuideCatalogSortMode,
    incrementalRefreshIntervalHours: Int,
    onDismissRequest: () -> Unit,
    onOpenTransfer: () -> Unit,
    onSelectSortMode: (BaGuideCatalogSortMode) -> Unit,
    onSelectIncrementalRefreshIntervalHours: (Int) -> Unit,
) {
    if (!show) return
    SnapshotWindowListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.BottomEnd,
        anchorBounds = anchorBounds,
        placement = SnapshotPopupPlacement.ButtonEnd,
        onDismissRequest = onDismissRequest,
        enableWindowDim = false,
    ) {
        val sortModes = BaGuideCatalogSortMode.entries
        val sortLabels = sortModes.map { mode -> stringResource(mode.labelRes) }
        val selectedSortLabel =
            sortLabels.getOrElse(sortModes.indexOf(sortMode)) {
                stringResource(sortMode.labelRes)
            }
        val refreshOptions = BaGuideCatalogIncrementalRefreshIntervalOptions
        val refreshOptionLabels =
            refreshOptions.map { hours ->
                when (hours) {
                    BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MIN_HOURS ->
                        stringResource(R.string.ba_catalog_more_incremental_refresh_12h)

                    else -> stringResource(R.string.ba_catalog_more_incremental_refresh_24h)
                }
            }
        val selectedRefreshLabel =
            refreshOptionLabels.getOrElse(refreshOptions.indexOf(incrementalRefreshIntervalHours)) {
                refreshOptionLabels.firstOrNull()
                    ?: stringResource(R.string.ba_catalog_more_incremental_refresh_12h)
            }
        val databaseIcon = appLucideDatabaseIcon()
        val sortIcon = appLucideSortIcon()
        val infoIcon = appLucideInfoIcon()
        val timeIcon = appLucideTimeIcon()
        val refreshIcon = appLucideRefreshIcon()
        val chevronRightIcon = appLucideChevronRightIcon()
        LiquidGlassActionMenu(
            backdrop = backdrop,
            minWidth = BaCatalogMoreMenuMinWidth,
            maxWidth = BaCatalogMoreMenuMaxWidth,
            maxHeight = BaCatalogMoreMenuMaxHeight,
            items =
                listOf(
                    LiquidGlassActionMenuActionRow(
                        id = "transfer",
                        text = stringResource(R.string.ba_catalog_more_transfer),
                        subtitle = stringResource(R.string.ba_catalog_more_transfer_summary),
                        leadingIcon = databaseIcon,
                        onClick = onOpenTransfer,
                    ),
                    LiquidGlassActionMenuSubmenuRow(
                        id = "sort",
                        text = stringResource(R.string.ba_catalog_more_sort_title),
                        subtitle = selectedSortLabel,
                        leadingIcon = sortIcon,
                        trailingIcon = chevronRightIcon,
                        submenuItems =
                            sortModes.mapIndexed { index, mode ->
                                LiquidGlassActionMenuSingleChoiceRow(
                                    id = mode.name,
                                    text = sortLabels[index],
                                    selected = sortMode == mode,
                                    leadingIcon = sortIcon,
                                    onClick = { onSelectSortMode(mode) },
                                )
                            },
                    ),
                    LiquidGlassActionMenuActionRow(
                        id = "refresh_scope",
                        text = stringResource(R.string.ba_catalog_more_refresh_scope_title),
                        subtitle = stringResource(R.string.ba_catalog_more_refresh_scope_summary),
                        leadingIcon = infoIcon,
                        enabled = false,
                        onClick = {},
                    ),
                    LiquidGlassActionMenuSubmenuRow(
                        id = "incremental_refresh",
                        text = stringResource(R.string.ba_catalog_more_incremental_refresh_title),
                        subtitle = selectedRefreshLabel,
                        leadingIcon = timeIcon,
                        trailingIcon = chevronRightIcon,
                        submenuItems =
                            refreshOptions.mapIndexed { index, hours ->
                                LiquidGlassActionMenuSingleChoiceRow(
                                    id = "${hours}h",
                                    text = refreshOptionLabels[index],
                                    subtitle = stringResource(R.string.ba_catalog_more_incremental_refresh_summary),
                                    selected = incrementalRefreshIntervalHours == hours,
                                    leadingIcon = timeIcon,
                                    onClick = { onSelectIncrementalRefreshIntervalHours(hours) },
                                )
                            },
                    ),
                    LiquidGlassActionMenuActionRow(
                        id = "full_refresh",
                        text = stringResource(R.string.ba_catalog_more_full_refresh_title),
                        subtitle = stringResource(R.string.ba_catalog_more_full_refresh_summary),
                        leadingIcon = refreshIcon,
                        enabled = false,
                        onClick = {},
                    ),
                ),
            onDismissRequest = onDismissRequest,
        )
    }
}
