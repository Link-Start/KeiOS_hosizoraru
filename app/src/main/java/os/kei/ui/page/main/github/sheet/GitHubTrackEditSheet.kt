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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubAppCandidateRow
import os.kei.ui.page.main.github.GitHubSelectedAppCard
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.widget.core.MiuixInfoItem
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

@Composable
internal fun GitHubTrackEditSheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    editingTrackedItem: GitHubTrackedApp?,
    repoUrlInput: String,
    appSearch: String,
    packageNameInput: String,
    repoUrlScanRunning: Boolean,
    packageNameScanRunning: Boolean,
    pickerExpanded: Boolean,
    selectedApp: InstalledAppItem?,
    appList: List<InstalledAppItem>,
    preferPreReleaseInput: Boolean,
    alwaysShowLatestReleaseDownloadButtonInput: Boolean,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onRepoUrlInputChange: (String) -> Unit,
    onAppSearchChange: (String) -> Unit,
    onPackageNameInputChange: (String) -> Unit,
    onScanRepoUrl: () -> Unit,
    onScanPackageName: () -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit
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
                    onAppSearchChange = onAppSearchChange,
                    onPickerExpandedChange = onPickerExpandedChange,
                    onSelectedAppChange = onSelectedAppChange
                )
            } else {
                GitHubTrackEditFormContent(
                    backdrop = backdrop,
                    repoUrlInput = repoUrlInput,
                    packageNameInput = packageNameInput,
                    repoUrlScanRunning = repoUrlScanRunning,
                    packageNameScanRunning = packageNameScanRunning,
                    selectedApp = selectedApp,
                    preferPreReleaseInput = preferPreReleaseInput,
                    alwaysShowLatestReleaseDownloadButtonInput = alwaysShowLatestReleaseDownloadButtonInput,
                    onRepoUrlInputChange = onRepoUrlInputChange,
                    onPackageNameInputChange = onPackageNameInputChange,
                    onScanRepoUrl = onScanRepoUrl,
                    onScanPackageName = onScanPackageName,
                    onPickerExpandedChange = onPickerExpandedChange,
                    onPreferPreReleaseInputChange = onPreferPreReleaseInputChange,
                    onAlwaysShowLatestReleaseDownloadButtonInputChange = onAlwaysShowLatestReleaseDownloadButtonInputChange
                )
            }
        }
    }
}

@Composable
private fun GitHubTrackEditFormContent(
    backdrop: LayerBackdrop,
    repoUrlInput: String,
    packageNameInput: String,
    repoUrlScanRunning: Boolean,
    packageNameScanRunning: Boolean,
    selectedApp: InstalledAppItem?,
    preferPreReleaseInput: Boolean,
    alwaysShowLatestReleaseDownloadButtonInput: Boolean,
    onRepoUrlInputChange: (String) -> Unit,
    onPackageNameInputChange: (String) -> Unit,
    onScanRepoUrl: () -> Unit,
    onScanPackageName: () -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit
) {
    val canScanRepoUrl = !repoUrlScanRunning &&
            !packageNameScanRunning &&
            (packageNameInput.isNotBlank() || selectedApp != null)
    val canScanPackageName = !repoUrlScanRunning &&
            !packageNameScanRunning &&
            repoUrlInput.isNotBlank()

    SheetContentColumn(verticalSpacing = 10.dp) {
        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_repository))
        SheetSectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetInputTitle(stringResource(R.string.github_track_sheet_input_repo))
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
            AppLiquidSearchField(
                value = repoUrlInput,
                onValueChange = onRepoUrlInputChange,
                label = stringResource(R.string.github_track_sheet_input_repo),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true
            )
            SheetDescriptionText(
                text = stringResource(R.string.github_track_sheet_summary_repo)
            )
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
                text = stringResource(R.string.github_track_sheet_summary_package_link)
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
        }
    }
}

@Composable
private fun GitHubTrackAppPickerContent(
    backdrop: LayerBackdrop,
    appSearch: String,
    selectedApp: InstalledAppItem?,
    appList: List<InstalledAppItem>,
    onAppSearchChange: (String) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val listMaxHeight = (configuration.screenHeightDp.dp * 0.60f).coerceIn(340.dp, 680.dp)
    val filteredApps = remember(appList, appSearch) {
        appList.filter { app ->
            appSearch.isBlank() ||
                    app.label.contains(appSearch, ignoreCase = true) ||
                    app.packageName.contains(appSearch, ignoreCase = true)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetInputTitle(stringResource(R.string.github_track_sheet_input_app_filter_title))
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.github_track_sheet_btn_collapse),
                    onClick = { onPickerExpandedChange(false) },
                    minHeight = 30.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1
                )
            }
            AppLiquidSearchField(
                value = appSearch,
                onValueChange = onAppSearchChange,
                label = stringResource(R.string.github_track_sheet_input_app_filter),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true
            )
            selectedApp?.let { app ->
                GitHubSelectedAppCard(selectedApp = app)
            }
            if (filteredApps.isEmpty()) {
                MiuixInfoItem(
                    stringResource(R.string.github_track_sheet_label_app_list),
                    stringResource(R.string.github_track_sheet_msg_app_no_match)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        GitHubAppCandidateRow(
                            app = app,
                            selected = selectedApp?.packageName == app.packageName,
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
