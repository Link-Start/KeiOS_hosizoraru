@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideAlertIcon
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_KEY_POINTS
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_MAGNET_THRESHOLD
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_MAX_MS
import os.kei.ui.page.main.settings.support.SUPER_ISLAND_RESTORE_DELAY_MIN_MS
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.settings.support.SettingsValueItem
import os.kei.ui.page.main.settings.support.formatMilliseconds
import kotlin.math.roundToInt

@Composable
internal fun SettingsNotifySection(
    state: SettingsNotifySectionState,
    actions: SettingsNotifySectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
    onSliderInteractionChanged: (Boolean) -> Unit = {},
) {
    val presentation = deriveNotifyPresentation(state)
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_notify_header),
        title = stringResource(R.string.settings_group_notify_title),
        sectionIcon = appLucideAlertIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
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
