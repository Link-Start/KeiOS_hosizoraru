@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.ui.page.main.github.RefreshIntervalOption
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.query.noOnlineShareTargetOption
import os.kei.ui.page.main.github.query.systemDefaultDownloaderOption
import os.kei.ui.page.main.github.query.systemDownloadManagerOption
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.sheet.UnsavedSheetDismissConfirmDialog
import os.kei.ui.page.main.widget.sheet.rememberUnsavedSheetDismissHandler

@Composable
internal fun GitHubCheckLogicSheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    lookupConfig: GitHubLookupConfig,
    trackedCount: Int,
    refreshIntervalHours: Int,
    checkAllTrackedPreReleasesInput: Boolean,
    checkAllDirectApkPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    preciseApkVersionEnabledInput: Boolean,
    scanSystemAppsByDefaultInput: Boolean,
    profileDepthInput: GitHubProfileDepth,
    shareImportFlowModeInput: GitHubShareImportFlowMode,
    appManagedShareInstallEnabledInput: Boolean,
    onlineShareTargetPackageInput: String,
    preferredDownloaderPackageInput: String,
    decisionAssistEnabledInput: Boolean,
    repositoryHealthCardEnabledInput: Boolean,
    apkTrustCheckEnabledInput: Boolean,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    showDownloaderPopup: Boolean,
    showOnlineShareTargetPopup: Boolean,
    showShareImportFlowModePopup: Boolean,
    downloaderPopupAnchorBounds: IntRect?,
    onlineShareTargetPopupAnchorBounds: IntRect?,
    shareImportFlowModePopupAnchorBounds: IntRect?,
    downloaderOptions: List<DownloaderOption>,
    hasKeiOsSelfTrack: Boolean,
    exportInProgress: Boolean,
    importInProgress: Boolean,
    debugActionsUpdateNotificationLoading: Boolean,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onEnsureKeiOsSelfTrack: () -> Unit,
    onSendDebugActionsUpdateNotification: () -> Unit,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
    onCheckAllDirectApkPreReleasesInputChange: (Boolean) -> Unit,
    onAggressiveApkFilteringInputChange: (Boolean) -> Unit,
    onPreciseApkVersionEnabledInputChange: (Boolean) -> Unit,
    onScanSystemAppsByDefaultInputChange: (Boolean) -> Unit,
    onProfileDepthInputChange: (GitHubProfileDepth) -> Unit,
    onShareImportFlowModeInputChange: (GitHubShareImportFlowMode) -> Unit,
    onAppManagedShareInstallEnabledInputChange: (Boolean) -> Unit,
    onPreferredDownloaderPackageInputChange: (String) -> Unit,
    onOnlineShareTargetPackageInputChange: (String) -> Unit,
    onDecisionAssistEnabledInputChange: (Boolean) -> Unit,
    onRepositoryHealthCardEnabledInputChange: (Boolean) -> Unit,
    onApkTrustCheckEnabledInputChange: (Boolean) -> Unit,
    onShowDownloaderPopupChange: (Boolean) -> Unit,
    onShowOnlineShareTargetPopupChange: (Boolean) -> Unit,
    onShowShareImportFlowModePopupChange: (Boolean) -> Unit,
    onDownloaderPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOnlineShareTargetPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onShareImportFlowModePopupAnchorBoundsChange: (IntRect?) -> Unit,
) {
    val context = LocalContext.current
    val logicChanged =
        checkAllTrackedPreReleasesInput != lookupConfig.checkAllTrackedPreReleases ||
            checkAllDirectApkPreReleasesInput != lookupConfig.checkAllDirectApkPreReleases ||
            aggressiveApkFilteringInput != lookupConfig.aggressiveApkFiltering ||
            preciseApkVersionEnabledInput != lookupConfig.preciseApkVersionEnabled ||
            scanSystemAppsByDefaultInput != lookupConfig.scanSystemAppsByDefault ||
            profileDepthInput != lookupConfig.profileDepth ||
            shareImportFlowModeInput != lookupConfig.shareImportFlowMode ||
            appManagedShareInstallEnabledInput != lookupConfig.appManagedShareInstallEnabled ||
            onlineShareTargetPackageInput != lookupConfig.onlineShareTargetPackage ||
            preferredDownloaderPackageInput != lookupConfig.preferredDownloaderPackage ||
            decisionAssistEnabledInput != lookupConfig.decisionAssistEnabled ||
            repositoryHealthCardEnabledInput != lookupConfig.repositoryHealthCardEnabled ||
            apkTrustCheckEnabledInput != lookupConfig.apkTrustCheckEnabled
    val dismissHandler =
        rememberUnsavedSheetDismissHandler(
            hasUnsavedChanges = logicChanged,
            onDismissRequest = onDismissRequest,
        )
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.github_check_sheet_title),
        onDismissRequest = dismissHandler.requestDismiss,
        allowDismiss = dismissHandler.allowDismiss,
        onBlockedDismissRequest = dismissHandler.requestDismiss,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = dismissHandler.requestDismiss,
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideConfirmIcon(),
                contentDescription = stringResource(R.string.github_check_sheet_cd_save),
                onClick = onApply,
            )
        },
    ) {
        val selectedRefreshOption = RefreshIntervalOption.fromHours(refreshIntervalHours)
        val allDownloaderOptions =
            remember(downloaderOptions) {
                listOf(systemDefaultDownloaderOption(context), systemDownloadManagerOption(context)) + downloaderOptions
            }
        val onlineShareTargetOptions =
            remember(installedOnlineShareTargets) {
                listOf(noOnlineShareTargetOption(context)) + installedOnlineShareTargets
            }
        val rawSelectedDownloaderLabel =
            allDownloaderOptions
                .firstOrNull {
                    it.packageName == preferredDownloaderPackageInput
                }?.label ?: systemDefaultDownloaderOption(context).label
        val selectedDownloaderLabel =
            if (appManagedShareInstallEnabledInput) {
                stringResource(R.string.app_name)
            } else {
                rawSelectedDownloaderLabel
            }
        val selectedOnlineShareTargetLabel =
            onlineShareTargetOptions
                .firstOrNull {
                    it.packageName == onlineShareTargetPackageInput
                }?.label ?: noOnlineShareTargetOption(context).label
        val effectiveOnlineShareTargetLabel =
            if (appManagedShareInstallEnabledInput) {
                stringResource(R.string.app_name)
            } else {
                selectedOnlineShareTargetLabel
            }

        SheetContentColumn(verticalSpacing = 10.dp) {
            GitHubCheckOverviewSection(
                backdrop = backdrop,
                logicChanged = logicChanged,
                trackedCount = trackedCount,
                selectedRefreshOption = selectedRefreshOption,
                selectedDownloaderPackage = preferredDownloaderPackageInput,
                selectedDownloaderLabel = selectedDownloaderLabel,
                appManagedShareInstallEnabled = appManagedShareInstallEnabledInput,
            )
            GitHubCheckStrategySection(
                checkAllTrackedPreReleasesInput = checkAllTrackedPreReleasesInput,
                checkAllDirectApkPreReleasesInput = checkAllDirectApkPreReleasesInput,
                aggressiveApkFilteringInput = aggressiveApkFilteringInput,
                preciseApkVersionEnabledInput = preciseApkVersionEnabledInput,
                scanSystemAppsByDefaultInput = scanSystemAppsByDefaultInput,
                profileDepthInput = profileDepthInput,
                onCheckAllTrackedPreReleasesInputChange = onCheckAllTrackedPreReleasesInputChange,
                onCheckAllDirectApkPreReleasesInputChange =
                onCheckAllDirectApkPreReleasesInputChange,
                onAggressiveApkFilteringInputChange = onAggressiveApkFilteringInputChange,
                onPreciseApkVersionEnabledInputChange = onPreciseApkVersionEnabledInputChange,
                onScanSystemAppsByDefaultInputChange = onScanSystemAppsByDefaultInputChange,
                onProfileDepthInputChange = onProfileDepthInputChange,
            )
            GitHubCheckTransferSection(
                backdrop = backdrop,
                selectedDownloaderLabel = selectedDownloaderLabel,
                allDownloaderOptions = allDownloaderOptions,
                preferredDownloaderPackageInput = preferredDownloaderPackageInput,
                showDownloaderPopup = showDownloaderPopup,
                downloaderPopupAnchorBounds = downloaderPopupAnchorBounds,
                shareImportFlowModeInput = shareImportFlowModeInput,
                appManagedShareInstallEnabledInput = appManagedShareInstallEnabledInput,
                selectedOnlineShareTargetLabel = effectiveOnlineShareTargetLabel,
                onlineShareTargetOptions = onlineShareTargetOptions,
                installedOnlineShareTargets = installedOnlineShareTargets,
                onlineShareTargetPackageInput = onlineShareTargetPackageInput,
                showOnlineShareTargetPopup = showOnlineShareTargetPopup,
                showShareImportFlowModePopup = showShareImportFlowModePopup,
                onlineShareTargetPopupAnchorBounds = onlineShareTargetPopupAnchorBounds,
                shareImportFlowModePopupAnchorBounds = shareImportFlowModePopupAnchorBounds,
                onPreferredDownloaderPackageInputChange = onPreferredDownloaderPackageInputChange,
                onShareImportFlowModeInputChange = onShareImportFlowModeInputChange,
                onAppManagedShareInstallEnabledInputChange =
                onAppManagedShareInstallEnabledInputChange,
                onOnlineShareTargetPackageInputChange = onOnlineShareTargetPackageInputChange,
                onShowDownloaderPopupChange = onShowDownloaderPopupChange,
                onShowOnlineShareTargetPopupChange = onShowOnlineShareTargetPopupChange,
                onShowShareImportFlowModePopupChange = onShowShareImportFlowModePopupChange,
                onDownloaderPopupAnchorBoundsChange = onDownloaderPopupAnchorBoundsChange,
                onOnlineShareTargetPopupAnchorBoundsChange = onOnlineShareTargetPopupAnchorBoundsChange,
                onShareImportFlowModePopupAnchorBoundsChange = onShareImportFlowModePopupAnchorBoundsChange,
            )
            GitHubCheckEnhancementSection(
                decisionAssistEnabledInput = decisionAssistEnabledInput,
                repositoryHealthCardEnabledInput = repositoryHealthCardEnabledInput,
                apkTrustCheckEnabledInput = apkTrustCheckEnabledInput,
                onDecisionAssistEnabledInputChange = onDecisionAssistEnabledInputChange,
                onRepositoryHealthCardEnabledInputChange = onRepositoryHealthCardEnabledInputChange,
                onApkTrustCheckEnabledInputChange = onApkTrustCheckEnabledInputChange,
            )
            GitHubCheckTracksSection(
                backdrop = backdrop,
                hasKeiOsSelfTrack = hasKeiOsSelfTrack,
                exportInProgress = exportInProgress,
                importInProgress = importInProgress,
                onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack,
            )
            GitHubCheckDebugSection(
                backdrop = backdrop,
                loading = debugActionsUpdateNotificationLoading,
                onSendDebugActionsUpdateNotification = onSendDebugActionsUpdateNotification,
            )
            GitHubCheckNotesSection()
        }
    }
    UnsavedSheetDismissConfirmDialog(
        show = dismissHandler.showConfirmDialog,
        onKeepEditing = dismissHandler.keepEditing,
        onDiscardChanges = dismissHandler.discardChanges,
    )
}
