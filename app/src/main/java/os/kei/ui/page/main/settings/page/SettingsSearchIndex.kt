package os.kei.ui.page.main.settings.page

import androidx.compose.runtime.Immutable
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

internal fun buildSettingsSearchTargets(resolveString: (Int) -> String): List<SettingsSearchTarget> {
    val accessCategoryLabel = resolveString(R.string.settings_category_access)
    val permissionsGroupLabel = resolveString(R.string.settings_group_permissions_title)
    val notificationPermissionLabel = resolveString(R.string.settings_notification_permission_title)
    val batteryOptimizationLabel = resolveString(R.string.settings_battery_optimization_title)
    val oemAutostartLabel = resolveString(R.string.settings_oem_autostart_title)
    val appListAccessLabel = resolveString(R.string.settings_app_list_access_title)
    val shizukuPermissionLabel = resolveString(R.string.settings_shizuku_permission_title)

    val appearanceCategoryLabel = resolveString(R.string.settings_category_appearance)
    val visualGroupLabel = resolveString(R.string.settings_group_visual_title)
    val themeModeLabel = resolveString(R.string.settings_theme_mode_title)
    val launcherIconDesignLabel = resolveString(R.string.settings_launcher_icon_design_title)
    val launcherIconAppleLabel = resolveString(R.string.settings_launcher_icon_design_apple)
    val launcherIconAndroidLabel = resolveString(R.string.settings_launcher_icon_design_android)
    val appLanguageLabel = resolveString(R.string.settings_app_language_title)
    val preloadingLabel = resolveString(R.string.settings_preloading_title)
    val homeShineLabel = resolveString(R.string.settings_home_shine_title)
    val homeDynamicFullEffectLabel = resolveString(R.string.settings_home_dynamic_full_effect_title)

    val effectsCategoryLabel = resolveString(R.string.settings_category_effects)
    val animationGroupLabel = resolveString(R.string.settings_group_animation_title)
    val transitionAnimationsLabel = resolveString(R.string.settings_transition_animations_title)
    val predictiveBackAnimationsLabel = resolveString(R.string.settings_predictive_back_animations_title)

    val componentEffectsGroupLabel = resolveString(R.string.settings_group_component_effects_title)
    val bottomBarLabel = resolveString(R.string.settings_bottom_bar_title)
    val miuixMainNavigationLabel = resolveString(R.string.settings_miuix_main_navigation_title)
    val actionbarStyleLabel = resolveString(R.string.settings_actionbar_style_title)
    val liquidSwitchLabel = resolveString(R.string.settings_liquid_switch_title)
    val liquidToastLabel = resolveString(R.string.settings_liquid_toast_title)
    val liquidSheetLabel = resolveString(R.string.settings_liquid_sheet_title)
    val liquidDialogLabel = resolveString(R.string.settings_liquid_dialog_title)
    val searchAutoFocusLabel = resolveString(R.string.settings_search_auto_focus_title)
    val gripAwareFloatingDockLabel = resolveString(R.string.settings_grip_aware_floating_dock_title)

    val backgroundGroupLabel = resolveString(R.string.settings_group_background_title)
    val nonHomeBackgroundLabel = resolveString(R.string.settings_non_home_background_title)
    val nonHomeBackgroundImageLabel = resolveString(R.string.settings_non_home_background_image_title)
    val nonHomeBackgroundOpacityLabel = resolveString(R.string.settings_non_home_background_opacity_title)
    val nonHomeBackgroundCropLabel = resolveString(R.string.settings_non_home_background_crop_title)

    val notifyGroupLabel = resolveString(R.string.settings_group_notify_title)
    val superIslandStyleLabel = resolveString(R.string.settings_super_island_style_title)
    val superIslandBypassLabel = resolveString(R.string.settings_super_island_bypass_title)
    val superIslandRestoreDelayLabel = resolveString(R.string.settings_super_island_restore_delay_title)

    val dataCategoryLabel = resolveString(R.string.settings_category_data)
    val copyGroupLabel = resolveString(R.string.settings_group_copy_title)
    val copyCapabilityLabel = resolveString(R.string.settings_copy_capability_title)

    val cacheHeaderLabel = resolveString(R.string.settings_cache_header)
    val cacheDiagnosticsLabel = resolveString(R.string.settings_cache_diagnostics_title)
    val cacheEntryOverviewLabel = resolveString(R.string.settings_cache_entry_overview_title)
    val cacheEntryGithubLabel = resolveString(R.string.settings_cache_entry_github_title)
    val cacheEntryBaPageLabel = resolveString(R.string.settings_cache_entry_ba_page_title)
    val cacheEntryBaGuideLabel = resolveString(R.string.settings_cache_entry_ba_guide_title)
    val cacheEntryOsLabel = resolveString(R.string.settings_cache_entry_os_title)
    val cacheEntryMcpLabel = resolveString(R.string.settings_cache_entry_mcp_title)

    val logGroupLabel = resolveString(R.string.settings_group_log_title)
    val logLevelLabel = resolveString(R.string.settings_log_level_title)
    val logFeedbackActionLabel = resolveString(R.string.settings_log_feedback_action)

    return listOf(
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
