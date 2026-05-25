package os.kei.ui.page.main.settings.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import java.util.Locale

internal enum class SettingsSearchCard {
    Permissions,
    Visual,
    Animation,
    ComponentEffects,
    Background,
    Notify,
    Copy,
    Cache,
    Log,
}

@Immutable
internal data class SettingsSearchTarget(
    val card: SettingsSearchCard,
    val category: SettingsCategory,
    private val tokens: List<String>,
) {
    fun matches(query: String): Boolean {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) return true
        return tokens.any { token ->
            token.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
    }
}

@Composable
internal fun rememberSettingsSearchTargets(): List<SettingsSearchTarget> {
    val accessCategoryLabel = stringResource(R.string.settings_category_access)
    val permissionsGroupLabel = stringResource(R.string.settings_group_permissions_title)
    val notificationPermissionLabel = stringResource(R.string.settings_notification_permission_title)
    val batteryOptimizationLabel = stringResource(R.string.settings_battery_optimization_title)
    val oemAutostartLabel = stringResource(R.string.settings_oem_autostart_title)
    val appListAccessLabel = stringResource(R.string.settings_app_list_access_title)
    val shizukuPermissionLabel = stringResource(R.string.settings_shizuku_permission_title)

    val appearanceCategoryLabel = stringResource(R.string.settings_category_appearance)
    val visualGroupLabel = stringResource(R.string.settings_group_visual_title)
    val themeModeLabel = stringResource(R.string.settings_theme_mode_title)
    val launcherIconDesignLabel = stringResource(R.string.settings_launcher_icon_design_title)
    val launcherIconAppleLabel = stringResource(R.string.settings_launcher_icon_design_apple)
    val launcherIconAndroidLabel = stringResource(R.string.settings_launcher_icon_design_android)
    val appLanguageLabel = stringResource(R.string.settings_app_language_title)
    val preloadingLabel = stringResource(R.string.settings_preloading_title)
    val homeShineLabel = stringResource(R.string.settings_home_shine_title)
    val homeDynamicFullEffectLabel = stringResource(R.string.settings_home_dynamic_full_effect_title)

    val effectsCategoryLabel = stringResource(R.string.settings_category_effects)
    val animationGroupLabel = stringResource(R.string.settings_group_animation_title)
    val transitionAnimationsLabel = stringResource(R.string.settings_transition_animations_title)
    val predictiveBackAnimationsLabel = stringResource(R.string.settings_predictive_back_animations_title)

    val componentEffectsGroupLabel = stringResource(R.string.settings_group_component_effects_title)
    val bottomBarLabel = stringResource(R.string.settings_bottom_bar_title)
    val miuixMainNavigationLabel = stringResource(R.string.settings_miuix_main_navigation_title)
    val actionbarStyleLabel = stringResource(R.string.settings_actionbar_style_title)
    val liquidSwitchLabel = stringResource(R.string.settings_liquid_switch_title)
    val liquidToastLabel = stringResource(R.string.settings_liquid_toast_title)
    val liquidSheetLabel = stringResource(R.string.settings_liquid_sheet_title)
    val liquidDialogLabel = stringResource(R.string.settings_liquid_dialog_title)
    val searchAutoFocusLabel = stringResource(R.string.settings_search_auto_focus_title)
    val gripAwareFloatingDockLabel = stringResource(R.string.settings_grip_aware_floating_dock_title)

    val backgroundGroupLabel = stringResource(R.string.settings_group_background_title)
    val nonHomeBackgroundLabel = stringResource(R.string.settings_non_home_background_title)
    val nonHomeBackgroundImageLabel = stringResource(R.string.settings_non_home_background_image_title)
    val nonHomeBackgroundOpacityLabel = stringResource(R.string.settings_non_home_background_opacity_title)
    val nonHomeBackgroundCropLabel = stringResource(R.string.settings_non_home_background_crop_title)

    val notifyGroupLabel = stringResource(R.string.settings_group_notify_title)
    val superIslandStyleLabel = stringResource(R.string.settings_super_island_style_title)
    val superIslandBypassLabel = stringResource(R.string.settings_super_island_bypass_title)
    val superIslandRestoreDelayLabel = stringResource(R.string.settings_super_island_restore_delay_title)

    val dataCategoryLabel = stringResource(R.string.settings_category_data)
    val copyGroupLabel = stringResource(R.string.settings_group_copy_title)
    val copyCapabilityLabel = stringResource(R.string.settings_copy_capability_title)

    val cacheHeaderLabel = stringResource(R.string.settings_cache_header)
    val cacheDiagnosticsLabel = stringResource(R.string.settings_cache_diagnostics_title)
    val cacheEntryOverviewLabel = stringResource(R.string.settings_cache_entry_overview_title)
    val cacheEntryGithubLabel = stringResource(R.string.settings_cache_entry_github_title)
    val cacheEntryBaPageLabel = stringResource(R.string.settings_cache_entry_ba_page_title)
    val cacheEntryBaGuideLabel = stringResource(R.string.settings_cache_entry_ba_guide_title)
    val cacheEntryOsLabel = stringResource(R.string.settings_cache_entry_os_title)
    val cacheEntryMcpLabel = stringResource(R.string.settings_cache_entry_mcp_title)

    val logGroupLabel = stringResource(R.string.settings_group_log_title)
    val logLevelLabel = stringResource(R.string.settings_log_level_title)
    val logFeedbackActionLabel = stringResource(R.string.settings_log_feedback_action)

    return remember(
        accessCategoryLabel,
        permissionsGroupLabel,
        notificationPermissionLabel,
        batteryOptimizationLabel,
        oemAutostartLabel,
        appListAccessLabel,
        shizukuPermissionLabel,
        appearanceCategoryLabel,
        visualGroupLabel,
        themeModeLabel,
        launcherIconDesignLabel,
        launcherIconAppleLabel,
        launcherIconAndroidLabel,
        appLanguageLabel,
        preloadingLabel,
        homeShineLabel,
        homeDynamicFullEffectLabel,
        effectsCategoryLabel,
        animationGroupLabel,
        transitionAnimationsLabel,
        predictiveBackAnimationsLabel,
        componentEffectsGroupLabel,
        bottomBarLabel,
        miuixMainNavigationLabel,
        actionbarStyleLabel,
        liquidSwitchLabel,
        liquidToastLabel,
        liquidSheetLabel,
        liquidDialogLabel,
        searchAutoFocusLabel,
        gripAwareFloatingDockLabel,
        backgroundGroupLabel,
        nonHomeBackgroundLabel,
        nonHomeBackgroundImageLabel,
        nonHomeBackgroundOpacityLabel,
        nonHomeBackgroundCropLabel,
        notifyGroupLabel,
        superIslandStyleLabel,
        superIslandBypassLabel,
        superIslandRestoreDelayLabel,
        dataCategoryLabel,
        copyGroupLabel,
        copyCapabilityLabel,
        cacheHeaderLabel,
        cacheDiagnosticsLabel,
        cacheEntryOverviewLabel,
        cacheEntryGithubLabel,
        cacheEntryBaPageLabel,
        cacheEntryBaGuideLabel,
        cacheEntryOsLabel,
        cacheEntryMcpLabel,
        logGroupLabel,
        logLevelLabel,
        logFeedbackActionLabel,
    ) {
        listOf(
            SettingsSearchTarget(
                card = SettingsSearchCard.Permissions,
                category = SettingsCategory.Access,
                tokens =
                    settingsTokens(
                        accessCategoryLabel,
                        permissionsGroupLabel,
                        notificationPermissionLabel,
                        batteryOptimizationLabel,
                        oemAutostartLabel,
                        appListAccessLabel,
                        shizukuPermissionLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Visual,
                category = SettingsCategory.Appearance,
                tokens =
                    settingsTokens(
                        appearanceCategoryLabel,
                        visualGroupLabel,
                        themeModeLabel,
                        launcherIconDesignLabel,
                        launcherIconAppleLabel,
                        launcherIconAndroidLabel,
                        appLanguageLabel,
                        preloadingLabel,
                        homeShineLabel,
                        homeDynamicFullEffectLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Animation,
                category = SettingsCategory.Effects,
                tokens =
                    settingsTokens(
                        effectsCategoryLabel,
                        animationGroupLabel,
                        transitionAnimationsLabel,
                        predictiveBackAnimationsLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.ComponentEffects,
                category = SettingsCategory.Effects,
                tokens =
                    settingsTokens(
                        effectsCategoryLabel,
                        componentEffectsGroupLabel,
                        bottomBarLabel,
                        miuixMainNavigationLabel,
                        actionbarStyleLabel,
                        liquidSwitchLabel,
                        liquidToastLabel,
                        liquidSheetLabel,
                        liquidDialogLabel,
                        searchAutoFocusLabel,
                        gripAwareFloatingDockLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Background,
                category = SettingsCategory.Appearance,
                tokens =
                    settingsTokens(
                        backgroundGroupLabel,
                        nonHomeBackgroundLabel,
                        nonHomeBackgroundImageLabel,
                        nonHomeBackgroundOpacityLabel,
                        nonHomeBackgroundCropLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Notify,
                category = SettingsCategory.Access,
                tokens =
                    settingsTokens(
                        accessCategoryLabel,
                        notifyGroupLabel,
                        superIslandStyleLabel,
                        superIslandBypassLabel,
                        superIslandRestoreDelayLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Copy,
                category = SettingsCategory.Data,
                tokens =
                    settingsTokens(
                        dataCategoryLabel,
                        copyGroupLabel,
                        copyCapabilityLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Cache,
                category = SettingsCategory.Data,
                tokens =
                    settingsTokens(
                        cacheHeaderLabel,
                        cacheDiagnosticsLabel,
                        cacheEntryOverviewLabel,
                        cacheEntryGithubLabel,
                        cacheEntryBaPageLabel,
                        cacheEntryBaGuideLabel,
                        cacheEntryOsLabel,
                        cacheEntryMcpLabel,
                    ),
            ),
            SettingsSearchTarget(
                card = SettingsSearchCard.Log,
                category = SettingsCategory.Data,
                tokens =
                    settingsTokens(
                        logGroupLabel,
                        logLevelLabel,
                        logFeedbackActionLabel,
                    ),
            ),
        )
    }
}

private fun settingsTokens(vararg values: String): List<String> = values.filter { it.isNotBlank() }

internal fun deriveSettingsSearchTargets(
    targets: List<SettingsSearchTarget>,
    query: String,
): List<SettingsSearchTarget> =
    if (query.isBlank()) {
        emptyList()
    } else {
        targets.filter { it.matches(query) }
    }
