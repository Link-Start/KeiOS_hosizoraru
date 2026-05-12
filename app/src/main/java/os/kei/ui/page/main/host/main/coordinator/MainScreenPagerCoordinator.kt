package os.kei.ui.page.main.host.main

import os.kei.core.system.ShizukuApiUtils
import os.kei.mcp.server.McpServerManager

internal data class MainScreenPagerCoordinator(
    val settingsReturnToken: Int,
    val liquidBottomBarEnabled: Boolean,
    val liquidActionBarLayeredStyleEnabled: Boolean,
    val gripAwareFloatingDockEnabled: Boolean,
    val homeIconHdrEnabled: Boolean,
    val homeDynamicFullEffectEnabled: Boolean,
    val preloadingEnabled: Boolean,
    val nonHomeBackgroundEnabled: Boolean,
    val nonHomeBackgroundUri: String,
    val nonHomeBackgroundOpacity: Float,
    val visibleBottomPageNames: Set<String>,
    val onVisibleBottomPageNamesChange: (Set<String>) -> Unit,
    val shizukuStatus: String,
    val shizukuApiUtils: ShizukuApiUtils,
    val mcpServerManager: McpServerManager,
    val onOpenGuideDetail: (String) -> Unit,
    val requestedBottomPage: String?,
    val requestedBottomPageToken: Int,
    val requestedGitHubRefreshToken: Int,
    val requestedGitHubManagedInstallConfirmToken: Int,
    val requestedGitHubActionsTrackId: String?,
    val requestedGitHubActionsSheetToken: Int,
    val onRequestedBottomPageConsumed: () -> Unit,
    val onBaGuideCatalogOpen: () -> Unit,
    val onBaGuideCatalogBack: () -> Unit
)

internal fun buildMainScreenPagerCoordinator(
    settingsReturnToken: Int,
    prefsState: MainScreenUiPrefsState,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    mcpServerManager: McpServerManager,
    onOpenGuideDetail: (String) -> Unit,
    requestedBottomPage: String?,
    requestedBottomPageToken: Int,
    requestedGitHubRefreshToken: Int,
    requestedGitHubManagedInstallConfirmToken: Int,
    requestedGitHubActionsTrackId: String?,
    requestedGitHubActionsSheetToken: Int,
    onRequestedBottomPageConsumed: () -> Unit,
    onBaGuideCatalogOpen: () -> Unit,
    onBaGuideCatalogBack: () -> Unit
): MainScreenPagerCoordinator {
    return MainScreenPagerCoordinator(
        settingsReturnToken = settingsReturnToken,
        liquidBottomBarEnabled = prefsState.liquidBottomBarEnabled,
        liquidActionBarLayeredStyleEnabled = prefsState.liquidActionBarLayeredStyleEnabled,
        gripAwareFloatingDockEnabled = prefsState.gripAwareFloatingDockEnabled,
        homeIconHdrEnabled = prefsState.homeIconHdrEnabled,
        homeDynamicFullEffectEnabled = prefsState.homeDynamicFullEffectEnabled,
        preloadingEnabled = prefsState.preloadingEnabled,
        nonHomeBackgroundEnabled = prefsState.nonHomeBackgroundEnabled,
        nonHomeBackgroundUri = prefsState.nonHomeBackgroundUri,
        nonHomeBackgroundOpacity = prefsState.nonHomeBackgroundOpacity,
        visibleBottomPageNames = prefsState.visibleBottomPageNames,
        onVisibleBottomPageNamesChange = prefsState::updateVisibleBottomPageNames,
        shizukuStatus = shizukuStatus,
        shizukuApiUtils = shizukuApiUtils,
        mcpServerManager = mcpServerManager,
        onOpenGuideDetail = onOpenGuideDetail,
        requestedBottomPage = requestedBottomPage,
        requestedBottomPageToken = requestedBottomPageToken,
        requestedGitHubRefreshToken = requestedGitHubRefreshToken,
        requestedGitHubManagedInstallConfirmToken = requestedGitHubManagedInstallConfirmToken,
        requestedGitHubActionsTrackId = requestedGitHubActionsTrackId,
        requestedGitHubActionsSheetToken = requestedGitHubActionsSheetToken,
        onRequestedBottomPageConsumed = onRequestedBottomPageConsumed,
        onBaGuideCatalogOpen = onBaGuideCatalogOpen,
        onBaGuideCatalogBack = onBaGuideCatalogBack
    )
}
