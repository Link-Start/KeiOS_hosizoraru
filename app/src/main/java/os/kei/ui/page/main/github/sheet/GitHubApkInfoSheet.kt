package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubApkInfoSheet(
    asset: GitHubReleaseAssetFile?,
    info: GitHubApkManifestInfo?,
    installedInfo: GitHubInstalledPackageInfo?,
    loading: Boolean,
    error: String,
    backdrop: LayerBackdrop,
    onDismissRequest: () -> Unit
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
                onClick = onDismissRequest
            )
        }
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSummaryCard(
                title = asset.name,
                badgeLabel = stringResource(R.string.github_apk_info_badge_manifest),
                badgeColor = MiuixTheme.colorScheme.primary
            ) {
                when {
                    loading -> DetailLine(stringResource(R.string.github_apk_info_loading))
                    error.isNotBlank() -> DetailLine(error)
                    info == null -> DetailLine(stringResource(R.string.github_apk_info_empty))
                    else -> {
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_package),
                            value = info.packageName
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_version),
                            value = listOf(info.versionName, info.versionCode)
                                .filter { it.isNotBlank() }
                                .joinToString(" / ")
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_api),
                            value = stringResource(
                                R.string.github_apk_info_value_api,
                                info.minSdk.ifBlank { "-" },
                                info.targetSdk.ifBlank { "-" }
                            )
                        )
                    }
                }
            }
            if (info != null) {
                InstalledPackageSection(
                    installedInfo = installedInfo,
                    manifestPackageName = info.packageName
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_abi),
                    empty = stringResource(R.string.github_apk_info_empty_abi),
                    values = info.nativeAbis
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_permissions),
                    empty = stringResource(R.string.github_apk_info_empty_permissions),
                    values = info.permissions
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_features),
                    empty = stringResource(R.string.github_apk_info_empty_features),
                    values = info.features
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_metadata),
                    empty = stringResource(R.string.github_apk_info_empty_metadata),
                    values = info.metadata.map { metadata ->
                        if (metadata.value.isBlank()) metadata.name else "${metadata.name}: ${metadata.value}"
                    }
                )
            }
        }
    }
}

@Composable
private fun InstalledPackageSection(
    installedInfo: GitHubInstalledPackageInfo?,
    manifestPackageName: String
) {
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_installed))
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (installedInfo == null) {
            DetailLine(
                stringResource(
                    R.string.github_apk_info_installed_missing,
                    manifestPackageName.ifBlank { "-" }
                )
            )
        } else {
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_installed_status),
                value = stringResource(R.string.github_apk_info_installed_present)
            )
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_app),
                value = installedInfo.appLabel
            )
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_local_version),
                value = listOf(
                    installedInfo.versionName,
                    installedInfo.versionCode.takeIf { it >= 0L }?.toString().orEmpty()
                ).filter { it.isNotBlank() }.joinToString(" / ")
            )
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_local_api),
                value = stringResource(
                    R.string.github_apk_info_value_api,
                    installedInfo.minSdk.takeIf { it >= 0 }?.toString().orEmpty().ifBlank { "-" },
                    installedInfo.targetSdk.takeIf { it >= 0 }?.toString().orEmpty().ifBlank { "-" }
                )
            )
        }
    }
}

@Composable
private fun InfoListSection(
    title: String,
    empty: String,
    values: List<String>
) {
    SheetSectionTitle(title)
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (values.isEmpty()) {
            SheetDescriptionText(empty)
        } else {
            values.take(INFO_LIST_LIMIT).forEach { value ->
                DetailLine(value, maxLines = 2)
            }
            if (values.size > INFO_LIST_LIMIT) {
                DetailLine(
                    stringResource(
                        R.string.github_apk_info_more_count,
                        values.size - INFO_LIST_LIMIT
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.36f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.64f),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailLine(
    text: String,
    maxLines: Int = 3
) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        fontSize = AppTypographyTokens.Supporting.fontSize,
        lineHeight = AppTypographyTokens.Supporting.lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

private const val INFO_LIST_LIMIT = 12
