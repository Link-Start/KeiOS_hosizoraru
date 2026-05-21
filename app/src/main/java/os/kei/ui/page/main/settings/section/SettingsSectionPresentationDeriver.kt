package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import os.kei.core.log.AppLogLevel
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.settings.support.SettingsAppListAccessMode
import os.kei.ui.page.main.settings.support.SettingsOemAutoStartState

@Immutable
internal data class SettingsSectionPresentationState(
    val active: Boolean,
)

internal fun settingsSectionContainerColor(
    presentation: SettingsSectionPresentationState,
    enabledCardColor: Color,
    disabledCardColor: Color,
): Color = if (presentation.active) enabledCardColor else disabledCardColor

internal fun derivePermissionKeepAlivePresentation(state: SettingsPermissionKeepAliveSectionState): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active =
            state.notificationsEnabled ||
                state.ignoringBatteryOptimizations ||
                state.oemAutoStartState == SettingsOemAutoStartState.Allowed ||
                state.shizukuGranted ||
                state.appListAccessMode != SettingsAppListAccessMode.Restricted,
    )

internal fun deriveVisualPresentation(state: SettingsVisualSectionState): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active =
            state.preloadingEnabled ||
                state.homeIconHdrEnabled ||
                state.homeDynamicFullEffectEnabled,
    )

internal fun deriveAnimationPresentation(state: SettingsAnimationSectionState): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active =
            state.transitionAnimationsEnabled ||
                state.predictiveBackAnimationsEnabled,
    )

internal fun deriveComponentEffectsPresentation(state: SettingsComponentEffectsSectionState): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active =
            state.liquidActionBarLayeredStyleEnabled ||
                state.liquidSwitchEnabled ||
                state.liquidToastEnabled ||
                state.reduceToastInterruptionEnabled ||
                state.liquidSheetEnabled ||
                state.liquidDialogEnabled ||
                state.liquidBottomBarEnabled ||
                state.miuixMainNavigationEnabled ||
                state.searchAutoFocusEnabled ||
                state.gripAwareFloatingDockEnabled,
    )

internal fun deriveNotifyPresentation(state: SettingsNotifySectionState): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active =
            state.superIslandNotificationEnabled ||
                state.superIslandBypassRestrictionEnabled,
    )

internal fun deriveCopyPresentation(state: SettingsCopySectionState): SettingsSectionPresentationState =
    SettingsSectionPresentationState(active = state.textCopyCapabilityExpanded)

internal fun deriveBackgroundPresentation(
    nonHomeBackgroundEnabled: Boolean,
    nonHomeBackgroundUri: String,
): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active = nonHomeBackgroundEnabled || nonHomeBackgroundUri.isNotBlank(),
    )

internal fun deriveCachePresentation(cacheDiagnosticsEnabled: Boolean): SettingsSectionPresentationState =
    SettingsSectionPresentationState(active = cacheDiagnosticsEnabled)

internal fun deriveLogPresentation(
    logLevel: AppLogLevel,
    logStats: AppLogStore.Stats,
): SettingsSectionPresentationState =
    SettingsSectionPresentationState(
        active = logLevel != AppLogLevel.Off || logStats.fileCount > 0,
    )
