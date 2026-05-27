@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.LauncherIconDesign
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsNavigationItem
import os.kei.ui.page.main.settings.support.SettingsPickerItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant

@Composable
internal fun SettingsVisualSection(
    state: SettingsVisualSectionState,
    actions: SettingsVisualSectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val presentation = deriveVisualPresentation(state)
    val themeModeOptions =
        listOf(
            AppThemeMode.FOLLOW_SYSTEM to stringResource(R.string.settings_theme_follow_system),
            AppThemeMode.LIGHT to stringResource(R.string.settings_theme_light_mode),
            AppThemeMode.DARK to stringResource(R.string.settings_theme_dark_mode),
        )
    val launcherIconOptions =
        listOf(
            LauncherIconDesign.Apple to stringResource(R.string.settings_launcher_icon_design_apple),
            LauncherIconDesign.Android to stringResource(R.string.settings_launcher_icon_design_android),
        )
    val currentThemeLabel =
        themeModeOptions.firstOrNull { it.first == state.appThemeMode }?.second
            ?: stringResource(R.string.settings_theme_follow_system)
    val currentLauncherIconLabel =
        launcherIconOptions.firstOrNull { it.first == state.launcherIconDesign }?.second
            ?: stringResource(R.string.settings_launcher_icon_design_apple)
    val themeSummary =
        when (state.appThemeMode) {
            AppThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_summary_follow_system)
            AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_summary_light)
            AppThemeMode.DARK -> stringResource(R.string.settings_theme_summary_dark)
        }
    val launcherIconSummary =
        when (state.launcherIconDesign) {
            LauncherIconDesign.Apple -> stringResource(R.string.settings_launcher_icon_design_summary_apple)
            LauncherIconDesign.Android -> stringResource(R.string.settings_launcher_icon_design_summary_android)
        }
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_visual_header),
        title = stringResource(R.string.settings_group_visual_title),
        sectionIcon = appLucideLayersIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
    ) {
        SettingsPickerItem(
            title = stringResource(R.string.settings_theme_mode_title),
            summary = themeSummary,
        ) {
            AppDropdownSelector(
                selectedText = currentThemeLabel,
                options = themeModeOptions.map { it.second },
                selectedIndex =
                    themeModeOptions
                        .indexOfFirst { it.first == state.appThemeMode }
                        .coerceAtLeast(0),
                expanded = state.showThemeModePopup,
                anchorBounds = state.themePopupAnchorBounds,
                onExpandedChange = actions.onShowThemeModePopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    actions.onAppThemeModeChanged(themeModeOptions[selectedIndex].first)
                },
                onAnchorBoundsChange = actions.onThemePopupAnchorBoundsChange,
                variant = GlassVariant.SheetAction,
            )
        }
        SettingsPickerItem(
            title = stringResource(R.string.settings_launcher_icon_design_title),
            summary = launcherIconSummary,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_launcher_icon_design_scope),
        ) {
            AppDropdownSelector(
                selectedText = currentLauncherIconLabel,
                options = launcherIconOptions.map { it.second },
                selectedIndex =
                    launcherIconOptions
                        .indexOfFirst {
                            it.first == state.launcherIconDesign
                        }.coerceAtLeast(0),
                expanded = state.showLauncherIconDesignPopup,
                anchorBounds = state.launcherIconDesignPopupAnchorBounds,
                onExpandedChange = actions.onShowLauncherIconDesignPopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    actions.onLauncherIconDesignChanged(launcherIconOptions[selectedIndex].first)
                },
                onAnchorBoundsChange = actions.onLauncherIconDesignPopupAnchorBoundsChange,
                variant = GlassVariant.SheetAction,
            )
        }
        SettingsNavigationItem(
            title = stringResource(R.string.settings_app_language_title),
            summary = stringResource(R.string.settings_app_language_summary),
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_app_language_scope),
            onClick = actions.onOpenAppLanguageSettings,
            enabled = state.appLanguageActionAvailable,
            trailing = {
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.Compact,
                    text = stringResource(R.string.common_open),
                    enabled = state.appLanguageActionAvailable,
                    onClick = actions.onOpenAppLanguageSettings,
                )
            },
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_preloading_title),
            summary =
                if (state.preloadingEnabled) {
                    stringResource(R.string.settings_preloading_summary_enabled)
                } else {
                    stringResource(R.string.settings_preloading_summary_disabled)
                },
            checked = state.preloadingEnabled,
            onCheckedChange = actions.onPreloadingEnabledChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_preloading_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_home_shine_title),
            summary =
                if (state.homeIconHdrEnabled) {
                    stringResource(R.string.settings_home_shine_summary_enabled)
                } else {
                    stringResource(R.string.settings_home_shine_summary_disabled)
                },
            checked = state.homeIconHdrEnabled,
            onCheckedChange = actions.onHomeIconHdrChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_home_shine_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_home_dynamic_full_effect_title),
            summary =
                if (state.homeDynamicFullEffectEnabled) {
                    stringResource(R.string.settings_home_dynamic_full_effect_summary_enabled)
                } else {
                    stringResource(R.string.settings_home_dynamic_full_effect_summary_disabled)
                },
            checked = state.homeDynamicFullEffectEnabled,
            onCheckedChange = actions.onHomeDynamicFullEffectChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_home_dynamic_full_effect_scope),
        )
    }
}
