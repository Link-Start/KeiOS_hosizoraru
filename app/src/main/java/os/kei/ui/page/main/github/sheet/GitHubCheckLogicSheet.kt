package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.core.install.ShizukuRuntimeCapabilities
import os.kei.feature.github.model.GitHubApkInstallUiMode
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
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubCheckLogicSheet(
    show: Boolean,
    category: GitHubCheckSheetCategory,
    backdrop: LayerBackdrop,
    lookupConfig: GitHubLookupConfig,
    trackedCount: Int,
    refreshIntervalHours: Int,
    refreshIntervalHoursInput: Int,
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    preciseApkVersionEnabledInput: Boolean,
    profileDepthInput: GitHubProfileDepth,
    shareImportLinkageEnabledInput: Boolean,
    shareImportFlowModeInput: GitHubShareImportFlowMode,
    apkInstallUiModeInput: GitHubApkInstallUiMode,
    onlineShareTargetPackageInput: String,
    preferredDownloaderPackageInput: String,
    decisionAssistEnabledInput: Boolean,
    repositoryHealthCardEnabledInput: Boolean,
    apkTrustCheckEnabledInput: Boolean,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    showCheckLogicIntervalPopup: Boolean,
    showDownloaderPopup: Boolean,
    showOnlineShareTargetPopup: Boolean,
    showShareImportFlowModePopup: Boolean,
    showApkInstallUiModePopup: Boolean,
    checkLogicIntervalPopupAnchorBounds: IntRect?,
    downloaderPopupAnchorBounds: IntRect?,
    onlineShareTargetPopupAnchorBounds: IntRect?,
    shareImportFlowModePopupAnchorBounds: IntRect?,
    apkInstallUiModePopupAnchorBounds: IntRect?,
    downloaderOptions: List<DownloaderOption>,
    hasKeiOsSelfTrack: Boolean,
    exportInProgress: Boolean,
    importInProgress: Boolean,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onCategoryChange: (GitHubCheckSheetCategory) -> Unit,
    onEnsureKeiOsSelfTrack: () -> Unit,
    onExportTrackedItems: () -> Unit,
    onImportTrackedItems: () -> Unit,
    onOpenStarImport: () -> Unit,
    onRefreshIntervalHoursInputChange: (Int) -> Unit,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
    onAggressiveApkFilteringInputChange: (Boolean) -> Unit,
    onPreciseApkVersionEnabledInputChange: (Boolean) -> Unit,
    onProfileDepthInputChange: (GitHubProfileDepth) -> Unit,
    onShareImportLinkageEnabledInputChange: (Boolean) -> Unit,
    onShareImportFlowModeInputChange: (GitHubShareImportFlowMode) -> Unit,
    onApkInstallUiModeInputChange: (GitHubApkInstallUiMode) -> Unit,
    onPreferredDownloaderPackageInputChange: (String) -> Unit,
    onOnlineShareTargetPackageInputChange: (String) -> Unit,
    onDecisionAssistEnabledInputChange: (Boolean) -> Unit,
    onRepositoryHealthCardEnabledInputChange: (Boolean) -> Unit,
    onApkTrustCheckEnabledInputChange: (Boolean) -> Unit,
    onShowCheckLogicIntervalPopupChange: (Boolean) -> Unit,
    onShowDownloaderPopupChange: (Boolean) -> Unit,
    onShowOnlineShareTargetPopupChange: (Boolean) -> Unit,
    onShowShareImportFlowModePopupChange: (Boolean) -> Unit,
    onShowApkInstallUiModePopupChange: (Boolean) -> Unit,
    onCheckLogicIntervalPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onDownloaderPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOnlineShareTargetPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onShareImportFlowModePopupAnchorBoundsChange: (IntRect?) -> Unit,
    onApkInstallUiModePopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    val context = LocalContext.current
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(category.titleRes),
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
        val selectedRefreshOption = RefreshIntervalOption.fromHours(refreshIntervalHoursInput)
        val allDownloaderOptions = remember(downloaderOptions) {
            listOf(
                systemDefaultDownloaderOption(context),
                systemDownloadManagerOption(context)
            ) + downloaderOptions
        }
        val onlineShareTargetOptions = remember(installedOnlineShareTargets) {
            listOf(noOnlineShareTargetOption(context)) + installedOnlineShareTargets
        }
        val selectedDownloaderLabel = allDownloaderOptions.firstOrNull {
            it.packageName == preferredDownloaderPackageInput
        }?.label ?: systemDefaultDownloaderOption(context).label
        val selectedOnlineShareTargetLabel = onlineShareTargetOptions.firstOrNull {
            it.packageName == onlineShareTargetPackageInput
        }?.label ?: noOnlineShareTargetOption(context).label
        val installUiLabel = context.getString(apkInstallUiModeInput.labelRes())
        val shareImportFlowModeLabel = context.getString(shareImportFlowModeInput.labelRes())
        val shizukuInstallCapability = remember(showApkInstallUiModePopup, category) {
            ShizukuRuntimeCapabilities().current()
        }
        val logicChanged = refreshIntervalHoursInput != refreshIntervalHours ||
                checkAllTrackedPreReleasesInput != lookupConfig.checkAllTrackedPreReleases ||
                aggressiveApkFilteringInput != lookupConfig.aggressiveApkFiltering ||
                preciseApkVersionEnabledInput != lookupConfig.preciseApkVersionEnabled ||
                profileDepthInput != lookupConfig.profileDepth ||
                shareImportLinkageEnabledInput != lookupConfig.shareImportLinkageEnabled ||
                shareImportFlowModeInput != lookupConfig.shareImportFlowMode ||
                apkInstallUiModeInput != lookupConfig.apkInstallUiMode ||
                onlineShareTargetPackageInput != lookupConfig.onlineShareTargetPackage ||
                preferredDownloaderPackageInput != lookupConfig.preferredDownloaderPackage ||
                decisionAssistEnabledInput != lookupConfig.decisionAssistEnabled ||
                repositoryHealthCardEnabledInput != lookupConfig.repositoryHealthCardEnabled ||
                apkTrustCheckEnabledInput != lookupConfig.apkTrustCheckEnabled

        SheetContentColumn(verticalSpacing = 10.dp) {
            GitHubCheckCategoryHeader(
                backdrop = backdrop,
                category = category,
                logicChanged = logicChanged,
                categories = GitHubCheckSheetCategory.entries,
                trackedCount = trackedCount,
                selectedRefreshOption = selectedRefreshOption,
                checkAllTrackedPreReleasesInput = checkAllTrackedPreReleasesInput,
                aggressiveApkFilteringInput = aggressiveApkFilteringInput,
                preciseApkVersionEnabledInput = preciseApkVersionEnabledInput,
                profileDepthInput = profileDepthInput,
                selectedDownloaderPackage = preferredDownloaderPackageInput,
                selectedDownloaderLabel = selectedDownloaderLabel,
                installUiLabel = installUiLabel,
                shizukuInstallCapability = shizukuInstallCapability,
                shareImportLinkageEnabled = shareImportLinkageEnabledInput,
                shareImportFlowModeLabel = shareImportFlowModeLabel,
                selectedOnlineShareTargetLabel = selectedOnlineShareTargetLabel,
                onlineShareTargetPackageInput = onlineShareTargetPackageInput,
                decisionAssistEnabledInput = decisionAssistEnabledInput,
                repositoryHealthCardEnabledInput = repositoryHealthCardEnabledInput,
                apkTrustCheckEnabledInput = apkTrustCheckEnabledInput,
                hasKeiOsSelfTrack = hasKeiOsSelfTrack,
                exportInProgress = exportInProgress,
                importInProgress = importInProgress,
                onCategoryChange = onCategoryChange
            )
            when (category) {
                GitHubCheckSheetCategory.UpdateChecks -> GitHubCheckStrategySection(
                    backdrop = backdrop,
                    selectedRefreshOption = selectedRefreshOption,
                    showCheckLogicIntervalPopup = showCheckLogicIntervalPopup,
                    checkLogicIntervalPopupAnchorBounds = checkLogicIntervalPopupAnchorBounds,
                    checkAllTrackedPreReleasesInput = checkAllTrackedPreReleasesInput,
                    aggressiveApkFilteringInput = aggressiveApkFilteringInput,
                    preciseApkVersionEnabledInput = preciseApkVersionEnabledInput,
                    onRefreshIntervalHoursInputChange = onRefreshIntervalHoursInputChange,
                    onCheckAllTrackedPreReleasesInputChange = onCheckAllTrackedPreReleasesInputChange,
                    onAggressiveApkFilteringInputChange = onAggressiveApkFilteringInputChange,
                    onPreciseApkVersionEnabledInputChange = onPreciseApkVersionEnabledInputChange,
                    onShowCheckLogicIntervalPopupChange = onShowCheckLogicIntervalPopupChange,
                    onCheckLogicIntervalPopupAnchorBoundsChange = onCheckLogicIntervalPopupAnchorBoundsChange
                )

                GitHubCheckSheetCategory.InstallFlow -> GitHubCheckDownloadInstallSection(
                    backdrop = backdrop,
                    selectedDownloaderLabel = selectedDownloaderLabel,
                    allDownloaderOptions = allDownloaderOptions,
                    preferredDownloaderPackageInput = preferredDownloaderPackageInput,
                    showDownloaderPopup = showDownloaderPopup,
                    downloaderPopupAnchorBounds = downloaderPopupAnchorBounds,
                    apkInstallUiModeInput = apkInstallUiModeInput,
                    showApkInstallUiModePopup = showApkInstallUiModePopup,
                    apkInstallUiModePopupAnchorBounds = apkInstallUiModePopupAnchorBounds,
                    onPreferredDownloaderPackageInputChange = onPreferredDownloaderPackageInputChange,
                    onApkInstallUiModeInputChange = onApkInstallUiModeInputChange,
                    onShowDownloaderPopupChange = onShowDownloaderPopupChange,
                    onShowApkInstallUiModePopupChange = onShowApkInstallUiModePopupChange,
                    onDownloaderPopupAnchorBoundsChange = onDownloaderPopupAnchorBoundsChange,
                    onApkInstallUiModePopupAnchorBoundsChange = onApkInstallUiModePopupAnchorBoundsChange,
                    shizukuInstallCapability = shizukuInstallCapability
                )

                GitHubCheckSheetCategory.ShareImport -> GitHubCheckShareImportSection(
                    backdrop = backdrop,
                    shareImportLinkageEnabledInput = shareImportLinkageEnabledInput,
                    shareImportFlowModeInput = shareImportFlowModeInput,
                    selectedOnlineShareTargetLabel = selectedOnlineShareTargetLabel,
                    onlineShareTargetOptions = onlineShareTargetOptions,
                    installedOnlineShareTargets = installedOnlineShareTargets,
                    onlineShareTargetPackageInput = onlineShareTargetPackageInput,
                    showOnlineShareTargetPopup = showOnlineShareTargetPopup,
                    showShareImportFlowModePopup = showShareImportFlowModePopup,
                    onlineShareTargetPopupAnchorBounds = onlineShareTargetPopupAnchorBounds,
                    shareImportFlowModePopupAnchorBounds = shareImportFlowModePopupAnchorBounds,
                    onShareImportLinkageEnabledInputChange = onShareImportLinkageEnabledInputChange,
                    onShareImportFlowModeInputChange = onShareImportFlowModeInputChange,
                    onOnlineShareTargetPackageInputChange = onOnlineShareTargetPackageInputChange,
                    onShowOnlineShareTargetPopupChange = onShowOnlineShareTargetPopupChange,
                    onShowShareImportFlowModePopupChange = onShowShareImportFlowModePopupChange,
                    onOnlineShareTargetPopupAnchorBoundsChange = onOnlineShareTargetPopupAnchorBoundsChange,
                    onShareImportFlowModePopupAnchorBoundsChange = onShareImportFlowModePopupAnchorBoundsChange
                )

                GitHubCheckSheetCategory.Insights -> GitHubCheckEnhancementSection(
                    profileDepthInput = profileDepthInput,
                    decisionAssistEnabledInput = decisionAssistEnabledInput,
                    repositoryHealthCardEnabledInput = repositoryHealthCardEnabledInput,
                    apkTrustCheckEnabledInput = apkTrustCheckEnabledInput,
                    onProfileDepthInputChange = onProfileDepthInputChange,
                    onDecisionAssistEnabledInputChange = onDecisionAssistEnabledInputChange,
                    onRepositoryHealthCardEnabledInputChange = onRepositoryHealthCardEnabledInputChange,
                    onApkTrustCheckEnabledInputChange = onApkTrustCheckEnabledInputChange
                )

                GitHubCheckSheetCategory.TrackData -> GitHubCheckTracksSection(
                    backdrop = backdrop,
                    hasKeiOsSelfTrack = hasKeiOsSelfTrack,
                    exportInProgress = exportInProgress,
                    importInProgress = importInProgress,
                    onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack,
                    onExportTrackedItems = onExportTrackedItems,
                    onImportTrackedItems = onImportTrackedItems,
                    onOpenStarImport = onOpenStarImport
                )

                GitHubCheckSheetCategory.Notes -> GitHubCheckNotesSection()
            }
        }
    }
}

@Composable
private fun GitHubCheckCategoryHeader(
    backdrop: LayerBackdrop,
    category: GitHubCheckSheetCategory,
    logicChanged: Boolean,
    categories: List<GitHubCheckSheetCategory>,
    trackedCount: Int,
    selectedRefreshOption: RefreshIntervalOption,
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    preciseApkVersionEnabledInput: Boolean,
    profileDepthInput: GitHubProfileDepth,
    selectedDownloaderPackage: String,
    selectedDownloaderLabel: String,
    installUiLabel: String,
    shizukuInstallCapability: os.kei.core.install.ShizukuInstallCapability,
    shareImportLinkageEnabled: Boolean,
    shareImportFlowModeLabel: String,
    selectedOnlineShareTargetLabel: String,
    onlineShareTargetPackageInput: String,
    decisionAssistEnabledInput: Boolean,
    repositoryHealthCardEnabledInput: Boolean,
    apkTrustCheckEnabledInput: Boolean,
    hasKeiOsSelfTrack: Boolean,
    exportInProgress: Boolean,
    importInProgress: Boolean,
    onCategoryChange: (GitHubCheckSheetCategory) -> Unit
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
        title = stringResource(category.titleRes),
        badgeLabel = saveStateLabel,
        badgeColor = saveStateColor
    ) {
        SheetDescriptionText(
            text = stringResource(category.summaryRes),
            maxLines = 3
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { item ->
                val selected = item == category
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(item.menuLabelRes),
                    onClick = { onCategoryChange(item) },
                    variant = if (selected) GlassVariant.SheetPrimaryAction else GlassVariant.SheetAction,
                    containerColor = if (selected) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        null
                    },
                    textColor = if (selected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    },
                    minHeight = 32.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1
                )
            }
        }
        GitHubCheckCategoryMetrics(
            category = category,
            backdrop = backdrop,
            trackedCount = trackedCount,
            selectedRefreshOption = selectedRefreshOption,
            checkAllTrackedPreReleasesInput = checkAllTrackedPreReleasesInput,
            aggressiveApkFilteringInput = aggressiveApkFilteringInput,
            preciseApkVersionEnabledInput = preciseApkVersionEnabledInput,
            profileDepthInput = profileDepthInput,
            selectedDownloaderPackage = selectedDownloaderPackage,
            selectedDownloaderLabel = selectedDownloaderLabel,
            installUiLabel = installUiLabel,
            shizukuInstallCapability = shizukuInstallCapability,
            shareImportLinkageEnabled = shareImportLinkageEnabled,
            shareImportFlowModeLabel = shareImportFlowModeLabel,
            selectedOnlineShareTargetLabel = selectedOnlineShareTargetLabel,
            onlineShareTargetPackageInput = onlineShareTargetPackageInput,
            decisionAssistEnabledInput = decisionAssistEnabledInput,
            repositoryHealthCardEnabledInput = repositoryHealthCardEnabledInput,
            apkTrustCheckEnabledInput = apkTrustCheckEnabledInput,
            hasKeiOsSelfTrack = hasKeiOsSelfTrack,
            exportInProgress = exportInProgress,
            importInProgress = importInProgress
        )
    }
}

@Composable
private fun GitHubCheckCategoryMetrics(
    category: GitHubCheckSheetCategory,
    backdrop: LayerBackdrop,
    trackedCount: Int,
    selectedRefreshOption: RefreshIntervalOption,
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    preciseApkVersionEnabledInput: Boolean,
    profileDepthInput: GitHubProfileDepth,
    selectedDownloaderPackage: String,
    selectedDownloaderLabel: String,
    installUiLabel: String,
    shizukuInstallCapability: os.kei.core.install.ShizukuInstallCapability,
    shareImportLinkageEnabled: Boolean,
    shareImportFlowModeLabel: String,
    selectedOnlineShareTargetLabel: String,
    onlineShareTargetPackageInput: String,
    decisionAssistEnabledInput: Boolean,
    repositoryHealthCardEnabledInput: Boolean,
    apkTrustCheckEnabledInput: Boolean,
    hasKeiOsSelfTrack: Boolean,
    exportInProgress: Boolean,
    importInProgress: Boolean
) {
    when (category) {
        GitHubCheckSheetCategory.UpdateChecks -> {
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_refresh_interval_compact),
                    value = stringResource(
                        R.string.github_check_sheet_value_refresh_interval_compact,
                        selectedRefreshOption.hours
                    ),
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_prerelease_compact),
                    value = enabledLabel(checkAllTrackedPreReleasesInput),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(checkAllTrackedPreReleasesInput),
                    backdrop = backdrop
                )
            }
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_precise_version_compact),
                    value = enabledLabel(preciseApkVersionEnabledInput),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(preciseApkVersionEnabledInput),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_filter_compact),
                    value = enabledLabel(aggressiveApkFilteringInput),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(aggressiveApkFilteringInput),
                    backdrop = backdrop
                )
            }
        }

        GitHubCheckSheetCategory.InstallFlow -> {
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_downloader_compact),
                    value = compactDownloaderLabel(
                        packageName = selectedDownloaderPackage,
                        fallbackLabel = selectedDownloaderLabel
                    ),
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_install_ui_compact),
                    value = installUiLabel,
                    modifier = Modifier.weight(1f),
                    valueColor = MiuixTheme.colorScheme.primary,
                    backdrop = backdrop
                )
            }
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_shizuku_compact),
                    value = if (shizukuInstallCapability.sessionReady) {
                        stringResource(R.string.github_check_sheet_value_ready)
                    } else {
                        stringResource(R.string.github_check_sheet_value_attention)
                    },
                    modifier = Modifier.weight(1f),
                    valueColor = if (shizukuInstallCapability.sessionReady) {
                        GitHubStatusPalette.Update
                    } else {
                        GitHubStatusPalette.Cache
                    },
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_track_count_compact),
                    value = stringResource(
                        R.string.github_check_sheet_value_track_count_compact,
                        trackedCount
                    ),
                    modifier = Modifier.weight(1f),
                    valueColor = trackedCountColor(trackedCount),
                    backdrop = backdrop
                )
            }
        }

        GitHubCheckSheetCategory.ShareImport -> {
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_share_import_compact),
                    value = enabledLabel(shareImportLinkageEnabled),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(shareImportLinkageEnabled),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_share_flow_compact),
                    value = shareImportFlowModeLabel,
                    modifier = Modifier.weight(1f),
                    valueColor = MiuixTheme.colorScheme.primary,
                    backdrop = backdrop
                )
            }
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_online_target_compact),
                    value = if (onlineShareTargetPackageInput.isBlank()) {
                        stringResource(R.string.github_check_sheet_value_unset)
                    } else {
                        selectedOnlineShareTargetLabel
                    },
                    modifier = Modifier.weight(1f),
                    valueColor = if (onlineShareTargetPackageInput.isBlank()) {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    } else {
                        GitHubStatusPalette.Active
                    },
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_install_ui_compact),
                    value = installUiLabel,
                    modifier = Modifier.weight(1f),
                    valueColor = MiuixTheme.colorScheme.primary,
                    backdrop = backdrop
                )
            }
        }

        GitHubCheckSheetCategory.Insights -> {
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_profile_compact),
                    value = stringResource(
                        if (profileDepthInput == GitHubProfileDepth.Deep) {
                            R.string.github_check_sheet_value_profile_deep
                        } else {
                            R.string.github_check_sheet_value_profile_basic
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    valueColor = if (profileDepthInput == GitHubProfileDepth.Deep) {
                        GitHubStatusPalette.Cache
                    } else {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    },
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_assist_compact),
                    value = enabledLabel(decisionAssistEnabledInput),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(decisionAssistEnabledInput),
                    backdrop = backdrop
                )
            }
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_health_compact),
                    value = enabledLabel(repositoryHealthCardEnabledInput),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(repositoryHealthCardEnabledInput),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_trust_compact),
                    value = enabledLabel(apkTrustCheckEnabledInput),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(apkTrustCheckEnabledInput),
                    backdrop = backdrop
                )
            }
        }

        GitHubCheckSheetCategory.TrackData -> {
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_track_count_compact),
                    value = stringResource(
                        R.string.github_check_sheet_value_track_count_compact,
                        trackedCount
                    ),
                    modifier = Modifier.weight(1f),
                    valueColor = trackedCountColor(trackedCount),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_self_track_compact),
                    value = if (hasKeiOsSelfTrack) {
                        stringResource(R.string.github_check_sheet_value_enabled)
                    } else {
                        stringResource(R.string.github_check_sheet_value_attention)
                    },
                    modifier = Modifier.weight(1f),
                    valueColor = if (hasKeiOsSelfTrack) {
                        GitHubStatusPalette.Update
                    } else {
                        GitHubStatusPalette.Cache
                    },
                    backdrop = backdrop
                )
            }
            GitHubCheckMetricRow {
                val transferBusy = exportInProgress || importInProgress
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_transfer_compact),
                    value = if (transferBusy) {
                        stringResource(R.string.github_check_sheet_value_busy)
                    } else {
                        stringResource(R.string.github_check_sheet_value_idle)
                    },
                    modifier = Modifier.weight(1f),
                    valueColor = if (transferBusy) GitHubStatusPalette.Cache else GitHubStatusPalette.Active,
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_share_import_compact),
                    value = enabledLabel(shareImportLinkageEnabled),
                    modifier = Modifier.weight(1f),
                    valueColor = enabledColor(shareImportLinkageEnabled),
                    backdrop = backdrop
                )
            }
        }

        GitHubCheckSheetCategory.Notes -> {
            GitHubCheckMetricRow {
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_refresh_interval_compact),
                    value = stringResource(
                        R.string.github_check_sheet_value_refresh_interval_compact,
                        selectedRefreshOption.hours
                    ),
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop
                )
                GitHubCheckMetric(
                    label = stringResource(R.string.github_check_sheet_metric_track_count_compact),
                    value = stringResource(
                        R.string.github_check_sheet_value_track_count_compact,
                        trackedCount
                    ),
                    modifier = Modifier.weight(1f),
                    valueColor = trackedCountColor(trackedCount),
                    backdrop = backdrop
                )
            }
        }
    }
}

@Composable
private fun GitHubCheckMetric(
    label: String,
    value: String,
    modifier: Modifier,
    backdrop: LayerBackdrop,
    valueColor: Color = MiuixTheme.colorScheme.onBackground
) {
    GitHubOverviewMetricItem(
        label = label,
        value = value,
        modifier = modifier,
        valueColor = valueColor,
        backdrop = backdrop,
        labelMaxLines = 1,
        valueMaxLines = 1,
        labelWeight = 0.38f,
        valueWeight = 0.62f
    )
}

@Composable
private fun enabledLabel(enabled: Boolean): String {
    return stringResource(
        if (enabled) {
            R.string.github_check_sheet_value_enabled
        } else {
            R.string.github_check_sheet_value_disabled
        }
    )
}

@Composable
private fun enabledColor(enabled: Boolean): Color {
    return if (enabled) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant
    }
}

@Composable
private fun trackedCountColor(trackedCount: Int): Color {
    return if (trackedCount > 0) {
        GitHubStatusPalette.Active
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant
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
