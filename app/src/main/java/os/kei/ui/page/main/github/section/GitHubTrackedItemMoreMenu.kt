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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.testing.KeiOsTestTags
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
import os.kei.ui.page.main.os.appLucidePinIcon
import os.kei.ui.page.main.os.appLucidePinOffIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.os.appLucideVersionIcon
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
// Six rows is the common case now that pinning is one of them, and at 276dp the sixth pushed Delete --
// the last row -- out of view. Raised to fit six outright; the rarer seven-row case still scrolls.
private val GitHubTrackedItemMoreMenuMaxHeight = 324.dp
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
    pinned: Boolean,
    onTogglePinned: (GitHubTrackedApp) -> Unit,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onIgnoreCurrentVersion: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenFdroidDetail: (GitHubTrackedApp) -> Unit,
    onOpenReleaseList: (GitHubTrackedApp) -> Unit,
    onOpenFdroidVersionList: (GitHubTrackedApp) -> Unit,
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var menuAnchorBounds by remember(item.id) { mutableStateOf<IntRect?>(null) }
    val showActionsAction = item.isGitHubRepositoryTrack()
    val showFdroidDetailAction = item.isFdroidRepositoryTrack()
    // Only a GitHub repository has a paged release feed behind it. The Git platforms reach releases
    // through their own strategies and are not wired to the list page yet, and a direct APK is one file
    // with no history behind it at all.
    val showReleaseListAction = item.isGitHubRepositoryTrack()
    // An F-Droid index does have a history — every build the repository ever published — it just is not
    // a *release* feed, so it gets its own page rather than the release list. Offered beside the detail
    // sheet rather than instead of it: the sheet describes this track's configuration and the one build
    // it selected, and the page is the history that build came out of.
    val showFdroidVersionListAction = item.isFdroidRepositoryTrack()
    val gitRepositoryReleaseNotesSupported =
        item.isGitRepositoryTrack() &&
            buildGitRepositoryTrackIdentity(item.repoUrl)?.platform in releaseNotesSupportedGitPlatforms
    val normalizedShowReleaseNotesAction =
        when {
            // Always offered on a repository track. It is the overflow menu — the place a reader looks
            // for exactly this — and gating it behind a setting meant the sheet existed but could not be
            // found. The other source kinds stay data-dependent below, because for them the notes really
            // can be absent.
            item.isGitHubRepositoryTrack() || gitRepositoryReleaseNotesSupported -> true

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
    val versionHistoryIcon = appLucideVersionIcon()
    val ignoreIcon = appLucidePauseIcon()
    val pinIcon = appLucidePinIcon()
    val pinOffIcon = appLucidePinOffIcon()
    val deleteIcon = appLucideTrashIcon()
    val menuMaxWidth =
        (appWindowWidthDp() * GITHUB_TRACKED_ITEM_MORE_MENU_WIDTH_FRACTION)
            .coerceIn(GitHubTrackedItemMoreMenuMinWidth, GitHubTrackedItemMoreMenuMaxWidth)
    Box(
        modifier = Modifier.capturePopupAnchor { menuAnchorBounds = it },
        contentAlignment = Alignment.Center,
    ) {
        AppCompactIconAction(
            modifier = Modifier.testTag(KeiOsTestTags.GitHubTrackedItemMoreButton),
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
                                        testTag = KeiOsTestTags.GitHubActionsMenuItem,
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
                                        testTag = KeiOsTestTags.GitHubFdroidDetailMenuItem,
                                        text = stringResource(R.string.github_fdroid_detail_title),
                                        leadingIcon = detailIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenFdroidDetail(item)
                                        },
                                    ),
                                )
                            }
                            if (showFdroidVersionListAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "fdroid_version_list",
                                        testTag = KeiOsTestTags.FdroidVersionListMenuItem,
                                        text = stringResource(
                                            R.string.github_fdroid_version_page_title,
                                        ),
                                        leadingIcon = versionHistoryIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenFdroidVersionList(item)
                                        },
                                    ),
                                )
                            }
                            if (showReleaseListAction) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "release_list",
                                        testTag = KeiOsTestTags.GitHubReleaseMenuItem,
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
                            // Above the version actions and below refresh: pinning is about where the
                            // card lives rather than about this release, so it reads as a list action.
                            add(
                                LiquidGlassActionMenuActionRow(
                                    id = "pin",
                                    testTag = KeiOsTestTags.GitHubTrackedItemPinMenuItem,
                                    text =
                                        stringResource(
                                            if (pinned) {
                                                R.string.github_item_menu_unpin
                                            } else {
                                                R.string.github_item_menu_pin
                                            },
                                        ),
                                    leadingIcon = if (pinned) pinOffIcon else pinIcon,
                                    onClick = {
                                        menuExpanded = false
                                        onTogglePinned(item)
                                    },
                                ),
                            )
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
