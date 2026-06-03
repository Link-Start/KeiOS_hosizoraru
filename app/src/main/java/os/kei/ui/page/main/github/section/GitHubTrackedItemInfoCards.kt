package os.kei.ui.page.main.github.section

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.RoundedRectangle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.isGitRepositoryTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.os.appLucideChevronDownIcon
import os.kei.ui.page.main.os.appLucideChevronUpIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("FunctionName")
@Composable
internal fun GitHubRepositoryLinkCard(
    item: GitHubTrackedApp,
    onOpenExternalUrl: (String) -> Unit,
) {
    val directIdentity = item.takeIf { it.isDirectApkDisplaySource() }
        ?.let { buildDirectApkTrackIdentity(it.repoUrl) }
    val gitIdentity = item.takeIf { it.isGitRepositoryTrack() }
        ?.let { buildGitRepositoryTrackIdentity(it.repoUrl) }
    val repoUrl = if (item.isGitHubRepositoryTrack()) {
        GitHubVersionUtils.buildRepositoryUrl(item.owner, item.repo)
    } else {
        item.repoUrl
    }
    GitHubLinkedInfoCard(
        label =
            stringResource(
                when {
                    item.isGitHubRepositoryTrack() -> R.string.github_item_label_repo
                    item.isGitRepositoryTrack() -> R.string.github_item_label_git_repo
                    else -> R.string.github_item_label_direct_apk
                },
            ),
        value = gitIdentity?.displayName ?: directIdentity?.displayName ?: "${item.owner}/${item.repo}",
        valueColor = MiuixTheme.colorScheme.onBackground,
        onClick = { onOpenExternalUrl(repoUrl) },
    )
}

private fun GitHubTrackedApp.isDirectApkDisplaySource(): Boolean {
    return !isGitHubRepositoryTrack() && !isGitRepositoryTrack()
}

@Suppress("FunctionName")
@Composable
internal fun GitHubReleaseVersionCard(
    label: String,
    value: String,
    textColor: Color,
    expanded: Boolean,
    emphasized: Boolean,
    valueMaxLines: Int = 1,
    onExpandedChange: (Boolean) -> Unit,
) {
    GitHubLinkedInfoCard(
        label = label,
        value = value,
        labelColor = textColor,
        valueColor = textColor,
        valueEmphasized = emphasized,
        valueMaxLines = valueMaxLines,
        trailingIcon = if (expanded) appLucideChevronUpIcon() else appLucideChevronDownIcon(),
        trailingIconColor = textColor,
        onClick = { onExpandedChange(!expanded) },
    )
}

@Suppress("FunctionName")
@Composable
internal fun GitHubLinkedInfoCard(
    label: String,
    value: String,
    labelColor: Color = MiuixTheme.colorScheme.primary,
    valueColor: Color,
    valueEmphasized: Boolean = false,
    valueMaxLines: Int = 1,
    trailingIcon: ImageVector? = null,
    trailingIconColor: Color = labelColor,
    onClick: () -> Unit,
) {
    val localBackdrop = rememberLayerBackdrop()
    val parentBackdrop = LocalLiquidParentBackdrop.current
    val activeBackdrop = parentBackdrop ?: localBackdrop
    val isDark = isSystemInDarkTheme()
    val surfaceColor =
        if (isDark) {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.56f)
        } else {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f)
        }
    GitHubInlineLiquidSurface(
        backdrop = activeBackdrop,
        captureBackdrop = if (parentBackdrop == null) localBackdrop else null,
        tint = MiuixTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f),
        surfaceColor = surfaceColor,
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight =
                    if (valueEmphasized) {
                        AppTypographyTokens.BodyEmphasis.fontWeight
                    } else {
                        AppTypographyTokens.Body.fontWeight
                    },
                maxLines = valueMaxLines.coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            trailingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = trailingIconColor,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
internal fun GitHubInlineLiquidSurface(
    backdrop: Backdrop,
    captureBackdrop: LayerBackdrop?,
    tint: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    shadowAlpha: Float = 0.05f,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        if (captureBackdrop != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(captureBackdrop),
            )
        }
        LiquidSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedRectangle(12.dp),
            tint = tint,
            surfaceColor = surfaceColor,
            blurRadius = UiPerformanceBudget.backdropBlur,
            lensRadius = UiPerformanceBudget.backdropLens,
            effectVariant = GlassVariant.Content,
            shadowAlpha = shadowAlpha,
            onClick = onClick,
            content = content,
        )
    }
}

@Composable
internal fun gitHubPreReleaseCardTextColor(): Color {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        Color(0xFF93C5FD)
    } else {
        Color(0xFF60A5FA)
    }
}
