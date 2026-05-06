package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubApkTrustReason
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.GitHubStatusPalette
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
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubTrackedItemAssetRow(
    asset: GitHubReleaseAssetFile,
    alwaysLatestReleaseDownload: Boolean,
    targetAccent: Color,
    summaryContainerColor: Color,
    summaryBorderColor: Color,
    contentBackdrop: LayerBackdrop,
    supportedAbis: List<String>,
    showApkTrustCheck: Boolean,
    context: Context,
    onOpenApkInDownloader: (GitHubReleaseAssetFile) -> Unit,
    onShareApkLink: (GitHubReleaseAssetFile) -> Unit
) {
    val actionAccent = when {
        alwaysLatestReleaseDownload -> targetAccent
        prefersApiAssetTransport(asset) -> GitHubStatusPalette.Active
        else -> GitHubStatusPalette.Update
    }
    val actionButtonColor = MiuixTheme.colorScheme.primary
    val abiLabel = assetAbiLabel(asset.name)
    val extensionLabel = assetFileExtensionLabel(asset.name)
    val displayName = assetDisplayName(asset.name)
    val sizeLabel = formatAssetSize(asset.sizeBytes, context)
    val relativeTimeLabel = assetRelativeTimeLabel(asset.updatedAtMillis, context)
    val preferredForDevice = assetIsPreferredForDevice(
        fileName = asset.name,
        supportedAbis = supportedAbis
    )
    val likelyCompatible = assetLikelyCompatibleWithDevice(
        fileName = asset.name,
        supportedAbis = supportedAbis
    )
    val apkTrustSignal = if (showApkTrustCheck) {
        buildGitHubApkTrustSignal(asset, supportedAbis)
    } else {
        null
    }
    AppSurfaceCard(
        containerColor = summaryContainerColor,
        borderColor = summaryBorderColor
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                AppCompactIconAction(
                    icon = appLucideDownloadIcon(),
                    contentDescription = sizeLabel,
                    tint = actionButtonColor,
                    onClick = { onOpenApkInDownloader(asset) },
                    minSize = 34.dp
                )
                AppCompactIconAction(
                    icon = appLucideShareIcon(),
                    contentDescription = context.getString(
                        R.string.github_cd_share_asset,
                        asset.name
                    ),
                    tint = actionButtonColor,
                    onClick = { onShareApkLink(asset) },
                    minSize = 34.dp
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                extensionLabel?.let { label ->
                    StatusPill(
                        label = label,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                abiLabel?.let { label ->
                    StatusPill(
                        label = label,
                        color = actionAccent
                    )
                }
                relativeTimeLabel?.let { label ->
                    StatusPill(
                        label = label,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
                StatusPill(
                    label = sizeLabel,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
                if (preferredForDevice) {
                    StatusPill(
                        label = stringResource(R.string.github_asset_badge_recommended),
                        color = GitHubStatusPalette.Update
                    )
                } else if (!likelyCompatible && abiLabel != null) {
                    StatusPill(
                        label = stringResource(R.string.github_asset_badge_incompatible),
                        color = GitHubStatusPalette.PreRelease
                    )
                }
                apkTrustSignal?.let { signal ->
                    StatusPill(
                        label = stringResource(signal.level.labelRes()),
                        color = signal.level.toStatusColor()
                    )
                }
            }
            apkTrustSignal
                ?.takeIf { signal ->
                    signal.level != GitHubDecisionLevel.Good ||
                            signal.reasons.any { it != GitHubApkTrustReason.ApkLike }
                }
                ?.takeIf { it.reasons.isNotEmpty() }
                ?.let { signal ->
                Text(
                    text = signal.reasons
                        .joinToString(" / ") { reason -> context.getString(reason.labelRes()) },
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
