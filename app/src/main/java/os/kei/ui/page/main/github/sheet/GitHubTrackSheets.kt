package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.core.ui.resource.resolveString
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.ui.page.main.github.GitHubOverviewMetricItem
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.RefreshIntervalOption
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.query.noOnlineShareTargetOption
import os.kei.ui.page.main.github.query.systemDefaultDownloaderOption
import os.kei.ui.page.main.github.query.systemDownloadManagerOption
import os.kei.ui.page.main.github.query.systemDownloadManagerPackageName
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubCheckLogicSheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    lookupConfig: GitHubLookupConfig,
    trackedCount: Int,
    refreshIntervalHours: Int,
    checkAllTrackedPreReleasesInput: Boolean,
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
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onEnsureKeiOsSelfTrack: () -> Unit,
    onSendDebugActionsUpdateNotification: () -> Unit,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
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
    onShareImportFlowModePopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    val context = LocalContext.current
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.github_check_sheet_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideConfirmIcon(),
                contentDescription = stringResource(R.string.github_check_sheet_cd_save),
                onClick = onApply
            )
        }
    ) {
        val selectedRefreshOption = RefreshIntervalOption.fromHours(refreshIntervalHours)
        val allDownloaderOptions = remember(downloaderOptions) {
            listOf(systemDefaultDownloaderOption(context), systemDownloadManagerOption(context)) + downloaderOptions
        }
        val onlineShareTargetOptions = remember(installedOnlineShareTargets) {
            listOf(noOnlineShareTargetOption(context)) + installedOnlineShareTargets
        }
        val rawSelectedDownloaderLabel = allDownloaderOptions.firstOrNull {
            it.packageName == preferredDownloaderPackageInput
        }?.label ?: systemDefaultDownloaderOption(context).label
        val selectedDownloaderLabel = if (appManagedShareInstallEnabledInput) {
            stringResource(R.string.app_name)
        } else {
            rawSelectedDownloaderLabel
        }
        val selectedOnlineShareTargetLabel = onlineShareTargetOptions.firstOrNull {
            it.packageName == onlineShareTargetPackageInput
        }?.label ?: noOnlineShareTargetOption(context).label
        val effectiveOnlineShareTargetLabel = if (appManagedShareInstallEnabledInput) {
            stringResource(R.string.app_name)
        } else {
            selectedOnlineShareTargetLabel
        }
        val logicChanged =
            checkAllTrackedPreReleasesInput != lookupConfig.checkAllTrackedPreReleases ||
            aggressiveApkFilteringInput != lookupConfig.aggressiveApkFiltering ||
                preciseApkVersionEnabledInput != lookupConfig.preciseApkVersionEnabled ||
                    scanSystemAppsByDefaultInput != lookupConfig.scanSystemAppsByDefault ||
                profileDepthInput != lookupConfig.profileDepth ||
                shareImportFlowModeInput != lookupConfig.shareImportFlowMode ||
                appManagedShareInstallEnabledInput !=
                lookupConfig.appManagedShareInstallEnabled ||
            onlineShareTargetPackageInput != lookupConfig.onlineShareTargetPackage ||
                preferredDownloaderPackageInput != lookupConfig.preferredDownloaderPackage ||
                decisionAssistEnabledInput != lookupConfig.decisionAssistEnabled ||
                repositoryHealthCardEnabledInput != lookupConfig.repositoryHealthCardEnabled ||
                apkTrustCheckEnabledInput != lookupConfig.apkTrustCheckEnabled

        SheetContentColumn(verticalSpacing = 10.dp) {
            GitHubCheckOverviewSection(
                backdrop = backdrop,
                logicChanged = logicChanged,
                trackedCount = trackedCount,
                selectedRefreshOption = selectedRefreshOption,
                selectedDownloaderPackage = preferredDownloaderPackageInput,
                selectedDownloaderLabel = selectedDownloaderLabel,
                appManagedShareInstallEnabled = appManagedShareInstallEnabledInput
            )
            GitHubCheckStrategySection(
                checkAllTrackedPreReleasesInput = checkAllTrackedPreReleasesInput,
                aggressiveApkFilteringInput = aggressiveApkFilteringInput,
                preciseApkVersionEnabledInput = preciseApkVersionEnabledInput,
                scanSystemAppsByDefaultInput = scanSystemAppsByDefaultInput,
                profileDepthInput = profileDepthInput,
                onCheckAllTrackedPreReleasesInputChange = onCheckAllTrackedPreReleasesInputChange,
                onAggressiveApkFilteringInputChange = onAggressiveApkFilteringInputChange,
                onPreciseApkVersionEnabledInputChange = onPreciseApkVersionEnabledInputChange,
                onScanSystemAppsByDefaultInputChange = onScanSystemAppsByDefaultInputChange,
                onProfileDepthInputChange = onProfileDepthInputChange
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
                onShareImportFlowModePopupAnchorBoundsChange = onShareImportFlowModePopupAnchorBoundsChange
            )
            GitHubCheckEnhancementSection(
                decisionAssistEnabledInput = decisionAssistEnabledInput,
                repositoryHealthCardEnabledInput = repositoryHealthCardEnabledInput,
                apkTrustCheckEnabledInput = apkTrustCheckEnabledInput,
                onDecisionAssistEnabledInputChange = onDecisionAssistEnabledInputChange,
                onRepositoryHealthCardEnabledInputChange = onRepositoryHealthCardEnabledInputChange,
                onApkTrustCheckEnabledInputChange = onApkTrustCheckEnabledInputChange
            )
            GitHubCheckTracksSection(
                backdrop = backdrop,
                hasKeiOsSelfTrack = hasKeiOsSelfTrack,
                exportInProgress = exportInProgress,
                importInProgress = importInProgress,
                onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack
            )
            GitHubCheckDebugSection(
                backdrop = backdrop,
                onSendDebugActionsUpdateNotification = onSendDebugActionsUpdateNotification
            )
            GitHubCheckNotesSection()
        }
    }
}

@Composable
private fun GitHubCheckOverviewSection(
    backdrop: LayerBackdrop,
    logicChanged: Boolean,
    trackedCount: Int,
    selectedRefreshOption: RefreshIntervalOption,
    selectedDownloaderPackage: String,
    selectedDownloaderLabel: String,
    appManagedShareInstallEnabled: Boolean
) {
    val saveStateLabel = if (logicChanged) {
        stringResource(R.string.common_pending_save)
    } else {
        stringResource(R.string.common_synced)
    }
    val saveStateColor = if (logicChanged) {
        GitHubStatusPalette.Cache
    } else {
        GitHubStatusPalette.Update
    }
    SheetSummaryCard(
        title = stringResource(R.string.github_check_sheet_section_overview),
        badgeLabel = saveStateLabel,
        badgeColor = saveStateColor
    ) {
        GitHubCheckMetricRow {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_metric_refresh_interval_compact),
                value = stringResource(
                    R.string.github_check_sheet_value_refresh_interval_compact,
                    selectedRefreshOption.hours
                ),
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.38f,
                valueWeight = 0.62f
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_metric_track_count_compact),
                value = stringResource(
                    R.string.github_check_sheet_value_track_count_compact,
                    trackedCount
                ),
                modifier = Modifier.weight(1f),
                valueColor = if (trackedCount > 0) {
                    GitHubStatusPalette.Active
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.38f,
                valueWeight = 0.62f
            )
        }
        GitHubCheckMetricRow {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_metric_downloader_compact),
                value = if (appManagedShareInstallEnabled) {
                    stringResource(R.string.app_name)
                } else {
                    compactDownloaderLabel(
                        packageName = selectedDownloaderPackage,
                        fallbackLabel = selectedDownloaderLabel
                    )
                },
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.38f,
                valueWeight = 0.62f
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_metric_share_import_compact),
                value = stringResource(R.string.github_check_sheet_value_registered),
                modifier = Modifier.weight(1f),
                valueColor = MiuixTheme.colorScheme.primary,
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.38f,
                valueWeight = 0.62f
            )
        }
    }
}

@Composable
private fun compactDownloaderLabel(
    packageName: String,
    fallbackLabel: String
): String {
    return when (packageName) {
        "" -> stringResource(R.string.github_check_sheet_value_downloader_default_compact)
        systemDownloadManagerPackageName -> stringResource(R.string.github_check_sheet_value_downloader_builtin_compact)
        else -> fallbackLabel
    }
}

@Composable
private fun GitHubCheckMetricRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun GitHubCheckStrategySection(
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    preciseApkVersionEnabledInput: Boolean,
    scanSystemAppsByDefaultInput: Boolean,
    profileDepthInput: GitHubProfileDepth,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
    onAggressiveApkFilteringInputChange: (Boolean) -> Unit,
    onPreciseApkVersionEnabledInputChange: (Boolean) -> Unit,
    onScanSystemAppsByDefaultInputChange: (Boolean) -> Unit,
    onProfileDepthInputChange: (GitHubProfileDepth) -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_checks))
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_prerelease_check),
            summary = stringResource(R.string.github_check_sheet_summary_prerelease_check)
        ) {
            AppSwitch(
                checked = checkAllTrackedPreReleasesInput,
                onCheckedChange = onCheckAllTrackedPreReleasesInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_aggressive_filter),
            summary = stringResource(R.string.github_check_sheet_summary_aggressive_filter)
        ) {
            AppSwitch(
                checked = aggressiveApkFilteringInput,
                onCheckedChange = onAggressiveApkFilteringInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_precise_apk_version),
            summary = stringResource(R.string.github_check_sheet_summary_precise_apk_version)
        ) {
            AppSwitch(
                checked = preciseApkVersionEnabledInput,
                onCheckedChange = onPreciseApkVersionEnabledInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_scan_system_apps),
            summary = stringResource(R.string.github_check_sheet_summary_scan_system_apps)
        ) {
            AppSwitch(
                checked = scanSystemAppsByDefaultInput,
                onCheckedChange = onScanSystemAppsByDefaultInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_deep_profile),
            summary = stringResource(R.string.github_check_sheet_summary_deep_profile)
        ) {
            AppSwitch(
                checked = profileDepthInput == GitHubProfileDepth.Deep,
                onCheckedChange = { enabled ->
                    onProfileDepthInputChange(
                        if (enabled) GitHubProfileDepth.Deep else GitHubProfileDepth.Basic
                    )
                }
            )
        }
    }
}

@Composable
private fun GitHubCheckTransferSection(
    backdrop: LayerBackdrop,
    selectedDownloaderLabel: String,
    allDownloaderOptions: List<DownloaderOption>,
    preferredDownloaderPackageInput: String,
    showDownloaderPopup: Boolean,
    downloaderPopupAnchorBounds: IntRect?,
    shareImportFlowModeInput: GitHubShareImportFlowMode,
    appManagedShareInstallEnabledInput: Boolean,
    selectedOnlineShareTargetLabel: String,
    onlineShareTargetOptions: List<OnlineShareTargetOption>,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    onlineShareTargetPackageInput: String,
    showOnlineShareTargetPopup: Boolean,
    showShareImportFlowModePopup: Boolean,
    onlineShareTargetPopupAnchorBounds: IntRect?,
    shareImportFlowModePopupAnchorBounds: IntRect?,
    onPreferredDownloaderPackageInputChange: (String) -> Unit,
    onShareImportFlowModeInputChange: (GitHubShareImportFlowMode) -> Unit,
    onAppManagedShareInstallEnabledInputChange: (Boolean) -> Unit,
    onOnlineShareTargetPackageInputChange: (String) -> Unit,
    onShowDownloaderPopupChange: (Boolean) -> Unit,
    onShowOnlineShareTargetPopupChange: (Boolean) -> Unit,
    onShowShareImportFlowModePopupChange: (Boolean) -> Unit,
    onDownloaderPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOnlineShareTargetPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onShareImportFlowModePopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    val context = LocalContext.current
    val flowModeOptions = GitHubShareImportFlowMode.entries
    val flowModeLabels = flowModeOptions.map { mode ->
        context.resolveString(mode.labelRes())
    }
    val selectedFlowModeIndex = flowModeOptions
        .indexOf(shareImportFlowModeInput)
        .coerceAtLeast(0)
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_transfer))
    SheetDescriptionText(
        text = stringResource(R.string.github_check_sheet_section_transfer_summary)
    )
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_share_import_linkage),
            summary = stringResource(R.string.github_check_sheet_summary_share_import_linkage)
        ) {}
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_share_import_flow_mode),
            summary = stringResource(R.string.github_check_sheet_summary_share_import_flow_mode)
        ) {
            AppDropdownSelector(
                selectedText = flowModeLabels.getOrElse(selectedFlowModeIndex) {
                    flowModeLabels.firstOrNull().orEmpty()
                },
                options = flowModeLabels,
                selectedIndex = selectedFlowModeIndex,
                expanded = showShareImportFlowModePopup,
                anchorBounds = shareImportFlowModePopupAnchorBounds,
                onExpandedChange = onShowShareImportFlowModePopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    onShareImportFlowModeInputChange(flowModeOptions[selectedIndex])
                },
                onAnchorBoundsChange = onShareImportFlowModePopupAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.SheetAction
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_app_managed_share_install),
            summary = stringResource(
                R.string.github_check_sheet_summary_app_managed_share_install
            )
        ) {
            AppSwitch(
                checked = appManagedShareInstallEnabledInput,
                onCheckedChange = onAppManagedShareInstallEnabledInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_share_to_installer),
            summary = when {
                appManagedShareInstallEnabledInput -> {
                    stringResource(R.string.github_check_sheet_summary_share_managed_by_app)
                }

                installedOnlineShareTargets.isNotEmpty() -> {
                    stringResource(R.string.github_check_sheet_summary_share_available)
                }

                else -> {
                    stringResource(R.string.github_check_sheet_summary_share_unavailable)
                }
            }
        ) {
            AppDropdownSelector(
                selectedText = selectedOnlineShareTargetLabel,
                options = if (appManagedShareInstallEnabledInput) {
                    listOf(stringResource(R.string.app_name))
                } else {
                    onlineShareTargetOptions.map { it.label }
                },
                selectedIndex = onlineShareTargetOptions.indexOfFirst {
                    onlineShareTargetPackageInput == it.packageName
                }.coerceAtLeast(0).takeUnless { appManagedShareInstallEnabledInput } ?: 0,
                expanded = showOnlineShareTargetPopup && !appManagedShareInstallEnabledInput,
                anchorBounds = onlineShareTargetPopupAnchorBounds,
                onExpandedChange = onShowOnlineShareTargetPopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    onOnlineShareTargetPackageInputChange(
                        onlineShareTargetOptions[selectedIndex].packageName
                    )
                },
                onAnchorBoundsChange = onOnlineShareTargetPopupAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                enabled = !appManagedShareInstallEnabledInput
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_downloader),
            summary = stringResource(
                if (appManagedShareInstallEnabledInput) {
                    R.string.github_check_sheet_summary_downloader_managed_by_app
                } else {
                    R.string.github_check_sheet_summary_downloader
                }
            )
        ) {
            AppDropdownSelector(
                selectedText = selectedDownloaderLabel,
                options = if (appManagedShareInstallEnabledInput) {
                    listOf(stringResource(R.string.app_name))
                } else {
                    allDownloaderOptions.map { it.label }
                },
                selectedIndex = allDownloaderOptions.indexOfFirst {
                    preferredDownloaderPackageInput == it.packageName
                }.coerceAtLeast(0).takeUnless { appManagedShareInstallEnabledInput } ?: 0,
                expanded = showDownloaderPopup && !appManagedShareInstallEnabledInput,
                anchorBounds = downloaderPopupAnchorBounds,
                onExpandedChange = onShowDownloaderPopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    onPreferredDownloaderPackageInputChange(
                        allDownloaderOptions[selectedIndex].packageName
                    )
                },
                onAnchorBoundsChange = onDownloaderPopupAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                enabled = !appManagedShareInstallEnabledInput
            )
        }
    }
}

@Composable
private fun GitHubCheckEnhancementSection(
    decisionAssistEnabledInput: Boolean,
    repositoryHealthCardEnabledInput: Boolean,
    apkTrustCheckEnabledInput: Boolean,
    onDecisionAssistEnabledInputChange: (Boolean) -> Unit,
    onRepositoryHealthCardEnabledInputChange: (Boolean) -> Unit,
    onApkTrustCheckEnabledInputChange: (Boolean) -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_enhancements))
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_decision_assist),
            summary = stringResource(R.string.github_check_sheet_summary_decision_assist)
        ) {
            AppSwitch(
                checked = decisionAssistEnabledInput,
                onCheckedChange = onDecisionAssistEnabledInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_repository_health),
            summary = stringResource(R.string.github_check_sheet_summary_repository_health)
        ) {
            AppSwitch(
                checked = repositoryHealthCardEnabledInput,
                onCheckedChange = onRepositoryHealthCardEnabledInputChange,
                enabled = decisionAssistEnabledInput
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_apk_trust),
            summary = stringResource(R.string.github_check_sheet_summary_apk_trust)
        ) {
            AppSwitch(
                checked = apkTrustCheckEnabledInput,
                onCheckedChange = onApkTrustCheckEnabledInputChange,
                enabled = decisionAssistEnabledInput
            )
        }
    }
}

@Composable
private fun GitHubCheckTracksSection(
    backdrop: LayerBackdrop,
    hasKeiOsSelfTrack: Boolean,
    exportInProgress: Boolean,
    importInProgress: Boolean,
    onEnsureKeiOsSelfTrack: () -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_tracks))
    SheetDescriptionText(
        text = stringResource(R.string.github_check_sheet_section_tracks_summary)
    )
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_track_current_app),
            summary = if (hasKeiOsSelfTrack) {
                stringResource(R.string.github_check_sheet_summary_track_current_app_exists)
            } else {
                stringResource(R.string.github_check_sheet_summary_track_current_app_missing)
            }
        ) {
            AppLiquidTextButton(
                backdrop = backdrop,
                text = if (hasKeiOsSelfTrack) {
                    stringResource(R.string.github_check_sheet_action_track_current_app_exists)
                } else {
                    stringResource(R.string.github_check_sheet_action_track_current_app)
                },
                onClick = onEnsureKeiOsSelfTrack,
                enabled = !hasKeiOsSelfTrack && !exportInProgress && !importInProgress,
                variant = GlassVariant.SheetAction
            )
        }
    }
}

@Composable
private fun GitHubCheckDebugSection(
    backdrop: LayerBackdrop,
    onSendDebugActionsUpdateNotification: () -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_debug))
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_debug_actions_update_notification),
            summary = stringResource(R.string.github_check_sheet_summary_debug_actions_update_notification)
        ) {
            AppLiquidTextButton(
                backdrop = backdrop,
                text = stringResource(R.string.github_check_sheet_action_debug_actions_update_notification),
                onClick = onSendDebugActionsUpdateNotification,
                variant = GlassVariant.SheetAction
            )
        }
    }
}

private fun GitHubShareImportFlowMode.labelRes(): Int {
    return when (this) {
        GitHubShareImportFlowMode.SheetAssisted -> R.string.github_share_import_flow_mode_sheet
        GitHubShareImportFlowMode.NotificationFirst -> R.string.github_share_import_flow_mode_notification
    }
}

@Composable
private fun GitHubCheckNotesSection() {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_notes))
    SheetSectionCard {
        SheetDescriptionText(
            text = stringResource(R.string.github_check_sheet_note_transfer)
        )
        SheetDescriptionText(
            text = stringResource(R.string.github_check_sheet_note_1)
        )
        SheetDescriptionText(
            text = stringResource(R.string.github_check_sheet_note_2)
        )
        SheetDescriptionText(
            text = stringResource(R.string.github_check_sheet_note_3)
        )
    }
}
