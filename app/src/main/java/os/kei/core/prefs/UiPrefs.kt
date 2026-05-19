package os.kei.core.prefs

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import os.kei.BuildConfig
import os.kei.core.icon.LauncherIconDesign
import os.kei.core.log.AppLogLevel

data class UiPrefsSnapshot(
    val liquidBottomBarEnabled: Boolean,
    val miuixMainNavigationEnabled: Boolean,
    val liquidActionBarLayeredStyleEnabled: Boolean,
    val liquidSwitchEnabled: Boolean,
    val transitionAnimationsEnabled: Boolean,
    val predictiveBackAnimationsEnabled: Boolean,
    val searchAutoFocusEnabled: Boolean,
    val gripAwareFloatingDockEnabled: Boolean,
    val homeIconHdrEnabled: Boolean,
    val homeDynamicFullEffectEnabled: Boolean,
    val preloadingEnabled: Boolean,
    val launcherIconDesign: LauncherIconDesign,
    val nonHomeBackgroundEnabled: Boolean,
    val nonHomeBackgroundUri: String,
    val nonHomeBackgroundOpacity: Float,
    val superIslandNotificationEnabled: Boolean,
    val superIslandBypassRestrictionEnabled: Boolean,
    val superIslandRestoreDelayMs: Int,
    val logLevel: AppLogLevel,
    val textCopyCapabilityExpanded: Boolean,
    val cacheDiagnosticsEnabled: Boolean,
    val appThemeMode: AppThemeMode,
    val visibleBottomPageNames: Set<String>,
)

object UiPrefs {
    private const val KV_ID = "ui_prefs"
    private const val KEY_LIQUID_BOTTOM_BAR = "liquid_bottom_bar"
    private const val KEY_MIUIX_MAIN_NAVIGATION = "miuix_main_navigation"
    private const val KEY_LIQUID_ACTION_BAR_LAYERED_STYLE = "liquid_action_bar_layered_style"
    private const val KEY_LIQUID_SWITCH = "liquid_switch"
    private const val KEY_TRANSITION_ANIMATIONS = "transition_animations"
    private const val KEY_PREDICTIVE_BACK_ANIMATIONS = "predictive_back_animations"
    private const val KEY_SEARCH_AUTO_FOCUS = "search_auto_focus"
    private const val KEY_GRIP_AWARE_FLOATING_DOCK = "grip_aware_floating_dock"
    private const val KEY_HOME_ICON_HDR = "home_icon_hdr"
    private const val KEY_HOME_DYNAMIC_FULL_EFFECT = "home_dynamic_full_effect"
    private const val KEY_PRELOADING_ENABLED = "preloading_enabled"
    private const val KEY_LAUNCHER_ICON_DESIGN = "launcher_icon_design"
    private const val KEY_NON_HOME_BACKGROUND_ENABLED = "non_home_background_enabled"
    private const val KEY_NON_HOME_BACKGROUND_URI = "non_home_background_uri"
    private const val KEY_NON_HOME_BACKGROUND_OPACITY = "non_home_background_opacity"
    private const val KEY_SUPER_ISLAND_NOTIFICATION = "super_island_notification"
    private const val KEY_SUPER_ISLAND_BYPASS_RESTRICTION = "super_island_bypass_restriction"
    private const val KEY_SUPER_ISLAND_RESTORE_DELAY_MS = "super_island_restore_delay_ms"
    private const val KEY_LOG_DEBUG = "log_debug"
    private const val KEY_LOG_LEVEL = "log_level"
    private const val KEY_TEXT_COPY_CAPABILITY_EXPANDED = "text_copy_capability_expanded"
    private const val KEY_CACHE_DIAGNOSTICS = "cache_diagnostics"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_VISIBLE_BOTTOM_PAGES = "visible_bottom_pages"
    private const val NON_HOME_BACKGROUND_OPACITY_DEFAULT = 0.16f
    private const val NON_HOME_BACKGROUND_OPACITY_MIN = 0.06f
    private const val NON_HOME_BACKGROUND_OPACITY_MAX = 0.40f
    const val SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS = 100
    const val SUPER_ISLAND_RESTORE_DELAY_MIN_MS = 50
    const val SUPER_ISLAND_RESTORE_DELAY_MAX_MS = 350
    private val DEFAULT_VISIBLE_BOTTOM_PAGE_NAMES = setOf("Os", "Mcp", "GitHub", "Ba")
    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }
    private val textCopyCapabilityExpandedState =
        MutableStateFlow(
            kv().decodeBool(KEY_TEXT_COPY_CAPABILITY_EXPANDED, false),
        )

    private fun kv(): MMKV = store

    private fun buildTypeAwareLogDebugKey(): String = "${KEY_LOG_DEBUG}_${BuildConfig.BUILD_TYPE}"

    private fun buildTypeAwareLogLevelKey(): String = "${KEY_LOG_LEVEL}_${BuildConfig.BUILD_TYPE}"

    fun isLiquidBottomBarEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_LIQUID_BOTTOM_BAR, defaultValue)

    fun setLiquidBottomBarEnabled(value: Boolean) {
        kv().encode(KEY_LIQUID_BOTTOM_BAR, value)
    }

    fun isMiuixMainNavigationEnabled(defaultValue: Boolean = false): Boolean = kv().decodeBool(KEY_MIUIX_MAIN_NAVIGATION, defaultValue)

    fun setMiuixMainNavigationEnabled(value: Boolean) {
        kv().encode(KEY_MIUIX_MAIN_NAVIGATION, value)
    }

    fun isLiquidActionBarLayeredStyleEnabled(defaultValue: Boolean = true): Boolean =
        kv().decodeBool(KEY_LIQUID_ACTION_BAR_LAYERED_STYLE, defaultValue)

    fun setLiquidActionBarLayeredStyleEnabled(value: Boolean) {
        kv().encode(KEY_LIQUID_ACTION_BAR_LAYERED_STYLE, value)
    }

    fun isLiquidSwitchEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_LIQUID_SWITCH, defaultValue)

    fun setLiquidSwitchEnabled(value: Boolean) {
        kv().encode(KEY_LIQUID_SWITCH, value)
    }

    fun isTransitionAnimationsEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_TRANSITION_ANIMATIONS, defaultValue)

    fun setTransitionAnimationsEnabled(value: Boolean) {
        kv().encode(KEY_TRANSITION_ANIMATIONS, value)
    }

    fun isPredictiveBackAnimationsEnabled(defaultValue: Boolean = true): Boolean =
        kv().decodeBool(KEY_PREDICTIVE_BACK_ANIMATIONS, defaultValue)

    fun setPredictiveBackAnimationsEnabled(value: Boolean) {
        kv().encode(KEY_PREDICTIVE_BACK_ANIMATIONS, value)
    }

    fun isSearchAutoFocusEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_SEARCH_AUTO_FOCUS, defaultValue)

    fun setSearchAutoFocusEnabled(value: Boolean) {
        kv().encode(KEY_SEARCH_AUTO_FOCUS, value)
    }

    fun isGripAwareFloatingDockEnabled(defaultValue: Boolean = false): Boolean = kv().decodeBool(KEY_GRIP_AWARE_FLOATING_DOCK, defaultValue)

    fun setGripAwareFloatingDockEnabled(value: Boolean) {
        kv().encode(KEY_GRIP_AWARE_FLOATING_DOCK, value)
    }

    fun isHomeIconHdrEnabled(defaultValue: Boolean = false): Boolean = kv().decodeBool(KEY_HOME_ICON_HDR, defaultValue)

    fun setHomeIconHdrEnabled(value: Boolean) {
        kv().encode(KEY_HOME_ICON_HDR, value)
    }

    fun isHomeDynamicFullEffectEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_HOME_DYNAMIC_FULL_EFFECT, defaultValue)

    fun setHomeDynamicFullEffectEnabled(value: Boolean) {
        kv().encode(KEY_HOME_DYNAMIC_FULL_EFFECT, value)
    }

    fun isPreloadingEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_PRELOADING_ENABLED, defaultValue)

    fun setPreloadingEnabled(value: Boolean) {
        kv().encode(KEY_PRELOADING_ENABLED, value)
    }

    fun getLauncherIconDesign(defaultValue: LauncherIconDesign = LauncherIconDesign.Android): LauncherIconDesign =
        LauncherIconDesign.fromStorageId(
            kv().decodeString(KEY_LAUNCHER_ICON_DESIGN, defaultValue.storageId),
        )

    fun setLauncherIconDesign(value: LauncherIconDesign) {
        kv().encode(KEY_LAUNCHER_ICON_DESIGN, value.storageId)
    }

    fun isNonHomeBackgroundEnabled(defaultValue: Boolean = false): Boolean = kv().decodeBool(KEY_NON_HOME_BACKGROUND_ENABLED, defaultValue)

    fun setNonHomeBackgroundEnabled(value: Boolean) {
        kv().encode(KEY_NON_HOME_BACKGROUND_ENABLED, value)
    }

    fun getNonHomeBackgroundUri(defaultValue: String = ""): String =
        kv().decodeString(KEY_NON_HOME_BACKGROUND_URI, defaultValue).orEmpty().trim()

    fun setNonHomeBackgroundUri(uri: String) {
        kv().encode(KEY_NON_HOME_BACKGROUND_URI, uri.trim())
    }

    fun getNonHomeBackgroundOpacity(defaultValue: Float = NON_HOME_BACKGROUND_OPACITY_DEFAULT): Float {
        val fallback =
            defaultValue.coerceIn(
                NON_HOME_BACKGROUND_OPACITY_MIN,
                NON_HOME_BACKGROUND_OPACITY_MAX,
            )
        return kv().decodeFloat(KEY_NON_HOME_BACKGROUND_OPACITY, fallback).coerceIn(
            NON_HOME_BACKGROUND_OPACITY_MIN,
            NON_HOME_BACKGROUND_OPACITY_MAX,
        )
    }

    fun setNonHomeBackgroundOpacity(value: Float) {
        kv().encode(
            KEY_NON_HOME_BACKGROUND_OPACITY,
            value.coerceIn(NON_HOME_BACKGROUND_OPACITY_MIN, NON_HOME_BACKGROUND_OPACITY_MAX),
        )
    }

    fun isSuperIslandNotificationEnabled(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_SUPER_ISLAND_NOTIFICATION, defaultValue)

    fun setSuperIslandNotificationEnabled(value: Boolean) {
        kv().encode(KEY_SUPER_ISLAND_NOTIFICATION, value)
    }

    fun isSuperIslandBypassRestrictionEnabled(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_SUPER_ISLAND_BYPASS_RESTRICTION, defaultValue)

    fun setSuperIslandBypassRestrictionEnabled(value: Boolean) {
        kv().encode(KEY_SUPER_ISLAND_BYPASS_RESTRICTION, value)
    }

    fun getSuperIslandRestoreDelayMs(defaultValue: Int = SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS): Int {
        val fallback =
            defaultValue.coerceIn(
                SUPER_ISLAND_RESTORE_DELAY_MIN_MS,
                SUPER_ISLAND_RESTORE_DELAY_MAX_MS,
            )
        return kv().decodeInt(KEY_SUPER_ISLAND_RESTORE_DELAY_MS, fallback).coerceIn(
            SUPER_ISLAND_RESTORE_DELAY_MIN_MS,
            SUPER_ISLAND_RESTORE_DELAY_MAX_MS,
        )
    }

    fun setSuperIslandRestoreDelayMs(value: Int) {
        kv().encode(
            KEY_SUPER_ISLAND_RESTORE_DELAY_MS,
            value.coerceIn(
                SUPER_ISLAND_RESTORE_DELAY_MIN_MS,
                SUPER_ISLAND_RESTORE_DELAY_MAX_MS,
            ),
        )
    }

    fun getLogLevel(defaultValue: AppLogLevel = AppLogLevel.fromStorageId(BuildConfig.DEFAULT_LOG_LEVEL_ID)): AppLogLevel {
        val store = kv()
        val levelKey = buildTypeAwareLogLevelKey()
        if (store.containsKey(levelKey)) {
            return AppLogLevel.fromStorageId(
                raw = store.decodeString(levelKey, defaultValue.storageId),
                fallback = defaultValue,
            )
        }
        val legacyKey = buildTypeAwareLogDebugKey()
        if (store.containsKey(legacyKey)) {
            return if (store.decodeBool(legacyKey, false)) {
                AppLogLevel.Debug
            } else {
                AppLogLevel.Off
            }
        }
        return defaultValue
    }

    fun setLogLevel(value: AppLogLevel) {
        kv().encode(buildTypeAwareLogLevelKey(), value.storageId)
    }

    fun isLogDebugEnabled(
        defaultValue: Boolean = AppLogLevel.fromStorageId(BuildConfig.DEFAULT_LOG_LEVEL_ID) == AppLogLevel.Debug,
    ): Boolean =
        getLogLevel(
            defaultValue = if (defaultValue) AppLogLevel.Debug else AppLogLevel.Off,
        ) == AppLogLevel.Debug

    fun setLogDebugEnabled(value: Boolean) {
        setLogLevel(if (value) AppLogLevel.Debug else AppLogLevel.Off)
    }

    fun isTextCopyCapabilityExpanded(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_TEXT_COPY_CAPABILITY_EXPANDED, defaultValue)

    fun setTextCopyCapabilityExpanded(value: Boolean) {
        kv().encode(KEY_TEXT_COPY_CAPABILITY_EXPANDED, value)
        textCopyCapabilityExpandedState.value = value
    }

    fun observeTextCopyCapabilityExpanded(): StateFlow<Boolean> = textCopyCapabilityExpandedState.asStateFlow()

    fun isCacheDiagnosticsEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_CACHE_DIAGNOSTICS, defaultValue)

    fun setCacheDiagnosticsEnabled(value: Boolean) {
        kv().encode(KEY_CACHE_DIAGNOSTICS, value)
    }

    fun getAppThemeMode(defaultValue: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM): AppThemeMode {
        val raw = kv().decodeString(KEY_THEME_MODE, null) ?: return defaultValue
        return AppThemeMode.entries.firstOrNull { it.name == raw } ?: defaultValue
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        kv().encode(KEY_THEME_MODE, mode.name)
    }

    fun loadVisibleBottomPageNames(): Set<String> {
        val store = kv()
        if (!store.containsKey(KEY_VISIBLE_BOTTOM_PAGES)) return DEFAULT_VISIBLE_BOTTOM_PAGE_NAMES
        val raw = store.decodeString(KEY_VISIBLE_BOTTOM_PAGES, "").orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun saveVisibleBottomPageNames(names: Set<String>) {
        val normalized =
            names
                .filter { it.isNotBlank() && it != "Home" }
                .joinToString(separator = ",")
        kv().encode(KEY_VISIBLE_BOTTOM_PAGES, normalized)
    }

    fun defaultSnapshot(appThemeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM): UiPrefsSnapshot =
        UiPrefsSnapshot(
            liquidBottomBarEnabled = true,
            miuixMainNavigationEnabled = false,
            liquidActionBarLayeredStyleEnabled = true,
            liquidSwitchEnabled = true,
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            searchAutoFocusEnabled = true,
            gripAwareFloatingDockEnabled = false,
            homeIconHdrEnabled = false,
            homeDynamicFullEffectEnabled = true,
            preloadingEnabled = true,
            launcherIconDesign = LauncherIconDesign.Android,
            nonHomeBackgroundEnabled = false,
            nonHomeBackgroundUri = "",
            nonHomeBackgroundOpacity = NON_HOME_BACKGROUND_OPACITY_DEFAULT,
            superIslandNotificationEnabled = false,
            superIslandBypassRestrictionEnabled = false,
            superIslandRestoreDelayMs = SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS,
            logLevel = AppLogLevel.fromStorageId(BuildConfig.DEFAULT_LOG_LEVEL_ID),
            textCopyCapabilityExpanded = false,
            cacheDiagnosticsEnabled = true,
            appThemeMode = appThemeMode,
            visibleBottomPageNames = DEFAULT_VISIBLE_BOTTOM_PAGE_NAMES,
        )

    fun loadSnapshot(): UiPrefsSnapshot {
        val store = kv()
        return UiPrefsSnapshot(
            liquidBottomBarEnabled = store.decodeBool(KEY_LIQUID_BOTTOM_BAR, true),
            miuixMainNavigationEnabled = store.decodeBool(KEY_MIUIX_MAIN_NAVIGATION, false),
            liquidActionBarLayeredStyleEnabled = store.decodeBool(KEY_LIQUID_ACTION_BAR_LAYERED_STYLE, true),
            liquidSwitchEnabled = store.decodeBool(KEY_LIQUID_SWITCH, true),
            transitionAnimationsEnabled = store.decodeBool(KEY_TRANSITION_ANIMATIONS, true),
            predictiveBackAnimationsEnabled = store.decodeBool(KEY_PREDICTIVE_BACK_ANIMATIONS, true),
            searchAutoFocusEnabled = store.decodeBool(KEY_SEARCH_AUTO_FOCUS, true),
            gripAwareFloatingDockEnabled = store.decodeBool(KEY_GRIP_AWARE_FLOATING_DOCK, false),
            homeIconHdrEnabled = store.decodeBool(KEY_HOME_ICON_HDR, false),
            homeDynamicFullEffectEnabled = store.decodeBool(KEY_HOME_DYNAMIC_FULL_EFFECT, true),
            preloadingEnabled = store.decodeBool(KEY_PRELOADING_ENABLED, true),
            launcherIconDesign = getLauncherIconDesign(),
            nonHomeBackgroundEnabled = store.decodeBool(KEY_NON_HOME_BACKGROUND_ENABLED, false),
            nonHomeBackgroundUri = store.decodeString(KEY_NON_HOME_BACKGROUND_URI, "").orEmpty().trim(),
            nonHomeBackgroundOpacity =
                store
                    .decodeFloat(
                        KEY_NON_HOME_BACKGROUND_OPACITY,
                        NON_HOME_BACKGROUND_OPACITY_DEFAULT,
                    ).coerceIn(NON_HOME_BACKGROUND_OPACITY_MIN, NON_HOME_BACKGROUND_OPACITY_MAX),
            superIslandNotificationEnabled = store.decodeBool(KEY_SUPER_ISLAND_NOTIFICATION, false),
            superIslandBypassRestrictionEnabled = store.decodeBool(KEY_SUPER_ISLAND_BYPASS_RESTRICTION, false),
            superIslandRestoreDelayMs = getSuperIslandRestoreDelayMs(),
            logLevel = getLogLevel(),
            textCopyCapabilityExpanded = store.decodeBool(KEY_TEXT_COPY_CAPABILITY_EXPANDED, false),
            cacheDiagnosticsEnabled = store.decodeBool(KEY_CACHE_DIAGNOSTICS, true),
            appThemeMode = getAppThemeMode(),
            visibleBottomPageNames = loadVisibleBottomPageNames(),
        )
    }
}
