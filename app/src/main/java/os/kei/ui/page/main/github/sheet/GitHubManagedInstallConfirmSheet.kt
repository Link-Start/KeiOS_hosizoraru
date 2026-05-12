package os.kei.ui.page.main.github.sheet

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubApkTrustReason
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.assetAbiLabel
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.github.buildGitHubApkTrustSignal
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
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
internal fun GitHubManagedInstallConfirmSheet(
    request: GitHubManagedInstallConfirmRequest?,
    info: GitHubApkManifestInfo?,
    installedInfo: GitHubInstalledPackageInfo?,
    loading: Boolean,
    error: String,
    running: Boolean,
    backdrop: LayerBackdrop,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    request ?: return
    val context = LocalContext.current
    val asset = request.asset
    val supportedAbis = remember { Build.SUPPORTED_ABIS?.toList().orEmpty() }
    val trustSignal = remember(asset, supportedAbis) {
        buildGitHubApkTrustSignal(asset, supportedAbis)
    }
    val preferredForDevice = remember(asset, supportedAbis) {
        assetIsPreferredForDevice(asset.name, supportedAbis)
    }
    val likelyCompatible = remember(asset, supportedAbis) {
        assetLikelyCompatibleWithDevice(asset.name, supportedAbis)
    }
    val abiLabel = assetAbiLabel(asset.name)
    val actionColor = if (trustSignal.level == GitHubDecisionLevel.Risk) {
        GitHubStatusPalette.Error
    } else {
        GitHubStatusPalette.Active
    }

    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_page_install_confirm_title),
        onDismissRequest = onDismissRequest,
        allowDismiss = !running,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
                enabled = !running
            )
        }
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSummaryCard(
                title = info?.appLabel
                    .orEmpty()
                    .ifBlank { request.item.appLabel }
                    .ifBlank { assetDisplayName(asset.name) },
                badgeLabel = stringResource(trustSignal.level.labelRes()),
                badgeColor = trustSignal.level.toStatusColor()
            ) {
                SheetDescriptionText(
                    text = stringResource(R.string.github_page_install_confirm_summary)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    InstallConfirmPill(
                        label = formatAssetSize(asset.sizeBytes, context),
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    abiLabel?.let {
                        InstallConfirmPill(
                            label = it,
                            color = if (likelyCompatible) {
                                GitHubStatusPalette.Update
                            } else {
                                GitHubStatusPalette.Error
                            }
                        )
                    }
                    if (preferredForDevice) {
                        InstallConfirmPill(
                            label = stringResource(R.string.github_asset_badge_recommended),
                            color = GitHubStatusPalette.Update
                        )
                    } else if (!likelyCompatible) {
                        InstallConfirmPill(
                            label = stringResource(R.string.github_asset_badge_incompatible),
                            color = GitHubStatusPalette.Error
                        )
                    }
                }
            }

            SheetSectionTitle(stringResource(R.string.github_page_install_confirm_section_asset))
            SheetSectionCard {
                ConfirmInfoRow(
                    label = stringResource(R.string.github_share_import_dialog_label_project),
                    value = "${request.item.owner}/${request.item.repo}"
                )
                ConfirmInfoRow(
                    label = stringResource(R.string.github_share_import_pending_label_asset),
                    value = asset.name
                )
                ConfirmInfoRow(
                    label = stringResource(R.string.github_apk_info_label_source),
                    value = stringResource(
                        if (asset.apiAssetUrl.isNotBlank()) {
                            R.string.github_asset_fetch_source_api
                        } else {
                            R.string.github_asset_transport_direct
                        }
                    )
                )
            }

            SheetSectionTitle(stringResource(R.string.github_page_install_confirm_section_manifest))
            SheetSectionCard {
                when {
                    loading -> LoadingManifestRow()
                    error.isNotBlank() -> ConfirmHintText(
                        text = error,
                        color = GitHubStatusPalette.Error
                    )

                    info == null -> ConfirmHintText(
                        text = stringResource(R.string.github_apk_info_empty),
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )

                    else -> ManifestInfoBlock(info, installedInfo)
                }
            }

            info?.let { manifestInfo ->
                SheetSectionTitle(stringResource(R.string.github_page_install_confirm_section_compare))
                SheetSectionCard {
                    LocalRemoteComparisonBlock(
                        info = manifestInfo,
                        installedInfo = installedInfo,
                        supportedAbis = supportedAbis
                    )
                }
            }

            trustSignal.reasons
                .takeIf { reasons ->
                    trustSignal.level != GitHubDecisionLevel.Good ||
                            reasons.any { it != GitHubApkTrustReason.ApkLike }
                }
                ?.let { reasons ->
                    SheetSectionTitle(stringResource(R.string.github_page_install_confirm_section_review))
                    SheetSectionCard {
                        ConfirmHintText(
                            text = reasons.joinToString(" / ") { reason ->
                                context.getString(reason.labelRes())
                            },
                            color = trustSignal.level.toStatusColor()
                        )
                    }
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onDismissRequest,
                    enabled = !running,
                    variant = GlassVariant.SheetAction
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = if (running) {
                        stringResource(R.string.common_processing)
                    } else {
                        stringResource(R.string.github_page_install_confirm_action_install)
                    },
                    leadingIcon = appLucidePackageIcon(),
                    containerColor = actionColor,
                    onClick = onConfirm,
                    enabled = !loading && !running
                )
            }
        }
    }
}

@Composable
private fun ManifestInfoBlock(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
) {
    ConfirmInfoRow(
        label = stringResource(R.string.github_apk_info_label_app),
        value = info.appLabel
    )
    ConfirmInfoRow(
        label = stringResource(R.string.github_apk_info_label_package),
        value = info.packageName
    )
    ConfirmInfoRow(
        label = stringResource(R.string.github_apk_info_label_version),
        value = listOf(info.versionName, info.versionCode)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
    )
    ConfirmInfoRow(
        label = stringResource(R.string.github_apk_info_label_api),
        value = stringResource(
            R.string.github_apk_info_value_api,
            info.minSdk.ifBlank { "-" },
            info.targetSdk.ifBlank { "-" }
        )
    )
    ConfirmInfoRow(
        label = stringResource(R.string.github_apk_info_label_abi),
        value = info.nativeAbis.takeIf { it.isNotEmpty() }?.joinToString(" / ")
            ?: stringResource(R.string.github_apk_info_diff_abi_universal)
    )
    ConfirmInfoRow(
        label = stringResource(R.string.github_apk_info_label_installed_status),
        value = installedInfo?.let { installed ->
            listOf(installed.versionName, installed.versionCode.takeIf { it >= 0L }?.toString())
                .filter { !it.isNullOrBlank() }
                .joinToString(" / ")
                .ifBlank { stringResource(R.string.github_apk_info_installed_present) }
        } ?: stringResource(
            R.string.github_apk_info_installed_missing,
            info.packageName.ifBlank { "-" }
        )
    )
}

@Composable
private fun LocalRemoteComparisonBlock(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    supportedAbis: List<String>
) {
    ComparisonInfoRow(
        label = stringResource(R.string.github_apk_info_label_app),
        localValue = installedInfo?.appLabel.orEmpty(),
        remoteValue = info.appLabel,
        compareWhenLocalMissing = false
    )
    ComparisonInfoRow(
        label = stringResource(R.string.github_page_install_compare_label_version_name),
        localValue = installedInfo?.versionName.orEmpty(),
        remoteValue = info.versionName
    )
    ComparisonInfoRow(
        label = stringResource(R.string.github_page_install_compare_label_version_code),
        localValue = installedInfo?.versionCode
            ?.takeIf { it >= 0L }
            ?.toString()
            .orEmpty(),
        remoteValue = info.versionCode
    )
    ComparisonInfoRow(
        label = stringResource(R.string.github_page_install_compare_label_target_api),
        localValue = installedInfo?.targetSdk
            ?.takeIf { it >= 0 }
            ?.toString()
            .orEmpty(),
        remoteValue = info.targetSdk
    )
    ComparisonInfoRow(
        label = stringResource(R.string.github_page_install_compare_label_abi),
        localValue = supportedAbis
            .take(2)
            .joinToString(" / "),
        remoteValue = info.nativeAbis.takeIf { it.isNotEmpty() }?.joinToString(" / ")
            ?: stringResource(R.string.github_apk_info_diff_abi_universal),
        compareWhenLocalMissing = false
    )
}

@Composable
private fun ComparisonInfoRow(
    label: String,
    localValue: String,
    remoteValue: String,
    compareWhenLocalMissing: Boolean = true
) {
    ConfirmInfoRow(
        label = label,
        value = comparisonValue(
            localValue = localValue,
            remoteValue = remoteValue,
            compareWhenLocalMissing = compareWhenLocalMissing
        )
    )
}

@Composable
private fun comparisonValue(
    localValue: String,
    remoteValue: String,
    compareWhenLocalMissing: Boolean
): String {
    val local = localValue.trim()
    val remote = remoteValue.trim()
    val remoteDisplay = remote.ifBlank { stringResource(R.string.common_na) }
    if (local.isBlank()) {
        return if (compareWhenLocalMissing) {
            stringResource(
                R.string.github_page_install_compare_arrow,
                stringResource(R.string.github_page_install_compare_not_installed),
                remoteDisplay
            )
        } else {
            remoteDisplay
        }
    }
    if (remote.isBlank() || local.equals(remote, ignoreCase = true)) {
        return local
    }
    return stringResource(R.string.github_page_install_compare_arrow, local, remote)
}

@Composable
private fun LoadingManifestRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidCircularProgressBar(size = 22.dp)
        Text(
            text = stringResource(R.string.github_apk_info_loading),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight
        )
    }
}

@Composable
private fun ConfirmInfoRow(
    label: String,
    value: String
) {
    AppInfoRow(
        label = label,
        value = value,
        labelWeight = 0.26f,
        valueWeight = 0.74f,
        valueTextAlign = TextAlign.Start,
        horizontalSpacing = 8.dp,
        rowVerticalPadding = 2.dp,
        valueMaxLines = 2,
        valueOverflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ConfirmHintText(
    text: String,
    color: Color
) {
    Text(
        text = text,
        color = color,
        fontSize = AppTypographyTokens.Body.fontSize,
        lineHeight = AppTypographyTokens.Body.lineHeight
    )
}

@Composable
private fun InstallConfirmPill(
    label: String,
    color: Color
) {
    StatusPill(label = label, color = color)
}

private fun GitHubDecisionLevel.labelRes(): Int {
    return when (this) {
        GitHubDecisionLevel.Good -> R.string.github_apk_trust_good
        GitHubDecisionLevel.Review -> R.string.github_apk_trust_review
        GitHubDecisionLevel.Risk -> R.string.github_apk_trust_risk
    }
}

private fun GitHubDecisionLevel.toStatusColor(): Color {
    return when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }
}

private fun GitHubApkTrustReason.labelRes(): Int {
    return when (this) {
        GitHubApkTrustReason.PreferredAbi -> R.string.github_apk_trust_reason_preferred_abi
        GitHubApkTrustReason.UniversalAsset -> R.string.github_apk_trust_reason_universal
        GitHubApkTrustReason.IncompatibleAbi -> R.string.github_apk_trust_reason_incompatible
        GitHubApkTrustReason.DebugBuild -> R.string.github_apk_trust_reason_debug
        GitHubApkTrustReason.UnsignedBuild -> R.string.github_apk_trust_reason_unsigned
        GitHubApkTrustReason.SourceArchive -> R.string.github_apk_trust_reason_source
        GitHubApkTrustReason.ApkLike -> R.string.github_apk_trust_reason_apk
        GitHubApkTrustReason.UnknownFormat -> R.string.github_apk_trust_reason_unknown_format
    }
}
