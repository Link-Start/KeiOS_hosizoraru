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
import os.kei.core.ui.resource.resolveString
import os.kei.feature.github.data.remote.isGitHubActionsApkArtifactArchive
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
    val canConfirmWithoutManifest = asset.isGitHubActionsApkArtifactArchive()
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
    val versionDecision = remember(info, installedInfo) {
        info?.let { manifestInfo ->
            buildInstallVersionDecision(manifestInfo, installedInfo)
        }
    }
    val abiDecision = remember(info, supportedAbis, likelyCompatible) {
        info?.let { manifestInfo ->
            buildInstallAbiDecision(
                info = manifestInfo,
                supportedAbis = supportedAbis,
                likelyCompatible = likelyCompatible
            )
        }
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
                title = installConfirmTitle(
                    info = info,
                    installedInfo = installedInfo,
                    fallbackAppLabel = request.item.appLabel,
                    assetName = asset.name
                ),
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
                    versionDecision?.let { decision ->
                        InstallConfirmPill(
                            label = stringResource(decision.labelRes()),
                            color = decision.statusColor()
                        )
                    }
                    abiDecision?.let { decision ->
                        InstallConfirmPill(
                            label = stringResource(decision.labelRes()),
                            color = decision.statusColor()
                        )
                    }
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

            SheetSectionTitle(stringResource(R.string.github_page_install_confirm_section_compare))
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

                    else -> InstallComparisonBlock(
                        info = info,
                        installedInfo = installedInfo,
                        supportedAbis = supportedAbis,
                        fallbackAppLabel = request.item.appLabel,
                        assetName = asset.name,
                        assetSizeBytes = asset.sizeBytes
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
                                context.resolveString(reason.labelRes())
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
                    enabled = (info != null || canConfirmWithoutManifest) && !loading && !running
                )
            }
        }
    }
}

@Composable
private fun InstallComparisonBlock(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    supportedAbis: List<String>,
    fallbackAppLabel: String,
    assetName: String,
    assetSizeBytes: Long
) {
    val context = LocalContext.current
    ComparisonInfoRow(
        label = stringResource(R.string.github_apk_info_label_package),
        localValue = installedInfo?.packageName.orEmpty(),
        remoteValue = info.packageName
    )
    ComparisonInfoRow(
        label = stringResource(R.string.github_apk_info_label_app),
        localValue = installedInfo?.appLabel.orEmpty(),
        remoteValue = resolvedRemoteAppLabel(
            info = info,
            installedInfo = installedInfo,
            fallbackAppLabel = fallbackAppLabel,
            assetName = assetName
        ),
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
        label = stringResource(R.string.github_page_install_compare_label_min_api),
        localValue = installedInfo?.minSdk
            ?.takeIf { it >= 0 }
            ?.toString()
            .orEmpty(),
        remoteValue = info.minSdk
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
    ComparisonInfoRow(
        label = stringResource(R.string.github_page_install_compare_label_size),
        localValue = installedInfo?.apkSizeBytes
            ?.takeIf { it > 0L }
            ?.let { formatAssetSize(it, context) }
            .orEmpty(),
        remoteValue = formatAssetSize(assetSizeBytes, context)
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
        ),
        valueTextAlign = TextAlign.End
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
    if (remote.isBlank() || local == remote) {
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
    value: String,
    valueTextAlign: TextAlign = TextAlign.Start
) {
    AppInfoRow(
        label = label,
        value = value,
        labelWeight = 0.38f,
        valueWeight = 0.62f,
        valueTextAlign = valueTextAlign,
        horizontalSpacing = 8.dp,
        rowVerticalPadding = 2.dp,
        labelMaxLines = 1,
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

private fun installConfirmTitle(
    info: GitHubApkManifestInfo?,
    installedInfo: GitHubInstalledPackageInfo?,
    fallbackAppLabel: String,
    assetName: String
): String {
    return info
        ?.let { manifestInfo ->
            resolvedRemoteAppLabel(
                info = manifestInfo,
                installedInfo = installedInfo,
                fallbackAppLabel = fallbackAppLabel,
                assetName = assetName
            )
        }
        .orEmpty()
        .ifBlank { installedInfo?.appLabel.orEmpty() }
        .ifBlank { fallbackAppLabel }
        .ifBlank { assetDisplayName(assetName) }
}

private fun resolvedRemoteAppLabel(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    fallbackAppLabel: String,
    assetName: String
): String {
    return info.appLabel
        .trim()
        .ifBlank { installedInfo?.appLabel.orEmpty().trim() }
        .ifBlank { fallbackAppLabel.trim() }
        .ifBlank { assetDisplayName(assetName) }
}

private fun buildInstallVersionDecision(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
): InstallVersionDecision {
    installedInfo ?: return InstallVersionDecision.Install
    val remoteVersionCode = info.versionCode.trim().toLongOrNull()
    val localVersionCode = installedInfo.versionCode.takeIf { it >= 0L }
    val sameVersionCode = remoteVersionCode != null &&
            localVersionCode != null &&
            remoteVersionCode == localVersionCode
    val sameVersionName = info.versionName
        .trim()
        .equals(installedInfo.versionName.trim(), ignoreCase = false)
    return when {
        sameVersionCode && sameVersionName -> InstallVersionDecision.Same
        remoteVersionCode != null && localVersionCode != null && remoteVersionCode < localVersionCode ->
            InstallVersionDecision.Downgrade

        else -> InstallVersionDecision.Update
    }
}

private fun buildInstallAbiDecision(
    info: GitHubApkManifestInfo,
    supportedAbis: List<String>,
    likelyCompatible: Boolean
): InstallAbiDecision {
    val remoteAbis = info.nativeAbis.map { it.trim() }.filter { it.isNotBlank() }
    if (remoteAbis.isEmpty()) return InstallAbiDecision.Universal
    val hasMatch = remoteAbis.any { remoteAbi ->
        supportedAbis.any { supported -> supported.equals(remoteAbi, ignoreCase = true) }
    }
    return when {
        hasMatch -> InstallAbiDecision.Match
        likelyCompatible -> InstallAbiDecision.Unknown
        else -> InstallAbiDecision.Mismatch
    }
}

private enum class InstallVersionDecision {
    Install,
    Update,
    Downgrade,
    Same
}

private fun InstallVersionDecision.labelRes(): Int {
    return when (this) {
        InstallVersionDecision.Install -> R.string.github_page_install_status_install
        InstallVersionDecision.Update -> R.string.github_page_install_status_update
        InstallVersionDecision.Downgrade -> R.string.github_page_install_status_downgrade
        InstallVersionDecision.Same -> R.string.github_page_install_status_same
    }
}

private fun InstallVersionDecision.statusColor(): Color {
    return when (this) {
        InstallVersionDecision.Install -> GitHubStatusPalette.Active
        InstallVersionDecision.Update -> GitHubStatusPalette.Update
        InstallVersionDecision.Downgrade -> GitHubStatusPalette.Error
        InstallVersionDecision.Same -> GitHubStatusPalette.Cache
    }
}

private enum class InstallAbiDecision {
    Match,
    Universal,
    Mismatch,
    Unknown
}

private fun InstallAbiDecision.labelRes(): Int {
    return when (this) {
        InstallAbiDecision.Match -> R.string.github_page_install_status_abi_match
        InstallAbiDecision.Universal -> R.string.github_page_install_status_abi_universal
        InstallAbiDecision.Mismatch -> R.string.github_page_install_status_abi_mismatch
        InstallAbiDecision.Unknown -> R.string.github_page_install_status_abi_unknown
    }
}

private fun InstallAbiDecision.statusColor(): Color {
    return when (this) {
        InstallAbiDecision.Match -> GitHubStatusPalette.Update
        InstallAbiDecision.Universal -> GitHubStatusPalette.Active
        InstallAbiDecision.Mismatch -> GitHubStatusPalette.Error
        InstallAbiDecision.Unknown -> GitHubStatusPalette.Cache
    }
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
