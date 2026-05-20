package os.kei.ui.page.main.github.actions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.assetRelativeTimeLabel
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubActionsArtifactCard(
    runMatch: GitHubActionsRunMatch,
    artifactMatch: GitHubActionsArtifactMatch,
    recommended: Boolean,
    hasToken: Boolean,
    managedInstallEnabled: Boolean,
    downloading: Boolean,
    sharing: Boolean,
    context: Context,
    isDark: Boolean,
    backdrop: LayerBackdrop,
    onInstall: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val artifact = artifactMatch.artifact
    val actionColor = if (artifact.expired) GitHubStatusPalette.Error else MiuixTheme.colorScheme.primary
    val kindColor = artifactKindColor(artifactMatch.traits.kind)
    val busy = downloading || sharing
    val canDownload = runMatch.traits.completed && !artifact.expired && !busy
    val canShare = hasToken && runMatch.traits.completed && !artifact.expired && !busy
    val neutralPillColor = MiuixTheme.colorScheme.onBackgroundVariant
    val actionButtonSize = 48.dp
    val sizeLabel = formatAssetSize(artifact.sizeBytes, context)
    val downloadActionDescription = stringResource(
        R.string.github_actions_cd_download_artifact,
        artifact.name
    )
    GitHubActionsSelectableCard(
        selected = false,
        isDark = isDark,
        onClick = onOpenDetail
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = artifactDisplayName(artifactMatch),
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (recommended) {
                GitHubActionsInfoPill(
                    label = stringResource(R.string.github_actions_badge_recommended),
                    color = GitHubStatusPalette.Update,
                    emphasized = true,
                    minWidth = GitHubActionsShortPillMinWidth
                )
            }
            if (artifactMatch.lastDownload != null) {
                GitHubActionsInfoPill(
                    label = stringResource(R.string.github_actions_badge_last_downloaded),
                    color = GitHubStatusPalette.Active,
                    emphasized = true,
                    minWidth = GitHubActionsStatePillMinWidth
                )
            }
        }
        GitHubActionsPillRow {
            GitHubActionsInfoPill(
                label = artifactKindLabel(artifactMatch.traits.kind),
                color = kindColor,
                minWidth = GitHubActionsShortPillMinWidth
            )
            artifactMatch.traits.version.takeIf { it.isNotBlank() }?.let { version ->
                GitHubActionsInfoPill(label = version, color = GitHubStatusPalette.Update)
            }
            artifactMatch.traits.abi.takeIf { it.isNotBlank() }?.let { abi ->
                GitHubActionsInfoPill(label = abi, color = GitHubStatusPalette.Active)
            }
            artifactMatch.lastDownload?.artifactPackageName
                ?.takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    GitHubActionsInfoPill(
                        label = packageName,
                        color = GitHubStatusPalette.Active
                    )
                }
            artifactMatch.traits.flavors.forEach { flavor ->
                GitHubActionsInfoPill(label = flavor, color = GitHubStatusPalette.PreRelease)
            }
            artifactMatch.traits.buildTypes
                .filterNot { it == "release" || it == "debug" }
                .forEach { buildType ->
                    GitHubActionsInfoPill(
                        label = artifactBuildTypeLabel(buildType),
                        color = artifactBuildTypeColor(buildType)
                    )
                }
            if (artifactMatch.traits.releaseLike) {
                GitHubActionsInfoPill(
                    label = stringResource(R.string.github_actions_badge_release),
                    color = GitHubStatusPalette.Update
                )
            }
            if (artifactMatch.traits.debugLike) {
                GitHubActionsInfoPill(
                    label = stringResource(R.string.github_actions_badge_debug),
                    color = GitHubStatusPalette.PreRelease
                )
            }
            if (artifactMatch.traits.universalLike) {
                GitHubActionsInfoPill(
                    label = stringResource(R.string.github_actions_badge_universal),
                    color = GitHubStatusPalette.Active
                )
            }
            if (artifact.expired) {
                GitHubActionsInfoPill(
                    label = stringResource(R.string.github_actions_badge_expired),
                    color = GitHubStatusPalette.Error,
                    emphasized = true
                )
            }
            assetRelativeTimeLabel(artifact.updatedAtMillis, context)?.let { label ->
                GitHubActionsInfoPill(label = label, color = neutralPillColor)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (managedInstallEnabled) {
                AppLiquidIconButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    icon = appLucidePackageIcon(),
                    contentDescription = stringResource(
                        if (downloading) {
                            R.string.github_cd_install_asset_running
                        } else {
                            R.string.github_cd_install_asset
                        },
                        artifact.name
                    ),
                    iconTint = if (canDownload) actionColor else neutralPillColor,
                    enabled = canDownload,
                    width = actionButtonSize,
                    height = actionButtonSize,
                    onClick = onInstall
                )
            }
            AppLiquidTextButton(
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                text = sizeLabel,
                leadingIcon = appLucideDownloadIcon(),
                enabled = canDownload,
                modifier = Modifier.semantics { contentDescription = downloadActionDescription },
                textColor = if (canDownload) actionColor else neutralPillColor,
                iconTint = if (canDownload) actionColor else neutralPillColor,
                minHeight = actionButtonSize,
                horizontalPadding = 12.dp,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
                textSize = AppTypographyTokens.Supporting.fontSize,
                textLineHeight = AppTypographyTokens.Supporting.lineHeight,
                onClick = onDownload,
            )
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                icon = appLucideShareIcon(),
                contentDescription = stringResource(
                    R.string.github_actions_cd_share_artifact,
                    artifact.name
                ),
                iconTint = if (canShare) actionColor else neutralPillColor,
                enabled = canShare,
                width = actionButtonSize,
                height = actionButtonSize,
                onClick = onShare
            )
        }
    }
}
