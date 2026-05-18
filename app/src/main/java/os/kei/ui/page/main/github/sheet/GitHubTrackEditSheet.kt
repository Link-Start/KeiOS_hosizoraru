package os.kei.ui.page.main.github.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSelectedAppCard
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.sheet.UnsavedSheetDismissConfirmDialog
import os.kei.ui.page.main.widget.sheet.rememberUnsavedSheetDismissHandler
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubTrackEditSheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    editingTrackedItem: GitHubTrackedApp?,
    repoUrlInput: String,
    repoScanCandidates: List<GitHubPackageRepositoryScanCandidate>,
    appSearch: String,
    packageNameInput: String,
    repoUrlScanRunning: Boolean,
    packageNameScanRunning: Boolean,
    pickerExpanded: Boolean,
    selectedApp: InstalledAppItem?,
    appList: List<InstalledAppItem>,
    trackedPackageNames: Set<String>,
    appListRefreshing: Boolean,
    addAppPickerRememberedFirstVisibleItemIndex: Int,
    addAppPickerRememberedFirstVisibleItemScrollOffset: Int,
    sourceModeInput: GitHubTrackedSourceMode,
    preferPreReleaseInput: Boolean,
    alwaysShowLatestReleaseDownloadButtonInput: Boolean,
    checkActionsUpdatesInput: Boolean,
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
    globalRefreshIntervalHours: Int,
    globalPreciseApkVersionEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onRepoUrlInputChange: (String) -> Unit,
    onSourceModeInputChange: (GitHubTrackedSourceMode) -> Unit,
    onAppSearchChange: (String) -> Unit,
    onPackageNameInputChange: (String) -> Unit,
    onScanRepoUrl: () -> Unit,
    onScanPackageName: () -> Unit,
    onRepoScanCandidateSelected: (GitHubPackageRepositoryScanCandidate) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onRefreshAppList: () -> Unit,
    onAddAppPickerScrollPositionChange: (Int, Int) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit,
    onCheckActionsUpdatesInputChange: (Boolean) -> Unit,
    onActionsUpdateIntervalModeInputChange: (GitHubTrackedActionsUpdateIntervalMode) -> Unit,
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit
) {
    val hasUnsavedChanges = hasGitHubTrackEditorUnsavedChanges(
        editingTrackedItem = editingTrackedItem,
        repoUrlInput = repoUrlInput,
        packageNameInput = packageNameInput,
        selectedApp = selectedApp,
        appSearch = appSearch,
        pickerExpanded = pickerExpanded,
        repoScanCandidates = repoScanCandidates,
        sourceModeInput = sourceModeInput,
        preferPreReleaseInput = preferPreReleaseInput,
        alwaysShowLatestReleaseDownloadButtonInput = alwaysShowLatestReleaseDownloadButtonInput,
        checkActionsUpdatesInput = checkActionsUpdatesInput,
        actionsUpdateIntervalModeInput = actionsUpdateIntervalModeInput,
        preciseApkVersionModeInput = preciseApkVersionModeInput
    )
    val dismissHandler = rememberUnsavedSheetDismissHandler(
        hasUnsavedChanges = hasUnsavedChanges,
        onDismissRequest = onDismissRequest
    )
    SnapshotWindowBottomSheet(
        show = show,
        title = if (editingTrackedItem == null) {
            stringResource(R.string.github_track_sheet_title_add)
        } else {
            stringResource(R.string.github_track_sheet_title_edit)
        },
        onDismissRequest = dismissHandler.requestDismiss,
        allowDismiss = dismissHandler.allowDismiss,
        onBlockedDismissRequest = dismissHandler.requestDismiss,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = dismissHandler.requestDismiss
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideConfirmIcon(),
                contentDescription = if (editingTrackedItem == null) {
                    stringResource(R.string.github_track_sheet_cd_confirm_add)
                } else {
                    stringResource(R.string.github_track_sheet_cd_confirm_save)
                },
                onClick = onApply
            )
        }
    ) {
        val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
        AnimatedContent(
            targetState = pickerExpanded,
            transitionSpec = {
                if (transitionAnimationsEnabled) {
                    (
                            fadeIn(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeInMs)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeOutMs))
                            ).using(
                            SizeTransform(clip = false) { _, _ ->
                                tween(durationMillis = AppMotionTokens.expandSizeInMs)
                            }
                        )
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "github_track_app_picker_content",
            modifier = Modifier.fillMaxWidth()
        ) { expanded ->
            if (expanded) {
                GitHubTrackAppPickerContent(
                    backdrop = backdrop,
                    appSearch = appSearch,
                    selectedApp = selectedApp,
                    appList = appList,
                    trackedPackageNames = trackedPackageNames,
                    editingPackageName = editingTrackedItem?.packageName.orEmpty(),
                    appListRefreshing = appListRefreshing,
                    rememberAddPickerScroll = editingTrackedItem == null,
                    rememberedFirstVisibleItemIndex = addAppPickerRememberedFirstVisibleItemIndex,
                    rememberedFirstVisibleItemScrollOffset =
                        addAppPickerRememberedFirstVisibleItemScrollOffset,
                    onAppSearchChange = onAppSearchChange,
                    onPickerExpandedChange = onPickerExpandedChange,
                    onRefreshAppList = onRefreshAppList,
                    onAddAppPickerScrollPositionChange = onAddAppPickerScrollPositionChange,
                    onSelectedAppChange = onSelectedAppChange
                )
            } else {
                GitHubTrackEditFormContent(
                    backdrop = backdrop,
                    repoUrlInput = repoUrlInput,
                    repoScanCandidates = repoScanCandidates,
                    packageNameInput = packageNameInput,
                    repoUrlScanRunning = repoUrlScanRunning,
                    packageNameScanRunning = packageNameScanRunning,
                    selectedApp = selectedApp,
                    sourceModeInput = sourceModeInput,
                    preferPreReleaseInput = preferPreReleaseInput,
                    alwaysShowLatestReleaseDownloadButtonInput = alwaysShowLatestReleaseDownloadButtonInput,
                    checkActionsUpdatesInput = checkActionsUpdatesInput,
                    actionsUpdateIntervalModeInput = actionsUpdateIntervalModeInput,
                    preciseApkVersionModeInput = preciseApkVersionModeInput,
                    globalRefreshIntervalHours = globalRefreshIntervalHours,
                    globalPreciseApkVersionEnabled = globalPreciseApkVersionEnabled,
                    onRepoUrlInputChange = onRepoUrlInputChange,
                    onSourceModeInputChange = onSourceModeInputChange,
                    onPackageNameInputChange = onPackageNameInputChange,
                    onScanRepoUrl = onScanRepoUrl,
                    onScanPackageName = onScanPackageName,
                    onRepoScanCandidateSelected = onRepoScanCandidateSelected,
                    onPickerExpandedChange = onPickerExpandedChange,
                    onPreferPreReleaseInputChange = onPreferPreReleaseInputChange,
                    onAlwaysShowLatestReleaseDownloadButtonInputChange =
                        onAlwaysShowLatestReleaseDownloadButtonInputChange,
                    onCheckActionsUpdatesInputChange = onCheckActionsUpdatesInputChange,
                    onActionsUpdateIntervalModeInputChange =
                        onActionsUpdateIntervalModeInputChange,
                    onPreciseApkVersionModeInputChange = onPreciseApkVersionModeInputChange
                )
            }
        }
    }
    UnsavedSheetDismissConfirmDialog(
        show = dismissHandler.showConfirmDialog,
        onKeepEditing = dismissHandler.keepEditing,
        onDiscardChanges = dismissHandler.discardChanges
    )
}

@Composable
private fun GitHubTrackEditFormContent(
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
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
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
    onActionsUpdateIntervalModeInputChange: (GitHubTrackedActionsUpdateIntervalMode) -> Unit,
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit
) {
    var sourceModeExpanded by remember { mutableStateOf(false) }
    var sourceModeAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var actionsIntervalExpanded by remember { mutableStateOf(false) }
    var actionsIntervalAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var preciseModeExpanded by remember { mutableStateOf(false) }
    var preciseModeAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val sourceModes = GitHubTrackedSourceMode.entries
    val sourceModeOptions = sourceModes.map { mode -> trackedSourceModeLabel(mode) }
    val sourceModeIndex = sourceModes.indexOf(sourceModeInput).coerceAtLeast(0)
    val directApkMode = sourceModeInput == GitHubTrackedSourceMode.DirectApk
    val actionsIntervalModes = GitHubTrackedActionsUpdateIntervalMode.entries
    val actionsIntervalOptions = actionsIntervalModes.map { mode ->
        actionsUpdateIntervalModeLabel(
            mode = mode,
            globalRefreshIntervalHours = globalRefreshIntervalHours
        )
    }
    val actionsIntervalIndex = actionsIntervalModes
        .indexOf(actionsUpdateIntervalModeInput)
        .coerceAtLeast(0)
    val actionsIntervalFollowsGlobal =
        actionsUpdateIntervalModeInput == GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
    val actionsIntervalSummary = if (actionsIntervalFollowsGlobal) {
        stringResource(
            R.string.github_track_sheet_summary_actions_update_interval_follow_global,
            refreshIntervalLabel(globalRefreshIntervalHours)
        )
    } else {
        stringResource(R.string.github_track_sheet_summary_actions_update_interval_custom)
    }
    val preciseModes = GitHubTrackedPreciseApkVersionMode.entries
    val preciseModeOptions = preciseModes.map { mode -> preciseApkVersionModeLabel(mode) }
    val preciseModeIndex = preciseModes.indexOf(preciseApkVersionModeInput).coerceAtLeast(0)
    val preciseModeFollowsGlobal =
        preciseApkVersionModeInput == GitHubTrackedPreciseApkVersionMode.FollowGlobal
    val preciseModeSummary = if (preciseModeFollowsGlobal) {
        stringResource(
            R.string.github_track_sheet_summary_precise_apk_version_follow_global,
            stringResource(
                if (globalPreciseApkVersionEnabled) {
                    R.string.github_check_sheet_value_enabled
                } else {
                    R.string.github_check_sheet_value_disabled
                }
            )
        )
    } else {
        stringResource(R.string.github_track_sheet_summary_precise_apk_version)
    }
    val preciseModeSummaryColor = if (preciseModeFollowsGlobal) {
        if (globalPreciseApkVersionEnabled) AppStatusColors.Fresh else AppStatusColors.Failed
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    }
    val canScanRepoUrl = !repoUrlScanRunning &&
            !packageNameScanRunning &&
            !directApkMode &&
            (packageNameInput.isNotBlank() || selectedApp != null)
    val canScanPackageName = !repoUrlScanRunning &&
            !packageNameScanRunning &&
            repoUrlInput.isNotBlank()
    val repoInputLabel = stringResource(
        if (directApkMode) {
            R.string.github_track_sheet_input_direct_apk
        } else {
            R.string.github_track_sheet_input_repo
        }
    )
    val repoSummary = stringResource(
        if (directApkMode) {
            R.string.github_track_sheet_summary_direct_apk
        } else {
            R.string.github_track_sheet_summary_repo
        }
    )
    val packageSummary = stringResource(
        if (directApkMode) {
            R.string.github_track_sheet_summary_package_direct_apk
        } else {
            R.string.github_track_sheet_summary_package_link
        }
    )

    SheetContentColumn(verticalSpacing = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SheetSectionTitle(
                text = stringResource(R.string.github_track_sheet_section_repository),
                modifier = Modifier.weight(1f)
            )
            AppDropdownSelector(
                selectedText = sourceModeOptions.getOrElse(sourceModeIndex) {
                    stringResource(R.string.github_track_sheet_source_mode_github)
                },
                options = sourceModeOptions,
                selectedIndex = sourceModeIndex,
                expanded = sourceModeExpanded,
                anchorBounds = sourceModeAnchorBounds,
                onExpandedChange = { sourceModeExpanded = it },
                onSelectedIndexChange = { index ->
                    sourceModes.getOrNull(index)?.let(onSourceModeInputChange)
                },
                onAnchorBoundsChange = { sourceModeAnchorBounds = it },
                backdrop = backdrop
            )
        }
        SheetSectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetInputTitle(repoInputLabel)
                if (!directApkMode) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = if (repoUrlScanRunning) {
                            stringResource(R.string.github_track_sheet_btn_scan_repo_running)
                        } else {
                            stringResource(R.string.github_track_sheet_btn_scan_repo)
                        },
                        enabled = canScanRepoUrl,
                        onClick = onScanRepoUrl,
                        minHeight = 30.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 4.dp,
                        textMaxLines = 1
                    )
                }
            }
            AppLiquidSearchField(
                value = repoUrlInput,
                onValueChange = onRepoUrlInputChange,
                label = repoInputLabel,
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true
            )
            SheetDescriptionText(
                text = repoSummary
            )
            if (!directApkMode && repoScanCandidates.isNotEmpty()) {
                RepositoryScanCandidateList(
                    candidates = repoScanCandidates,
                    selectedRepoUrl = repoUrlInput,
                    onCandidateClick = onRepoScanCandidateSelected
                )
            }
        }

        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_package_app))
        SheetSectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetInputTitle(stringResource(R.string.github_track_sheet_input_package_title))
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = if (packageNameScanRunning) {
                        stringResource(R.string.github_track_sheet_btn_scan_package_running)
                    } else {
                        stringResource(R.string.github_track_sheet_btn_scan_package)
                    },
                    enabled = canScanPackageName,
                    onClick = onScanPackageName,
                    minHeight = 30.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1
                )
            }
            AppLiquidSearchField(
                value = packageNameInput,
                onValueChange = onPackageNameInputChange,
                label = stringResource(R.string.github_track_sheet_input_package),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true
            )
            SheetDescriptionText(
                text = packageSummary
            )
            SheetControlRow(
                label = stringResource(R.string.github_track_sheet_label_selected_app),
                summary = if (selectedApp == null) {
                    stringResource(R.string.github_track_sheet_summary_app_binding_none)
                } else {
                    null
                }
            ) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.github_track_sheet_btn_select_app),
                    onClick = { onPickerExpandedChange(true) }
                )
            }
            selectedApp?.let { app ->
                GitHubSelectedAppCard(selectedApp = app)
            }
        }

        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_check_option))
        SheetSectionCard {
            if (directApkMode) {
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_prefer_prerelease),
                    summary = stringResource(
                        R.string.github_track_sheet_summary_prefer_prerelease_direct_apk
                    )
                ) {
                    AppSwitch(
                        checked = preferPreReleaseInput,
                        onCheckedChange = onPreferPreReleaseInputChange
                    )
                }
                SheetDescriptionText(
                    text = stringResource(R.string.github_track_sheet_summary_direct_apk_check_options)
                )
            } else {
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_prefer_prerelease),
                    summary = stringResource(R.string.github_track_sheet_summary_prefer_prerelease)
                ) {
                    AppSwitch(
                        checked = preferPreReleaseInput,
                        onCheckedChange = onPreferPreReleaseInputChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_always_show_latest_release_download),
                    summary = stringResource(R.string.github_track_sheet_summary_always_show_latest_release_download)
                ) {
                    AppSwitch(
                        checked = alwaysShowLatestReleaseDownloadButtonInput,
                        onCheckedChange = onAlwaysShowLatestReleaseDownloadButtonInputChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_check_actions_updates),
                    summary = stringResource(R.string.github_track_sheet_summary_check_actions_updates)
                ) {
                    AppSwitch(
                        checked = checkActionsUpdatesInput,
                        onCheckedChange = onCheckActionsUpdatesInputChange
                    )
                }
                if (checkActionsUpdatesInput) {
                    SheetControlRow(
                        label = stringResource(R.string.github_track_sheet_label_actions_update_interval),
                        summary = actionsIntervalSummary
                    ) {
                        AppDropdownSelector(
                            selectedText = actionsIntervalOptions.getOrElse(actionsIntervalIndex) {
                                stringResource(
                                    R.string.github_track_sheet_actions_update_interval_follow_global
                                )
                            },
                            options = actionsIntervalOptions,
                            selectedIndex = actionsIntervalIndex,
                            expanded = actionsIntervalExpanded,
                            anchorBounds = actionsIntervalAnchorBounds,
                            onExpandedChange = { actionsIntervalExpanded = it },
                            onSelectedIndexChange = { index ->
                                actionsIntervalModes.getOrNull(index)
                                    ?.let(onActionsUpdateIntervalModeInputChange)
                            },
                            onAnchorBoundsChange = { actionsIntervalAnchorBounds = it },
                            backdrop = backdrop
                        )
                    }
                }
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_precise_apk_version),
                    summary = preciseModeSummary,
                    summaryColor = preciseModeSummaryColor
                ) {
                    AppDropdownSelector(
                        selectedText = preciseModeOptions.getOrElse(preciseModeIndex) {
                            stringResource(R.string.github_track_sheet_precise_apk_version_follow_global)
                        },
                        options = preciseModeOptions,
                        selectedIndex = preciseModeIndex,
                        expanded = preciseModeExpanded,
                        anchorBounds = preciseModeAnchorBounds,
                        onExpandedChange = { preciseModeExpanded = it },
                        onSelectedIndexChange = { index ->
                            preciseModes.getOrNull(index)?.let(onPreciseApkVersionModeInputChange)
                        },
                        onAnchorBoundsChange = { preciseModeAnchorBounds = it },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}

@Composable
private fun trackedSourceModeLabel(mode: GitHubTrackedSourceMode): String {
    return when (mode) {
        GitHubTrackedSourceMode.GitHubRepository ->
            stringResource(R.string.github_track_sheet_source_mode_github)

        GitHubTrackedSourceMode.DirectApk ->
            stringResource(R.string.github_track_sheet_source_mode_direct_apk)
    }
}

@Composable
private fun preciseApkVersionModeLabel(mode: GitHubTrackedPreciseApkVersionMode): String {
    return when (mode) {
        GitHubTrackedPreciseApkVersionMode.FollowGlobal ->
            stringResource(R.string.github_track_sheet_precise_apk_version_follow_global)
        GitHubTrackedPreciseApkVersionMode.Enabled ->
            stringResource(R.string.github_track_sheet_precise_apk_version_enabled)
        GitHubTrackedPreciseApkVersionMode.Disabled ->
            stringResource(R.string.github_track_sheet_precise_apk_version_disabled)
    }
}

@Composable
private fun actionsUpdateIntervalModeLabel(
    mode: GitHubTrackedActionsUpdateIntervalMode,
    globalRefreshIntervalHours: Int
): String {
    return when (mode) {
        GitHubTrackedActionsUpdateIntervalMode.FollowGlobal ->
            stringResource(
                R.string.github_track_sheet_actions_update_interval_follow_global_format,
                refreshIntervalLabel(globalRefreshIntervalHours)
            )

        GitHubTrackedActionsUpdateIntervalMode.Minutes15 ->
            stringResource(R.string.github_track_sheet_actions_update_interval_15m)

        GitHubTrackedActionsUpdateIntervalMode.Minutes30 ->
            stringResource(R.string.github_track_sheet_actions_update_interval_30m)

        GitHubTrackedActionsUpdateIntervalMode.Hour1 ->
            stringResource(R.string.github_track_sheet_actions_update_interval_1h)

        GitHubTrackedActionsUpdateIntervalMode.Hours2 ->
            stringResource(R.string.github_track_sheet_actions_update_interval_2h)

        GitHubTrackedActionsUpdateIntervalMode.Hours3 ->
            stringResource(R.string.github_track_sheet_actions_update_interval_3h)
    }
}

@Composable
private fun refreshIntervalLabel(hours: Int): String {
    return when (hours) {
        1 -> stringResource(R.string.github_refresh_interval_1h)
        3 -> stringResource(R.string.github_refresh_interval_3h)
        6 -> stringResource(R.string.github_refresh_interval_6h)
        12 -> stringResource(R.string.github_refresh_interval_12h)
        else -> stringResource(R.string.github_refresh_interval_3h)
    }
}

