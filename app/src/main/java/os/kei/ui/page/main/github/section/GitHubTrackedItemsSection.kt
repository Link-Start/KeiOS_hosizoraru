package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.feature.github.model.isKeiOsSelfTrack
import os.kei.ui.page.main.github.AppIcon
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.buildGitHubRepositoryHealth
import os.kei.ui.page.main.github.formatApkVersionValue
import os.kei.ui.page.main.github.formatReleaseMetaValue
import os.kei.ui.page.main.github.githubPreReleaseLinkUrl
import os.kei.ui.page.main.github.githubReleaseHintMessage
import os.kei.ui.page.main.github.githubStableReleaseLinkUrl
import os.kei.ui.page.main.github.githubTrackedDisplaySubtitle
import os.kei.ui.page.main.github.githubTrackedDisplayTitle
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.github.stableVersionColor
import os.kei.ui.page.main.github.statusActionUrl
import os.kei.ui.page.main.github.statusColor
import os.kei.ui.page.main.github.statusIcon
import os.kei.ui.page.main.github.statusMessage
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppInfoListBody
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSupportingBlock
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.motion.appExpandIn
import os.kei.ui.page.main.widget.motion.appExpandOut
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.GitHubTrackedItemsSection(
    lookupConfig: GitHubLookupConfig,
    trackedItems: List<GitHubTrackedApp>,
    filteredTracked: List<GitHubTrackedApp>,
    sortedTracked: List<GitHubTrackedApp>,
    appLastUpdatedAtByTrackId: Map<String, Long>,
    checkStates: SnapshotStateMap<String, VersionCheckUi>,
    itemRefreshLoading: SnapshotStateMap<String, Boolean>,
    contentBackdrop: LayerBackdrop,
    isDark: Boolean,
    apkAssetBundles: SnapshotStateMap<String, GitHubReleaseAssetBundle>,
    apkAssetLoading: SnapshotStateMap<String, Boolean>,
    apkAssetErrors: SnapshotStateMap<String, String>,
    apkAssetExpanded: SnapshotStateMap<String, Boolean>,
    managedInstallLoading: SnapshotStateMap<String, Boolean>,
    actionsRecommendedRunSnapshots: SnapshotStateMap<String, GitHubActionsRecommendedRunSnapshot>,
    trackedCardExpanded: SnapshotStateMap<String, Boolean>,
    trackedLocalVersionExpanded: SnapshotStateMap<String, Boolean>,
    trackedStableVersionExpanded: SnapshotStateMap<String, Boolean>,
    trackedPreReleaseVersionExpanded: SnapshotStateMap<String, Boolean>,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenTrackSheetForEdit: (GitHubTrackedApp) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    onCollapseTrackedCard: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onLocalVersionExpandedChange: (String, Boolean) -> Unit,
    onStableVersionExpandedChange: (String, Boolean) -> Unit,
    onPreReleaseVersionExpandedChange: (String, Boolean) -> Unit,
    onCollapseApkAssetPanel: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean) -> Unit,
    onOpenDecisionAssistDetail: (GitHubDecisionAssistDetailType, GitHubTrackedApp) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenApkInfo: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    onOpenApkInDownloader: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
    context: Context,
    supportedAbis: List<String>
) {
    if (trackedItems.isEmpty()) {
        item {
            MiuixInfoItem(
                stringResource(R.string.github_list_label_track_list),
                stringResource(R.string.github_list_msg_empty)
            )
        }
    } else if (filteredTracked.isEmpty()) {
        item {
            MiuixInfoItem(
                stringResource(R.string.github_list_label_search_result),
                stringResource(R.string.github_list_msg_no_match)
            )
        }
    } else {
        items(
            items = sortedTracked,
            key = { it.id },
            contentType = { "tracked_app" }
        ) { item ->
            val expanded = trackedCardExpanded[item.id] == true
            val state = checkStates[item.id] ?: pendingVersionCheckUi(context)
            val itemLookupConfig = lookupConfig.forTrackedItem(item)
            val displayTitle = item.githubTrackedDisplayTitle(state)
            val displaySubtitle = item.githubTrackedDisplaySubtitle(state, displayTitle)
            AppLiquidAccordionCard(
                backdrop = contentBackdrop,
                title = displayTitle,
                subtitle = displaySubtitle,
                expanded = expanded,
                onExpandedChange = {
                    trackedCardExpanded[item.id] = it
                    if (!it) {
                        val collapseState = checkStates[item.id] ?: VersionCheckUi()
                        onCollapseTrackedCard(item, collapseState)
                    }
                },
                headerStartAction = {
                    AppIcon(
                        packageName = item.packageName,
                        localRefreshKey = state.localVersionCode to state.localVersion,
                        size = 24.dp
                    )
                },
                titleAccessory = {
                    if (item.isKeiOsSelfTrack()) {
                        StatusPill(
                            label = stringResource(R.string.github_track_badge_current_app),
                            color = GitHubStatusPalette.Active,
                            size = AppStatusPillSize.Compact
                        )
                    }
                },
                onHeaderLongClick = { onOpenTrackSheetForEdit(item) },
                clipContent = false,
                headerActions = {
                    val isItemRefreshLoading = itemRefreshLoading[item.id] == true
                    val alwaysLatestReleaseDownload = item.alwaysShowLatestReleaseDownloadButton
                    val latestReleaseAccent = Color(0xFF06B6D4)
                    val statusColor = state.statusColor(
                        neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    val statusReleaseUrl = state.statusActionUrl(
                        owner = item.owner,
                        repo = item.repo
                    )
                    val canLoadApkAssets = item.isGitHubRepositoryTrack() &&
                            (
                                    alwaysLatestReleaseDownload ||
                                            state.hasUpdate == true ||
                                            state.recommendsPreRelease ||
                                            state.hasPreReleaseUpdate
                                    )
                    val isAssetPanelExpanded = apkAssetExpanded[item.id] == true
                    val isAssetPanelLoading = apkAssetLoading[item.id] == true
                    val statusIcon = when {
                        alwaysLatestReleaseDownload && isAssetPanelLoading -> appLucideRefreshIcon()
                        alwaysLatestReleaseDownload && isAssetPanelExpanded -> appLucideCloseIcon()
                        alwaysLatestReleaseDownload -> appLucideDownloadIcon()
                        isAssetPanelLoading -> appLucideRefreshIcon()
                        canLoadApkAssets && isAssetPanelExpanded -> appLucideCloseIcon()
                        else -> state.statusIcon()
                    }
                    val iconTint = if (alwaysLatestReleaseDownload) latestReleaseAccent else statusColor
                    AppCompactIconAction(
                        icon = statusIcon,
                        contentDescription = state.statusMessage(context)
                            .ifBlank { stringResource(R.string.github_cd_status) },
                        tint = iconTint,
                        enabled = canLoadApkAssets || statusReleaseUrl.isNotBlank(),
                        onClick = {
                            if (canLoadApkAssets) {
                                if (isAssetPanelExpanded) {
                                    onCollapseApkAssetPanel(item, state)
                                } else {
                                    onLoadApkAssets(item, state, true, false)
                                }
                            } else {
                                onOpenExternalUrl(statusReleaseUrl)
                            }
                        }
                    )
                    if (isItemRefreshLoading) {
                        val checkingContentDescription = stringResource(R.string.github_msg_checking)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .semantics {
                                    contentDescription = checkingContentDescription
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            LiquidCircularProgressBar(
                                size = 16.dp,
                                strokeWidth = 2.dp,
                                activeColor = iconTint,
                                inactiveColor = iconTint.copy(alpha = 0.18f)
                            )
                        }
                    } else {
                        GitHubTrackedItemMoreActions(
                            item = item,
                            state = state,
                            iconTint = iconTint,
                            showReleaseNotesAction = lookupConfig.decisionAssistEnabled,
                            onRefreshTrackedItem = onRefreshTrackedItem,
                            onOpenActionsSheet = onOpenActionsSheet,
                            onOpenReleaseNotes = {
                                onOpenDecisionAssistDetail(
                                    GitHubDecisionAssistDetailType.ReleaseNotes,
                                    item
                                )
                            },
                            onRequestDeleteTrackedItem = onRequestDeleteTrackedItem
                        )
                    }
                }
            ) {
                AppInfoListBody(
                    modifier = Modifier.fillMaxWidth(),
                    verticalSpacing = CardLayoutRhythm.denseSectionGap
                ) {
                    GitHubRepositoryLinkCard(
                        item = item,
                        onOpenExternalUrl = onOpenExternalUrl
                    )
                    GitHubDirectApkRemoteHealthCard(
                        item = item,
                        state = state,
                        onOpenExternalUrl = onOpenExternalUrl
                    )
                    val appUpdatedAtLabel = formatReleaseUpdatedAtCompact(
                        appLastUpdatedAtByTrackId[item.id]?.takeIf { it > 0L }
                    ) ?: stringResource(R.string.common_unknown)
                    val actionsRunSnapshot = actionsRecommendedRunSnapshots[item.id]
                    val localText = formatLocalVersionText(context, checkStates[item.id])
                    if (localText != null) {
                        val localVersionColor = if (state.isLocalAppUninstalled()) {
                            MiuixTheme.colorScheme.onBackgroundVariant
                        } else {
                            MiuixTheme.colorScheme.primary
                        }
                        val localVersionExpanded = trackedLocalVersionExpanded[item.id] == true
                        GitHubReleaseVersionCard(
                            label = stringResource(R.string.github_item_label_local_version),
                            value = localText,
                            textColor = localVersionColor,
                            expanded = localVersionExpanded,
                            emphasized = !state.isLocalAppUninstalled(),
                            valueMaxLines = 2,
                            onExpandedChange = { expanded ->
                                onLocalVersionExpandedChange(item.id, expanded)
                            }
                        )
                        AnimatedVisibility(
                            visible = localVersionExpanded,
                            enter = appExpandIn(),
                            exit = appExpandOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
                            ) {
                                GitHubLinkedInfoCard(
                                    label = stringResource(R.string.github_item_label_updated_at),
                                    value = appUpdatedAtLabel,
                                    valueColor = GitHubStatusPalette.Active,
                                    onClick = {
                                        onLocalVersionExpandedChange(item.id, false)
                                    }
                                )
                                if (item.checkActionsUpdates) {
                                    GitHubLinkedInfoCard(
                                        label = stringResource(R.string.github_item_label_actions_run),
                                        value = formatActionsRunSnapshotValue(actionsRunSnapshot),
                                        valueColor = GitHubStatusPalette.Cache,
                                        valueMaxLines = 2,
                                        onClick = {
                                            onOpenActionsSheet(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    val showStableRelease = state.hasStableRelease &&
                        (state.latestStableName.isNotBlank() ||
                            state.latestStableRawTag.isNotBlank() ||
                            state.latestTag.isNotBlank())
                    val showPreRelease = state.showPreReleaseInfo &&
                            (state.latestPreName.isNotBlank() ||
                                    state.latestPreRawTag.isNotBlank() ||
                                    state.preReleaseInfo.isNotBlank())
                    if (showStableRelease) {
                        val latestColor = state.stableVersionColor(
                            neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        val stableReleaseMeta = formatReleaseMetaValue(
                            preciseInfo = state.latestStableApkVersion.takeIf {
                                itemLookupConfig.preciseApkVersionEnabled
                            },
                            releaseName = state.latestStableName.ifBlank { state.latestTag },
                            rawTag = state.latestStableRawTag
                        )
                        val directReleaseNotes = state.latestStableApkVersion
                            ?.releaseNotes
                            .orEmpty()
                            .takeIf { item.isDirectApkTrack() }
                            .orEmpty()
                            .compactDirectReleaseNotes()
                        val showStableDetails = stableReleaseMeta.isNotBlank() ||
                                directReleaseNotes.isNotBlank()
                        val stableExpanded = trackedStableVersionExpanded[item.id] == true
                        GitHubReleaseVersionCard(
                            label = stringResource(
                                if (item.isDirectApkTrack()) {
                                    R.string.github_item_label_remote_version
                                } else {
                                    R.string.github_item_label_stable_version
                                }
                            ),
                            value = formatApkVersionValue(
                                preciseInfo = state.latestStableApkVersion.takeIf {
                                    itemLookupConfig.preciseApkVersionEnabled
                                },
                                releaseName = state.latestStableName.ifBlank { state.latestTag },
                                rawTag = state.latestStableRawTag
                            ),
                            textColor = latestColor,
                            expanded = stableExpanded,
                            emphasized = state.hasUpdate == true && !state.recommendsPreRelease,
                            valueMaxLines = 2,
                            onExpandedChange = { expanded ->
                                onStableVersionExpandedChange(item.id, expanded)
                            }
                        )
                        AnimatedVisibility(
                            visible = stableExpanded && showStableDetails,
                            enter = appExpandIn(),
                            exit = appExpandOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
                            ) {
                                if (stableReleaseMeta.isNotBlank()) {
                                    GitHubLinkedInfoCard(
                                        label = stringResource(
                                            if (item.isDirectApkTrack()) {
                                                R.string.github_item_label_remote_release
                                            } else {
                                                R.string.github_item_label_stable_release
                                            }
                                        ),
                                        value = stableReleaseMeta,
                                        valueColor = MiuixTheme.colorScheme.primary,
                                        valueMaxLines = 2,
                                        onClick = {
                                            onOpenExternalUrl(
                                                state.githubStableReleaseLinkUrl(
                                                    item.owner,
                                                    item.repo
                                                )
                                            )
                                        }
                                    )
                                }
                                if (item.isDirectApkTrack() && directReleaseNotes.isNotBlank()) {
                                    GitHubLinkedInfoCard(
                                        label = stringResource(R.string.github_release_notes_title),
                                        value = directReleaseNotes,
                                        valueColor = MiuixTheme.colorScheme.onBackground,
                                        valueMaxLines = 3,
                                        onClick = {
                                            onOpenDecisionAssistDetail(
                                                GitHubDecisionAssistDetailType.ReleaseNotes,
                                                item
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (showPreRelease) {
                        val preReleaseCardTextColor = gitHubPreReleaseCardTextColor()
                        val preReleaseMeta = formatReleaseMetaValue(
                            preciseInfo = state.latestPreApkVersion.takeIf {
                                itemLookupConfig.preciseApkVersionEnabled
                            },
                            releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                            rawTag = state.latestPreRawTag
                        )
                        val preExpanded = trackedPreReleaseVersionExpanded[item.id] == true
                        GitHubReleaseVersionCard(
                            label = stringResource(R.string.github_item_label_prerelease_version),
                            value = formatApkVersionValue(
                                preciseInfo = state.latestPreApkVersion.takeIf {
                                    itemLookupConfig.preciseApkVersionEnabled
                                },
                                releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                                rawTag = state.latestPreRawTag
                            ),
                            textColor = preReleaseCardTextColor,
                            expanded = preExpanded,
                            emphasized = state.recommendsPreRelease || state.hasPreReleaseUpdate,
                            valueMaxLines = 2,
                            onExpandedChange = { expanded ->
                                onPreReleaseVersionExpandedChange(item.id, expanded)
                            }
                        )
                        AnimatedVisibility(
                            visible = preExpanded && preReleaseMeta.isNotBlank(),
                            enter = appExpandIn(),
                            exit = appExpandOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
                            ) {
                                GitHubLinkedInfoCard(
                                    label = stringResource(R.string.github_item_label_prerelease_release),
                                    value = preReleaseMeta,
                                    labelColor = preReleaseCardTextColor,
                                    valueColor = preReleaseCardTextColor,
                                    valueMaxLines = 2,
                                    onClick = {
                                        onOpenExternalUrl(
                                            state.githubPreReleaseLinkUrl(item.owner, item.repo)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    if (state.releaseHint.isNotBlank()) {
                        AppSupportingBlock(
                            text = githubReleaseHintMessage(context, state.releaseHint),
                            accentColor = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }

                    val assetBundle = apkAssetBundles[item.id]
                    val assetLoading = apkAssetLoading[item.id] == true
                    val assetError = apkAssetErrors[item.id].orEmpty()
                    val assetExpanded = apkAssetExpanded[item.id] == true
                    if (item.isGitHubRepositoryTrack() &&
                        lookupConfig.decisionAssistEnabled &&
                        lookupConfig.repositoryHealthCardEnabled
                    ) {
                        val health = buildGitHubRepositoryHealth(item, state)
                        GitHubHealthPreviewBlock(
                            health = health,
                            onClick = {
                                onOpenDecisionAssistDetail(
                                    GitHubDecisionAssistDetailType.RepositoryHealth,
                                    item
                                )
                            }
                        )
                    }
                    if (item.isGitHubRepositoryTrack()) {
                        GitHubTrackedItemAssetPanel(
                            item = item,
                            state = state,
                            lookupConfig = itemLookupConfig,
                            isDark = isDark,
                            contentBackdrop = contentBackdrop,
                            assetBundle = assetBundle,
                            assetLoading = assetLoading,
                            assetError = assetError,
                            assetExpanded = assetExpanded,
                            managedInstallLoading = managedInstallLoading,
                            onOpenExternalUrl = onOpenExternalUrl,
                            onLoadApkAssets = onLoadApkAssets,
                            onOpenApkInfo = onOpenApkInfo,
                            onOpenApkInDownloader = onOpenApkInDownloader,
                            onShareApkLink = onShareApkLink,
                            context = context,
                            supportedAbis = supportedAbis
                        )
                    }
                }
            }
        }
    }
}

private fun String.compactDirectReleaseNotes(): String {
    return lineSequence()
        .map { it.trim().trimStart('-', '*', '•').trim() }
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .take(240)
        .trim()
}
