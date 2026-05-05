package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaCalendarPoolNotifyLeadOption
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class BaNotificationSettingsSheetState(
    val apNotifyEnabled: Boolean,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
    val calendarUpcomingNotifyEnabled: Boolean,
    val calendarEndingNotifyEnabled: Boolean,
    val poolUpcomingNotifyEnabled: Boolean,
    val poolEndingNotifyEnabled: Boolean,
    val calendarPoolChangeNotifyEnabled: Boolean,
    val calendarPoolNotifyLeadHours: Int,
    val apNotifyThresholdText: String,
)

@Composable
internal fun BaNotificationSettingsSheet(
    show: Boolean,
    backdrop: Backdrop?,
    state: BaNotificationSettingsSheetState,
    onApNotifyEnabledChange: (Boolean) -> Unit,
    onArenaRefreshNotifyEnabledChange: (Boolean) -> Unit,
    onCafeVisitNotifyEnabledChange: (Boolean) -> Unit,
    onCalendarUpcomingNotifyEnabledChange: (Boolean) -> Unit,
    onCalendarEndingNotifyEnabledChange: (Boolean) -> Unit,
    onPoolUpcomingNotifyEnabledChange: (Boolean) -> Unit,
    onPoolEndingNotifyEnabledChange: (Boolean) -> Unit,
    onCalendarPoolChangeNotifyEnabledChange: (Boolean) -> Unit,
    onCalendarPoolNotifyLeadHoursSelected: (Int) -> Unit,
    onApNotifyThresholdTextChange: (String) -> Unit,
    onApNotifyThresholdDone: () -> Unit,
    onDismissRequest: () -> Unit,
    onSaveRequest: () -> Unit,
) {
    val settingsAccent = Color(0xFF3B82F6)
    var leadDropdownExpanded by remember { mutableStateOf(false) }
    var leadDropdownAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_notification_settings_title),
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
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Ok,
                contentDescription = stringResource(R.string.common_save),
                variant = GlassVariant.Bar,
                onClick = onSaveRequest,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(stringResource(R.string.ba_settings_section_notifications))
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_card_ap_title),
                    color = settingsAccent,
                )
                SheetControlRow(label = stringResource(R.string.ba_settings_label_ap_notify)) {
                    AppSwitch(
                        checked = state.apNotifyEnabled,
                        onCheckedChange = onApNotifyEnabledChange,
                    )
                }
                if (state.apNotifyEnabled) {
                    SheetControlRow(
                        label = stringResource(R.string.ba_settings_label_ap_threshold),
                        summary = stringResource(
                            R.string.ba_settings_summary_ap_threshold,
                            BA_AP_MAX
                        ),
                    ) {
                        AppLiquidSearchField(
                            modifier = Modifier.width(70.dp),
                            value = state.apNotifyThresholdText,
                            onValueChange = { input ->
                                onApNotifyThresholdTextChange(normalizeBaApThresholdInput(input))
                            },
                            onImeActionDone = onApNotifyThresholdDone,
                            label = "120",
                            backdrop = backdrop,
                            variant = GlassVariant.SheetInput,
                            singleLine = true,
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp,
                            textColor = Color(0xFF22C55E),
                        )
                    }
                }
            }
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_card_daily_notify_title),
                    color = settingsAccent,
                )
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_arena_refresh_notify),
                    summary = stringResource(R.string.ba_settings_summary_arena_refresh_notify),
                ) {
                    AppSwitch(
                        checked = state.arenaRefreshNotifyEnabled,
                        onCheckedChange = onArenaRefreshNotifyEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_cafe_visit_notify),
                    summary = stringResource(R.string.ba_settings_summary_cafe_visit_notify),
                ) {
                    AppSwitch(
                        checked = state.cafeVisitNotifyEnabled,
                        onCheckedChange = onCafeVisitNotifyEnabledChange,
                    )
                }
            }
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_card_calendar_pool_notify_title),
                    color = settingsAccent,
                )
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_calendar_pool_notify_lead),
                    summary = stringResource(R.string.ba_settings_summary_calendar_pool_notify_lead),
                ) {
                    BaCalendarPoolNotifyLeadDropdown(
                        backdrop = backdrop,
                        selectedHours = state.calendarPoolNotifyLeadHours,
                        expanded = leadDropdownExpanded,
                        anchorBounds = leadDropdownAnchorBounds,
                        onExpandedChange = { leadDropdownExpanded = it },
                        onAnchorBoundsChange = { leadDropdownAnchorBounds = it },
                        onSelected = onCalendarPoolNotifyLeadHoursSelected,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_calendar_upcoming_notify),
                    summary = stringResource(R.string.ba_settings_summary_calendar_upcoming_notify),
                ) {
                    AppSwitch(
                        checked = state.calendarUpcomingNotifyEnabled,
                        onCheckedChange = onCalendarUpcomingNotifyEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_calendar_ending_notify),
                    summary = stringResource(R.string.ba_settings_summary_calendar_ending_notify),
                ) {
                    AppSwitch(
                        checked = state.calendarEndingNotifyEnabled,
                        onCheckedChange = onCalendarEndingNotifyEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_pool_upcoming_notify),
                    summary = stringResource(R.string.ba_settings_summary_pool_upcoming_notify),
                ) {
                    AppSwitch(
                        checked = state.poolUpcomingNotifyEnabled,
                        onCheckedChange = onPoolUpcomingNotifyEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_pool_ending_notify),
                    summary = stringResource(R.string.ba_settings_summary_pool_ending_notify),
                ) {
                    AppSwitch(
                        checked = state.poolEndingNotifyEnabled,
                        onCheckedChange = onPoolEndingNotifyEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_calendar_pool_change_notify),
                    summary = stringResource(R.string.ba_settings_summary_calendar_pool_change_notify),
                ) {
                    AppSwitch(
                        checked = state.calendarPoolChangeNotifyEnabled,
                        onCheckedChange = onCalendarPoolChangeNotifyEnabledChange,
                    )
                }
            }
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_note_timezone),
                    color = Color(0xFFF59E0B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun normalizeBaApThresholdInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(3)
    if (digits.isBlank()) return ""
    return digits.toIntOrNull()?.coerceIn(0, BA_AP_MAX)?.toString().orEmpty()
}

@Composable
private fun BaCalendarPoolNotifyLeadDropdown(
    backdrop: Backdrop?,
    selectedHours: Int,
    expanded: Boolean,
    anchorBounds: IntRect?,
    onExpandedChange: (Boolean) -> Unit,
    onAnchorBoundsChange: (IntRect?) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val options = BaCalendarPoolNotifyLeadOption.entries
    val selected = BaCalendarPoolNotifyLeadOption.fromHours(selectedHours)
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
