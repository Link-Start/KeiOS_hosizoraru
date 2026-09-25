@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.core.prefs.SuperIslandAutoClose
import os.kei.core.prefs.SuperIslandFloatBehavior
import os.kei.ui.page.main.os.appLucideAlertIcon
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_KEY_POINTS
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_MAGNET_THRESHOLD
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_MAX_MS
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_MIN_MS
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsPickerItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.settings.support.SettingsValueItem
import os.kei.ui.page.main.settings.support.formatMilliseconds
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant
import kotlin.math.roundToInt

@Composable
internal fun SettingsNotifySection(
    state: SettingsNotifySectionState,
    actions: SettingsNotifySectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
    onSliderInteractionChanged: (Boolean) -> Unit = {},
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val presentation = deriveNotifyPresentation(state)
    var floatBehaviorExpanded by remember { mutableStateOf(false) }
    var floatBehaviorAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val floatBehaviorOptions =
        listOf(
            SuperIslandFloatBehavior.StartAndFinish to
                stringResource(R.string.settings_super_island_float_behavior_start_finish),
            SuperIslandFloatBehavior.StartOnly to
                stringResource(R.string.settings_super_island_float_behavior_start_only),
            SuperIslandFloatBehavior.SummaryOnly to
                stringResource(R.string.settings_super_island_float_behavior_summary_only),
        )
    val selectedFloatBehaviorIndex =
        floatBehaviorOptions
            .indexOfFirst { it.first == state.superIslandFloatBehavior }
            .coerceAtLeast(0)
    val selectedFloatBehaviorLabel =
        floatBehaviorOptions
            .getOrElse(selectedFloatBehaviorIndex) { floatBehaviorOptions.first() }
            .second
    val floatBehaviorSummary =
        when (state.superIslandFloatBehavior) {
            SuperIslandFloatBehavior.StartAndFinish ->
                stringResource(R.string.settings_super_island_float_behavior_summary_start_finish)
            SuperIslandFloatBehavior.StartOnly ->
                stringResource(R.string.settings_super_island_float_behavior_summary_start_only)
            SuperIslandFloatBehavior.SummaryOnly ->
                stringResource(R.string.settings_super_island_float_behavior_summary_summary_only)
        }
    var autoCloseExpanded by remember { mutableStateOf(false) }
    var autoCloseAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val autoCloseOptions =
        listOf(
            SuperIslandAutoClose.Never to stringResource(R.string.settings_super_island_auto_close_never),
            SuperIslandAutoClose.FiveMinutes to stringResource(R.string.settings_super_island_auto_close_5m),
            SuperIslandAutoClose.FifteenMinutes to stringResource(R.string.settings_super_island_auto_close_15m),
            SuperIslandAutoClose.ThirtyMinutes to stringResource(R.string.settings_super_island_auto_close_30m),
            SuperIslandAutoClose.OneHour to stringResource(R.string.settings_super_island_auto_close_1h),
            SuperIslandAutoClose.ThreeHours to stringResource(R.string.settings_super_island_auto_close_3h),
            SuperIslandAutoClose.TwelveHours to stringResource(R.string.settings_super_island_auto_close_12h),
        )
    val selectedAutoCloseIndex =
        autoCloseOptions.indexOfFirst { it.first == state.superIslandAutoClose }.coerceAtLeast(0)
    val selectedAutoCloseLabel = autoCloseOptions[selectedAutoCloseIndex].second
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_notify_header),
        title = stringResource(R.string.settings_group_notify_title),
        subtitle = stringResource(R.string.settings_group_notify_summary),
        sectionIcon = appLucideAlertIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
        exportBackdropToContent = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SettingsToggleItem(
            title = stringResource(R.string.settings_super_island_style_title),
            summary =
                if (state.superIslandNotificationEnabled) {
                    stringResource(R.string.settings_super_island_style_summary_enabled)
                } else {
                    stringResource(R.string.settings_super_island_style_summary_disabled)
                },
            checked = state.superIslandNotificationEnabled,
            onCheckedChange = actions.onSuperIslandNotificationChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_super_island_style_scope),
        )
        SettingsPickerItem(
            title = stringResource(R.string.settings_super_island_float_behavior_title),
            summary = floatBehaviorSummary,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_super_island_float_behavior_scope),
        ) {
            AppDropdownSelector(
                selectedText = selectedFloatBehaviorLabel,
                options = floatBehaviorOptions.map { it.second },
                selectedIndex = selectedFloatBehaviorIndex,
                expanded = floatBehaviorExpanded && state.superIslandNotificationEnabled,
                anchorBounds = floatBehaviorAnchorBounds,
                onExpandedChange = { expanded ->
                    floatBehaviorExpanded = expanded && state.superIslandNotificationEnabled
                },
                onSelectedIndexChange = { selectedIndex ->
                    floatBehaviorOptions
                        .getOrNull(selectedIndex)
                        ?.first
                        ?.let(actions.onSuperIslandFloatBehaviorChanged)
                    floatBehaviorExpanded = false
                },
                onAnchorBoundsChange = { floatBehaviorAnchorBounds = it },
                enabled = state.superIslandNotificationEnabled,
                variant = GlassVariant.SheetAction,
                popupMaxWidth = 260.dp,
                popupMatchAnchorWidth = true,
            )
        }
        SettingsPickerItem(
            title = stringResource(R.string.settings_super_island_auto_close_title),
            summary =
                if (state.superIslandAutoClose == SuperIslandAutoClose.Never) {
                    stringResource(R.string.settings_super_island_auto_close_summary_never)
                } else {
                    stringResource(R.string.settings_super_island_auto_close_summary_timed, selectedAutoCloseLabel)
                },
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_super_island_auto_close_scope),
        ) {
            AppDropdownSelector(
                selectedText = selectedAutoCloseLabel,
                options = autoCloseOptions.map { it.second },
                selectedIndex = selectedAutoCloseIndex,
                expanded = autoCloseExpanded && state.superIslandNotificationEnabled,
                anchorBounds = autoCloseAnchorBounds,
                onExpandedChange = { expanded ->
                    autoCloseExpanded = expanded && state.superIslandNotificationEnabled
                },
                onSelectedIndexChange = { selectedIndex ->
                    autoCloseOptions
                        .getOrNull(selectedIndex)
                        ?.first
                        ?.let(actions.onSuperIslandAutoCloseChanged)
                    autoCloseExpanded = false
                },
                onAnchorBoundsChange = { autoCloseAnchorBounds = it },
                enabled = state.superIslandNotificationEnabled,
                variant = GlassVariant.SheetAction,
                popupMaxWidth = 260.dp,
                popupMatchAnchorWidth = true,
            )
        }
        SettingsToggleItem(
            title = stringResource(R.string.settings_super_island_bypass_title),
            summary =
                if (state.superIslandBypassRestrictionEnabled) {
                    stringResource(R.string.settings_super_island_bypass_summary_enabled)
                } else {
                    stringResource(R.string.settings_super_island_bypass_summary_disabled)
                },
            checked = state.superIslandBypassRestrictionEnabled,
            onCheckedChange = actions.onSuperIslandBypassRestrictionChanged,
            infoKey = stringResource(R.string.common_note),
            infoValue = stringResource(R.string.settings_super_island_bypass_note),
        )
        val restoreDelayTitle = stringResource(R.string.settings_super_island_restore_delay_title)
        SettingsValueItem(
            title = restoreDelayTitle,
            summary =
                stringResource(
                    R.string.settings_super_island_restore_delay_summary,
                    formatMilliseconds(state.superIslandRestoreDelayMs),
                ),
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_super_island_restore_delay_scope),
        )
        SettingsLiquidKeyPointSlider(
            value =
                state.superIslandRestoreDelayMs.toFloat().coerceIn(
                    SUPER_ISLAND_RESTORE_DELAY_MIN_MS,
                    SUPER_ISLAND_RESTORE_DELAY_MAX_MS,
                ),
            onValueChange = { value ->
                actions.onSuperIslandRestoreDelayMsChanged(value.roundToInt())
            },
            valueRange = SUPER_ISLAND_RESTORE_DELAY_MIN_MS..SUPER_ISLAND_RESTORE_DELAY_MAX_MS,
            keyPoints = SUPER_ISLAND_RESTORE_DELAY_KEY_POINTS,
            magnetThreshold = SUPER_ISLAND_RESTORE_DELAY_MAGNET_THRESHOLD,
            enabled = state.superIslandBypassRestrictionEnabled,
            contentDescription = restoreDelayTitle,
            onInteractionChanged = onSliderInteractionChanged,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
        )
        SettingsInfoItem(
            key = stringResource(R.string.common_note),
            value =
                stringResource(
                    R.string.settings_super_island_restore_delay_note,
                    formatMilliseconds(SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS.roundToInt()),
                ),
        )
    }
}
