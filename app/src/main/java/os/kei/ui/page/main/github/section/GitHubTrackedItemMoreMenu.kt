package os.kei.ui.page.main.github.section

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isFdroidRepositoryTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.feature.github.model.isGitRepositoryTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

private val GitHubTrackedItemMoreMenuMinWidth = 156.dp
private val GitHubTrackedItemMoreMenuMaxWidth = 196.dp
private val GitHubTrackedItemMoreMenuMaxHeight = 276.dp
private const val GITHUB_TRACKED_ITEM_MORE_MENU_WIDTH_FRACTION = 0.50f
private val releaseNotesSupportedGitPlatforms =
    setOf(
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
    onOpenFdroidDetail: (GitHubTrackedApp) -> Unit,
    onOpenReleaseList: (GitHubTrackedApp) -> Unit,
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var menuAnchorBounds by remember(item.id) { mutableStateOf<IntRect?>(null) }
    val showActionsAction = item.isGitHubRepositoryTrack()
    val showFdroidDetailAction = item.isFdroidRepositoryTrack()
    // Only a GitHub repository has a paged release feed behind it. The Git platforms reach releases
    // through their own strategies and are not wired to the list page yet; a direct APK or an F-Droid
    // index has no release history at all.
    val showReleaseListAction = item.isGitHubRepositoryTrack()
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

            item.isFdroidRepositoryTrack() -> {
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
    val refreshIcon = appLucideRefreshIcon()
    val actionsIcon = appLucideBranchIcon()
    val detailIcon = appLucideInfoIcon()
    val releaseNotesIcon = appLucideNotesIcon()
    val ignoreIcon = appLucidePauseIcon()
    val deleteIcon = appLucideTrashIcon()
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
        key("github-tracked-item-more-popup") {
            SnapshotWindowListPopup(
                show = menuExpanded,
                alignment = PopupPositionProvider.Align.BottomEnd,
                anchorBounds = menuAnchorBounds,
                placement = SnapshotPopupPlacement.ButtonEnd,
                onDismissRequest = { menuExpanded = false },
            ) {
                LiquidGlassActionMenu(
                    minWidth = GitHubTrackedItemMoreMenuMinWidth,
                    maxWidth = menuMaxWidth,
                    maxHeight = GitHubTrackedItemMoreMenuMaxHeight,
                    items =
                        buildList {
                            add(
                                LiquidGlassActionMenuActionRow(
                                    id = "refresh",
                                    text = stringResource(R.string.common_refresh),
                                    leadingIcon = refreshIcon,
                                    onClick = {
                                        menuExpanded = false
                                        onRefreshTrackedItem(item)
                                    },
                                ),
                            )
                            if (showActionsAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "actions",
                                        text = stringResource(R.string.github_actions_menu),
                                        leadingIcon = actionsIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenActionsSheet(item)
                                        },
                                    ),
                                )
                            }
                            if (showFdroidDetailAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "fdroid_detail",
                                        text = stringResource(R.string.github_fdroid_detail_title),
                                        leadingIcon = detailIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenFdroidDetail(item)
                                        },
                                    ),
                                )
                            }
                            if (showReleaseListAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "release_list",
                                        text = stringResource(R.string.github_release_page_title),
                                        leadingIcon = releaseNotesIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenReleaseList(item)
                                        },
                                    ),
                                )
                            }
                            if (normalizedShowReleaseNotesAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "release_notes",
                                        text = stringResource(R.string.github_release_notes_title),
                                        leadingIcon = releaseNotesIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenReleaseNotes()
                                        },
                                    ),
                                )
                            }
                            if (showIgnoreCurrentVersionAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "ignore_current_version",
                                        text = stringResource(R.string.github_item_menu_ignore_current_version),
                                        leadingIcon = ignoreIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onIgnoreCurrentVersion(item, state)
                                        },
                                    ),
                                )
                            }
                            add(
                                LiquidGlassActionMenuActionRow(
                                    id = "delete",
                                    text = stringResource(R.string.github_track_sheet_btn_delete),
                                    leadingIcon = deleteIcon,
                                    variant = GlassVariant.SheetDangerAction,
                                    onClick = {
                                        menuExpanded = false
                                        onRequestDeleteTrackedItem(item)
                                    },
                                ),
                            )
                        },
                    onDismissRequest = { menuExpanded = false },
                )
            }
        }
    }
}
