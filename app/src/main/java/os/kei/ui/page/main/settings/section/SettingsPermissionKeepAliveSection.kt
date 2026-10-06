@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.appLucideBellIcon
import os.kei.ui.page.main.os.appLucideLockIcon
import os.kei.ui.page.main.settings.state.SettingsCardExpansionId
import os.kei.ui.page.main.settings.support.SettingsAppListAccessMode
import os.kei.ui.page.main.settings.support.SettingsAppStandbyBucketState
import os.kei.ui.page.main.settings.support.SettingsButtonActionItem
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsOemAutoStartState
import os.kei.ui.page.main.settings.support.SettingsValueItem
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import os.kei.core.privilege.PrivilegeMode
import os.kei.ui.page.main.settings.support.SettingsPickerItem
import os.kei.ui.page.main.widget.glass.AppDropdownSelector

@Composable
internal fun SettingsPermissionKeepAliveSection(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
    isCardExpanded: (SettingsCardExpansionId) -> Boolean,
    onCardExpandedChange: (SettingsCardExpansionId, Boolean) -> Unit,
    onlyCardId: SettingsCardExpansionId? = null,
) {
    val permissionPresentation = derivePermissionPresentation(state)
    val keepAlivePresentation = deriveKeepAlivePresentation(state)
    val accessibilityGuardPresentation = deriveAccessibilityGuardPresentation(state.accessibilityGuardState)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.sectionGap),
    ) {
        if (onlyCardId == null || onlyCardId == SettingsCardExpansionId.Permissions) {
            SettingsPermissionCard(
                state = state,
                actions = actions,
                containerColor =
                    settingsSectionContainerColor(
                        permissionPresentation,
                        enabledCardColor,
                        disabledCardColor,
                    ),
                expanded = isCardExpanded(SettingsCardExpansionId.Permissions),
                onExpandedChange = { onCardExpandedChange(SettingsCardExpansionId.Permissions, it) },
            )
        }
        if (onlyCardId == null || onlyCardId == SettingsCardExpansionId.KeepAlive) {
            SettingsKeepAliveCard(
                state = state,
                actions = actions,
                containerColor =
                    settingsSectionContainerColor(
                        keepAlivePresentation,
                        enabledCardColor,
                        disabledCardColor,
                    ),
                expanded = isCardExpanded(SettingsCardExpansionId.KeepAlive),
                onExpandedChange = { onCardExpandedChange(SettingsCardExpansionId.KeepAlive, it) },
            )
        }
        if (onlyCardId == null || onlyCardId == SettingsCardExpansionId.AccessibilityGuardPolicy) {
            SettingsAccessibilityGuardPolicyCard(
                state = state,
                actions = actions,
                containerColor =
                    settingsSectionContainerColor(
                        accessibilityGuardPresentation,
                        enabledCardColor,
                        disabledCardColor,
                    ),
                expanded = isCardExpanded(SettingsCardExpansionId.AccessibilityGuardPolicy),
                onExpandedChange = {
                    onCardExpandedChange(SettingsCardExpansionId.AccessibilityGuardPolicy, it)
                },
            )
        }
        if (onlyCardId == null || onlyCardId == SettingsCardExpansionId.AccessibilityGuardHistory) {
            SettingsAccessibilityGuardHistoryCard(
                state = state,
                containerColor =
                    settingsSectionContainerColor(
                        accessibilityGuardPresentation,
                        enabledCardColor,
                        disabledCardColor,
                    ),
                expanded = isCardExpanded(SettingsCardExpansionId.AccessibilityGuardHistory),
                onExpandedChange = {
                    onCardExpandedChange(SettingsCardExpansionId.AccessibilityGuardHistory, it)
                },
            )
        }
    }
}

@Composable
private fun SettingsPermissionCard(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
    containerColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_permissions_header),
        title = stringResource(R.string.settings_group_permissions_title),
        subtitle = stringResource(R.string.settings_group_permissions_summary),
        sectionIcon = appLucideLockIcon(),
        containerColor = containerColor,
        exportBackdropToContent = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        NotificationPermissionItem(state = state, actions = actions)
        AppListAccessItem(state = state, actions = actions)
        PrivilegedModePickerItem(state = state, actions = actions)
        if (state.privilegeMode != PrivilegeMode.Disabled) {
            PrivilegedAccessItem(state = state, actions = actions)
        }
    }
}

@Composable
private fun SettingsKeepAliveCard(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
    containerColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_keep_alive_header),
        title = stringResource(R.string.settings_group_keep_alive_title),
        subtitle = stringResource(R.string.settings_group_keep_alive_summary),
        sectionIcon = appLucideBellIcon(),
        containerColor = containerColor,
        exportBackdropToContent = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        AndroidBackgroundItem(state = state, actions = actions)
        BackgroundRecoveryItem(state = state)
        BatteryOptimizationItem(state = state, actions = actions)
        OemAutoStartItem(state = state, actions = actions)
    }
}

@Composable
private fun NotificationPermissionItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    val permissionReady = state.notificationPermissionGranted && state.notificationsEnabled
    SettingsButtonActionItem(
        title = stringResource(R.string.settings_notification_permission_title),
        summary =
            if (permissionReady) {
                stringResource(R.string.settings_notification_permission_summary_granted)
            } else {
                stringResource(R.string.settings_notification_permission_summary_restricted)
            },
        infoKey = stringResource(R.string.settings_permissions_info_status),
        infoValue =
            if (permissionReady) {
                stringResource(R.string.settings_notification_permission_status_granted)
            } else {
                stringResource(R.string.settings_notification_permission_status_restricted)
            },
        trailing = {
            AppStandaloneLiquidTextButton(
                variant = GlassVariant.Compact,
                text =
                    if (state.notificationPermissionGranted) {
                        stringResource(R.string.common_open)
                    } else {
                        stringResource(R.string.settings_notification_permission_action_request)
                    },
                enabled =
                    if (state.notificationPermissionGranted) {
                        state.notificationSettingsActionAvailable
                    } else {
                        true
                    },
                onClick = {
                    if (state.notificationPermissionGranted) {
                        actions.onOpenNotificationSettings()
                    } else {
                        actions.onRequestNotificationPermission()
                    }
                },
            )
        },
    )
}

@Composable
private fun AppListAccessItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    SettingsButtonActionItem(
        title = stringResource(R.string.settings_app_list_access_title),
        summary =
            when (state.appListAccessMode) {
                SettingsAppListAccessMode.Privileged -> {
                    stringResource(
                        R.string.settings_app_list_access_summary_privileged,
                        appListPrivilegeModeLabel(state.appListPrivilegeMode ?: state.privilegeMode),
                    )
                }

                SettingsAppListAccessMode.Direct -> {
                    stringResource(R.string.settings_app_list_access_summary_direct)
                }

                SettingsAppListAccessMode.Restricted -> {
                    stringResource(R.string.settings_app_list_access_summary_restricted)
                }
            },
        infoKey = stringResource(R.string.settings_app_list_access_info_mode),
        infoValue =
            when (state.appListAccessMode) {
                SettingsAppListAccessMode.Privileged -> {
                    stringResource(
                        R.string.settings_app_list_access_mode_privileged,
                        appListPrivilegeModeLabel(state.appListPrivilegeMode ?: state.privilegeMode),
                        state.appListDetectedCount,
                    )
                }

                SettingsAppListAccessMode.Direct -> {
                    stringResource(
                        R.string.settings_app_list_access_mode_direct,
                        state.appListDetectedCount,
                    )
                }

                SettingsAppListAccessMode.Restricted -> {
                    stringResource(R.string.settings_app_list_access_mode_restricted)
                }
            },
        trailing = {
            AppStandaloneLiquidTextButton(
                variant = GlassVariant.Compact,
                text =
                    if (state.appListSettingsActionAvailable) {
                        stringResource(R.string.common_open)
                    } else {
                        stringResource(R.string.common_refresh)
                    },
                enabled = state.appListSettingsActionAvailable || state.privilegeGranted,
                onClick = {
                    if (state.appListSettingsActionAvailable) {
                        actions.onOpenAppListPermissionSettings()
                    } else {
                        actions.onCheckOrRequestPrivilege()
                    }
                },
            )
        },
    )
}

@Composable
private fun appListPrivilegeModeLabel(mode: PrivilegeMode): String =
    when (mode) {
        PrivilegeMode.Disabled -> stringResource(R.string.settings_privileged_mode_disabled)
        PrivilegeMode.Shizuku -> stringResource(R.string.settings_privileged_mode_shizuku)
        PrivilegeMode.Root -> stringResource(R.string.settings_privileged_mode_root)
    }

@Composable
private fun PrivilegedModePickerItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            PrivilegeMode.Disabled to stringResource(R.string.settings_privileged_mode_disabled),
            PrivilegeMode.Shizuku to stringResource(R.string.settings_privileged_mode_shizuku),
            PrivilegeMode.Root to stringResource(R.string.settings_privileged_mode_root),
        )
    val selectedIndex = options.indexOfFirst { it.first == state.privilegeMode }.coerceAtLeast(0)
    SettingsPickerItem(
        title = stringResource(R.string.settings_privileged_mode_title),
        summary =
            when (state.privilegeMode) {
                PrivilegeMode.Disabled -> stringResource(R.string.settings_privileged_mode_summary_disabled)
                PrivilegeMode.Shizuku -> stringResource(R.string.settings_privileged_mode_summary_shizuku)
                PrivilegeMode.Root -> stringResource(R.string.settings_privileged_mode_summary_root)
            },
        infoKey = stringResource(R.string.common_scope),
        infoValue = stringResource(R.string.settings_privileged_mode_scope),
    ) {
        AppDropdownSelector(
            selectedText = options[selectedIndex].second,
            options = options.map { it.second },
            selectedIndex = selectedIndex,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onSelectedIndexChange = { index ->
                options.getOrNull(index)?.first?.let(actions.onPrivilegeModeChanged)
                expanded = false
            },
            variant = GlassVariant.SheetAction,
            popupMaxWidth = 260.dp,
            popupMatchAnchorWidth = true,
        )
    }
}

@Composable
private fun PrivilegedAccessItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    val isRoot = state.privilegeMode == PrivilegeMode.Root
    SettingsButtonActionItem(
        title =
            if (isRoot) {
                stringResource(R.string.settings_root_permission_title)
            } else {
                stringResource(R.string.settings_shizuku_permission_title)
            },
        summary =
            when {
                isRoot && state.privilegeGranted -> stringResource(R.string.settings_root_permission_summary_granted)
                isRoot -> stringResource(R.string.settings_root_permission_summary_restricted)
                state.privilegeGranted -> stringResource(R.string.settings_shizuku_permission_summary_granted)
                else -> stringResource(R.string.settings_shizuku_permission_summary_restricted)
            },
        infoKey = stringResource(R.string.settings_permissions_info_status),
        infoValue =
            localizedPrivilegeStatusText(
                status = state.privilegeStatus,
                granted = state.privilegeGranted,
            ).ifBlank {
                if (state.privilegeGranted) {
                    stringResource(R.string.settings_shizuku_permission_status_granted)
                } else {
                    stringResource(R.string.settings_shizuku_permission_status_restricted)
                }
            },
        trailing = {
            AppStandaloneLiquidTextButton(
                variant = GlassVariant.Compact,
                text =
                    when {
                        state.privilegeGranted -> stringResource(R.string.common_refresh)
                        isRoot -> stringResource(R.string.settings_root_permission_action_request)
                        else -> stringResource(R.string.settings_shizuku_permission_action_request)
                    },
                onClick = actions.onCheckOrRequestPrivilege,
            )
        },
    )
}

@Composable
private fun AndroidBackgroundItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    SettingsButtonActionItem(
        title = stringResource(R.string.settings_android_background_title),
        summary = androidBackgroundSummary(state),
        infoKey = stringResource(R.string.settings_permissions_info_status),
        infoValue = androidBackgroundStatus(state),
        trailing = {
            AppStandaloneLiquidTextButton(
                variant = GlassVariant.Compact,
                text = stringResource(R.string.common_open),
                enabled = state.androidBackgroundSettingsActionAvailable,
                onClick = actions.onOpenAndroidBackgroundSettings,
            )
        },
    )
}

@Composable
private fun BackgroundRecoveryItem(state: SettingsPermissionKeepAliveSectionState) {
    SettingsValueItem(
        title = stringResource(R.string.settings_background_recovery_title),
        summary = backgroundRecoverySummary(state),
        infoKey = stringResource(R.string.settings_background_recovery_info_count),
        infoValue = backgroundRecoveryStatus(state),
    )
}

@Composable
private fun BatteryOptimizationItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    SettingsButtonActionItem(
        title = stringResource(R.string.settings_battery_optimization_title),
        summary =
            if (state.ignoringBatteryOptimizations) {
                stringResource(R.string.settings_battery_optimization_summary_ignored)
            } else {
                stringResource(R.string.settings_battery_optimization_summary_restricted)
            },
        infoKey = stringResource(R.string.settings_battery_optimization_info_status),
        infoValue =
            if (state.ignoringBatteryOptimizations) {
                stringResource(R.string.settings_battery_optimization_status_ignored)
            } else {
                stringResource(R.string.settings_battery_optimization_status_restricted)
            },
        trailing = {
            AppStandaloneLiquidTextButton(
                variant = GlassVariant.Compact,
                text =
                    if (state.ignoringBatteryOptimizations) {
                        stringResource(R.string.common_open)
                    } else {
                        stringResource(R.string.settings_battery_optimization_action_request)
                    },
                enabled = state.batteryOptimizationActionAvailable,
                onClick = actions.onOpenBatteryOptimizationSettings,
            )
        },
    )
}

@Composable
private fun OemAutoStartItem(
    state: SettingsPermissionKeepAliveSectionState,
    actions: SettingsPermissionKeepAliveSectionActions,
) {
    SettingsButtonActionItem(
        title = stringResource(R.string.settings_oem_autostart_title),
        summary = oemAutoStartSummary(state),
        infoKey = stringResource(R.string.settings_permissions_info_status),
        infoValue = oemAutoStartStatus(state),
        trailing = {
            if (state.oemAutoStartActionAvailable) {
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.Compact,
                    text =
                        if (state.oemAutoStartState == SettingsOemAutoStartState.Allowed) {
                            stringResource(R.string.common_open)
                        } else {
                            stringResource(R.string.settings_oem_autostart_action_request)
                        },
                    onClick = actions.onOpenOemAutoStartSettings,
                )
            }
        },
    )
}

@Composable
private fun oemAutoStartSummary(state: SettingsPermissionKeepAliveSectionState): String =
    when (state.oemAutoStartState) {
        SettingsOemAutoStartState.Allowed -> {
            stringResource(
                R.string.settings_oem_autostart_summary_allowed,
                state.oemAutoStartVendorLabel,
            )
        }

        SettingsOemAutoStartState.Restricted -> {
            stringResource(
                R.string.settings_oem_autostart_summary_restricted,
                state.oemAutoStartVendorLabel,
            )
        }

        SettingsOemAutoStartState.Unknown -> {
            stringResource(
                R.string.settings_oem_autostart_summary_unknown,
                state.oemAutoStartVendorLabel,
            )
        }

        SettingsOemAutoStartState.Fallback -> {
            stringResource(R.string.settings_oem_autostart_summary_fallback)
        }

        SettingsOemAutoStartState.Unsupported -> {
            stringResource(R.string.settings_oem_autostart_summary_unsupported)
        }
    }

@Composable
private fun oemAutoStartStatus(state: SettingsPermissionKeepAliveSectionState): String =
    when (state.oemAutoStartState) {
        SettingsOemAutoStartState.Allowed -> {
            stringResource(
                R.string.settings_oem_autostart_status_allowed,
                state.oemAutoStartVendorLabel,
            )
        }

        SettingsOemAutoStartState.Restricted -> {
            stringResource(
                R.string.settings_oem_autostart_status_restricted,
                state.oemAutoStartVendorLabel,
            )
        }

        SettingsOemAutoStartState.Unknown -> {
            stringResource(
                R.string.settings_oem_autostart_status_unknown,
                state.oemAutoStartVendorLabel,
            )
        }

        SettingsOemAutoStartState.Fallback -> {
            stringResource(R.string.settings_oem_autostart_status_fallback)
        }

        SettingsOemAutoStartState.Unsupported -> {
            stringResource(R.string.settings_oem_autostart_status_unsupported)
        }
    }

@Composable
private fun androidBackgroundSummary(state: SettingsPermissionKeepAliveSectionState): String =
    when {
        state.androidBackgroundRestricted -> {
            stringResource(R.string.settings_android_background_summary_restricted)
        }

        state.androidPowerSaveMode -> {
            stringResource(R.string.settings_android_background_summary_power_save)
        }

        state.appStandbyBucket.isStandbyLimited() -> {
            stringResource(
                R.string.settings_android_background_summary_standby_limited,
                appStandbyBucketLabel(state.appStandbyBucket),
            )
        }

        state.androidDeviceIdleMode -> {
            stringResource(R.string.settings_android_background_summary_idle)
        }

        else -> {
            stringResource(R.string.settings_android_background_summary_normal)
        }
    }

@Composable
private fun androidBackgroundStatus(state: SettingsPermissionKeepAliveSectionState): String =
    when {
        state.androidBackgroundRestricted -> {
            stringResource(
                R.string.settings_android_background_status_restricted,
                appStandbyBucketLabel(state.appStandbyBucket),
            )
        }

        state.androidPowerSaveMode -> {
            stringResource(
                R.string.settings_android_background_status_power_save,
                appStandbyBucketLabel(state.appStandbyBucket),
            )
        }

        state.appStandbyBucket.isStandbyLimited() -> {
            stringResource(
                R.string.settings_android_background_status_standby_limited,
                appStandbyBucketLabel(state.appStandbyBucket),
            )
        }

        state.androidDeviceIdleMode -> {
            stringResource(
                R.string.settings_android_background_status_idle,
                appStandbyBucketLabel(state.appStandbyBucket),
            )
        }

        else -> {
            stringResource(
                R.string.settings_android_background_status_normal,
                appStandbyBucketLabel(state.appStandbyBucket),
            )
        }
    }

@Composable
private fun appStandbyBucketLabel(bucket: SettingsAppStandbyBucketState): String =
    when (bucket) {
        SettingsAppStandbyBucketState.Exempted -> {
            stringResource(R.string.settings_app_standby_bucket_exempted)
        }

        SettingsAppStandbyBucketState.Active -> {
            stringResource(R.string.settings_app_standby_bucket_active)
        }

        SettingsAppStandbyBucketState.WorkingSet -> {
            stringResource(R.string.settings_app_standby_bucket_working_set)
        }

        SettingsAppStandbyBucketState.Frequent -> {
            stringResource(R.string.settings_app_standby_bucket_frequent)
        }

        SettingsAppStandbyBucketState.Rare -> {
            stringResource(R.string.settings_app_standby_bucket_rare)
        }

        SettingsAppStandbyBucketState.Restricted -> {
            stringResource(R.string.settings_app_standby_bucket_restricted)
        }

        SettingsAppStandbyBucketState.Never -> {
            stringResource(R.string.settings_app_standby_bucket_never)
        }

        SettingsAppStandbyBucketState.Unknown -> {
            stringResource(R.string.settings_app_standby_bucket_unknown)
        }
    }

private fun SettingsAppStandbyBucketState.isStandbyLimited(): Boolean =
    this == SettingsAppStandbyBucketState.Rare ||
        this == SettingsAppStandbyBucketState.Restricted ||
        this == SettingsAppStandbyBucketState.Never

@Composable
private fun backgroundRecoverySummary(state: SettingsPermissionKeepAliveSectionState): String {
    val snapshot = state.backgroundRecoverySnapshot
    if (snapshot.lastFinishedAtMs <= 0L) {
        return stringResource(R.string.settings_background_recovery_summary_empty)
    }
    val action = backgroundRecoveryActionLabel(snapshot.lastAction)
    val time = formatBackgroundRecoveryTime(snapshot.lastFinishedAtMs)
    val elapsed = formatBackgroundRecoveryElapsed(snapshot.lastElapsedMs)
    val failureReason =
        if (snapshot.lastFailureReason.isBlank()) {
            stringResource(R.string.common_unknown)
        } else {
            snapshot.lastFailureReason
        }
    return if (snapshot.lastFailed) {
        stringResource(
            R.string.settings_background_recovery_summary_failed,
            action,
            time,
            failureReason,
        )
    } else {
        stringResource(
            R.string.settings_background_recovery_summary_succeeded,
            action,
            time,
            elapsed,
        )
    }
}

@Composable
private fun backgroundRecoveryStatus(state: SettingsPermissionKeepAliveSectionState): String {
    val snapshot = state.backgroundRecoverySnapshot
    if (snapshot.recoveryCount <= 0) {
        return stringResource(R.string.settings_background_recovery_status_empty)
    }
    return if (snapshot.lastFailed) {
        stringResource(R.string.settings_background_recovery_status_failed, snapshot.recoveryCount)
    } else {
        stringResource(R.string.settings_background_recovery_status_succeeded, snapshot.recoveryCount)
    }
}

@Composable
private fun backgroundRecoveryActionLabel(action: String): String =
    when (action) {
        Intent.ACTION_BOOT_COMPLETED -> {
            stringResource(R.string.settings_background_recovery_action_boot_completed)
        }

        Intent.ACTION_MY_PACKAGE_REPLACED -> {
            stringResource(R.string.settings_background_recovery_action_package_replaced)
        }

        Intent.ACTION_TIME_CHANGED -> {
            stringResource(R.string.settings_background_recovery_action_time_changed)
        }

        Intent.ACTION_TIMEZONE_CHANGED -> {
            stringResource(R.string.settings_background_recovery_action_timezone_changed)
        }

        else -> {
            stringResource(R.string.settings_background_recovery_action_unknown)
        }
    }

private fun formatBackgroundRecoveryTime(epochMs: Long): String =
    DateFormat
        .format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdHm"), epochMs)
        .toString()

@Composable
private fun formatBackgroundRecoveryElapsed(elapsedMs: Long): String =
    if (elapsedMs < 1000L) {
        stringResource(R.string.settings_background_recovery_elapsed_ms, elapsedMs.coerceAtLeast(0L))
    } else {
        stringResource(R.string.settings_background_recovery_elapsed_s, elapsedMs / 1000L)
    }
