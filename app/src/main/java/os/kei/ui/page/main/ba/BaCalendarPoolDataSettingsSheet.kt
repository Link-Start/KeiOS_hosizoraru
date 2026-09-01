@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BaCalendarRefreshIntervalOption
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.LiquidSheetSurfaceTone
import os.kei.ui.page.main.widget.sheet.SheetCardSurfaceTone
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionHeader
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaCalendarPoolDataSettingsSheet(
    show: Boolean,
    backdrop: Backdrop?,
    pageKind: BaCalendarPoolPageKind,
    snapshot: BaPageSnapshot,
    refreshIntervalDropdownExpanded: Boolean,
    refreshIntervalDropdownAnchorBounds: IntRect?,
    onRefreshIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onRefreshIntervalDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onRefreshIntervalSelected: (Int) -> Unit,
    onShowEndedActivitiesChange: (Boolean) -> Unit,
    onShowEndedPoolsChange: (Boolean) -> Unit,
    onShowCalendarPoolImagesChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val settingsAccent = Color(0xFF3B82F6)
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_calendar_pool_data_settings_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_close),
                variant = GlassVariant.Bar,
                onClick = onDismissRequest,
            )
        },
        surfaceTone = LiquidSheetSurfaceTone.Readable,
    ) {
        SheetContentColumn(verticalSpacing = 14.dp) {
            SheetSectionHeader(stringResource(R.string.ba_settings_section_sync))
            SheetSectionCard(surfaceTone = SheetCardSurfaceTone.Readable) {
                Text(
                    text = stringResource(R.string.ba_settings_card_sync_title),
                    color = settingsAccent,
                )
                SheetControlRow(
                    label = stringResource(R.string.ba_cd_refresh_interval),
                    summary = stringResource(R.string.ba_settings_summary_refresh_interval),
                ) {
                    BaCalendarPoolRefreshIntervalDropdown(
                        backdrop = backdrop,
                        selectedHours = snapshot.calendarRefreshIntervalHours,
                        expanded = refreshIntervalDropdownExpanded,
                        anchorBounds = refreshIntervalDropdownAnchorBounds,
                        onExpandedChange = onRefreshIntervalDropdownExpandedChange,
                        onAnchorBoundsChange = onRefreshIntervalDropdownAnchorBoundsChange,
                        onSelected = onRefreshIntervalSelected,
                    )
                }
            }

            SheetSectionHeader(stringResource(R.string.ba_settings_section_content))
            SheetSectionCard(surfaceTone = SheetCardSurfaceTone.Readable) {
                val showActivityToggle = pageKind != BaCalendarPoolPageKind.Pool
                val showPoolToggle = pageKind != BaCalendarPoolPageKind.Calendar
                if (showActivityToggle) {
                    SheetControlRow(label = stringResource(R.string.ba_settings_label_show_ended_activity)) {
                        AppSwitch(
                            checked = snapshot.showEndedActivities,
                            onCheckedChange = onShowEndedActivitiesChange,
                        )
                    }
                }
                if (showPoolToggle) {
                    SheetControlRow(label = stringResource(R.string.ba_settings_label_show_ended_pool)) {
                        AppSwitch(
                            checked = snapshot.showEndedPools,
                            onCheckedChange = onShowEndedPoolsChange,
                        )
                    }
                }
                SheetControlRow(label = stringResource(R.string.ba_settings_label_show_images)) {
                    AppSwitch(
                        checked = snapshot.showCalendarPoolImages,
                        onCheckedChange = onShowCalendarPoolImagesChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun BaCalendarPoolRefreshIntervalDropdown(
    backdrop: Backdrop?,
    selectedHours: Int,
    expanded: Boolean,
    anchorBounds: IntRect?,
    onExpandedChange: (Boolean) -> Unit,
    onAnchorBoundsChange: (IntRect?) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val options = BaCalendarRefreshIntervalOption.entries
    val selected = BaCalendarRefreshIntervalOption.fromHours(selectedHours)
    AppDropdownSelector(
        modifier = Modifier.width(128.dp),
        selectedText = stringResource(selected.labelRes),
        options = options.map { stringResource(it.labelRes) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        expanded = expanded,
        anchorBounds = anchorBounds,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { option ->
                onSelected(option.hours)
            }
            onExpandedChange(false)
        },
        onAnchorBoundsChange = onAnchorBoundsChange,
        backdrop = backdrop,
        variant = GlassVariant.SheetAction,
        textColor = MiuixTheme.colorScheme.primary,
        horizontalPadding = 10.dp,
        anchorAlignment = Alignment.CenterEnd,
    )
}
