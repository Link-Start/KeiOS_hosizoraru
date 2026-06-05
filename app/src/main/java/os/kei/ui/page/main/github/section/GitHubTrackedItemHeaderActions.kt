package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.asset.directApkAssetPanelData
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.github.statusActionUrl
import os.kei.ui.page.main.github.statusColor
import os.kei.ui.page.main.github.statusIcon
import os.kei.ui.page.main.github.statusMessage
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("FunctionName")
@Composable
internal fun GitHubTrackedItemHeaderActions(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    displayTitle: String,
    lookupConfig: GitHubLookupConfig,
    checkState: GitHubTrackedItemsCheckState,
    assetState: GitHubTrackedItemsAssetState,
    actions: GitHubTrackedItemsActions,
    context: Context,
) {
    val actionState =
        gitHubTrackedItemHeaderActionState(
            item = item,
            state = state,
            displayTitle = displayTitle,
            checkState = checkState,
            assetState = assetState,
            context = context,
        )
    AppCompactIconAction(
        icon = actionState.icon,
        contentDescription = actionState.contentDescription,
        tint = actionState.iconTint,
        enabled = actionState.enabled,
        onClick = {
            if (actionState.canToggleDirectApkAssets) {
                assetState.apkAssetExpanded[item.id] = !actionState.assetPanelExpanded
            } else if (actionState.canLoadRepositoryApkAssets) {
                if (actionState.assetPanelExpanded) {
                    actions.onCollapseApkAssetPanel(item, state)
                } else {
                    actions.onLoadApkAssets(
                        item,
                        state,
                        true,
                        false,
                        actionState.localAppUninstalled,
                    )
                }
            } else {
                actions.onOpenExternalUrl(actionState.statusReleaseUrl)
            }
        },
    )
    if (actionState.itemRefreshLoading) {
        GitHubTrackedItemCheckingIndicator(iconTint = actionState.iconTint)
    } else {
        GitHubTrackedItemMoreActions(
            item = item,
            state = state,
            iconTint = actionState.iconTint,
            showReleaseNotesAction = lookupConfig.decisionAssistEnabled,
            onRefreshTrackedItem = actions.onRefreshTrackedItem,
            onOpenActionsSheet = actions.onOpenActionsSheet,
            onOpenReleaseNotes = {
                actions.onOpenDecisionAssistDetail(
                    GitHubDecisionAssistDetailType.ReleaseNotes,
                    item,
                )
            },
            onIgnoreCurrentVersion = actions.onIgnoreCurrentTrackedVersion,
            onRequestDeleteTrackedItem = actions.onRequestDeleteTrackedItem,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun GitHubTrackedItemCheckingIndicator(iconTint: Color) {
    val checkingContentDescription = stringResource(R.string.github_msg_checking)
    Box(
        modifier =
            Modifier
                .size(18.dp)
                .semantics {
                    contentDescription = checkingContentDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        LiquidCircularProgressBar(
            size = 16.dp,
            strokeWidth = 2.dp,
            activeColor = iconTint,
            inactiveColor = iconTint.copy(alpha = 0.18f),
        )
    }
}

@Composable
private fun gitHubTrackedItemHeaderActionState(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    displayTitle: String,
    checkState: GitHubTrackedItemsCheckState,
    assetState: GitHubTrackedItemsAssetState,
    context: Context,
): GitHubTrackedItemHeaderActionState {
    val itemRefreshLoading = checkState.itemRefreshLoading[item.id] == true
    val alwaysLatestReleaseDownload = item.alwaysShowLatestReleaseDownloadButton
    val localAppUninstalled = state.isLocalAppUninstalled()
    val directAssetPanelData = item.directApkAssetPanelData(state)
    val statusColor =
        state.statusColor(
            neutralColor = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    val statusReleaseUrl =
        state.statusActionUrl(
            owner = item.owner,
            repo = item.repo,
        )
    val canLoadRepositoryApkAssets =
        item.isGitHubRepositoryTrack() &&
            (
                alwaysLatestReleaseDownload ||
                    state.hasUpdate == true ||
                    state.recommendsPreRelease ||
                    state.hasPreReleaseUpdate ||
                    (
                        localAppUninstalled &&
                            state.apkAssetTarget(
                                owner = item.owner,
                                repo = item.repo,
                                context = context,
                                allowLatestReleaseFallback = true,
                            ) != null
                    )
            )
    val canToggleDirectApkAssets = item.isDirectApkTrack() && directAssetPanelData != null
    val canLoadApkAssets = canLoadRepositoryApkAssets || canToggleDirectApkAssets
    val assetPanelExpanded = assetState.apkAssetExpanded[item.id] == true
    val assetPanelLoading = assetState.apkAssetLoading[item.id] == true
    val icon =
        when {
            assetPanelLoading -> appLucideRefreshIcon()
            canLoadApkAssets && assetPanelExpanded -> appLucideCloseIcon()
            localAppUninstalled && canLoadApkAssets -> appLucidePackageIcon()
            alwaysLatestReleaseDownload -> appLucideDownloadIcon()
            else -> state.statusIcon()
        }
    val iconTint =
        when {
            localAppUninstalled && canLoadApkAssets -> GitHubStatusPalette.Install
            alwaysLatestReleaseDownload -> latestReleaseAccent
            else -> statusColor
        }
    return GitHubTrackedItemHeaderActionState(
        itemRefreshLoading = itemRefreshLoading,
        canToggleDirectApkAssets = canToggleDirectApkAssets,
        canLoadRepositoryApkAssets = canLoadRepositoryApkAssets,
        assetPanelExpanded = assetPanelExpanded,
        localAppUninstalled = localAppUninstalled,
        statusReleaseUrl = statusReleaseUrl,
        icon = icon,
        iconTint = iconTint,
        contentDescription =
            if (localAppUninstalled && canLoadApkAssets) {
                stringResource(R.string.github_cd_install_tracked_item, displayTitle)
            } else {
                state
                    .statusMessage(context)
                    .ifBlank { stringResource(R.string.github_cd_status) }
            },
        enabled = canLoadApkAssets || statusReleaseUrl.isNotBlank(),
    )
}

private data class GitHubTrackedItemHeaderActionState(
    val itemRefreshLoading: Boolean,
    val canToggleDirectApkAssets: Boolean,
    val canLoadRepositoryApkAssets: Boolean,
    val assetPanelExpanded: Boolean,
    val localAppUninstalled: Boolean,
    val statusReleaseUrl: String,
    val icon: ImageVector,
    val iconTint: Color,
    val contentDescription: String,
    val enabled: Boolean,
)

private val latestReleaseAccent = Color(0xFF06B6D4)
