package os.kei.ui.page.main.settings.page

import androidx.compose.runtime.Composable
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
    return listOf(
        SettingsSearchTarget(
            card = SettingsSearchCard.Permissions,
            category = SettingsCategory.Access,
            tokens = settingsTokens(
                stringResource(R.string.settings_category_access),
                stringResource(R.string.settings_group_permissions_title),
                stringResource(R.string.settings_notification_permission_title),
                stringResource(R.string.settings_battery_optimization_title),
                stringResource(R.string.settings_oem_autostart_title),
                stringResource(R.string.settings_app_list_access_title),
                stringResource(R.string.settings_shizuku_permission_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Visual,
            category = SettingsCategory.Appearance,
            tokens = settingsTokens(
                stringResource(R.string.settings_category_appearance),
                stringResource(R.string.settings_group_visual_title),
                stringResource(R.string.settings_theme_mode_title),
                stringResource(R.string.settings_app_language_title),
                stringResource(R.string.settings_preloading_title),
                stringResource(R.string.settings_home_shine_title),
                stringResource(R.string.settings_home_dynamic_full_effect_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Animation,
            category = SettingsCategory.Appearance,
            tokens = settingsTokens(
                stringResource(R.string.settings_group_animation_title),
                stringResource(R.string.settings_transition_animations_title),
                stringResource(R.string.settings_predictive_back_animations_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.ComponentEffects,
            category = SettingsCategory.Appearance,
            tokens = settingsTokens(
                stringResource(R.string.settings_group_component_effects_title),
                stringResource(R.string.settings_bottom_bar_title),
                stringResource(R.string.settings_actionbar_style_title),
                stringResource(R.string.settings_liquid_switch_title),
                stringResource(R.string.settings_grip_aware_floating_dock_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Background,
            category = SettingsCategory.Appearance,
            tokens = settingsTokens(
                stringResource(R.string.settings_group_background_title),
                stringResource(R.string.settings_non_home_background_title),
                stringResource(R.string.settings_non_home_background_image_title),
                stringResource(R.string.settings_non_home_background_opacity_title),
                stringResource(R.string.settings_non_home_background_crop_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Notify,
            category = SettingsCategory.Notify,
            tokens = settingsTokens(
                stringResource(R.string.settings_category_notify),
                stringResource(R.string.settings_group_notify_title),
                stringResource(R.string.settings_super_island_style_title),
                stringResource(R.string.settings_super_island_bypass_title),
                stringResource(R.string.settings_super_island_restore_delay_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Copy,
            category = SettingsCategory.Data,
            tokens = settingsTokens(
                stringResource(R.string.settings_category_data),
                stringResource(R.string.settings_group_copy_title),
                stringResource(R.string.settings_copy_capability_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Cache,
            category = SettingsCategory.Data,
            tokens = settingsTokens(
                stringResource(R.string.settings_cache_header),
                stringResource(R.string.settings_cache_diagnostics_title),
                stringResource(R.string.settings_cache_entry_overview_title),
                stringResource(R.string.settings_cache_entry_github_title),
                stringResource(R.string.settings_cache_entry_ba_page_title),
                stringResource(R.string.settings_cache_entry_ba_guide_title),
                stringResource(R.string.settings_cache_entry_os_title),
                stringResource(R.string.settings_cache_entry_mcp_title),
            ),
        ),
        SettingsSearchTarget(
            card = SettingsSearchCard.Log,
            category = SettingsCategory.Data,
            tokens = settingsTokens(
                stringResource(R.string.settings_group_log_title),
                stringResource(R.string.settings_log_debug_title),
            ),
        ),
    )
}

private fun settingsTokens(vararg values: String): List<String> {
    return values.filter { it.isNotBlank() }
}
