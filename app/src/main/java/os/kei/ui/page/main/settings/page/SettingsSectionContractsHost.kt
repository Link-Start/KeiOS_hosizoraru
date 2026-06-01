package os.kei.ui.page.main.settings.page

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import os.kei.R
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.LauncherIconDesign
import os.kei.ui.page.main.settings.state.SettingsPageUiState
import os.kei.ui.page.main.settings.state.SettingsSectionContractBundle
import os.kei.ui.page.main.settings.state.rememberSettingsSectionContractBundle
import os.kei.ui.page.main.settings.support.SettingsAppLanguageController
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationSnapshot
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveController
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveSnapshot

@Composable
internal fun rememberSettingsPageSectionContracts(
    context: Context,
    pageUiState: SettingsPageUiState,
    permissionKeepAliveState: SettingsPermissionKeepAliveSnapshot,
    batteryOptimizationState: SettingsBatteryOptimizationSnapshot,
    permissionKeepAliveController: SettingsPermissionKeepAliveController,
    batteryOptimizationController: SettingsBatteryOptimizationController,
    appLanguageController: SettingsAppLanguageController,
    notificationPermissionGranted: Boolean,
    preloadingEnabled: Boolean,
    onPreloadingEnabledChanged: (Boolean) -> Unit,
    launcherIconDesign: LauncherIconDesign,
    onLauncherIconDesignChanged: (LauncherIconDesign) -> Unit,
    homeIconHdrEnabled: Boolean,
    onHomeIconHdrChanged: (Boolean) -> Unit,
    homeDynamicFullEffectEnabled: Boolean,
    onHomeDynamicFullEffectChanged: (Boolean) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChanged: (AppThemeMode) -> Unit,
    transitionAnimationsEnabled: Boolean,
    onTransitionAnimationsChanged: (Boolean) -> Unit,
    predictiveBackAnimationsEnabled: Boolean,
    onPredictiveBackAnimationsChanged: (Boolean) -> Unit,
    searchAutoFocusEnabled: Boolean,
    onSearchAutoFocusChanged: (Boolean) -> Unit,
    liquidActionBarLayeredStyleEnabled: Boolean,
    onLiquidActionBarLayeredStyleChanged: (Boolean) -> Unit,
    liquidSwitchEnabled: Boolean,
    onLiquidSwitchChanged: (Boolean) -> Unit,
    liquidToastEnabled: Boolean,
    onLiquidToastChanged: (Boolean) -> Unit,
    reduceToastInterruptionEnabled: Boolean,
    onReduceToastInterruptionChanged: (Boolean) -> Unit,
    liquidSheetEnabled: Boolean,
    onLiquidSheetChanged: (Boolean) -> Unit,
    liquidDialogEnabled: Boolean,
    onLiquidDialogChanged: (Boolean) -> Unit,
    gripAwareFloatingDockEnabled: Boolean,
    onGripAwareFloatingDockChanged: (Boolean) -> Unit,
    superIslandNotificationEnabled: Boolean,
    onSuperIslandNotificationChanged: (Boolean) -> Unit,
    superIslandBypassRestrictionEnabled: Boolean,
    onSuperIslandBypassRestrictionChanged: (Boolean) -> Unit,
    superIslandRestoreDelayMs: Int,
    onSuperIslandRestoreDelayMsChanged: (Int) -> Unit,
    textCopyCapabilityExpanded: Boolean,
    onTextCopyCapabilityExpandedChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onCheckOrRequestShizuku: () -> Unit,
): SettingsSectionContractBundle =
    rememberSettingsSectionContractBundle(
        notificationPermissionGranted = notificationPermissionGranted,
        notificationsEnabled = permissionKeepAliveState.notificationsEnabled,
        notificationSettingsActionAvailable = permissionKeepAliveState.notificationSettingsActionAvailable,
        preloadingEnabled = preloadingEnabled,
        launcherIconDesign = launcherIconDesign,
        homeIconHdrEnabled = homeIconHdrEnabled,
        homeDynamicFullEffectEnabled = homeDynamicFullEffectEnabled,
        appThemeMode = appThemeMode,
        appLanguageActionAvailable = appLanguageController.actionAvailable,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
        searchAutoFocusEnabled = searchAutoFocusEnabled,
        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
        liquidSwitchEnabled = liquidSwitchEnabled,
        liquidToastEnabled = liquidToastEnabled,
        reduceToastInterruptionEnabled = reduceToastInterruptionEnabled,
        liquidSheetEnabled = liquidSheetEnabled,
        liquidDialogEnabled = liquidDialogEnabled,
        gripAwareFloatingDockEnabled = gripAwareFloatingDockEnabled,
        superIslandNotificationEnabled = superIslandNotificationEnabled,
        superIslandBypassRestrictionEnabled = superIslandBypassRestrictionEnabled,
        superIslandRestoreDelayMs = superIslandRestoreDelayMs,
        ignoringBatteryOptimizations = batteryOptimizationState.ignoringBatteryOptimizations,
        batteryOptimizationActionAvailable = batteryOptimizationState.requestActionAvailable,
        oemAutoStartState = permissionKeepAliveState.oemAutoStartState,
        oemAutoStartVendorLabel = permissionKeepAliveState.oemAutoStartVendorLabel,
        oemAutoStartActionAvailable = permissionKeepAliveState.oemAutoStartActionAvailable,
        appListAccessMode = permissionKeepAliveState.appListAccessMode,
        appListDetectedCount = permissionKeepAliveState.appListDetectedCount,
        appListSettingsActionAvailable = permissionKeepAliveState.appListSettingsActionAvailable,
        shizukuGranted = permissionKeepAliveState.shizukuGranted,
        shizukuStatusText = permissionKeepAliveState.shizukuStatusText,
        textCopyCapabilityExpanded = textCopyCapabilityExpanded,
        pageUiState = pageUiState,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onOpenNotificationSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = permissionKeepAliveController.openNotificationSettings(),
                messageRes = R.string.settings_notification_permission_toast_open_failed,
            )
        },
        onPreloadingEnabledChanged = onPreloadingEnabledChanged,
        onLauncherIconDesignChanged = onLauncherIconDesignChanged,
        onHomeIconHdrChanged = onHomeIconHdrChanged,
        onHomeDynamicFullEffectChanged = onHomeDynamicFullEffectChanged,
        onAppThemeModeChanged = onAppThemeModeChanged,
        onOpenAppLanguageSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = appLanguageController.openAppLanguageSettings(),
                messageRes = R.string.settings_app_language_toast_open_failed,
            )
        },
        onTransitionAnimationsChanged = onTransitionAnimationsChanged,
        onPredictiveBackAnimationsChanged = onPredictiveBackAnimationsChanged,
        onSearchAutoFocusChanged = onSearchAutoFocusChanged,
        onLiquidActionBarLayeredStyleChanged = onLiquidActionBarLayeredStyleChanged,
        onLiquidSwitchChanged = onLiquidSwitchChanged,
        onLiquidToastChanged = onLiquidToastChanged,
        onReduceToastInterruptionChanged = onReduceToastInterruptionChanged,
        onLiquidSheetChanged = onLiquidSheetChanged,
        onLiquidDialogChanged = onLiquidDialogChanged,
        onGripAwareFloatingDockChanged = onGripAwareFloatingDockChanged,
        onSuperIslandNotificationChanged = onSuperIslandNotificationChanged,
        onSuperIslandBypassRestrictionChanged = onSuperIslandBypassRestrictionChanged,
        onSuperIslandRestoreDelayMsChanged = onSuperIslandRestoreDelayMsChanged,
        onOpenBatteryOptimizationSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = batteryOptimizationController.openBatteryOptimizationSettings(batteryOptimizationState),
                messageRes = R.string.settings_battery_optimization_toast_open_failed,
            )
        },
        onOpenOemAutoStartSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = permissionKeepAliveController.openOemAutoStartSettings(),
                messageRes = R.string.settings_oem_autostart_toast_open_failed,
            )
        },
        onOpenAppListPermissionSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = permissionKeepAliveController.openAppListPermissionSettings(),
                messageRes = R.string.settings_app_list_access_toast_open_failed,
            )
        },
        onCheckOrRequestShizuku = onCheckOrRequestShizuku,
        onTextCopyCapabilityExpandedChanged = onTextCopyCapabilityExpandedChanged,
    )

private fun showSettingsToastIfClosed(
    context: Context,
    opened: Boolean,
    messageRes: Int,
) {
    if (opened) return
    Toast
        .makeText(
            context,
            context.getString(messageRes),
            Toast.LENGTH_SHORT,
        ).show()
}
