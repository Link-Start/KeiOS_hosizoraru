@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSelectedAppCard
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubTrackEditFormContent(
    backdrop: LayerBackdrop,
    repoUrlInput: String,
    repoScanCandidates: List<GitHubPackageRepositoryScanCandidate>,
    packageNameInput: String,
    repoUrlScanRunning: Boolean,
    packageNameScanRunning: Boolean,
    selectedApp: InstalledAppItem?,
    sourceModeInput: GitHubTrackedSourceMode,
    preferPreReleaseInput: Boolean,
    alwaysShowLatestReleaseDownloadButtonInput: Boolean,
    checkActionsUpdatesInput: Boolean,
    updateIntervalModeInput: GitHubTrackedUpdateIntervalMode,
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
    sourceModeDropdownExpanded: Boolean,
    sourceModeDropdownAnchorBounds: IntRect?,
    updateIntervalDropdownExpanded: Boolean,
    updateIntervalDropdownAnchorBounds: IntRect?,
    actionsIntervalDropdownExpanded: Boolean,
    actionsIntervalDropdownAnchorBounds: IntRect?,
    preciseModeDropdownExpanded: Boolean,
    preciseModeDropdownAnchorBounds: IntRect?,
    globalRefreshIntervalHours: Int,
    globalPreciseApkVersionEnabled: Boolean,
    onRepoUrlInputChange: (String) -> Unit,
    onSourceModeInputChange: (GitHubTrackedSourceMode) -> Unit,
    onPackageNameInputChange: (String) -> Unit,
    onScanRepoUrl: () -> Unit,
    onScanPackageName: () -> Unit,
    onRepoScanCandidateSelected: (GitHubPackageRepositoryScanCandidate) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit,
    onCheckActionsUpdatesInputChange: (Boolean) -> Unit,
    onUpdateIntervalModeInputChange: (GitHubTrackedUpdateIntervalMode) -> Unit,
    onActionsUpdateIntervalModeInputChange: (GitHubTrackedActionsUpdateIntervalMode) -> Unit,
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit,
    onSourceModeDropdownExpandedChange: (Boolean) -> Unit,
    onSourceModeDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onUpdateIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onUpdateIntervalDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onActionsIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onActionsIntervalDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onPreciseModeDropdownExpandedChange: (Boolean) -> Unit,
    onPreciseModeDropdownAnchorBoundsChange: (IntRect?) -> Unit,
) {
    val sourceModes = GitHubTrackedSourceMode.entries
    val sourceModeOptions = sourceModes.map { mode -> trackedSourceModeLabel(mode) }
    val sourceModeIndex = sourceModes.indexOf(sourceModeInput).coerceAtLeast(0)
    val directApkMode = sourceModeInput == GitHubTrackedSourceMode.DirectApk
    val gitRepositoryMode = sourceModeInput == GitHubTrackedSourceMode.GitRepository
    val githubRepositoryMode = sourceModeInput == GitHubTrackedSourceMode.GitHubRepository
    val updateIntervalModes = GitHubTrackedUpdateIntervalMode.entries
    val updateIntervalOptions =
        updateIntervalModes.map { mode ->
            updateIntervalModeLabel(
                mode = mode,
                globalRefreshIntervalHours = globalRefreshIntervalHours,
            )
        }
    val updateIntervalIndex = updateIntervalModes.indexOf(updateIntervalModeInput).coerceAtLeast(0)
    val updateIntervalFollowsGlobal =
        updateIntervalModeInput == GitHubTrackedUpdateIntervalMode.FollowGlobal
    val updateIntervalSummary =
        if (updateIntervalFollowsGlobal) {
            stringResource(
                R.string.github_track_sheet_summary_update_interval_follow_global,
                refreshIntervalLabel(globalRefreshIntervalHours),
            )
        } else {
            stringResource(R.string.github_track_sheet_summary_update_interval_custom)
        }
    val actionsIntervalModes = GitHubTrackedActionsUpdateIntervalMode.entries
    val actionsIntervalOptions =
        actionsIntervalModes.map { mode ->
            actionsUpdateIntervalModeLabel(
                mode = mode,
                globalRefreshIntervalHours = globalRefreshIntervalHours,
            )
        }
    val actionsIntervalIndex =
        actionsIntervalModes
            .indexOf(actionsUpdateIntervalModeInput)
            .coerceAtLeast(0)
    val actionsIntervalFollowsGlobal =
        actionsUpdateIntervalModeInput == GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
    val actionsIntervalSummary =
        if (actionsIntervalFollowsGlobal) {
            stringResource(
                R.string.github_track_sheet_summary_actions_update_interval_follow_global,
                refreshIntervalLabel(globalRefreshIntervalHours),
            )
        } else {
            stringResource(R.string.github_track_sheet_summary_actions_update_interval_custom)
        }
    val preciseModes = GitHubTrackedPreciseApkVersionMode.entries
    val preciseModeOptions = preciseModes.map { mode -> preciseApkVersionModeLabel(mode) }
    val preciseModeIndex = preciseModes.indexOf(preciseApkVersionModeInput).coerceAtLeast(0)
    val preciseModeFollowsGlobal =
        preciseApkVersionModeInput == GitHubTrackedPreciseApkVersionMode.FollowGlobal
    val preciseModeSummary =
        if (preciseModeFollowsGlobal) {
            stringResource(
                R.string.github_track_sheet_summary_precise_apk_version_follow_global,
                stringResource(
                    if (globalPreciseApkVersionEnabled) {
                        R.string.github_check_sheet_value_enabled
                    } else {
                        R.string.github_check_sheet_value_disabled
                    },
                ),
            )
        } else {
            stringResource(R.string.github_track_sheet_summary_precise_apk_version)
        }
    val preciseModeSummaryColor =
        if (preciseModeFollowsGlobal) {
            if (globalPreciseApkVersionEnabled) AppStatusColors.Fresh else AppStatusColors.Failed
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
        }
    val canScanRepoUrl =
        !repoUrlScanRunning &&
            !packageNameScanRunning &&
            githubRepositoryMode &&
            (packageNameInput.isNotBlank() || selectedApp != null)
    val canScanPackageName =
        !repoUrlScanRunning &&
            !packageNameScanRunning &&
            !gitRepositoryMode &&
            repoUrlInput.isNotBlank()
    val repoInputLabel =
        stringResource(
            when {
                directApkMode -> R.string.github_track_sheet_input_direct_apk
                gitRepositoryMode -> R.string.github_track_sheet_input_git
                else -> R.string.github_track_sheet_input_repo
            },
        )
    val repoSummary =
        stringResource(
            when {
                directApkMode -> R.string.github_track_sheet_summary_direct_apk
                gitRepositoryMode -> R.string.github_track_sheet_summary_git
                else -> R.string.github_track_sheet_summary_repo
            },
        )
    val packageSummary =
        stringResource(
            if (directApkMode) {
                R.string.github_track_sheet_summary_package_direct_apk
            } else {
                R.string.github_track_sheet_summary_package_link
            },
        )

    SheetContentColumn(verticalSpacing = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetSectionTitle(
                text = stringResource(R.string.github_track_sheet_section_repository),
                modifier = Modifier.weight(1f),
            )
            AppDropdownSelector(
                selectedText =
                    sourceModeOptions.getOrElse(sourceModeIndex) {
                        stringResource(R.string.github_track_sheet_source_mode_github)
                    },
                options = sourceModeOptions,
                selectedIndex = sourceModeIndex,
                expanded = sourceModeDropdownExpanded,
                anchorBounds = sourceModeDropdownAnchorBounds,
                onExpandedChange = onSourceModeDropdownExpandedChange,
                onSelectedIndexChange = { index ->
                    sourceModes.getOrNull(index)?.let(onSourceModeInputChange)
                },
                onAnchorBoundsChange = onSourceModeDropdownAnchorBoundsChange,
                backdrop = backdrop,
            )
        }
        SheetSectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetInputTitle(repoInputLabel)
                if (githubRepositoryMode) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text =
                            if (repoUrlScanRunning) {
                                stringResource(R.string.github_track_sheet_btn_scan_repo_running)
                            } else {
                                stringResource(R.string.github_track_sheet_btn_scan_repo)
                            },
                        enabled = canScanRepoUrl,
                        onClick = onScanRepoUrl,
                        minHeight = 30.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 4.dp,
                        textMaxLines = 1,
                    )
                }
            }
            AppLiquidSearchField(
                value = repoUrlInput,
                onValueChange = onRepoUrlInputChange,
                label = repoInputLabel,
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true,
            )
            SheetDescriptionText(
                text = repoSummary,
            )
            if (!directApkMode && repoScanCandidates.isNotEmpty()) {
                RepositoryScanCandidateList(
                    candidates = repoScanCandidates,
                    selectedRepoUrl = repoUrlInput,
                    onCandidateClick = onRepoScanCandidateSelected,
                )
            }
        }

        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_package_app))
        SheetSectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetInputTitle(stringResource(R.string.github_track_sheet_input_package_title))
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text =
                        if (packageNameScanRunning) {
                            stringResource(R.string.github_track_sheet_btn_scan_package_running)
                        } else {
                            stringResource(R.string.github_track_sheet_btn_scan_package)
                        },
                    enabled = canScanPackageName,
                    onClick = onScanPackageName,
                    minHeight = 30.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1,
                )
            }
            AppLiquidSearchField(
                value = packageNameInput,
                onValueChange = onPackageNameInputChange,
                label = stringResource(R.string.github_track_sheet_input_package),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true,
            )
            SheetDescriptionText(
                text = packageSummary,
            )
            SheetControlRow(
                label = stringResource(R.string.github_track_sheet_label_selected_app),
                summary =
                    if (selectedApp == null) {
                        stringResource(R.string.github_track_sheet_summary_app_binding_none)
                    } else {
                        null
                    },
            ) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.github_track_sheet_btn_select_app),
                    onClick = { onPickerExpandedChange(true) },
                )
            }
            selectedApp?.let { app ->
                GitHubSelectedAppCard(selectedApp = app)
            }
        }

        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_check_option))
        SheetSectionCard {
            SheetControlRow(
                label = stringResource(R.string.github_track_sheet_label_update_interval),
                summary = updateIntervalSummary,
            ) {
                AppDropdownSelector(
                    selectedText =
                        updateIntervalOptions.getOrElse(updateIntervalIndex) {
                            stringResource(R.string.github_track_sheet_update_interval_follow_global)
                        },
                    options = updateIntervalOptions,
                    selectedIndex = updateIntervalIndex,
                    expanded = updateIntervalDropdownExpanded,
                    anchorBounds = updateIntervalDropdownAnchorBounds,
                    onExpandedChange = onUpdateIntervalDropdownExpandedChange,
                    onSelectedIndexChange = { index ->
                        updateIntervalModes.getOrNull(index)?.let(onUpdateIntervalModeInputChange)
                    },
                    onAnchorBoundsChange = onUpdateIntervalDropdownAnchorBoundsChange,
                    backdrop = backdrop,
                )
            }
            if (directApkMode) {
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_prefer_prerelease),
                    summary =
                        stringResource(
                            R.string.github_track_sheet_summary_prefer_prerelease_direct_apk,
                        ),
                ) {
                    AppSwitch(
                        checked = preferPreReleaseInput,
                        onCheckedChange = onPreferPreReleaseInputChange,
                    )
                }
                SheetDescriptionText(
                    text = stringResource(R.string.github_track_sheet_summary_direct_apk_check_options),
                )
            } else {
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_prefer_prerelease),
                    summary = stringResource(R.string.github_track_sheet_summary_prefer_prerelease),
                ) {
                    AppSwitch(
                        checked = preferPreReleaseInput,
                        onCheckedChange = onPreferPreReleaseInputChange,
                    )
                }
                if (githubRepositoryMode) {
                    SheetControlRow(
                        label = stringResource(
                            R.string.github_track_sheet_label_always_show_latest_release_download,
                        ),
                        summary = stringResource(
                            R.string.github_track_sheet_summary_always_show_latest_release_download,
                        ),
                    ) {
                        AppSwitch(
                            checked = alwaysShowLatestReleaseDownloadButtonInput,
                            onCheckedChange = onAlwaysShowLatestReleaseDownloadButtonInputChange,
                        )
                    }
                    SheetControlRow(
                        label = stringResource(R.string.github_track_sheet_label_check_actions_updates),
                        summary = stringResource(R.string.github_track_sheet_summary_check_actions_updates),
                    ) {
                        AppSwitch(
                            checked = checkActionsUpdatesInput,
                            onCheckedChange = onCheckActionsUpdatesInputChange,
                        )
                    }
                    if (checkActionsUpdatesInput) {
                        SheetControlRow(
                            label = stringResource(R.string.github_track_sheet_label_actions_update_interval),
                            summary = actionsIntervalSummary,
                        ) {
                            AppDropdownSelector(
                                selectedText =
                                    actionsIntervalOptions.getOrElse(actionsIntervalIndex) {
                                        stringResource(
                                            R.string.github_track_sheet_actions_update_interval_follow_global,
                                        )
                                    },
                                options = actionsIntervalOptions,
                                selectedIndex = actionsIntervalIndex,
                                expanded = actionsIntervalDropdownExpanded,
                                anchorBounds = actionsIntervalDropdownAnchorBounds,
                                onExpandedChange = onActionsIntervalDropdownExpandedChange,
                                onSelectedIndexChange = { index ->
                                    actionsIntervalModes
                                        .getOrNull(index)
                                        ?.let(onActionsUpdateIntervalModeInputChange)
                                },
                                onAnchorBoundsChange = onActionsIntervalDropdownAnchorBoundsChange,
                                backdrop = backdrop,
                            )
                        }
                    }
                }
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_precise_apk_version),
                    summary = preciseModeSummary,
                    summaryColor = preciseModeSummaryColor,
                ) {
                    AppDropdownSelector(
                        selectedText =
                            preciseModeOptions.getOrElse(preciseModeIndex) {
                                stringResource(R.string.github_track_sheet_precise_apk_version_follow_global)
                            },
                        options = preciseModeOptions,
                        selectedIndex = preciseModeIndex,
                        expanded = preciseModeDropdownExpanded,
                        anchorBounds = preciseModeDropdownAnchorBounds,
                        onExpandedChange = onPreciseModeDropdownExpandedChange,
                        onSelectedIndexChange = { index ->
                            preciseModes.getOrNull(index)?.let(onPreciseApkVersionModeInputChange)
                        },
                        onAnchorBoundsChange = onPreciseModeDropdownAnchorBoundsChange,
                        backdrop = backdrop,
                    )
                }
            }
        }
    }
}
