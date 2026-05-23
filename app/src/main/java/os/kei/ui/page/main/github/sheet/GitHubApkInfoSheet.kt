@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubApkInfoSheet(
    asset: GitHubReleaseAssetFile?,
    info: GitHubApkManifestInfo?,
    installedInfo: GitHubInstalledPackageInfo?,
    loading: Boolean,
    error: String,
    sheetState: GitHubApkInfoSheetUiState,
    backdrop: LayerBackdrop,
    managedInstallEnabled: Boolean,
    managedInstallRunning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    asset ?: return
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_apk_info_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSummaryCard(
                title = asset.name,
                badgeLabel = stringResource(R.string.github_apk_info_badge_manifest),
                badgeColor = MiuixTheme.colorScheme.primary,
            ) {
                when {
                    loading -> {
                        DetailLine(stringResource(R.string.github_apk_info_loading))
                    }

                    error.isNotBlank() -> {
                        DetailLine(error)
                    }

                    info == null -> {
                        DetailLine(stringResource(R.string.github_apk_info_empty))
                    }

                    else -> {
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_package),
                            value = info.packageName,
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_version),
                            value =
                                listOf(info.versionName, info.versionCode)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" / "),
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_api),
                            value =
                                stringResource(
                                    R.string.github_apk_info_value_api,
                                    info.minSdk.ifBlank { "-" },
                                    info.targetSdk.ifBlank { "-" },
                                ),
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_source),
                            value = apkInfoSourceLabel(info.fetchSource),
                        )
                    }
                }
            }
            if (info != null) {
                ApkInfoActionRow(
                    backdrop = backdrop,
                    loading = loading,
                    managedInstallEnabled = managedInstallEnabled,
                    managedInstallRunning = managedInstallRunning,
                    onRefresh = onRefresh,
                    onInstall = onInstall,
                    onDownload = onDownload,
                    onShare = onShare,
                )
                AppLiquidSearchField(
                    value = sheetState.query,
                    onValueChange = onSearchQueryChange,
                    label = stringResource(R.string.github_apk_info_search_label),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.SearchField,
                    singleLine = true,
                )
                ApkTrustReportSection(
                    info = info,
                    installedInfo = installedInfo,
                )
                ApkDifferenceSection(
                    info = info,
                    installedInfo = installedInfo,
                )
                InstalledPackageSection(
                    info = info,
                    installedInfo = installedInfo,
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_abi),
                    empty = stringResource(R.string.github_apk_info_empty_abi),
                    values = sheetState.nativeAbiValues,
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_permissions),
                    empty = stringResource(R.string.github_apk_info_empty_permissions),
                    values = sheetState.permissionValues,
                    colors = sheetState.permissionColors,
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_features),
                    empty = stringResource(R.string.github_apk_info_empty_features),
                    values = sheetState.featureValues,
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_metadata),
                    empty = stringResource(R.string.github_apk_info_empty_metadata),
                    values = sheetState.metadataValues,
                )
                SignatureSection(info.signatureInfo, sheetState.normalizedQuery)
                ApkInfoMeaningSection()
                ManifestTreeSection(
                    nodeGroups = sheetState.manifestNodeGroups,
                    query = sheetState.normalizedQuery,
                )
            }
        }
    }
}

@Composable
private fun ApkInfoActionRow(
    backdrop: LayerBackdrop,
    loading: Boolean,
    managedInstallEnabled: Boolean,
    managedInstallRunning: Boolean,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLiquidIconButton(
            backdrop = backdrop,
            icon = appLucideRefreshIcon(),
            contentDescription =
                stringResource(
                    if (loading) R.string.github_apk_info_action_refreshing else R.string.github_apk_info_action_refresh,
                ),
            onClick = onRefresh,
            enabled = !loading,
            variant = GlassVariant.SheetAction,
            iconTint =
                if (loading) {
                    MiuixTheme.colorScheme.onBackgroundVariant
                } else {
                    MiuixTheme.colorScheme.primary
                },
        )
        if (managedInstallEnabled) {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = appLucidePackageIcon(),
                contentDescription =
                    stringResource(
                        if (managedInstallRunning) {
                            R.string.github_apk_info_action_installing
                        } else {
                            R.string.github_apk_info_action_install
                        },
                    ),
                onClick = onInstall,
                enabled = !managedInstallRunning,
                variant = GlassVariant.SheetPrimaryAction,
            )
        }
        AppLiquidIconButton(
            backdrop = backdrop,
            icon = appLucideDownloadIcon(),
            contentDescription = stringResource(R.string.github_apk_info_action_download),
            onClick = onDownload,
            variant = GlassVariant.SheetAction,
        )
        AppLiquidIconButton(
            backdrop = backdrop,
            icon = appLucideShareIcon(),
            contentDescription = stringResource(R.string.github_apk_info_action_share),
            onClick = onShare,
            variant = GlassVariant.SheetAction,
        )
    }
}
