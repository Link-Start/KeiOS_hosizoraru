package os.kei.ui.page.main.github.section

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.feature.github.model.isGitRepositoryTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.liquidGlassDropdownItemAccent
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GitHubTrackedItemMoreMenuMinWidth = 148.dp
private val GitHubTrackedItemMoreMenuMaxWidth = 170.dp
private val GitHubTrackedItemMoreMenuMaxHeight = 276.dp
private const val GITHUB_TRACKED_ITEM_MORE_MENU_WIDTH_FRACTION = 0.40f
private val releaseNotesSupportedGitPlatforms = setOf(
    GitRepositoryPlatform.GitHub,
    GitRepositoryPlatform.Gitee,
    GitRepositoryPlatform.GitLab,
    GitRepositoryPlatform.Gitea,
)

@Suppress("FunctionName")
@Composable
internal fun GitHubTrackedItemMoreActions(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    iconTint: Color,
    showReleaseNotesAction: Boolean,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onIgnoreCurrentVersion: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var menuAnchorBounds by remember(item.id) { mutableStateOf<IntRect?>(null) }
    val showActionsAction = item.isGitHubRepositoryTrack()
    val gitRepositoryReleaseNotesSupported =
        item.isGitRepositoryTrack() &&
            buildGitRepositoryTrackIdentity(item.repoUrl)?.platform in releaseNotesSupportedGitPlatforms
    val normalizedShowReleaseNotesAction =
        when {
            item.isGitHubRepositoryTrack() || gitRepositoryReleaseNotesSupported -> {
                showReleaseNotesAction
            }

            item.isDirectApkTrack() -> {
                state.latestStableApkVersion
                    ?.releaseNotes
                    .orEmpty()
                    .isNotBlank() ||
                    state.latestPreApkVersion
                        ?.releaseNotes
                        .orEmpty()
                        .isNotBlank()
            }

            else -> {
                false
            }
        }
    val showIgnoreCurrentVersionAction =
        state.recommendsPreRelease ||
            state.hasUpdate == true ||
            state.hasPreReleaseUpdate
    val optionSize =
        2 +
            (if (showActionsAction) 1 else 0) +
            (if (normalizedShowReleaseNotesAction) 1 else 0) +
            (if (showIgnoreCurrentVersionAction) 1 else 0)
    val refreshIcon = appLucideRefreshIcon()
    val actionsIcon = appLucideBranchIcon()
    val releaseNotesIcon = appLucideNotesIcon()
    val ignoreIcon = appLucidePauseIcon()
    val deleteIcon = appLucideTrashIcon()
    val dangerTint =
        liquidGlassDropdownItemAccent(
            isDark = isSystemInDarkTheme(),
            accentColor = MiuixTheme.colorScheme.error,
            variant = GlassVariant.SheetDangerAction,
        )
    val menuMaxWidth =
        (appWindowWidthDp() * GITHUB_TRACKED_ITEM_MORE_MENU_WIDTH_FRACTION)
            .coerceIn(GitHubTrackedItemMoreMenuMinWidth, GitHubTrackedItemMoreMenuMaxWidth)
    Box(
        modifier = Modifier.capturePopupAnchor { menuAnchorBounds = it },
        contentAlignment = Alignment.Center,
    ) {
        AppCompactIconAction(
            icon = appLucideMoreIcon(),
            contentDescription = stringResource(R.string.github_item_cd_more_actions),
            tint = if (state.loading) iconTint.copy(alpha = 0.68f) else iconTint,
            enabled = true,
            onClick = { menuExpanded = !menuExpanded },
        )
        if (menuExpanded) {
            SnapshotWindowListPopup(
                show = true,
                alignment = PopupPositionProvider.Align.BottomEnd,
                anchorBounds = menuAnchorBounds,
                placement = SnapshotPopupPlacement.ButtonEnd,
                enableWindowDim = false,
                onDismissRequest = { menuExpanded = false },
            ) {
                LiquidGlassDropdownColumn(
                    minWidth = GitHubTrackedItemMoreMenuMinWidth,
                    maxWidth = menuMaxWidth,
                    maxHeight = GitHubTrackedItemMoreMenuMaxHeight,
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
                        },
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
                            },
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
                            },
                        )
                    }
                    if (showIgnoreCurrentVersionAction) {
                        GitHubTrackedItemMenuAction(
                            text = stringResource(R.string.github_item_menu_ignore_current_version),
                            leadingIcon = ignoreIcon,
                            index = optionIndex++,
                            optionSize = optionSize,
                            onClick = {
                                menuExpanded = false
                                onIgnoreCurrentVersion(item, state)
                            },
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
                        },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun GitHubTrackedItemMenuAction(
    text: String,
    leadingIcon: ImageVector,
    index: Int,
    optionSize: Int,
    onClick: () -> Unit,
    contentTint: Color? = null,
    variant: GlassVariant = GlassVariant.SheetAction,
) {
    LiquidGlassDropdownActionItem(
        text = text,
        leadingIcon = leadingIcon,
        onClick = onClick,
        index = index,
        optionSize = optionSize,
        contentTint = contentTint,
        variant = variant,
    )
}
