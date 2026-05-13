package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.github.GitHubRepositoryHealth
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.formatReleaseValue
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.repositoryHealthLabelRes
import os.kei.ui.page.main.github.repositoryHealthStatusColor
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideChevronDownIcon
import os.kei.ui.page.main.os.appLucideChevronUpIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.liquidGlassDropdownItemAccent
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GitHubTrackedItemMoreMenuMinWidth = 148.dp
private val GitHubTrackedItemMoreMenuMaxWidth = 170.dp
private val GitHubTrackedItemMoreMenuMaxHeight = 232.dp
private const val GitHubTrackedItemMoreMenuWidthFraction = 0.40f

@Composable
internal fun GitHubRepositoryLinkCard(
    item: GitHubTrackedApp,
    onOpenExternalUrl: (String) -> Unit
) {
    val directIdentity = if (item.isGitHubRepositoryTrack()) {
        null
    } else {
        buildDirectApkTrackIdentity(item.repoUrl)
    }
    val repoUrl = if (item.isGitHubRepositoryTrack()) {
        GitHubVersionUtils.buildRepositoryUrl(item.owner, item.repo)
    } else {
        item.repoUrl
    }
    GitHubLinkedInfoCard(
        label = stringResource(
            if (item.isGitHubRepositoryTrack()) {
                R.string.github_item_label_repo
            } else {
                R.string.github_item_label_direct_apk
            }
        ),
        value = directIdentity?.displayName ?: "${item.owner}/${item.repo}",
        valueColor = MiuixTheme.colorScheme.onBackground,
        onClick = { onOpenExternalUrl(repoUrl) }
    )
}

@Composable
internal fun GitHubReleaseVersionCard(
    label: String,
    value: String,
    textColor: Color,
    expanded: Boolean,
    emphasized: Boolean,
    valueMaxLines: Int = 1,
    onExpandedChange: (Boolean) -> Unit
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
        onClick = { onExpandedChange(!expanded) }
    )
}

@Composable
internal fun formatActionsRunSnapshotValue(
    snapshot: GitHubActionsRecommendedRunSnapshot?
): String {
    if (snapshot == null) {
        return stringResource(R.string.github_item_actions_run_not_recorded)
    }
    val checkedAt = formatReleaseUpdatedAtCompact(snapshot.checkedAtMillis)
        ?: stringResource(R.string.common_unknown)
    return stringResource(
        R.string.github_item_actions_run_value,
        snapshot.runLabel,
        checkedAt
    )
}

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
    onClick: () -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.56f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f)
    }
    GitHubInlineLiquidSurface(
        backdrop = backdrop,
        tint = MiuixTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f),
        surfaceColor = surfaceColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = if (valueEmphasized) {
                    AppTypographyTokens.BodyEmphasis.fontWeight
                } else {
                    AppTypographyTokens.Body.fontWeight
                },
                maxLines = valueMaxLines.coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            trailingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = trailingIconColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun GitHubInlineLiquidSurface(
    backdrop: LayerBackdrop,
    tint: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
        )
        LiquidSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tint = tint,
            surfaceColor = surfaceColor,
            blurRadius = UiPerformanceBudget.backdropBlur,
            lensRadius = UiPerformanceBudget.backdropLens,
            onClick = onClick,
            content = content
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

@Composable
internal fun GitHubHealthPreviewBlock(
    health: GitHubRepositoryHealth,
    onClick: () -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = isSystemInDarkTheme()
    val color = health.level.repositoryHealthStatusColor()
    val surfaceColor = if (isDark) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.56f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f)
    }
    GitHubInlineLiquidSurface(
        backdrop = backdrop,
        tint = color.copy(alpha = if (isDark) 0.16f else 0.10f),
        surfaceColor = surfaceColor,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.github_item_label_health_score),
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = stringResource(
                        R.string.github_health_score_level_value,
                        health.score,
                        stringResource(health.level.repositoryHealthLabelRes())
                    ),
                    color = color,
                    size = AppStatusPillSize.Compact,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
internal fun GitHubTrackedItemMoreActions(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    iconTint: Color,
    showReleaseNotesAction: Boolean,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var menuAnchorBounds by remember(item.id) { mutableStateOf<IntRect?>(null) }
    val showActionsAction = item.isGitHubRepositoryTrack()
    val normalizedShowReleaseNotesAction = showReleaseNotesAction && showActionsAction
    val optionSize = 2 +
            if (showActionsAction) 1 else 0 +
                    if (normalizedShowReleaseNotesAction) 1 else 0
    val refreshIcon = appLucideRefreshIcon()
    val actionsIcon = appLucideBranchIcon()
    val releaseNotesIcon = appLucideNotesIcon()
    val deleteIcon = appLucideTrashIcon()
    val dangerTint = liquidGlassDropdownItemAccent(
        isDark = isSystemInDarkTheme(),
        accentColor = MiuixTheme.colorScheme.error,
        variant = GlassVariant.SheetDangerAction
    )
    val menuMaxWidth =
        (LocalConfiguration.current.screenWidthDp.dp * GitHubTrackedItemMoreMenuWidthFraction)
            .coerceIn(GitHubTrackedItemMoreMenuMinWidth, GitHubTrackedItemMoreMenuMaxWidth)
    Box(
        modifier = Modifier.capturePopupAnchor { menuAnchorBounds = it },
        contentAlignment = Alignment.Center
    ) {
        AppCompactIconAction(
            icon = appLucideMoreIcon(),
            contentDescription = stringResource(R.string.github_item_cd_more_actions),
            tint = if (state.loading) iconTint.copy(alpha = 0.68f) else iconTint,
            enabled = true,
            onClick = { menuExpanded = !menuExpanded }
        )
        if (menuExpanded) {
            SnapshotWindowListPopup(
                show = true,
                alignment = PopupPositionProvider.Align.BottomEnd,
                anchorBounds = menuAnchorBounds,
                placement = SnapshotPopupPlacement.ButtonEnd,
                enableWindowDim = false,
                onDismissRequest = { menuExpanded = false }
            ) {
                LiquidGlassDropdownColumn(
                    minWidth = GitHubTrackedItemMoreMenuMinWidth,
                    maxWidth = menuMaxWidth,
                    maxHeight = GitHubTrackedItemMoreMenuMaxHeight
                ) {
                    var optionIndex = 0
                    GitHubTrackedItemMenuAction(
                        text = stringResource(R.string.common_refresh),
                        leadingIcon = refreshIcon,
                        index = optionIndex++,
                        optionSize = optionSize,
                        onClick = {
                            menuExpanded = false
                            onRefreshTrackedItem(item)
                        }
                    )
                    if (showActionsAction) {
                        GitHubTrackedItemMenuAction(
                            text = stringResource(R.string.github_actions_menu),
                            leadingIcon = actionsIcon,
                            index = optionIndex++,
                            optionSize = optionSize,
                            onClick = {
                                menuExpanded = false
                                onOpenActionsSheet(item)
                            }
                        )
                    }
                    if (normalizedShowReleaseNotesAction) {
                        GitHubTrackedItemMenuAction(
                            text = stringResource(R.string.github_release_notes_title),
                            leadingIcon = releaseNotesIcon,
                            index = optionIndex++,
                            optionSize = optionSize,
                            onClick = {
                                menuExpanded = false
                                onOpenReleaseNotes()
                            }
                        )
                    }
                    GitHubTrackedItemMenuAction(
                        text = stringResource(R.string.github_track_sheet_btn_delete),
                        leadingIcon = deleteIcon,
                        index = optionSize - 1,
                        optionSize = optionSize,
                        variant = GlassVariant.SheetDangerAction,
                        contentTint = dangerTint,
                        onClick = {
                            menuExpanded = false
                            onRequestDeleteTrackedItem(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubTrackedItemMenuAction(
    text: String,
    leadingIcon: ImageVector,
    index: Int,
    optionSize: Int,
    onClick: () -> Unit,
    contentTint: Color? = null,
    variant: GlassVariant = GlassVariant.SheetAction
) {
    LiquidGlassDropdownActionItem(
        text = text,
        leadingIcon = leadingIcon,
        onClick = onClick,
        index = index,
        optionSize = optionSize,
        contentTint = contentTint,
        variant = variant
    )
}

internal fun formatLocalVersionText(
    context: Context,
    state: VersionCheckUi?
): String? {
    state ?: return null
    val rawLocalVersion = state.localVersion.trim()
    if (state.isLocalAppUninstalled()) {
        return context.getString(R.string.github_item_value_local_version_uninstalled)
    }
    if (rawLocalVersion.isBlank()) return null
    val normalizedLocalVersion = formatReleaseValue(
        releaseName = rawLocalVersion,
        rawTag = rawLocalVersion
    )
    return if (state.localVersionCode >= 0L) {
        "$normalizedLocalVersion (${state.localVersionCode})"
    } else {
        normalizedLocalVersion
    }
}

internal fun pendingVersionCheckUi(context: Context): VersionCheckUi {
    val pending = context.getString(R.string.github_item_value_check_pending)
    return VersionCheckUi(
        localVersion = pending,
        message = pending
    )
}

@Composable
internal fun GitHubAssetCountBubble(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val localBackdrop = rememberLayerBackdrop()
    val activeBackdrop = localBackdrop.takeIf { LocalLiquidControlsEnabled.current }
    val shape = CircleShape
    val bubbleModifier = Modifier
        .clip(shape)
        .then(
            if (activeBackdrop == null) {
                Modifier.background(color.copy(alpha = if (isDark) 0.18f else 0.12f))
            } else {
                Modifier
            }
        )
        .border(
            width = 0.8.dp,
            color = color.copy(alpha = if (isDark) 0.34f else 0.24f),
            shape = shape
        )
    val content: @Composable () -> Unit = {
        if (loading) {
            LiquidCircularProgressBar(
                size = 14.dp,
                strokeWidth = 2.dp,
                activeColor = color,
                inactiveColor = color.copy(alpha = 0.18f)
            )
        } else {
            Text(
                text = label,
                color = if (isDark) color else color.copy(alpha = 0.96f),
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
                fontWeight = AppTypographyTokens.Caption.fontWeight,
                maxLines = 1
            )
        }
    }
    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        if (activeBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(localBackdrop)
            )
            LiquidSurface(
                backdrop = activeBackdrop,
                modifier = Modifier
                    .matchParentSize()
                    .then(bubbleModifier),
                shape = shape,
                isInteractive = false,
                surfaceColor = color.copy(alpha = if (isDark) 0.18f else 0.12f),
                blurRadius = resolvedGlassBlurDp(
                    UiPerformanceBudget.backdropBlur,
                    GlassVariant.Compact
                ),
                lensRadius = resolvedGlassLensDp(
                    UiPerformanceBudget.backdropLens,
                    GlassVariant.Compact
                ),
                shadow = false
            ) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(bubbleModifier),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}
