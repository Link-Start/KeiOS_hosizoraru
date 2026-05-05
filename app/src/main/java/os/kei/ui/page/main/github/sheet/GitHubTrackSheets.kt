package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.github.GitHubOverviewMetricItem
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.RefreshIntervalOption
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.query.noOnlineShareTargetOption
import os.kei.ui.page.main.github.query.systemDefaultDownloaderOption
import os.kei.ui.page.main.github.query.systemDownloadManagerOption
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
import os.kei.ui.page.main.widget.sheet.SheetFieldBlock
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
    refreshIntervalHoursInput: Int,
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    shareImportLinkageEnabledInput: Boolean,
    onlineShareTargetPackageInput: String,
    preferredDownloaderPackageInput: String,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    showCheckLogicIntervalPopup: Boolean,
    showDownloaderPopup: Boolean,
    showOnlineShareTargetPopup: Boolean,
    checkLogicIntervalPopupAnchorBounds: IntRect?,
    downloaderPopupAnchorBounds: IntRect?,
    onlineShareTargetPopupAnchorBounds: IntRect?,
    downloaderOptions: List<DownloaderOption>,
    hasKeiOsSelfTrack: Boolean,
    exportInProgress: Boolean,
    importInProgress: Boolean,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onEnsureKeiOsSelfTrack: () -> Unit,
    onExportTrackedItems: () -> Unit,
    onImportTrackedItems: () -> Unit,
    onOpenStarImport: () -> Unit,
    onRefreshIntervalHoursInputChange: (Int) -> Unit,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
    onAggressiveApkFilteringInputChange: (Boolean) -> Unit,
    onShareImportLinkageEnabledInputChange: (Boolean) -> Unit,
    onPreferredDownloaderPackageInputChange: (String) -> Unit,
    onOnlineShareTargetPackageInputChange: (String) -> Unit,
    onShowCheckLogicIntervalPopupChange: (Boolean) -> Unit,
    onShowDownloaderPopupChange: (Boolean) -> Unit,
    onShowOnlineShareTargetPopupChange: (Boolean) -> Unit,
    onCheckLogicIntervalPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onDownloaderPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOnlineShareTargetPopupAnchorBoundsChange: (IntRect?) -> Unit
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
        val selectedRefreshOption = RefreshIntervalOption.fromHours(refreshIntervalHoursInput)
        val allDownloaderOptions = remember(downloaderOptions) {
            listOf(systemDefaultDownloaderOption(context), systemDownloadManagerOption(context)) + downloaderOptions
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
        val logicChanged = refreshIntervalHoursInput != refreshIntervalHours ||
            checkAllTrackedPreReleasesInput != lookupConfig.checkAllTrackedPreReleases ||
            aggressiveApkFilteringInput != lookupConfig.aggressiveApkFiltering ||
            shareImportLinkageEnabledInput != lookupConfig.shareImportLinkageEnabled ||
            onlineShareTargetPackageInput != lookupConfig.onlineShareTargetPackage ||
            preferredDownloaderPackageInput != lookupConfig.preferredDownloaderPackage

        SheetContentColumn(verticalSpacing = 10.dp) {
            GitHubCheckOverviewSection(
                backdrop = backdrop,
                logicChanged = logicChanged,
                trackedCount = trackedCount,
                selectedRefreshOption = selectedRefreshOption,
                selectedDownloaderLabel = selectedDownloaderLabel,
                shareImportLinkageEnabled = shareImportLinkageEnabledInput
            )
            GitHubCheckStrategySection(
                backdrop = backdrop,
                selectedRefreshOption = selectedRefreshOption,
                showCheckLogicIntervalPopup = showCheckLogicIntervalPopup,
                checkLogicIntervalPopupAnchorBounds = checkLogicIntervalPopupAnchorBounds,
                checkAllTrackedPreReleasesInput = checkAllTrackedPreReleasesInput,
                aggressiveApkFilteringInput = aggressiveApkFilteringInput,
                onRefreshIntervalHoursInputChange = onRefreshIntervalHoursInputChange,
                onCheckAllTrackedPreReleasesInputChange = onCheckAllTrackedPreReleasesInputChange,
                onAggressiveApkFilteringInputChange = onAggressiveApkFilteringInputChange,
                onShowCheckLogicIntervalPopupChange = onShowCheckLogicIntervalPopupChange,
                onCheckLogicIntervalPopupAnchorBoundsChange = onCheckLogicIntervalPopupAnchorBoundsChange
            )
            GitHubCheckTransferSection(
                backdrop = backdrop,
                selectedDownloaderLabel = selectedDownloaderLabel,
                allDownloaderOptions = allDownloaderOptions,
                preferredDownloaderPackageInput = preferredDownloaderPackageInput,
                showDownloaderPopup = showDownloaderPopup,
                downloaderPopupAnchorBounds = downloaderPopupAnchorBounds,
                shareImportLinkageEnabledInput = shareImportLinkageEnabledInput,
                selectedOnlineShareTargetLabel = selectedOnlineShareTargetLabel,
                onlineShareTargetOptions = onlineShareTargetOptions,
                installedOnlineShareTargets = installedOnlineShareTargets,
                onlineShareTargetPackageInput = onlineShareTargetPackageInput,
                showOnlineShareTargetPopup = showOnlineShareTargetPopup,
                onlineShareTargetPopupAnchorBounds = onlineShareTargetPopupAnchorBounds,
                onPreferredDownloaderPackageInputChange = onPreferredDownloaderPackageInputChange,
                onShareImportLinkageEnabledInputChange = onShareImportLinkageEnabledInputChange,
                onOnlineShareTargetPackageInputChange = onOnlineShareTargetPackageInputChange,
                onShowDownloaderPopupChange = onShowDownloaderPopupChange,
                onShowOnlineShareTargetPopupChange = onShowOnlineShareTargetPopupChange,
                onDownloaderPopupAnchorBoundsChange = onDownloaderPopupAnchorBoundsChange,
                onOnlineShareTargetPopupAnchorBoundsChange = onOnlineShareTargetPopupAnchorBoundsChange
            )
            GitHubCheckTracksSection(
                backdrop = backdrop,
                hasKeiOsSelfTrack = hasKeiOsSelfTrack,
                exportInProgress = exportInProgress,
                importInProgress = importInProgress,
                onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack,
                onExportTrackedItems = onExportTrackedItems,
                onImportTrackedItems = onImportTrackedItems,
                onOpenStarImport = onOpenStarImport
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
    selectedDownloaderLabel: String,
    shareImportLinkageEnabled: Boolean
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
                label = stringResource(R.string.github_check_sheet_label_refresh_interval),
                value = stringResource(selectedRefreshOption.labelRes),
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.46f,
                valueWeight = 0.54f
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_label_track_count),
                value = stringResource(R.string.github_check_sheet_value_track_count, trackedCount),
                modifier = Modifier.weight(1f),
                valueColor = if (trackedCount > 0) {
                    GitHubStatusPalette.Active
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.46f,
                valueWeight = 0.54f
            )
        }
        GitHubCheckMetricRow {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_label_downloader),
                value = selectedDownloaderLabel,
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.42f,
                valueWeight = 0.58f
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_check_sheet_metric_share_import),
                value = stringResource(
                    if (shareImportLinkageEnabled) {
                        R.string.github_check_sheet_value_enabled
                    } else {
                        R.string.github_check_sheet_value_disabled
                    }
                ),
                modifier = Modifier.weight(1f),
                valueColor = if (shareImportLinkageEnabled) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
                backdrop = backdrop,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.46f,
                valueWeight = 0.54f
            )
        }
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
    backdrop: LayerBackdrop,
    selectedRefreshOption: RefreshIntervalOption,
    showCheckLogicIntervalPopup: Boolean,
    checkLogicIntervalPopupAnchorBounds: IntRect?,
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    onRefreshIntervalHoursInputChange: (Int) -> Unit,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
    onAggressiveApkFilteringInputChange: (Boolean) -> Unit,
    onShowCheckLogicIntervalPopupChange: (Boolean) -> Unit,
    onCheckLogicIntervalPopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    val context = LocalContext.current
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_checks))
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_refresh_interval),
            summary = stringResource(R.string.github_check_sheet_summary_refresh_interval)
        ) {
            AppDropdownSelector(
                selectedText = stringResource(selectedRefreshOption.labelRes),
                options = RefreshIntervalOption.entries.map { option ->
                    context.getString(option.labelRes)
                },
                selectedIndex = RefreshIntervalOption.entries.indexOf(selectedRefreshOption),
                expanded = showCheckLogicIntervalPopup,
                anchorBounds = checkLogicIntervalPopupAnchorBounds,
                onExpandedChange = onShowCheckLogicIntervalPopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    onRefreshIntervalHoursInputChange(RefreshIntervalOption.entries[selectedIndex].hours)
                },
                onAnchorBoundsChange = onCheckLogicIntervalPopupAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.SheetAction
            )
        }
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
    shareImportLinkageEnabledInput: Boolean,
    selectedOnlineShareTargetLabel: String,
    onlineShareTargetOptions: List<OnlineShareTargetOption>,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    onlineShareTargetPackageInput: String,
    showOnlineShareTargetPopup: Boolean,
    onlineShareTargetPopupAnchorBounds: IntRect?,
    onPreferredDownloaderPackageInputChange: (String) -> Unit,
    onShareImportLinkageEnabledInputChange: (Boolean) -> Unit,
    onOnlineShareTargetPackageInputChange: (String) -> Unit,
    onShowDownloaderPopupChange: (Boolean) -> Unit,
    onShowOnlineShareTargetPopupChange: (Boolean) -> Unit,
    onDownloaderPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOnlineShareTargetPopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_transfer))
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_downloader),
            summary = stringResource(R.string.github_check_sheet_summary_downloader)
        ) {
            AppDropdownSelector(
                selectedText = selectedDownloaderLabel,
                options = allDownloaderOptions.map { it.label },
                selectedIndex = allDownloaderOptions.indexOfFirst {
                    preferredDownloaderPackageInput == it.packageName
                }.coerceAtLeast(0),
                expanded = showDownloaderPopup,
                anchorBounds = downloaderPopupAnchorBounds,
                onExpandedChange = onShowDownloaderPopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    onPreferredDownloaderPackageInputChange(
                        allDownloaderOptions[selectedIndex].packageName
                    )
                },
                onAnchorBoundsChange = onDownloaderPopupAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.SheetAction
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_share_import_linkage),
            summary = stringResource(R.string.github_check_sheet_summary_share_import_linkage)
        ) {
            AppSwitch(
                checked = shareImportLinkageEnabledInput,
                onCheckedChange = onShareImportLinkageEnabledInputChange
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_share_to_installer),
            summary = if (installedOnlineShareTargets.isNotEmpty()) {
                stringResource(R.string.github_check_sheet_summary_share_available)
            } else {
                stringResource(R.string.github_check_sheet_summary_share_unavailable)
            }
        ) {
            AppDropdownSelector(
                selectedText = selectedOnlineShareTargetLabel,
                options = onlineShareTargetOptions.map { it.label },
                selectedIndex = onlineShareTargetOptions.indexOfFirst {
                    onlineShareTargetPackageInput == it.packageName
                }.coerceAtLeast(0),
                expanded = showOnlineShareTargetPopup,
                anchorBounds = onlineShareTargetPopupAnchorBounds,
                onExpandedChange = onShowOnlineShareTargetPopupChange,
                onSelectedIndexChange = { selectedIndex ->
                    onOnlineShareTargetPackageInputChange(
                        onlineShareTargetOptions[selectedIndex].packageName
                    )
                },
                onAnchorBoundsChange = onOnlineShareTargetPopupAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.SheetAction
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
    onEnsureKeiOsSelfTrack: () -> Unit,
    onExportTrackedItems: () -> Unit,
    onImportTrackedItems: () -> Unit,
    onOpenStarImport: () -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_tracks))
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
        SheetFieldBlock(
            title = stringResource(R.string.github_check_sheet_label_track_transfer),
            summary = stringResource(R.string.github_check_sheet_summary_track_transfer)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppLiquidTextButton(
                            backdrop = backdrop,
                            text = if (exportInProgress) {
                                stringResource(R.string.github_check_sheet_action_exporting)
                            } else {
                                stringResource(R.string.github_check_sheet_action_export_tracks)
                            },
                            onClick = onExportTrackedItems,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !exportInProgress && !importInProgress,
                            variant = GlassVariant.SheetAction
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppLiquidTextButton(
                            backdrop = backdrop,
                            text = if (importInProgress) {
                                stringResource(R.string.github_check_sheet_action_importing)
                            } else {
                                stringResource(R.string.github_check_sheet_action_import_tracks)
                            },
                            onClick = onImportTrackedItems,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !exportInProgress && !importInProgress,
                            variant = GlassVariant.SheetAction
                        )
                    }
                }
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_check_sheet_action_import_stars),
                    onClick = onOpenStarImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !exportInProgress && !importInProgress,
                    variant = GlassVariant.SheetAction
                )
            }
        }
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
