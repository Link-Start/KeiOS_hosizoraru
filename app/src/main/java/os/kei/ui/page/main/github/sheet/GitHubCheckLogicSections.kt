package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.ui.page.main.github.RefreshIntervalOption
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetFieldBlock
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle

@Composable
internal fun GitHubCheckStrategySection(
    backdrop: LayerBackdrop,
    selectedRefreshOption: RefreshIntervalOption,
    showCheckLogicIntervalPopup: Boolean,
    checkLogicIntervalPopupAnchorBounds: IntRect?,
    checkAllTrackedPreReleasesInput: Boolean,
    aggressiveApkFilteringInput: Boolean,
    preciseApkVersionEnabledInput: Boolean,
    onRefreshIntervalHoursInputChange: (Int) -> Unit,
    onCheckAllTrackedPreReleasesInputChange: (Boolean) -> Unit,
    onAggressiveApkFilteringInputChange: (Boolean) -> Unit,
    onPreciseApkVersionEnabledInputChange: (Boolean) -> Unit,
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
        SheetControlRow(
            label = stringResource(R.string.github_check_sheet_label_precise_apk_version),
            summary = stringResource(R.string.github_check_sheet_summary_precise_apk_version)
        ) {
            AppSwitch(
                checked = preciseApkVersionEnabledInput,
                onCheckedChange = onPreciseApkVersionEnabledInputChange
            )
        }
    }
}

@Composable
internal fun GitHubCheckDownloadFlowSection(
    backdrop: LayerBackdrop,
    selectedDownloaderLabel: String,
    allDownloaderOptions: List<DownloaderOption>,
    preferredDownloaderPackageInput: String,
    showDownloaderPopup: Boolean,
    downloaderPopupAnchorBounds: IntRect?,
    onPreferredDownloaderPackageInputChange: (String) -> Unit,
    onShowDownloaderPopupChange: (Boolean) -> Unit,
    onDownloaderPopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_transfer))
    SheetDescriptionText(
        text = stringResource(R.string.github_check_sheet_section_transfer_summary)
    )
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
    }
}

@Composable
internal fun GitHubCheckShareImportSection(
    backdrop: LayerBackdrop,
    shareImportLinkageEnabledInput: Boolean,
    shareImportFlowModeInput: GitHubShareImportFlowMode,
    selectedOnlineShareTargetLabel: String,
    onlineShareTargetOptions: List<OnlineShareTargetOption>,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    onlineShareTargetPackageInput: String,
    showOnlineShareTargetPopup: Boolean,
    showShareImportFlowModePopup: Boolean,
    onlineShareTargetPopupAnchorBounds: IntRect?,
    shareImportFlowModePopupAnchorBounds: IntRect?,
    onShareImportLinkageEnabledInputChange: (Boolean) -> Unit,
    onShareImportFlowModeInputChange: (GitHubShareImportFlowMode) -> Unit,
    onOnlineShareTargetPackageInputChange: (String) -> Unit,
    onShowOnlineShareTargetPopupChange: (Boolean) -> Unit,
    onShowShareImportFlowModePopupChange: (Boolean) -> Unit,
    onOnlineShareTargetPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onShareImportFlowModePopupAnchorBoundsChange: (IntRect?) -> Unit
) {
    val context = LocalContext.current
    val flowModeOptions = GitHubShareImportFlowMode.entries
    val flowModeLabels = flowModeOptions.map { mode ->
        context.getString(mode.labelRes())
    }
    val selectedFlowModeIndex = flowModeOptions
        .indexOf(shareImportFlowModeInput)
        .coerceAtLeast(0)
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_share_import))
    SheetDescriptionText(
        text = stringResource(R.string.github_check_sheet_section_share_import_summary)
    )
    SheetSectionCard {
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
internal fun GitHubCheckEnhancementSection(
    profileDepthInput: GitHubProfileDepth,
    decisionAssistEnabledInput: Boolean,
    repositoryHealthCardEnabledInput: Boolean,
    apkTrustCheckEnabledInput: Boolean,
    onProfileDepthInputChange: (GitHubProfileDepth) -> Unit,
    onDecisionAssistEnabledInputChange: (Boolean) -> Unit,
    onRepositoryHealthCardEnabledInputChange: (Boolean) -> Unit,
    onApkTrustCheckEnabledInputChange: (Boolean) -> Unit
) {
    SheetSectionTitle(stringResource(R.string.github_check_sheet_section_enhancements))
    SheetDescriptionText(
        text = stringResource(R.string.github_check_sheet_section_enhancements_summary)
    )
    SheetSectionCard {
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
internal fun GitHubCheckTracksSection(
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

internal fun GitHubShareImportFlowMode.labelRes(): Int {
    return when (this) {
        GitHubShareImportFlowMode.SheetAssisted -> R.string.github_share_import_flow_mode_sheet
        GitHubShareImportFlowMode.NotificationFirst -> R.string.github_share_import_flow_mode_notification
    }
}

@Composable
internal fun GitHubCheckNotesSection() {
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
