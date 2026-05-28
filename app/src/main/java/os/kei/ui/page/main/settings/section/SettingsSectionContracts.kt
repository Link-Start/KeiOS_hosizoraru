package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntRect
import os.kei.core.prefs.LauncherIconDesign
import os.kei.core.prefs.AppThemeMode
import os.kei.ui.page.main.settings.support.SettingsAppListAccessMode
import os.kei.ui.page.main.settings.support.SettingsOemAutoStartState

@Immutable
internal data class SettingsPermissionKeepAliveSectionState(
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val notificationSettingsActionAvailable: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val batteryOptimizationActionAvailable: Boolean,
    val oemAutoStartState: SettingsOemAutoStartState,
    val oemAutoStartVendorLabel: String,
    val oemAutoStartActionAvailable: Boolean,
    val appListAccessMode: SettingsAppListAccessMode,
    val appListDetectedCount: Int,
    val appListSettingsActionAvailable: Boolean,
    val shizukuGranted: Boolean,
    val shizukuStatusText: String,
)

internal data class SettingsPermissionKeepAliveSectionActions(
    val onRequestNotificationPermission: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onOpenBatteryOptimizationSettings: () -> Unit,
    val onOpenOemAutoStartSettings: () -> Unit,
    val onOpenAppListPermissionSettings: () -> Unit,
    val onCheckOrRequestShizuku: () -> Unit,
)

@Immutable
internal data class SettingsVisualSectionState(
    val preloadingEnabled: Boolean,
    val launcherIconDesign: LauncherIconDesign,
    val homeIconHdrEnabled: Boolean,
    val homeDynamicFullEffectEnabled: Boolean,
    val appThemeMode: AppThemeMode,
    val appLanguageActionAvailable: Boolean,
    val showThemeModePopup: Boolean,
    val themePopupAnchorBounds: IntRect?,
    val showLauncherIconDesignPopup: Boolean,
    val launcherIconDesignPopupAnchorBounds: IntRect?,
)

internal data class SettingsVisualSectionActions(
    val onPreloadingEnabledChanged: (Boolean) -> Unit,
    val onLauncherIconDesignChanged: (LauncherIconDesign) -> Unit,
    val onHomeIconHdrChanged: (Boolean) -> Unit,
    val onHomeDynamicFullEffectChanged: (Boolean) -> Unit,
    val onAppThemeModeChanged: (AppThemeMode) -> Unit,
    val onOpenAppLanguageSettings: () -> Unit,
    val onShowThemeModePopupChange: (Boolean) -> Unit,
    val onThemePopupAnchorBoundsChange: (IntRect?) -> Unit,
    val onShowLauncherIconDesignPopupChange: (Boolean) -> Unit,
    val onLauncherIconDesignPopupAnchorBoundsChange: (IntRect?) -> Unit,
)

@Immutable
internal data class SettingsAnimationSectionState(
    val transitionAnimationsEnabled: Boolean,
    val predictiveBackAnimationsEnabled: Boolean,
)

internal data class SettingsAnimationSectionActions(
    val onTransitionAnimationsChanged: (Boolean) -> Unit,
    val onPredictiveBackAnimationsChanged: (Boolean) -> Unit,
)

@Immutable
internal data class SettingsComponentEffectsSectionState(
    val liquidActionBarLayeredStyleEnabled: Boolean,
    val liquidSwitchEnabled: Boolean,
    val liquidToastEnabled: Boolean,
    val reduceToastInterruptionEnabled: Boolean,
    val liquidSheetEnabled: Boolean,
    val liquidDialogEnabled: Boolean,
    val liquidBottomBarEnabled: Boolean,
    val searchAutoFocusEnabled: Boolean,
    val gripAwareFloatingDockEnabled: Boolean,
)

internal data class SettingsComponentEffectsSectionActions(
    val onLiquidActionBarLayeredStyleChanged: (Boolean) -> Unit,
    val onLiquidSwitchChanged: (Boolean) -> Unit,
    val onLiquidToastChanged: (Boolean) -> Unit,
    val onReduceToastInterruptionChanged: (Boolean) -> Unit,
    val onLiquidSheetChanged: (Boolean) -> Unit,
    val onLiquidDialogChanged: (Boolean) -> Unit,
    val onLiquidBottomBarChanged: (Boolean) -> Unit,
    val onSearchAutoFocusChanged: (Boolean) -> Unit,
    val onGripAwareFloatingDockChanged: (Boolean) -> Unit,
)

@Immutable
internal data class SettingsNotifySectionState(
    val superIslandNotificationEnabled: Boolean,
    val superIslandBypassRestrictionEnabled: Boolean,
    val superIslandRestoreDelayMs: Int,
)

internal data class SettingsNotifySectionActions(
    val onSuperIslandNotificationChanged: (Boolean) -> Unit,
    val onSuperIslandBypassRestrictionChanged: (Boolean) -> Unit,
    val onSuperIslandRestoreDelayMsChanged: (Int) -> Unit,
)

@Immutable
internal data class SettingsCopySectionState(
    val textCopyCapabilityExpanded: Boolean,
)

internal data class SettingsCopySectionActions(
    val onTextCopyCapabilityExpandedChanged: (Boolean) -> Unit,
)
