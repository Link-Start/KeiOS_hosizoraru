package os.kei.ui.page.main.github.sheet

import android.os.Build
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette
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
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
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
                ApkDifferenceSection(
                    info = info,
                    installedInfo = installedInfo
                )
                InstalledPackageSection(
                    info = info,
                    installedInfo = installedInfo,
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
@OptIn(ExperimentalLayoutApi::class)
private fun ApkDifferenceSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
) {
    val signals = buildApkDifferenceSignals(
        info = info,
        installedInfo = installedInfo,
        strings = apkDifferenceStrings()
    )
    if (signals.isEmpty()) return
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_diff))
    SheetSectionCard(verticalSpacing = 6.dp) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            signals.forEach { signal ->
                StatusPill(
                    label = signal.label,
                    color = signal.color
                )
            }
        }
    }
}

@Composable
private fun InstalledPackageSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
) {
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_installed))
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (installedInfo?.appLabel?.isNotBlank() == true) {
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_app),
                value = installedInfo.appLabel
            )
        }
        val versionColors = compareVersionColors(
            remoteVersionCode = info.versionCode.toLongOrNull(),
            localVersionCode = installedInfo?.versionCode ?: -1L
        )
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_version),
            remoteLabel = info.remoteVersionLabel(),
            localLabel = installedInfo?.localVersionLabel()
                ?: stringResource(R.string.github_apk_info_diff_not_installed),
            remoteColor = versionColors.remote,
            localColor = versionColors.local
        )
        val apiColors = compareApiColors(
            remoteTargetSdk = info.targetSdk.toIntOrNull(),
            localTargetSdk = installedInfo?.targetSdk ?: -1
        )
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_api),
            remoteLabel = stringResource(
                R.string.github_apk_info_value_api,
                info.minSdk.ifBlank { "-" },
                info.targetSdk.ifBlank { "-" }
            ),
            localLabel = installedInfo?.let { local ->
                stringResource(
                    R.string.github_apk_info_value_api,
                    local.minSdk.takeIf { it >= 0 }?.toString().orEmpty().ifBlank { "-" },
                    local.targetSdk.takeIf { it >= 0 }?.toString().orEmpty().ifBlank { "-" }
                )
            } ?: stringResource(R.string.github_apk_info_diff_not_installed),
            remoteColor = apiColors.remote,
            localColor = apiColors.local
        )
        val abiSignal = buildAbiSignal(info.nativeAbis, apkDifferenceStrings())
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_abi),
            remoteLabel = info.nativeAbis.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: stringResource(R.string.github_apk_info_diff_abi_universal),
            localLabel = Build.SUPPORTED_ABIS.orEmpty().take(2).joinToString(", ").ifBlank { "-" },
            remoteColor = abiSignal.color,
            localColor = GitHubStatusPalette.Active
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ComparisonPillRow(
    label: String,
    remoteLabel: String,
    localLabel: String,
    remoteColor: Color,
    localColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.24f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            modifier = Modifier.weight(0.76f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                label = stringResource(R.string.github_apk_info_compare_remote_format, remoteLabel),
                color = remoteColor
            )
            StatusPill(
                label = stringResource(R.string.github_apk_info_compare_local_format, localLabel),
                color = localColor
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

@Composable
private fun apkDifferenceStrings(): ApkDifferenceStrings {
    return ApkDifferenceStrings(
        notInstalled = stringResource(R.string.github_apk_info_diff_not_installed),
        remoteVersionHigher = stringResource(R.string.github_apk_info_diff_remote_version_higher),
        localVersionHigher = stringResource(R.string.github_apk_info_diff_local_version_higher),
        sameVersion = stringResource(R.string.github_apk_info_diff_same_version),
        versionManual = stringResource(R.string.github_apk_info_diff_version_manual),
        targetHigher = stringResource(R.string.github_apk_info_diff_target_higher),
        targetSame = stringResource(R.string.github_apk_info_diff_target_same),
        targetLower = stringResource(R.string.github_apk_info_diff_target_lower),
        abiUniversal = stringResource(R.string.github_apk_info_diff_abi_universal),
        abiMatch = stringResource(R.string.github_apk_info_diff_abi_match),
        abiMismatch = stringResource(R.string.github_apk_info_diff_abi_mismatch)
    )
}

private fun buildApkDifferenceSignals(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    strings: ApkDifferenceStrings
): List<ApkDifferenceSignal> {
    val signals = mutableListOf<ApkDifferenceSignal>()
    val remoteVersionCode = info.versionCode.toLongOrNull()
    val remoteTargetSdk = info.targetSdk.toIntOrNull()
    if (installedInfo == null) {
        signals += ApkDifferenceSignal(
            label = strings.notInstalled,
            color = GitHubStatusPalette.Active
        )
    } else {
        signals += when {
            remoteVersionCode == null || installedInfo.versionCode < 0L ->
                ApkDifferenceSignal(strings.versionManual, GitHubStatusPalette.Cache)

            remoteVersionCode > installedInfo.versionCode ->
                ApkDifferenceSignal(strings.remoteVersionHigher, GitHubStatusPalette.Update)

            remoteVersionCode < installedInfo.versionCode ->
                ApkDifferenceSignal(strings.localVersionHigher, GitHubStatusPalette.PreRelease)

            else -> ApkDifferenceSignal(strings.sameVersion, GitHubStatusPalette.Stable)
        }
        if (remoteTargetSdk != null && installedInfo.targetSdk >= 0) {
            signals += when {
                remoteTargetSdk > installedInfo.targetSdk ->
                    ApkDifferenceSignal(strings.targetHigher, GitHubStatusPalette.Update)

                remoteTargetSdk < installedInfo.targetSdk ->
                    ApkDifferenceSignal(strings.targetLower, GitHubStatusPalette.Cache)

                else -> ApkDifferenceSignal(strings.targetSame, GitHubStatusPalette.Stable)
            }
        }
    }
    signals += buildAbiSignal(info.nativeAbis, strings)
    return signals
}

private fun compareVersionColors(
    remoteVersionCode: Long?,
    localVersionCode: Long
): ComparisonColors {
    return when {
        remoteVersionCode == null || localVersionCode < 0L ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Cache)

        remoteVersionCode > localVersionCode ->
            ComparisonColors(GitHubStatusPalette.Update, GitHubStatusPalette.Stable)

        remoteVersionCode < localVersionCode ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.PreRelease)

        else -> ComparisonColors(GitHubStatusPalette.Stable, GitHubStatusPalette.Stable)
    }
}

private fun compareApiColors(
    remoteTargetSdk: Int?,
    localTargetSdk: Int
): ComparisonColors {
    return when {
        remoteTargetSdk == null || localTargetSdk < 0 ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Cache)

        remoteTargetSdk > localTargetSdk ->
            ComparisonColors(GitHubStatusPalette.Update, GitHubStatusPalette.Stable)

        remoteTargetSdk < localTargetSdk ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Active)

        else -> ComparisonColors(GitHubStatusPalette.Stable, GitHubStatusPalette.Stable)
    }
}

private fun GitHubApkManifestInfo.remoteVersionLabel(): String {
    return listOf(versionName, versionCode).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }
}

private fun GitHubInstalledPackageInfo.localVersionLabel(): String {
    return listOf(
        versionName,
        versionCode.takeIf { it >= 0L }?.toString().orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }
}

private fun buildAbiSignal(
    nativeAbis: List<String>,
    strings: ApkDifferenceStrings
): ApkDifferenceSignal {
    if (nativeAbis.isEmpty()) {
        return ApkDifferenceSignal(strings.abiUniversal, GitHubStatusPalette.Stable)
    }
    val supportedAbis = Build.SUPPORTED_ABIS.orEmpty().map { it.lowercase() }.toSet()
    val hasDeviceAbi = nativeAbis.any { abi -> abi.lowercase() in supportedAbis }
    return if (hasDeviceAbi) {
        ApkDifferenceSignal(strings.abiMatch, GitHubStatusPalette.Update)
    } else {
        ApkDifferenceSignal(strings.abiMismatch, GitHubStatusPalette.Error)
    }
}

private data class ApkDifferenceSignal(
    val label: String,
    val color: Color
)

private data class ComparisonColors(
    val remote: Color,
    val local: Color
)

private data class ApkDifferenceStrings(
    val notInstalled: String,
    val remoteVersionHigher: String,
    val localVersionHigher: String,
    val sameVersion: String,
    val versionManual: String,
    val targetHigher: String,
    val targetSame: String,
    val targetLower: String,
    val abiUniversal: String,
    val abiMatch: String,
    val abiMismatch: String
)
