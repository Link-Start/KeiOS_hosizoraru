package os.kei.ui.page.main.settings.page

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import os.kei.R
import os.kei.core.prefs.AppThemeMode
import os.kei.ui.page.main.settings.state.SettingsPageUiState
import os.kei.ui.page.main.settings.state.SettingsSectionContractBundle
import os.kei.ui.page.main.settings.state.rememberSettingsSectionContractBundle
import os.kei.ui.page.main.settings.support.SettingsAppLanguageController
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveController

@Composable
internal fun rememberSettingsPageSectionContracts(
    context: Context,
    pageUiState: SettingsPageUiState,
    permissionKeepAliveController: SettingsPermissionKeepAliveController,
    batteryOptimizationController: SettingsBatteryOptimizationController,
    appLanguageController: SettingsAppLanguageController,
    notificationPermissionGranted: Boolean,
    preloadingEnabled: Boolean,
    onPreloadingEnabledChanged: (Boolean) -> Unit,
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
    liquidBottomBarEnabled: Boolean,
    onLiquidBottomBarChanged: (Boolean) -> Unit,
    miuixMainNavigationEnabled: Boolean,
    onMiuixMainNavigationChanged: (Boolean) -> Unit,
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
    onCheckOrRequestShizuku: () -> Unit
): SettingsSectionContractBundle {
    return rememberSettingsSectionContractBundle(
        notificationPermissionGranted = notificationPermissionGranted,
        notificationsEnabled = permissionKeepAliveController.notificationsEnabled,
        notificationSettingsActionAvailable = permissionKeepAliveController.notificationSettingsActionAvailable,
        preloadingEnabled = preloadingEnabled,
        homeIconHdrEnabled = homeIconHdrEnabled,
        homeDynamicFullEffectEnabled = homeDynamicFullEffectEnabled,
        appThemeMode = appThemeMode,
        appLanguageActionAvailable = appLanguageController.actionAvailable,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
        searchAutoFocusEnabled = searchAutoFocusEnabled,
        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
        liquidSwitchEnabled = liquidSwitchEnabled,
        liquidBottomBarEnabled = liquidBottomBarEnabled,
        miuixMainNavigationEnabled = miuixMainNavigationEnabled,
        gripAwareFloatingDockEnabled = gripAwareFloatingDockEnabled,
        superIslandNotificationEnabled = superIslandNotificationEnabled,
        superIslandBypassRestrictionEnabled = superIslandBypassRestrictionEnabled,
        superIslandRestoreDelayMs = superIslandRestoreDelayMs,
        ignoringBatteryOptimizations = batteryOptimizationController.ignoringBatteryOptimizations,
        batteryOptimizationActionAvailable = batteryOptimizationController.requestActionAvailable,
        oemAutoStartState = permissionKeepAliveController.oemAutoStartState,
        oemAutoStartVendorLabel = permissionKeepAliveController.oemAutoStartVendorLabel,
        oemAutoStartActionAvailable = permissionKeepAliveController.oemAutoStartActionAvailable,
        appListAccessMode = permissionKeepAliveController.appListAccessMode,
        appListDetectedCount = permissionKeepAliveController.appListDetectedCount,
        appListSettingsActionAvailable = permissionKeepAliveController.appListSettingsActionAvailable,
        shizukuGranted = permissionKeepAliveController.shizukuGranted,
        shizukuStatusText = permissionKeepAliveController.shizukuStatusText,
        textCopyCapabilityExpanded = textCopyCapabilityExpanded,
        pageUiState = pageUiState,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onOpenNotificationSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = permissionKeepAliveController.openNotificationSettings(),
                messageRes = R.string.settings_notification_permission_toast_open_failed
            )
        },
        onPreloadingEnabledChanged = onPreloadingEnabledChanged,
        onHomeIconHdrChanged = onHomeIconHdrChanged,
        onHomeDynamicFullEffectChanged = onHomeDynamicFullEffectChanged,
        onAppThemeModeChanged = onAppThemeModeChanged,
        onOpenAppLanguageSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = appLanguageController.openAppLanguageSettings(),
                messageRes = R.string.settings_app_language_toast_open_failed
            )
        },
        onTransitionAnimationsChanged = onTransitionAnimationsChanged,
        onPredictiveBackAnimationsChanged = onPredictiveBackAnimationsChanged,
        onSearchAutoFocusChanged = onSearchAutoFocusChanged,
        onLiquidActionBarLayeredStyleChanged = onLiquidActionBarLayeredStyleChanged,
        onLiquidSwitchChanged = onLiquidSwitchChanged,
        onLiquidBottomBarChanged = onLiquidBottomBarChanged,
        onMiuixMainNavigationChanged = onMiuixMainNavigationChanged,
        onGripAwareFloatingDockChanged = onGripAwareFloatingDockChanged,
        onSuperIslandNotificationChanged = onSuperIslandNotificationChanged,
        onSuperIslandBypassRestrictionChanged = onSuperIslandBypassRestrictionChanged,
        onSuperIslandRestoreDelayMsChanged = onSuperIslandRestoreDelayMsChanged,
        onOpenBatteryOptimizationSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = batteryOptimizationController.openBatteryOptimizationSettings(),
                messageRes = R.string.settings_battery_optimization_toast_open_failed
            )
        },
        onOpenOemAutoStartSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = permissionKeepAliveController.openOemAutoStartSettings(),
                messageRes = R.string.settings_oem_autostart_toast_open_failed
            )
        },
        onOpenAppListPermissionSettings = {
            showSettingsToastIfClosed(
                context = context,
                opened = permissionKeepAliveController.openAppListPermissionSettings(),
                messageRes = R.string.settings_app_list_access_toast_open_failed
            )
        },
        onCheckOrRequestShizuku = onCheckOrRequestShizuku,
        onTextCopyCapabilityExpandedChanged = onTextCopyCapabilityExpandedChanged
    )
}

private fun showSettingsToastIfClosed(
    context: Context,
    opened: Boolean,
    messageRes: Int
) {
    if (opened) return
    Toast.makeText(
        context,
        context.getString(messageRes),
        Toast.LENGTH_SHORT
    ).show()
}
