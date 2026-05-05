package os.kei.ui.page.main.github.sheet

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
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubTrackEditSheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    editingTrackedItem: GitHubTrackedApp?,
    repoUrlInput: String,
    appSearch: String,
    packageNameInput: String,
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
    onScanPackageName: () -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit,
    onRequestDelete: () -> Unit
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
        SheetContentColumn(
            scrollable = !pickerExpanded,
            verticalSpacing = 8.dp
        ) {
            SheetSectionTitle(stringResource(R.string.github_track_sheet_section_repo_app))
            SheetSectionCard {
                SheetInputTitle(stringResource(R.string.github_track_sheet_input_repo))
                AppLiquidSearchField(
                    value = repoUrlInput,
                    onValueChange = onRepoUrlInputChange,
                    label = stringResource(R.string.github_track_sheet_input_repo),
                    backdrop = backdrop,
                    variant = GlassVariant.SheetInput,
                    singleLine = true
                )
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
                        enabled = !packageNameScanRunning && repoUrlInput.isNotBlank(),
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
                    text = stringResource(R.string.github_track_sheet_summary_package_optional)
                )
                SheetInputTitle(stringResource(R.string.github_track_sheet_input_app_filter_title))
                AppLiquidSearchField(
                    value = appSearch,
                    onValueChange = onAppSearchChange,
                    label = stringResource(R.string.github_track_sheet_input_app_filter),
                    backdrop = backdrop,
                    variant = GlassVariant.SheetInput,
                    singleLine = true
                )
                SheetControlRow(
                    label = stringResource(R.string.github_track_sheet_label_selected_app),
                    summary = if (selectedApp == null) {
                        stringResource(R.string.github_track_sheet_selected_none)
                    } else {
                        null
                    }
                ) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = if (pickerExpanded) {
                            stringResource(R.string.github_track_sheet_btn_collapse)
                        } else {
                            stringResource(R.string.github_track_sheet_btn_select_app)
                        },
                        onClick = { onPickerExpandedChange(!pickerExpanded) }
                    )
                }
                selectedApp?.let { app ->
                    GitHubSelectedAppCard(selectedApp = app)
                }
            }
            if (pickerExpanded) {
                val filteredApps = remember(appList, appSearch) {
                    appList.filter { app ->
                        appSearch.isBlank() ||
                            app.label.contains(appSearch, ignoreCase = true) ||
                            app.packageName.contains(appSearch, ignoreCase = true)
                    }
                }
                SheetSectionTitle(stringResource(R.string.github_track_sheet_section_app_candidates))
                SheetSectionCard(verticalSpacing = 6.dp) {
                    if (filteredApps.isEmpty()) {
                        MiuixInfoItem(
                            stringResource(R.string.github_track_sheet_label_app_list),
                            stringResource(R.string.github_track_sheet_msg_app_no_match)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
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
            if (editingTrackedItem != null) {
                SheetSectionTitle(
                    text = stringResource(R.string.github_track_sheet_danger_title),
                    danger = true
                )
                SheetSectionCard {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetDangerAction,
                        text = stringResource(R.string.github_track_sheet_btn_delete),
                        textColor = MiuixTheme.colorScheme.error,
                        onClick = onRequestDelete
                    )
                }
            }
        }
    }
}
