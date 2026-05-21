@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsToggleItem

@Composable
internal fun SettingsComponentEffectsSection(
    state: SettingsComponentEffectsSectionState,
    actions: SettingsComponentEffectsSectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val presentation = deriveComponentEffectsPresentation(state)
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_component_effects_header),
        title = stringResource(R.string.settings_group_component_effects_title),
        sectionIcon = appLucideConfigIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
    ) {
        SettingsToggleItem(
            title = stringResource(R.string.settings_actionbar_style_title),
            summary =
                if (state.liquidActionBarLayeredStyleEnabled) {
                    stringResource(R.string.settings_actionbar_style_summary_enabled)
                } else {
                    stringResource(R.string.settings_actionbar_style_summary_disabled)
                },
            checked = state.liquidActionBarLayeredStyleEnabled,
            onCheckedChange = actions.onLiquidActionBarLayeredStyleChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_actionbar_style_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_liquid_switch_title),
            summary =
                if (state.liquidSwitchEnabled) {
                    stringResource(R.string.settings_liquid_switch_summary_enabled)
                } else {
                    stringResource(R.string.settings_liquid_switch_summary_disabled)
                },
            checked = state.liquidSwitchEnabled,
            onCheckedChange = actions.onLiquidSwitchChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_liquid_switch_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_liquid_toast_title),
            summary =
                if (state.liquidToastEnabled) {
                    stringResource(R.string.settings_liquid_toast_summary_enabled)
                } else {
                    stringResource(R.string.settings_liquid_toast_summary_disabled)
                },
            checked = state.liquidToastEnabled,
            onCheckedChange = actions.onLiquidToastChanged,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_reduce_toast_interruption_title),
            summary =
                if (state.reduceToastInterruptionEnabled) {
                    stringResource(R.string.settings_reduce_toast_interruption_summary_enabled)
                } else {
                    stringResource(R.string.settings_reduce_toast_interruption_summary_disabled)
                },
            checked = state.reduceToastInterruptionEnabled,
            onCheckedChange = actions.onReduceToastInterruptionChanged,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_liquid_sheet_title),
            summary =
                if (state.liquidSheetEnabled) {
                    stringResource(R.string.settings_liquid_sheet_summary_enabled)
                } else {
                    stringResource(R.string.settings_liquid_sheet_summary_disabled)
                },
            checked = state.liquidSheetEnabled,
            onCheckedChange = actions.onLiquidSheetChanged,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_liquid_dialog_title),
            summary =
                if (state.liquidDialogEnabled) {
                    stringResource(R.string.settings_liquid_dialog_summary_enabled)
                } else {
                    stringResource(R.string.settings_liquid_dialog_summary_disabled)
                },
            checked = state.liquidDialogEnabled,
            onCheckedChange = actions.onLiquidDialogChanged,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_bottom_bar_title),
            summary =
                when {
                    state.miuixMainNavigationEnabled -> {
                        stringResource(R.string.settings_bottom_bar_summary_miuix_active)
                    }

                    state.liquidBottomBarEnabled -> {
                        stringResource(R.string.settings_bottom_bar_summary_enabled)
                    }

                    else -> {
                        stringResource(R.string.settings_bottom_bar_summary_disabled)
                    }
                },
            checked = state.liquidBottomBarEnabled,
            onCheckedChange = actions.onLiquidBottomBarChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_bottom_bar_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_miuix_main_navigation_title),
            summary =
                if (state.miuixMainNavigationEnabled) {
                    stringResource(R.string.settings_miuix_main_navigation_summary_enabled)
                } else {
                    stringResource(R.string.settings_miuix_main_navigation_summary_disabled)
                },
            checked = state.miuixMainNavigationEnabled,
            onCheckedChange = actions.onMiuixMainNavigationChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_miuix_main_navigation_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_search_auto_focus_title),
            summary =
                if (state.searchAutoFocusEnabled) {
                    stringResource(R.string.settings_search_auto_focus_summary_enabled)
                } else {
                    stringResource(R.string.settings_search_auto_focus_summary_disabled)
                },
            checked = state.searchAutoFocusEnabled,
            onCheckedChange = actions.onSearchAutoFocusChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_search_auto_focus_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_grip_aware_floating_dock_title),
            summary =
                if (state.gripAwareFloatingDockEnabled) {
                    stringResource(R.string.settings_grip_aware_floating_dock_summary_enabled)
                } else {
                    stringResource(R.string.settings_grip_aware_floating_dock_summary_disabled)
                },
            checked = state.gripAwareFloatingDockEnabled,
            onCheckedChange = actions.onGripAwareFloatingDockChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_grip_aware_floating_dock_scope),
        )
    }
}
