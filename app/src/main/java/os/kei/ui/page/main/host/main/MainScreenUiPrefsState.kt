package os.kei.ui.page.main.host.main

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import os.kei.core.icon.LauncherIconController
import os.kei.core.icon.LauncherIconDesign
import os.kei.core.log.AppLogLevel
import os.kei.core.log.AppLogger
import os.kei.core.prefs.UiPrefsSnapshot
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.server.McpServerManager

@Stable
internal class MainScreenUiPrefsState(
    private val snapshot: UiPrefsSnapshot,
    private val appContext: Context,
    private val mcpServerManager: McpServerManager,
    private val viewModel: MainScreenPrefsViewModel,
) {
    val liquidBottomBarEnabled: Boolean get() = snapshot.liquidBottomBarEnabled
    val miuixMainNavigationEnabled: Boolean get() = snapshot.miuixMainNavigationEnabled
    val liquidActionBarLayeredStyleEnabled: Boolean get() = snapshot.liquidActionBarLayeredStyleEnabled
    val liquidSwitchEnabled: Boolean get() = snapshot.liquidSwitchEnabled
    val liquidToastEnabled: Boolean get() = snapshot.liquidToastEnabled
    val transitionAnimationsEnabled: Boolean get() = snapshot.transitionAnimationsEnabled
    val predictiveBackAnimationsEnabled: Boolean get() = snapshot.predictiveBackAnimationsEnabled
    val searchAutoFocusEnabled: Boolean get() = snapshot.searchAutoFocusEnabled
    val gripAwareFloatingDockEnabled: Boolean get() = snapshot.gripAwareFloatingDockEnabled
    val homeIconHdrEnabled: Boolean get() = snapshot.homeIconHdrEnabled
    val homeDynamicFullEffectEnabled: Boolean get() = snapshot.homeDynamicFullEffectEnabled
    val preloadingEnabled: Boolean get() = snapshot.preloadingEnabled
    val launcherIconDesign: LauncherIconDesign get() = snapshot.launcherIconDesign
    val nonHomeBackgroundEnabled: Boolean get() = snapshot.nonHomeBackgroundEnabled
    val nonHomeBackgroundUri: String get() = snapshot.nonHomeBackgroundUri
    val nonHomeBackgroundOpacity: Float get() = snapshot.nonHomeBackgroundOpacity
    val superIslandNotificationEnabled: Boolean get() = snapshot.superIslandNotificationEnabled
    val superIslandBypassRestrictionEnabled: Boolean get() = snapshot.superIslandBypassRestrictionEnabled
    val superIslandRestoreDelayMs: Int get() = snapshot.superIslandRestoreDelayMs
    val logLevel: AppLogLevel get() = snapshot.logLevel
    val textCopyCapabilityExpanded: Boolean get() = snapshot.textCopyCapabilityExpanded
    val cacheDiagnosticsEnabled: Boolean get() = snapshot.cacheDiagnosticsEnabled
    val visibleBottomPageNames: Set<String> get() = snapshot.visibleBottomPageNames

    fun updateLiquidBottomBarEnabled(value: Boolean) {
        viewModel.updateLiquidBottomBarEnabled(value)
    }

    fun updateMiuixMainNavigationEnabled(value: Boolean) {
        viewModel.updateMiuixMainNavigationEnabled(value)
    }

    fun updateLiquidActionBarLayeredStyleEnabled(value: Boolean) {
        viewModel.updateLiquidActionBarLayeredStyleEnabled(value)
    }

    fun updateLiquidSwitchEnabled(value: Boolean) {
        viewModel.updateLiquidSwitchEnabled(value)
    }

    fun updateLiquidToastEnabled(value: Boolean) {
        viewModel.updateLiquidToastEnabled(value)
    }

    fun updateTransitionAnimationsEnabled(value: Boolean) {
        viewModel.updateTransitionAnimationsEnabled(value)
    }

    fun updatePredictiveBackAnimationsEnabled(value: Boolean) {
        viewModel.updatePredictiveBackAnimationsEnabled(value)
    }

    fun updateSearchAutoFocusEnabled(value: Boolean) {
        viewModel.updateSearchAutoFocusEnabled(value)
    }

    fun updateGripAwareFloatingDockEnabled(value: Boolean) {
        viewModel.updateGripAwareFloatingDockEnabled(value)
    }

    fun updateHomeIconHdrEnabled(value: Boolean) {
        viewModel.updateHomeIconHdrEnabled(value)
    }

    fun updateHomeDynamicFullEffectEnabled(value: Boolean) {
        viewModel.updateHomeDynamicFullEffectEnabled(value)
    }

    fun updatePreloadingEnabled(value: Boolean) {
        viewModel.updatePreloadingEnabled(value)
    }

    fun updateLauncherIconDesign(value: LauncherIconDesign) {
        if (value == snapshot.launcherIconDesign) return
        viewModel.updateLauncherIconDesign(value)
        LauncherIconController.applyDesign(appContext, value)
    }

    fun updateNonHomeBackgroundEnabled(value: Boolean) {
        viewModel.updateNonHomeBackgroundEnabled(value)
    }

    fun updateNonHomeBackgroundUri(value: String) {
        viewModel.updateNonHomeBackgroundUri(value)
    }

    fun updateNonHomeBackgroundOpacity(value: Float) {
        viewModel.updateNonHomeBackgroundOpacity(value)
    }

    fun updateSuperIslandNotificationEnabled(value: Boolean) {
        viewModel.updateSuperIslandNotificationEnabled(value)
        mcpServerManager.refreshNotificationNow()
        McpNotificationHelper.refreshCurrentNotificationStyle(appContext)
    }

    fun updateSuperIslandBypassRestrictionEnabled(value: Boolean) {
        viewModel.updateSuperIslandBypassRestrictionEnabled(value)
        mcpServerManager.refreshNotificationNow()
        McpNotificationHelper.refreshCurrentNotificationStyle(appContext)
    }

    fun updateSuperIslandRestoreDelayMs(value: Int) {
        viewModel.updateSuperIslandRestoreDelayMs(value)
        mcpServerManager.refreshNotificationNow()
        McpNotificationHelper.refreshCurrentNotificationStyle(appContext)
    }

    fun updateLogLevel(value: AppLogLevel) {
        viewModel.updateLogLevel(value)
        AppLogger.setLogLevel(value)
    }

    fun updateTextCopyCapabilityExpanded(value: Boolean) {
        viewModel.updateTextCopyCapabilityExpanded(value)
    }

    fun updateCacheDiagnosticsEnabled(value: Boolean) {
        viewModel.updateCacheDiagnosticsEnabled(value)
    }

    fun updateVisibleBottomPageNames(value: Set<String>) {
        viewModel.updateVisibleBottomPageNames(value)
    }
}

@Composable
internal fun rememberMainScreenUiPrefsState(
    snapshot: UiPrefsSnapshot,
    appContext: Context,
    mcpServerManager: McpServerManager,
    viewModel: MainScreenPrefsViewModel,
): MainScreenUiPrefsState =
    remember(snapshot, appContext, mcpServerManager, viewModel) {
        MainScreenUiPrefsState(
            snapshot = snapshot,
            appContext = appContext,
            mcpServerManager = mcpServerManager,
            viewModel = viewModel,
        )
    }
