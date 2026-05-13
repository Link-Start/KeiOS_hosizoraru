package os.kei.ui.page.main.github.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubAppCandidateRow
import os.kei.ui.page.main.github.GitHubSelectedAppCard
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidCheckbox
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
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.Text
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
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
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
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = if (editingTrackedItem == null) {
            stringResource(R.string.github_track_sheet_title_add)
        } else {
            stringResource(R.string.github_track_sheet_title_edit)
        },
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
                    preciseApkVersionModeInput = preciseApkVersionModeInput,
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
                    onPreciseApkVersionModeInputChange = onPreciseApkVersionModeInputChange
                )
            }
        }
    }
}

@Composable
private fun GitHubTrackAppPickerControls(
    backdrop: LayerBackdrop,
    includeUserApps: Boolean,
    includeSystemApps: Boolean,
    includeTrackedApps: Boolean,
    sortMode: GitHubTrackAppPickerSortMode,
    sortDirection: GitHubTrackAppPickerSortDirection,
    onIncludeUserAppsChange: (Boolean) -> Unit,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onIncludeTrackedAppsChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubTrackAppPickerSortMode) -> Unit,
    onSortDirectionChange: (GitHubTrackAppPickerSortDirection) -> Unit
) {
    var sortModeExpanded by remember { mutableStateOf(false) }
    var sortDirectionExpanded by remember { mutableStateOf(false) }
    var sortModeAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var sortDirectionAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val sortModes = GitHubTrackAppPickerSortMode.entries
    val sortDirections = GitHubTrackAppPickerSortDirection.entries
    val sortOptions = sortModes.map { mode -> stringResource(mode.labelRes) }
    val directionOptions = sortDirections.map { direction -> stringResource(direction.labelRes) }
    val sortIndex = sortModes.indexOf(sortMode).coerceAtLeast(0)
    val directionIndex = sortDirections.indexOf(sortDirection).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GitHubTrackAppPickerButtonRow(
            label = stringResource(R.string.github_track_sheet_app_filter_scope_label)
        ) {
            GitHubTrackAppTypeCheckbox(
                backdrop = backdrop,
                text = stringResource(R.string.github_track_sheet_app_filter_user_apps),
                checked = includeUserApps,
                onCheckedChange = onIncludeUserAppsChange,
                modifier = Modifier.weight(1f)
            )
            GitHubTrackAppTypeCheckbox(
                backdrop = backdrop,
                text = stringResource(R.string.github_track_sheet_app_filter_system_apps),
                checked = includeSystemApps,
                onCheckedChange = onIncludeSystemAppsChange,
                modifier = Modifier.weight(1f)
            )
            GitHubTrackAppTypeCheckbox(
                backdrop = backdrop,
                text = stringResource(R.string.github_track_sheet_app_filter_tracked_apps),
                checked = includeTrackedApps,
                onCheckedChange = onIncludeTrackedAppsChange,
                modifier = Modifier.weight(1f)
            )
        }
        GitHubTrackAppPickerSortRow(
            label = stringResource(R.string.github_track_sheet_app_sort_label)
        ) {
            AppDropdownSelector(
                selectedText = stringResource(
                    R.string.github_track_sheet_app_sort_dropdown_format,
                    sortOptions.getOrElse(sortIndex) { "" }
                ),
                options = sortOptions,
                selectedIndex = sortIndex,
                expanded = sortModeExpanded,
                anchorBounds = sortModeAnchorBounds,
                onExpandedChange = { sortModeExpanded = it },
                onSelectedIndexChange = { index ->
                    sortModes.getOrNull(index)?.let { mode ->
                        onSortModeChange(mode)
                        if (mode.isTimeSort()) {
                            onSortDirectionChange(GitHubTrackAppPickerSortDirection.Descending)
                        }
                    }
                },
                onAnchorBoundsChange = { sortModeAnchorBounds = it },
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                variant = GlassVariant.Content,
                minHeight = 32.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp
            )
            AppDropdownSelector(
                selectedText = stringResource(
                    R.string.github_track_sheet_app_sort_direction_dropdown_format,
                    directionOptions.getOrElse(directionIndex) { "" }
                ),
                options = directionOptions,
                selectedIndex = directionIndex,
                expanded = sortDirectionExpanded,
                anchorBounds = sortDirectionAnchorBounds,
                onExpandedChange = { sortDirectionExpanded = it },
                onSelectedIndexChange = { index ->
                    sortDirections.getOrNull(index)?.let(onSortDirectionChange)
                },
                onAnchorBoundsChange = { sortDirectionAnchorBounds = it },
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                variant = GlassVariant.Content,
                minHeight = 32.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp
            )
        }
    }
}

@Composable
private fun GitHubTrackAppPickerSortRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetInputTitle(label)
        content()
    }
}

@Composable
private fun GitHubTrackAppTypeCheckbox(
    backdrop: LayerBackdrop,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLiquidCheckbox(
            checked = checked,
            onCheckedChange = null,
            backdrop = backdrop
        )
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GitHubTrackAppPickerButtonRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetInputTitle(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
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
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
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
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit
) {
    var sourceModeExpanded by remember { mutableStateOf(false) }
    var sourceModeAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var preciseModeExpanded by remember { mutableStateOf(false) }
    var preciseModeAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val sourceModes = GitHubTrackedSourceMode.entries
    val sourceModeOptions = sourceModes.map { mode -> trackedSourceModeLabel(mode) }
    val sourceModeIndex = sourceModes.indexOf(sourceModeInput).coerceAtLeast(0)
    val directApkMode = sourceModeInput == GitHubTrackedSourceMode.DirectApk
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
private fun GitHubTrackAppPickerContent(
    backdrop: LayerBackdrop,
    appSearch: String,
    selectedApp: InstalledAppItem?,
    appList: List<InstalledAppItem>,
    trackedPackageNames: Set<String>,
    editingPackageName: String,
    appListRefreshing: Boolean,
    rememberAddPickerScroll: Boolean,
    rememberedFirstVisibleItemIndex: Int,
    rememberedFirstVisibleItemScrollOffset: Int,
    onAppSearchChange: (String) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onRefreshAppList: () -> Unit,
    onAddAppPickerScrollPositionChange: (Int, Int) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val listMaxHeight = (configuration.screenHeightDp.dp * 0.60f).coerceIn(340.dp, 680.dp)
    val savedPreferences = remember { GitHubTrackStore.loadAppPickerPreferences() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var includeUserApps by remember {
        mutableStateOf(savedPreferences.includeUserApps)
    }
    var includeSystemApps by remember {
        mutableStateOf(savedPreferences.includeSystemApps)
    }
    var includeTrackedApps by remember {
        mutableStateOf(savedPreferences.includeTrackedApps)
    }
    var sortMode by remember {
        mutableStateOf(GitHubTrackAppPickerSortMode.fromStorageId(savedPreferences.sortModeId))
    }
    var sortDirection by remember {
        mutableStateOf(
            GitHubTrackAppPickerSortDirection.fromStorageId(savedPreferences.sortDirectionId)
        )
    }
    var initialAppFocusApplied by remember(selectedApp?.packageName) {
        mutableStateOf(false)
    }
    val showInstallSourcePill = sortMode.showsInstallSourcePill()
    val filteredApps =
        remember(
            appList,
            appSearch,
            includeUserApps,
            includeSystemApps,
            includeTrackedApps,
            trackedPackageNames,
            selectedApp,
            editingPackageName,
            sortMode,
            sortDirection
        ) {
            filterAndSortGitHubTrackAppCandidates(
                apps = appList,
                query = appSearch,
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                trackedPackageNames = trackedPackageNames,
                pinnedPackageNames = setOf(
                    selectedApp?.packageName.orEmpty(),
                    editingPackageName
                ),
                sortMode = sortMode,
                sortDirection = sortDirection
            )
        }
    fun saveAppPickerPreferences() {
        GitHubTrackStore.saveAppPickerPreferences(
            GitHubAppPickerPreferences(
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                sortModeId = sortMode.storageId,
                sortDirectionId = sortDirection.storageId
            )
        )
    }

    fun scrollAppListToTop() {
        coroutineScope.launch {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(
        filteredApps,
        selectedApp?.packageName,
        rememberAddPickerScroll,
        rememberedFirstVisibleItemIndex,
        rememberedFirstVisibleItemScrollOffset,
        initialAppFocusApplied
    ) {
        if (initialAppFocusApplied || filteredApps.isEmpty()) return@LaunchedEffect
        if (rememberAddPickerScroll && selectedApp == null) {
            listState.scrollToItem(
                rememberedFirstVisibleItemIndex.coerceIn(filteredApps.indices),
                rememberedFirstVisibleItemScrollOffset.coerceAtLeast(0)
            )
        } else {
            listState.scrollToItem(
                gitHubTrackAppCandidateInitialScrollIndex(
                    candidates = filteredApps,
                    selectedPackageName = selectedApp?.packageName
                )
            )
        }
        initialAppFocusApplied = true
    }

    LaunchedEffect(rememberAddPickerScroll, listState) {
        if (!rememberAddPickerScroll) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onAddAppPickerScrollPositionChange(index, offset)
            }
    }

    SheetContentColumn(
        scrollable = false,
        verticalSpacing = 10.dp
    ) {
        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_app_candidates))
        SheetSectionCard(verticalSpacing = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetInputTitle(
                    text = stringResource(R.string.github_track_sheet_input_app_filter_title),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = if (appListRefreshing) {
                            stringResource(R.string.common_loading)
                        } else {
                            stringResource(R.string.common_refresh)
                        },
                        leadingIcon = appLucideRefreshIcon(),
                        enabled = !appListRefreshing,
                        onClick = onRefreshAppList,
                        minHeight = 30.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 4.dp,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis
                    )
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.github_track_sheet_btn_collapse),
                        onClick = { onPickerExpandedChange(false) },
                        minHeight = 30.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 4.dp,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis
                    )
                }
            }
            AppLiquidSearchField(
                value = appSearch,
                onValueChange = { value ->
                    onAppSearchChange(value)
                    scrollAppListToTop()
                },
                label = stringResource(R.string.github_track_sheet_input_app_filter),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true
            )
            GitHubTrackAppPickerControls(
                backdrop = backdrop,
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                sortMode = sortMode,
                sortDirection = sortDirection,
                onIncludeUserAppsChange = {
                    includeUserApps = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onIncludeSystemAppsChange = {
                    includeSystemApps = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onIncludeTrackedAppsChange = {
                    includeTrackedApps = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onSortModeChange = {
                    sortMode = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onSortDirectionChange = {
                    sortDirection = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                }
            )
            MiuixInfoItem(
                stringResource(R.string.github_track_sheet_label_app_list),
                stringResource(
                    R.string.github_track_sheet_app_result_count_format,
                    filteredApps.size,
                    appList.size
                )
            )
            selectedApp?.let { app ->
                GitHubSelectedAppCard(
                    selectedApp = app,
                    showInstallSource = showInstallSourcePill
                )
            }
            if (filteredApps.isEmpty()) {
                MiuixInfoItem(
                    stringResource(R.string.github_track_sheet_label_app_list),
                    stringResource(R.string.github_track_sheet_msg_app_no_match)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "github_app_candidate" }
                    ) { app ->
                        GitHubAppCandidateRow(
                            app = app,
                            selected = selectedApp?.packageName == app.packageName,
                            showInstallSource = showInstallSourcePill,
                            onClick = {
                                onSelectedAppChange(app)
                                onPickerExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }
    }
}
