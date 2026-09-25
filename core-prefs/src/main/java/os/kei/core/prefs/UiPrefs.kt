package os.kei.core.prefs

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import os.kei.core.log.AppLogLevel

data class UiPrefsSnapshot(
    val liquidSwitchEnabled: Boolean,
    val transitionAnimationsEnabled: Boolean,
    val predictiveBackAnimationsEnabled: Boolean,
    val searchAutoFocusEnabled: Boolean,
    val gripAwareFloatingDockEnabled: Boolean,
    /**
     * Whether a regular-width window opens as a sidebar rather than a tab bar.
     *
     * Persisted rather than kept in composition state because it is a *choice*, not a transient view state: the
     * adaptable style narrows a window past the sidebar's floor and shows a tab bar without discarding the
     * preference, and the same has to hold across a process restart. Default false follows the HIG's
     * "Consider using a tab bar first".
     */
    val sidebarNavigationPreferred: Boolean,
    val homeIconHdrEnabled: Boolean,
    val homeDynamicFullEffectEnabled: Boolean,
    val preloadingEnabled: Boolean,
    val launcherIconDesign: LauncherIconDesign,
    val privilegeModeId: String,
    val nonHomeBackgroundEnabled: Boolean,
    val nonHomeBackgroundUri: String,
    val nonHomeBackgroundOpacity: Float,
    val nonHomeBackgroundContentScale: NonHomeBackgroundContentScale,
    val nonHomeBackgroundAlignment: NonHomeBackgroundAlignment,
    val nonHomeBackgroundPageStyle: NonHomeBackgroundPageStyle,
    val nonHomeBackgroundScrim: Float,
    val nonHomeBackgroundDepthEnabled: Boolean,
    val nonHomeBackgroundSaturation: Float,
    val superIslandNotificationEnabled: Boolean,
    val superIslandFloatBehavior: SuperIslandFloatBehavior,
    val superIslandAutoClose: SuperIslandAutoClose,
    val superIslandFirstFloatEnabled: Boolean,
    val superIslandBypassRestrictionEnabled: Boolean,
    val superIslandRestoreDelayMs: Int,
    val logLevel: AppLogLevel,
    val textCopyCapabilityExpanded: Boolean,
    val cacheDiagnosticsEnabled: Boolean,
    val liquidToastEnabled: Boolean,
    val reduceToastInterruptionEnabled: Boolean,
    val appThemeMode: AppThemeMode,
    val visibleBottomPageNames: Set<String>,
)

enum class SuperIslandFloatBehavior(
    val storageId: String,
) {
    SummaryOnly("summary_only"),
    StartOnly("start_only"),
    StartAndFinish("start_and_finish"),
    ;

    val firstFloatEnabled: Boolean
        get() = this != SummaryOnly

    val finishFloatEnabled: Boolean
        get() = this == StartAndFinish

    companion object {
        fun fromStorageId(raw: String?): SuperIslandFloatBehavior =
            entries.firstOrNull { it.storageId == raw } ?: StartAndFinish
    }
}

/**
 * How long HyperOS keeps a Super Island and its focus notification before closing them.
 *
 * One choice sets both protocol fields: `param_v2.timeout` in minutes, which the official guide
 * (pId=2131) calls the notification's default disappearance time (default 720), and
 * `param_island.islandTimeout` in seconds, the island's own (default 60 * 60). Without a value, the
 * notification closed after 12 hours and its island after one, while GitHub refresh set its own.
 *
 * The protocol has no "never" value: per Xiaomi's FAQ `timeout = 0` means the default, and `-1`
 * closes it after about five seconds. So [Never] is a long finite time, 35,000 minutes (about 24
 * days), chosen so that neither field overflows a 32-bit millisecond count if the host converts
 * it (35,000 min and 2,100,000 s are both under 2^31 ms). GitHub refresh keeps its own short
 * timeouts: a refresh notification must go away.
 */
enum class SuperIslandAutoClose(
    val storageId: String,
    val minutes: Int,
) {
    Never("never", 35_000),
    FiveMinutes("5m", 5),
    FifteenMinutes("15m", 15),
    ThirtyMinutes("30m", 30),
    OneHour("1h", 60),
    ThreeHours("3h", 180),
    TwelveHours("12h", 720),
    ;

    /** `param_v2.timeout`, in minutes. */
    val focusTimeoutMinutes: Int
        get() = minutes

    /** `param_island.islandTimeout`, in seconds. */
    val islandTimeoutSeconds: Int
        get() = minutes * 60

    companion object {
        fun fromStorageId(raw: String?): SuperIslandAutoClose =
            entries.firstOrNull { it.storageId == raw } ?: Never
    }
}

enum class NonHomeBackgroundContentScale(
    val storageId: String,
) {
    Crop("crop"),
    Fit("fit"),
    FillBounds("fill_bounds"),
    ;

    companion object {
        fun fromStorageId(raw: String?): NonHomeBackgroundContentScale =
            entries.firstOrNull { it.storageId == raw } ?: Crop
    }
}

enum class NonHomeBackgroundAlignment(
    val storageId: String,
) {
    Top("top"),
    Center("center"),
    Bottom("bottom"),
    Start("start"),
    End("end"),
    ;

    companion object {
        fun fromStorageId(raw: String?): NonHomeBackgroundAlignment =
            entries.firstOrNull { it.storageId == raw } ?: Center
    }
}

enum class NonHomeBackgroundPageStyle(
    val storageId: String,
) {
    Standard("standard"),
    Readable("readable"),
    Soft("soft"),
    Focused("focused"),
    ;

    companion object {
        fun fromStorageId(raw: String?): NonHomeBackgroundPageStyle =
            entries.firstOrNull { it.storageId == raw } ?: Standard
    }
}

object UiPrefs {
    private const val KV_ID = "ui_prefs"
    private const val KEY_LIQUID_SWITCH = "liquid_switch"
    private const val KEY_TRANSITION_ANIMATIONS = "transition_animations"
    private const val KEY_PREDICTIVE_BACK_ANIMATIONS = "predictive_back_animations"
    private const val KEY_SEARCH_AUTO_FOCUS = "search_auto_focus"
    private const val KEY_GRIP_AWARE_FLOATING_DOCK = "grip_aware_floating_dock"
    private const val KEY_SIDEBAR_NAVIGATION = "sidebar_navigation_preferred"
    private const val KEY_HOME_ICON_HDR = "home_icon_hdr"
    private const val KEY_HOME_DYNAMIC_FULL_EFFECT = "home_dynamic_full_effect"
    private const val KEY_PRELOADING_ENABLED = "preloading_enabled"
    private const val KEY_LAUNCHER_ICON_DESIGN = "launcher_icon_design"
    private const val KEY_PRIVILEGE_MODE = "privilege_mode"
    const val PRIVILEGE_MODE_DEFAULT_ID = "disabled"
    private const val KEY_NON_HOME_BACKGROUND_ENABLED = "non_home_background_enabled"
    private const val KEY_NON_HOME_BACKGROUND_URI = "non_home_background_uri"
    private const val KEY_NON_HOME_BACKGROUND_OPACITY = "non_home_background_opacity"
    private const val KEY_NON_HOME_BACKGROUND_CONTENT_SCALE = "non_home_background_content_scale"
    private const val KEY_NON_HOME_BACKGROUND_ALIGNMENT = "non_home_background_alignment"
    private const val KEY_NON_HOME_BACKGROUND_PAGE_STYLE = "non_home_background_page_style"
    private const val KEY_NON_HOME_BACKGROUND_SCRIM = "non_home_background_scrim"
    private const val KEY_NON_HOME_BACKGROUND_DEPTH = "non_home_background_depth"
    private const val KEY_NON_HOME_BACKGROUND_SATURATION = "non_home_background_saturation"
    private const val KEY_SUPER_ISLAND_NOTIFICATION = "super_island_notification"
    private const val KEY_SUPER_ISLAND_FLOAT_BEHAVIOR = "super_island_float_behavior"
    private const val KEY_SUPER_ISLAND_FIRST_FLOAT = "super_island_first_float"
    private const val KEY_SUPER_ISLAND_AUTO_CLOSE = "super_island_auto_close"
    private const val KEY_SUPER_ISLAND_BYPASS_RESTRICTION = "super_island_bypass_restriction"
    private const val KEY_SUPER_ISLAND_RESTORE_DELAY_MS = "super_island_restore_delay_ms"
    private const val KEY_LOG_DEBUG = "log_debug"
    private const val KEY_LOG_LEVEL = "log_level"
    private const val KEY_TEXT_COPY_CAPABILITY_EXPANDED = "text_copy_capability_expanded"
    private const val KEY_CACHE_DIAGNOSTICS = "cache_diagnostics"
    private const val KEY_LIQUID_TOAST = "liquid_toast"
    private const val KEY_REDUCE_TOAST_INTERRUPTION = "reduce_toast_interruption"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_VISIBLE_BOTTOM_PAGES = "visible_bottom_pages"
    /**
     * How strongly the non-Home background image is allowed to show, derived rather than dialled in.
     *
     * Apple's Materials guidance handles legibility over a rich background with a *local* dimming layer:
     * "If the underlying content is bright, consider adding a dark dimming layer of 35% opacity. If the
     * underlying content is sufficiently dark... you don't need to apply a dimming layer." KeiOS's
     * equivalent is `appManagedBackgroundReadableStrengthCeiling`, which is the strongest composite that
     * still keeps primary text at WCAG AA against a worst-case image.
     *
     * So both ends of the slider come from that ceiling instead of taste:
     *  - [NON_HOME_BACKGROUND_OPACITY_DEFAULT] is the strongest wallpaper that needs **no** dimming at
     *    all — the ceiling itself (0.357 in dark theme, which binds).
     *  - [NON_HOME_BACKGROUND_OPACITY_MAX] is where Apple's 35% cap is reached: `ceiling / (1 - 0.35)`.
     *
     * The old 0.16 default long predates glass sampling the page. While the chrome refracted a flat
     * token, a stronger wallpaper only ever made the mismatch louder, so the number was pushed down to
     * hide it; now that the chrome carries the page composite, the wallpaper is what gives the material
     * something to bend, and 0.16 leaves it with almost nothing.
     */
    const val NON_HOME_BACKGROUND_OPACITY_DEFAULT = 0.35f
    const val NON_HOME_BACKGROUND_OPACITY_MIN = 0.06f
    const val NON_HOME_BACKGROUND_OPACITY_MAX = 0.55f
    private const val NON_HOME_BACKGROUND_SCRIM_DEFAULT = 0.00f
    private const val NON_HOME_BACKGROUND_SCRIM_MIN = 0.00f
    private const val NON_HOME_BACKGROUND_SCRIM_MAX = 0.40f
    private const val NON_HOME_BACKGROUND_SATURATION_DEFAULT = 1.00f
    private const val NON_HOME_BACKGROUND_SATURATION_MIN = 0.60f
    private const val NON_HOME_BACKGROUND_SATURATION_MAX = 1.20f
    const val SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS = 100
    const val SUPER_ISLAND_RESTORE_DELAY_MIN_MS = 50
    const val SUPER_ISLAND_RESTORE_DELAY_MAX_MS = 350
    private val DEFAULT_VISIBLE_BOTTOM_PAGE_NAMES = setOf("Os", "Mcp", "GitHub", "Ba")
    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }
    private val textCopyCapabilityExpandedState by lazy {
        MutableStateFlow(
            kv().decodeBool(KEY_TEXT_COPY_CAPABILITY_EXPANDED, false),
        )
    }

    private fun kv(): MMKV = store

    fun configureRuntimeDefaults(
        buildType: String,
        defaultLogLevelId: String,
    ) {
        UiPrefsRuntimeDefaults.configure(
            buildType = buildType,
            defaultLogLevel = AppLogLevel.fromStorageId(defaultLogLevelId),
        )
    }

    private fun buildTypeAwareLogDebugKey(): String = "${KEY_LOG_DEBUG}_${UiPrefsRuntimeDefaults.buildType}"

    private fun buildTypeAwareLogLevelKey(): String = "${KEY_LOG_LEVEL}_${UiPrefsRuntimeDefaults.buildType}"

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

    fun isSidebarNavigationPreferred(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_SIDEBAR_NAVIGATION, defaultValue)

    fun setSidebarNavigationPreferred(value: Boolean) {
        kv().encode(KEY_SIDEBAR_NAVIGATION, value)
    }

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

    /**
     * Storage id of the selected privileged backend.
     *
     * The id stays opaque here because the [os.kei.core.privilege.PrivilegeMode] enum lives in
     * core-system, which this module deliberately does not depend on.
     */
    fun getPrivilegeModeId(defaultValue: String = PRIVILEGE_MODE_DEFAULT_ID): String =
        kv().decodeString(KEY_PRIVILEGE_MODE, defaultValue).orEmpty().trim().ifBlank { defaultValue }

    fun setPrivilegeModeId(value: String) {
        kv().encode(KEY_PRIVILEGE_MODE, value.trim())
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

    fun getNonHomeBackgroundContentScale(
        defaultValue: NonHomeBackgroundContentScale = NonHomeBackgroundContentScale.Crop,
    ): NonHomeBackgroundContentScale =
        NonHomeBackgroundContentScale.fromStorageId(
            kv().decodeString(KEY_NON_HOME_BACKGROUND_CONTENT_SCALE, defaultValue.storageId),
        )

    fun setNonHomeBackgroundContentScale(value: NonHomeBackgroundContentScale) {
        kv().encode(KEY_NON_HOME_BACKGROUND_CONTENT_SCALE, value.storageId)
    }

    fun getNonHomeBackgroundAlignment(
        defaultValue: NonHomeBackgroundAlignment = NonHomeBackgroundAlignment.Center,
    ): NonHomeBackgroundAlignment =
        NonHomeBackgroundAlignment.fromStorageId(
            kv().decodeString(KEY_NON_HOME_BACKGROUND_ALIGNMENT, defaultValue.storageId),
        )

    fun setNonHomeBackgroundAlignment(value: NonHomeBackgroundAlignment) {
        kv().encode(KEY_NON_HOME_BACKGROUND_ALIGNMENT, value.storageId)
    }

    fun getNonHomeBackgroundPageStyle(
        defaultValue: NonHomeBackgroundPageStyle = NonHomeBackgroundPageStyle.Standard,
    ): NonHomeBackgroundPageStyle =
        NonHomeBackgroundPageStyle.fromStorageId(
            kv().decodeString(KEY_NON_HOME_BACKGROUND_PAGE_STYLE, defaultValue.storageId),
        )

    fun setNonHomeBackgroundPageStyle(value: NonHomeBackgroundPageStyle) {
        kv().encode(KEY_NON_HOME_BACKGROUND_PAGE_STYLE, value.storageId)
    }

    fun getNonHomeBackgroundScrim(defaultValue: Float = NON_HOME_BACKGROUND_SCRIM_DEFAULT): Float {
        val fallback =
            defaultValue.coerceIn(
                NON_HOME_BACKGROUND_SCRIM_MIN,
                NON_HOME_BACKGROUND_SCRIM_MAX,
            )
        return kv().decodeFloat(KEY_NON_HOME_BACKGROUND_SCRIM, fallback).coerceIn(
            NON_HOME_BACKGROUND_SCRIM_MIN,
            NON_HOME_BACKGROUND_SCRIM_MAX,
        )
    }

    fun setNonHomeBackgroundScrim(value: Float) {
        kv().encode(
            KEY_NON_HOME_BACKGROUND_SCRIM,
            value.coerceIn(NON_HOME_BACKGROUND_SCRIM_MIN, NON_HOME_BACKGROUND_SCRIM_MAX),
        )
    }

    fun isNonHomeBackgroundDepthEnabled(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_NON_HOME_BACKGROUND_DEPTH, defaultValue)

    fun setNonHomeBackgroundDepthEnabled(value: Boolean) {
        kv().encode(KEY_NON_HOME_BACKGROUND_DEPTH, value)
    }

    fun getNonHomeBackgroundSaturation(defaultValue: Float = NON_HOME_BACKGROUND_SATURATION_DEFAULT): Float {
        val fallback =
            defaultValue.coerceIn(
                NON_HOME_BACKGROUND_SATURATION_MIN,
                NON_HOME_BACKGROUND_SATURATION_MAX,
            )
        return kv().decodeFloat(KEY_NON_HOME_BACKGROUND_SATURATION, fallback).coerceIn(
            NON_HOME_BACKGROUND_SATURATION_MIN,
            NON_HOME_BACKGROUND_SATURATION_MAX,
        )
    }

    fun setNonHomeBackgroundSaturation(value: Float) {
        kv().encode(
            KEY_NON_HOME_BACKGROUND_SATURATION,
            value.coerceIn(NON_HOME_BACKGROUND_SATURATION_MIN, NON_HOME_BACKGROUND_SATURATION_MAX),
        )
    }

    fun isSuperIslandNotificationEnabled(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_SUPER_ISLAND_NOTIFICATION, defaultValue)

    fun setSuperIslandNotificationEnabled(value: Boolean) {
        kv().encode(KEY_SUPER_ISLAND_NOTIFICATION, value)
    }

    fun getSuperIslandFloatBehavior(
        defaultValue: SuperIslandFloatBehavior = SuperIslandFloatBehavior.StartAndFinish,
    ): SuperIslandFloatBehavior {
        val store = kv()
        if (store.containsKey(KEY_SUPER_ISLAND_FLOAT_BEHAVIOR)) {
            return SuperIslandFloatBehavior.fromStorageId(
                store.decodeString(KEY_SUPER_ISLAND_FLOAT_BEHAVIOR, defaultValue.storageId),
            )
        }
        if (store.containsKey(KEY_SUPER_ISLAND_FIRST_FLOAT)) {
            return if (store.decodeBool(KEY_SUPER_ISLAND_FIRST_FLOAT, true)) {
                SuperIslandFloatBehavior.StartAndFinish
            } else {
                SuperIslandFloatBehavior.SummaryOnly
            }
        }
        return defaultValue
    }

    fun setSuperIslandFloatBehavior(value: SuperIslandFloatBehavior) {
        kv().encode(KEY_SUPER_ISLAND_FLOAT_BEHAVIOR, value.storageId)
        kv().encode(KEY_SUPER_ISLAND_FIRST_FLOAT, value.firstFloatEnabled)
    }

    fun getSuperIslandAutoClose(): SuperIslandAutoClose =
        SuperIslandAutoClose.fromStorageId(kv().decodeString(KEY_SUPER_ISLAND_AUTO_CLOSE, null))

    fun setSuperIslandAutoClose(value: SuperIslandAutoClose) {
        kv().encode(KEY_SUPER_ISLAND_AUTO_CLOSE, value.storageId)
    }

    fun isSuperIslandFirstFloatEnabled(defaultValue: Boolean = true): Boolean =
        getSuperIslandFloatBehavior(
            defaultValue =
                if (defaultValue) {
                    SuperIslandFloatBehavior.StartAndFinish
                } else {
                    SuperIslandFloatBehavior.SummaryOnly
                },
        ).firstFloatEnabled

    fun isSuperIslandFinishFloatEnabled(defaultValue: Boolean = true): Boolean =
        getSuperIslandFloatBehavior(
            defaultValue =
                if (defaultValue) {
                    SuperIslandFloatBehavior.StartAndFinish
                } else {
                    SuperIslandFloatBehavior.StartOnly
                },
        ).finishFloatEnabled

    fun setSuperIslandFirstFloatEnabled(value: Boolean) {
        setSuperIslandFloatBehavior(
            if (value) {
                SuperIslandFloatBehavior.StartAndFinish
            } else {
                SuperIslandFloatBehavior.SummaryOnly
            },
        )
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

    fun getLogLevel(defaultValue: AppLogLevel = UiPrefsRuntimeDefaults.defaultLogLevel): AppLogLevel {
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
        defaultValue: Boolean = UiPrefsRuntimeDefaults.defaultLogLevel == AppLogLevel.Debug,
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

    fun isLiquidToastEnabled(defaultValue: Boolean = true): Boolean = kv().decodeBool(KEY_LIQUID_TOAST, defaultValue)

    fun setLiquidToastEnabled(value: Boolean) {
        kv().encode(KEY_LIQUID_TOAST, value)
    }

    fun isReduceToastInterruptionEnabled(defaultValue: Boolean = false): Boolean =
        kv().decodeBool(KEY_REDUCE_TOAST_INTERRUPTION, defaultValue)

    fun setReduceToastInterruptionEnabled(value: Boolean) {
        kv().encode(KEY_REDUCE_TOAST_INTERRUPTION, value)
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
            liquidSwitchEnabled = true,
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            searchAutoFocusEnabled = true,
            gripAwareFloatingDockEnabled = false,
            sidebarNavigationPreferred = false,
            homeIconHdrEnabled = false,
            homeDynamicFullEffectEnabled = true,
            preloadingEnabled = true,
            launcherIconDesign = LauncherIconDesign.Android,
            privilegeModeId = PRIVILEGE_MODE_DEFAULT_ID,
            nonHomeBackgroundEnabled = false,
            nonHomeBackgroundUri = "",
            nonHomeBackgroundOpacity = NON_HOME_BACKGROUND_OPACITY_DEFAULT,
            nonHomeBackgroundContentScale = NonHomeBackgroundContentScale.Crop,
            nonHomeBackgroundAlignment = NonHomeBackgroundAlignment.Center,
            nonHomeBackgroundPageStyle = NonHomeBackgroundPageStyle.Standard,
            nonHomeBackgroundScrim = NON_HOME_BACKGROUND_SCRIM_DEFAULT,
            nonHomeBackgroundDepthEnabled = false,
            nonHomeBackgroundSaturation = NON_HOME_BACKGROUND_SATURATION_DEFAULT,
            superIslandNotificationEnabled = false,
            superIslandFloatBehavior = SuperIslandFloatBehavior.StartAndFinish,
            superIslandAutoClose = SuperIslandAutoClose.Never,
            superIslandFirstFloatEnabled = true,
            superIslandBypassRestrictionEnabled = false,
            superIslandRestoreDelayMs = SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS,
            logLevel = UiPrefsRuntimeDefaults.defaultLogLevel,
            textCopyCapabilityExpanded = false,
            cacheDiagnosticsEnabled = true,
            liquidToastEnabled = true,
            reduceToastInterruptionEnabled = false,
            appThemeMode = appThemeMode,
            visibleBottomPageNames = DEFAULT_VISIBLE_BOTTOM_PAGE_NAMES,
        )

    fun loadSnapshot(): UiPrefsSnapshot {
        val store = kv()
        val superIslandFloatBehavior = getSuperIslandFloatBehavior()
        return UiPrefsSnapshot(
            liquidSwitchEnabled = store.decodeBool(KEY_LIQUID_SWITCH, true),
            transitionAnimationsEnabled = store.decodeBool(KEY_TRANSITION_ANIMATIONS, true),
            predictiveBackAnimationsEnabled = store.decodeBool(KEY_PREDICTIVE_BACK_ANIMATIONS, true),
            searchAutoFocusEnabled = store.decodeBool(KEY_SEARCH_AUTO_FOCUS, true),
            gripAwareFloatingDockEnabled = store.decodeBool(KEY_GRIP_AWARE_FLOATING_DOCK, false),
            sidebarNavigationPreferred = store.decodeBool(KEY_SIDEBAR_NAVIGATION, false),
            homeIconHdrEnabled = store.decodeBool(KEY_HOME_ICON_HDR, false),
            homeDynamicFullEffectEnabled = store.decodeBool(KEY_HOME_DYNAMIC_FULL_EFFECT, true),
            preloadingEnabled = store.decodeBool(KEY_PRELOADING_ENABLED, true),
            launcherIconDesign = getLauncherIconDesign(),
            privilegeModeId = getPrivilegeModeId(),
            nonHomeBackgroundEnabled = store.decodeBool(KEY_NON_HOME_BACKGROUND_ENABLED, false),
            nonHomeBackgroundUri = store.decodeString(KEY_NON_HOME_BACKGROUND_URI, "").orEmpty().trim(),
            nonHomeBackgroundOpacity =
                store
                    .decodeFloat(
                        KEY_NON_HOME_BACKGROUND_OPACITY,
                        NON_HOME_BACKGROUND_OPACITY_DEFAULT,
                    ).coerceIn(NON_HOME_BACKGROUND_OPACITY_MIN, NON_HOME_BACKGROUND_OPACITY_MAX),
            nonHomeBackgroundContentScale = getNonHomeBackgroundContentScale(),
            nonHomeBackgroundAlignment = getNonHomeBackgroundAlignment(),
            nonHomeBackgroundPageStyle = getNonHomeBackgroundPageStyle(),
            nonHomeBackgroundScrim = getNonHomeBackgroundScrim(),
            nonHomeBackgroundDepthEnabled = isNonHomeBackgroundDepthEnabled(),
            nonHomeBackgroundSaturation = getNonHomeBackgroundSaturation(),
            superIslandNotificationEnabled = store.decodeBool(KEY_SUPER_ISLAND_NOTIFICATION, false),
            superIslandFloatBehavior = superIslandFloatBehavior,
            superIslandAutoClose = getSuperIslandAutoClose(),
            superIslandFirstFloatEnabled = superIslandFloatBehavior.firstFloatEnabled,
            superIslandBypassRestrictionEnabled = store.decodeBool(KEY_SUPER_ISLAND_BYPASS_RESTRICTION, false),
            superIslandRestoreDelayMs = getSuperIslandRestoreDelayMs(),
            logLevel = getLogLevel(),
            textCopyCapabilityExpanded = store.decodeBool(KEY_TEXT_COPY_CAPABILITY_EXPANDED, false),
            cacheDiagnosticsEnabled = store.decodeBool(KEY_CACHE_DIAGNOSTICS, true),
            liquidToastEnabled = store.decodeBool(KEY_LIQUID_TOAST, true),
            reduceToastInterruptionEnabled = store.decodeBool(KEY_REDUCE_TOAST_INTERRUPTION, false),
            appThemeMode = getAppThemeMode(),
            visibleBottomPageNames = loadVisibleBottomPageNames(),
        )
    }
}
