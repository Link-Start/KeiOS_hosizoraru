package os.kei.ui.page.main.os.shortcut

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

private const val SETTINGS_PACKAGE = "com.android.settings"
private const val GMS_PACKAGE = "com.google.android.gms"

@Composable
internal fun rememberBuiltInActivityShortcutCards(
    defaults: OsGoogleSystemServiceConfig,
    defaultIntentFlags: String,
): List<OsActivityShortcutCard> {
    val androidSettingsAppName = stringResource(R.string.os_activity_builtin_android_settings_app_name)
    val crossDeviceAppName = stringResource(R.string.os_activity_builtin_cross_device_app_name)

    val googleSettingsTitle = stringResource(R.string.os_activity_builtin_google_settings_title)
    val googleSettingsSubtitle = stringResource(R.string.os_activity_builtin_google_settings_subtitle)
    val googleSettingsAppName = stringResource(R.string.os_activity_builtin_google_settings_app_name)
    val googleSettingsPackage = stringResource(R.string.os_activity_builtin_google_settings_package)
    val googleSettingsClass = stringResource(R.string.os_activity_builtin_google_settings_class)

    val extraDimTitle = stringResource(R.string.os_activity_builtin_extra_dim_title)
    val extraDimSubtitle = stringResource(R.string.os_activity_builtin_extra_dim_subtitle)
    val appBatteryTitle = stringResource(R.string.os_activity_builtin_app_battery_usage_title)
    val appBatterySubtitle = stringResource(R.string.os_activity_builtin_app_battery_usage_subtitle)
    val notificationTitle = stringResource(R.string.os_activity_builtin_notification_settings_title)
    val notificationSubtitle = stringResource(R.string.os_activity_builtin_notification_settings_subtitle)
    val appManagementTitle = stringResource(R.string.os_activity_builtin_app_management_title)
    val appManagementSubtitle = stringResource(R.string.os_activity_builtin_app_management_subtitle)
    val appMemoryTitle = stringResource(R.string.os_activity_builtin_app_memory_usage_title)
    val appMemorySubtitle = stringResource(R.string.os_activity_builtin_app_memory_usage_subtitle)
    val languageSettingsTitle = stringResource(R.string.os_activity_builtin_language_settings_title)
    val languageSettingsSubtitle = stringResource(R.string.os_activity_builtin_language_settings_subtitle)
    val fullScreenDisplayTitle = stringResource(R.string.os_activity_builtin_fullscreen_display_title)
    val fullScreenDisplaySubtitle = stringResource(R.string.os_activity_builtin_fullscreen_display_subtitle)
    val crossDeviceTitle = stringResource(R.string.os_activity_builtin_cross_device_services_title)
    val crossDeviceSubtitle = stringResource(R.string.os_activity_builtin_cross_device_services_subtitle)

    return remember(
        defaults,
        defaultIntentFlags,
        androidSettingsAppName,
        crossDeviceAppName,
        googleSettingsTitle,
        googleSettingsSubtitle,
        googleSettingsAppName,
        googleSettingsPackage,
        googleSettingsClass,
        extraDimTitle,
        extraDimSubtitle,
        appBatteryTitle,
        appBatterySubtitle,
        notificationTitle,
        notificationSubtitle,
        appManagementTitle,
        appManagementSubtitle,
        appMemoryTitle,
        appMemorySubtitle,
        languageSettingsTitle,
        languageSettingsSubtitle,
        fullScreenDisplayTitle,
        fullScreenDisplaySubtitle,
        crossDeviceTitle,
        crossDeviceSubtitle,
    ) {
        listOf(
            builtInActivityShortcutCard(
                id = BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID,
                title = googleSettingsTitle,
                subtitle = googleSettingsSubtitle,
                appName = googleSettingsAppName,
                packageName = googleSettingsPackage,
                className = googleSettingsClass,
                intentAction = Intent.ACTION_VIEW,
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_EXTRA_DIM_CARD_ID,
                title = extraDimTitle,
                subtitle = extraDimSubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.Settings\$ReduceBrightColorsSettingsActivity",
                intentAction = "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_APP_BATTERY_USAGE_CARD_ID,
                title = appBatteryTitle,
                subtitle = appBatterySubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.Settings\$AppBatteryUsageActivity",
                intentAction = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_NOTIFICATION_SETTINGS_CARD_ID,
                title = notificationTitle,
                subtitle = notificationSubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.Settings\$ConfigureNotificationSettingsActivity",
                intentAction = "android.settings.NOTIFICATION_SETTINGS",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_APP_MANAGEMENT_CARD_ID,
                title = appManagementTitle,
                subtitle = appManagementSubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.Settings\$ManageApplicationsActivity",
                intentAction = "android.settings.MANAGE_APPLICATIONS_SETTINGS",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_APP_MEMORY_USAGE_CARD_ID,
                title = appMemoryTitle,
                subtitle = appMemorySubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.Settings\$AppMemoryUsageActivity",
                intentAction = "android.settings.APP_MEMORY_USAGE",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_LANGUAGE_SETTINGS_CARD_ID,
                title = languageSettingsTitle,
                subtitle = languageSettingsSubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.LanguageSettings",
                intentAction = "android.settings.LANGUAGE_SETTINGS",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_FULLSCREEN_DISPLAY_SETTINGS_CARD_ID,
                title = fullScreenDisplayTitle,
                subtitle = fullScreenDisplaySubtitle,
                appName = androidSettingsAppName,
                packageName = SETTINGS_PACKAGE,
                className = "com.android.settings.SubSettings",
                intentAction = Intent.ACTION_MAIN,
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
                intentExtras =
                    listOf(
                        ShortcutIntentExtra(
                            key = "settings:show_fragment_title",
                            type = ShortcutIntentExtraType.String,
                            value = "全面屏设置",
                        ),
                        ShortcutIntentExtra(
                            key = ":android:no_headers",
                            type = ShortcutIntentExtraType.Boolean,
                            value = "true",
                        ),
                        ShortcutIntentExtra(
                            key = "innerApp",
                            type = ShortcutIntentExtraType.Boolean,
                            value = "false",
                        ),
                        ShortcutIntentExtra(
                            key = "fromActivity",
                            type = ShortcutIntentExtraType.Boolean,
                            value = "false",
                        ),
                        ShortcutIntentExtra(
                            key = ":settings:show_fragment",
                            type = ShortcutIntentExtraType.String,
                            value = "com.android.settings.FullScreenDisplaySettings",
                        ),
                    ),
            ),
            builtInActivityShortcutCard(
                id = BUILTIN_CROSS_DEVICE_SERVICES_CARD_ID,
                title = crossDeviceTitle,
                subtitle = crossDeviceSubtitle,
                appName = crossDeviceAppName,
                packageName = GMS_PACKAGE,
                className = "com.google.android.gms.multidevice.ui.link.LinkDevicesSettingsActivity",
                intentAction = "com.google.android.gms.multidevice.ACTION_LINK_DEVICES",
                defaultIntentFlags = defaultIntentFlags,
                defaults = defaults,
            ),
        )
    }
}

internal fun builtInActivityShortcutCard(
    id: String,
    title: String,
    subtitle: String,
    appName: String,
    packageName: String,
    className: String,
    intentAction: String,
    intentUriData: String = "",
    defaultIntentFlags: String,
    defaults: OsGoogleSystemServiceConfig,
    intentExtras: List<ShortcutIntentExtra> = emptyList(),
): OsActivityShortcutCard =
    OsActivityShortcutCard(
        id = id,
        visible = true,
        isBuiltInSample = true,
        config =
            OsGoogleSystemServiceConfig(
                title = title,
                subtitle = subtitle,
                appName = appName,
                packageName = packageName,
                className = className,
                intentAction = intentAction,
                intentFlags = defaultIntentFlags,
                intentUriData = intentUriData,
                intentExtras = intentExtras,
            ).normalized(defaults),
    )
