@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubApkTrustReason
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.GitHubAssetFileCard
import os.kei.ui.page.main.github.asset.assetAbiLabel
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.asset.assetFileExtensionLabel
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.github.asset.assetRelativeTimeLabel
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.github.asset.prefersApiAssetTransport
import os.kei.ui.page.main.github.buildGitHubApkTrustSignal
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubTrackedItemAssetRow(
    asset: GitHubReleaseAssetFile,
    alwaysLatestReleaseDownload: Boolean,
    targetAccent: Color,
    summaryContainerColor: Color,
    summaryBorderColor: Color,
    supportedAbis: List<String>,
    relativeTimeNowMillis: Long,
    showApkTrustCheck: Boolean,
    managedInstallEnabled: Boolean,
    managedInstallRunning: Boolean,
    installActionColor: Color,
    context: Context,
    onOpenApkInfo: () -> Unit,
    onInstallApk: () -> Unit,
    onOpenApkInDownloader: () -> Unit,
    onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
) {
    val actionAccent =
        when {
            alwaysLatestReleaseDownload -> targetAccent
            prefersApiAssetTransport(asset) -> GitHubStatusPalette.Active
            else -> GitHubStatusPalette.Update
        }
    val actionButtonColor = MiuixTheme.colorScheme.primary
    val abiLabel = assetAbiLabel(asset.name)
    val extensionLabel = assetFileExtensionLabel(asset.name)
    val isApkAsset = asset.name.endsWith(".apk", ignoreCase = true)
    val displayName = assetDisplayName(asset.name)
    val sizeLabel = formatAssetSize(asset.sizeBytes, context)
    val relativeTimeLabel =
        assetRelativeTimeLabel(
            updatedAtMillis = asset.updatedAtMillis,
            nowMillis = relativeTimeNowMillis,
            context = context,
        )
    val preferredForDevice =
        assetIsPreferredForDevice(
            fileName = asset.name,
            supportedAbis = supportedAbis,
        )
    val likelyCompatible =
        assetLikelyCompatibleWithDevice(
            fileName = asset.name,
            supportedAbis = supportedAbis,
        )
    val apkTrustSignal =
        if (showApkTrustCheck) {
            buildGitHubApkTrustSignal(asset, supportedAbis)
        } else {
            null
        }
    GitHubAssetFileCard(
        title = displayName,
        containerColor = summaryContainerColor,
        borderColor = summaryBorderColor,
        titleMaxLines = 2,
        pills = {
            extensionLabel?.let { label ->
                AssetStatusPill(
                    label = label,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            abiLabel?.let { label ->
                AssetStatusPill(
                    label = label,
                    color = actionAccent,
                )
            }
            relativeTimeLabel?.let { label ->
                AssetStatusPill(
                    label = label,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            AssetStatusPill(
                label = sizeLabel,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            if (preferredForDevice) {
                AssetStatusPill(
                    label = stringResource(R.string.github_asset_badge_recommended),
                    color = GitHubStatusPalette.Update,
                )
            } else if (!likelyCompatible && abiLabel != null) {
                AssetStatusPill(
                    label = stringResource(R.string.github_asset_badge_incompatible),
                    color = GitHubStatusPalette.PreRelease,
                )
            }
            apkTrustSignal?.let { signal ->
                AssetStatusPill(
                    label = stringResource(signal.level.labelRes()),
                    color = signal.level.toStatusColor(),
                )
            }
        },
        actions = {
            if (isApkAsset) {
                AppCompactIconAction(
                    icon = appLucideInfoIcon(),
                    contentDescription =
                        context.getString(
                            R.string.github_cd_view_apk_info,
                            asset.name,
                        ),
                    tint = actionButtonColor,
                    onClick = onOpenApkInfo,
                    minSize = 34.dp,
                )
            }
            if (managedInstallEnabled && isApkAsset) {
                AppCompactIconAction(
                    icon = appLucidePackageIcon(),
                    contentDescription =
                        context.getString(
                            if (managedInstallRunning) {
                                R.string.github_cd_install_asset_running
                            } else {
                                R.string.github_cd_install_asset
                            },
                            asset.name,
                        ),
                    tint = installActionColor,
                    enabled = !managedInstallRunning,
                    onClick = onInstallApk,
                    minSize = 34.dp,
                )
            }
            AppCompactIconAction(
                icon = appLucideDownloadIcon(),
                contentDescription =
                    context.getString(
                        R.string.github_cd_download_asset,
                        asset.name,
                    ),
                tint = actionButtonColor,
                onClick = onOpenApkInDownloader,
                minSize = 34.dp,
            )
            AppCompactIconAction(
                icon = appLucideShareIcon(),
                contentDescription =
                    context.getString(
                        R.string.github_cd_share_asset,
                        asset.name,
                    ),
                tint = actionButtonColor,
                onClick = { onShareApkLink(asset) },
                minSize = 34.dp,
            )
        },
        supportingContent = {
            apkTrustSignal
                ?.takeIf { signal ->
                    signal.level != GitHubDecisionLevel.Good ||
                        signal.reasons.any { it != GitHubApkTrustReason.ApkLike }
                }?.takeIf { it.reasons.isNotEmpty() }
                ?.let { signal ->
                    Text(
                        text =
                            signal.reasons
                                .joinToString(" / ") { reason -> context.getString(reason.labelRes()) },
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        },
    )
}

@Composable
private fun AssetStatusPill(
    label: String,
    color: Color,
) {
    StatusPill(
        label = label,
        color = color,
        size = AppStatusPillSize.Compact,
    )
}

private fun GitHubDecisionLevel.labelRes(): Int =
    when (this) {
        GitHubDecisionLevel.Good -> R.string.github_apk_trust_good
        GitHubDecisionLevel.Review -> R.string.github_apk_trust_review
        GitHubDecisionLevel.Risk -> R.string.github_apk_trust_risk
    }

private fun GitHubDecisionLevel.toStatusColor(): Color =
    when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }

private fun GitHubApkTrustReason.labelRes(): Int =
    when (this) {
        GitHubApkTrustReason.PreferredAbi -> R.string.github_apk_trust_reason_preferred_abi
        GitHubApkTrustReason.UniversalAsset -> R.string.github_apk_trust_reason_universal
        GitHubApkTrustReason.IncompatibleAbi -> R.string.github_apk_trust_reason_incompatible
        GitHubApkTrustReason.DebugBuild -> R.string.github_apk_trust_reason_debug
        GitHubApkTrustReason.UnsignedBuild -> R.string.github_apk_trust_reason_unsigned
        GitHubApkTrustReason.SourceArchive -> R.string.github_apk_trust_reason_source
        GitHubApkTrustReason.ApkLike -> R.string.github_apk_trust_reason_apk
        GitHubApkTrustReason.UnknownFormat -> R.string.github_apk_trust_reason_unknown_format
    }
