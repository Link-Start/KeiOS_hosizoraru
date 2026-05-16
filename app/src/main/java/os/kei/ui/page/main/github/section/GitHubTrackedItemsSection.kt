package os.kei.ui.page.main.github.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.feature.github.model.isKeiOsSelfTrack
import os.kei.ui.page.main.github.AppIcon
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.asset.directApkAssetPanelData
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
import os.kei.ui.page.main.os.appLucidePackageIcon
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

@Suppress("FunctionName")
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.GitHubTrackedItemsSection(
    content: GitHubTrackedItemsContent,
    surfaces: GitHubTrackedItemsSurfaces,
    checkState: GitHubTrackedItemsCheckState,
    assetState: GitHubTrackedItemsAssetState,
    expansionState: GitHubTrackedItemsExpansionState,
    runtime: GitHubTrackedItemsRuntime,
    actions: GitHubTrackedItemsActions,
) {
    val context = runtime.context
    if (content.trackedItems.isEmpty()) {
        item {
            MiuixInfoItem(
                stringResource(R.string.github_list_label_track_list),
                stringResource(R.string.github_list_msg_empty),
            )
        }
    } else if (content.filteredTracked.isEmpty()) {
        item {
            MiuixInfoItem(
                stringResource(R.string.github_list_label_search_result),
                stringResource(R.string.github_list_msg_no_match),
            )
        }
    } else {
        items(
            items = content.sortedTracked,
            key = { it.id },
            contentType = { "tracked_app" },
        ) { item ->
            val expanded = expansionState.trackedCardExpanded[item.id] == true
            val state = checkState.checkStates[item.id] ?: pendingVersionCheckUi(context)
            val itemLookupConfig = content.lookupConfig.forTrackedItem(item)
            val installedAppLabel = content.installedAppLabelsByPackage[item.packageName.trim()].orEmpty()
            val displayTitle =
                item.githubTrackedDisplayTitle(
                    state = state,
                    installedAppLabel = installedAppLabel,
                )
            val displaySubtitle = item.githubTrackedDisplaySubtitle(state, displayTitle)
            AppLiquidAccordionCard(
                backdrop = surfaces.contentBackdrop,
                title = displayTitle,
                subtitle = displaySubtitle,
                expanded = expanded,
                onExpandedChange = {
                    expansionState.trackedCardExpanded[item.id] = it
                    if (!it) {
                        val collapseState = checkState.checkStates[item.id] ?: VersionCheckUi()
                        actions.onCollapseTrackedCard(item, collapseState)
                    }
                },
                headerStartAction = {
                    AppIcon(
                        packageName = item.packageName,
                        localRefreshKey = state.localVersionCode to state.localVersion,
                        size = 24.dp,
                    )
                },
                titleAccessory = {
                    if (item.isKeiOsSelfTrack()) {
                        StatusPill(
                            label = stringResource(R.string.github_track_badge_current_app),
                            color = GitHubStatusPalette.Active,
                            size = AppStatusPillSize.Compact,
                        )
                    }
                },
                onHeaderLongClick = { actions.onOpenTrackSheetForEdit(item) },
                clipContent = false,
                headerActions = {
                    val isItemRefreshLoading = checkState.itemRefreshLoading[item.id] == true
                    val alwaysLatestReleaseDownload = item.alwaysShowLatestReleaseDownloadButton
                    val latestReleaseAccent = Color(0xFF06B6D4)
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
                    val canToggleDirectApkAssets =
                        item.isDirectApkTrack() && directAssetPanelData != null
                    val canLoadApkAssets = canLoadRepositoryApkAssets || canToggleDirectApkAssets
                    val isAssetPanelExpanded = assetState.apkAssetExpanded[item.id] == true
                    val isAssetPanelLoading = assetState.apkAssetLoading[item.id] == true
                    val statusIcon =
                        when {
                            isAssetPanelLoading -> appLucideRefreshIcon()
                            canLoadApkAssets && isAssetPanelExpanded -> appLucideCloseIcon()
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
                    AppCompactIconAction(
                        icon = statusIcon,
                        contentDescription =
                            if (localAppUninstalled && canLoadApkAssets) {
                                stringResource(R.string.github_cd_install_tracked_item, displayTitle)
                            } else {
                                state
                                    .statusMessage(context)
                                    .ifBlank { stringResource(R.string.github_cd_status) }
                            },
                        tint = iconTint,
                        enabled = canLoadApkAssets || statusReleaseUrl.isNotBlank(),
                        onClick = {
                            if (canToggleDirectApkAssets) {
                                assetState.apkAssetExpanded[item.id] = !isAssetPanelExpanded
                            } else if (canLoadRepositoryApkAssets) {
                                if (isAssetPanelExpanded) {
                                    actions.onCollapseApkAssetPanel(item, state)
                                } else {
                                    actions.onLoadApkAssets(item, state, true, false, localAppUninstalled)
                                }
                            } else {
                                actions.onOpenExternalUrl(statusReleaseUrl)
                            }
                        },
                    )
                    if (isItemRefreshLoading) {
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
                    } else {
                        GitHubTrackedItemMoreActions(
                            item = item,
                            state = state,
                            iconTint = iconTint,
                            showReleaseNotesAction = content.lookupConfig.decisionAssistEnabled,
                            onRefreshTrackedItem = actions.onRefreshTrackedItem,
                            onOpenActionsSheet = actions.onOpenActionsSheet,
                            onOpenReleaseNotes = {
                                actions.onOpenDecisionAssistDetail(
                                    GitHubDecisionAssistDetailType.ReleaseNotes,
                                    item,
                                )
                            },
                            onRequestDeleteTrackedItem = actions.onRequestDeleteTrackedItem,
                        )
                    }
                },
            ) {
                AppInfoListBody(
                    modifier = Modifier.fillMaxWidth(),
                    verticalSpacing = CardLayoutRhythm.denseSectionGap,
                ) {
                    GitHubRepositoryLinkCard(
                        item = item,
                        onOpenExternalUrl = actions.onOpenExternalUrl,
                    )
                    val appUpdatedAtLabel =
                        formatReleaseUpdatedAtCompact(
                            content.appLastUpdatedAtByTrackId[item.id]?.takeIf { it > 0L },
                        ) ?: stringResource(R.string.common_unknown)
                    val actionsRunSnapshot = checkState.actionsRecommendedRunSnapshots[item.id]
                    val localText = formatLocalVersionText(context, checkState.checkStates[item.id])
                    if (localText != null) {
                        val localVersionColor =
                            if (state.isLocalAppUninstalled()) {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            } else {
                                MiuixTheme.colorScheme.primary
                            }
                        val localVersionExpanded =
                            expansionState.trackedLocalVersionExpanded[item.id] == true
                        GitHubReleaseVersionCard(
                            label = stringResource(R.string.github_item_label_local_version),
                            value = localText,
                            textColor = localVersionColor,
                            expanded = localVersionExpanded,
                            emphasized = !state.isLocalAppUninstalled(),
                            valueMaxLines = 2,
                            onExpandedChange = { expanded ->
                                actions.onLocalVersionExpandedChange(item.id, expanded)
                            },
                        )
                        AnimatedVisibility(
                            visible = localVersionExpanded,
                            enter = appExpandIn(),
                            exit = appExpandOut(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
                            ) {
                                GitHubLinkedInfoCard(
                                    label = stringResource(R.string.github_item_label_updated_at),
                                    value = appUpdatedAtLabel,
                                    valueColor = GitHubStatusPalette.Active,
                                    onClick = {
                                        actions.onLocalVersionExpandedChange(item.id, false)
                                    },
                                )
                                if (item.checkActionsUpdates) {
                                    GitHubLinkedInfoCard(
                                        label = stringResource(R.string.github_item_label_actions_run),
                                        value = formatActionsRunSnapshotValue(actionsRunSnapshot),
                                        valueColor = GitHubStatusPalette.Cache,
                                        valueMaxLines = 2,
                                        onClick = {
                                            actions.onOpenActionsSheet(item)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val showStableRelease =
                        state.hasStableRelease &&
                            (
                                state.latestStableName.isNotBlank() ||
                                    state.latestStableRawTag.isNotBlank() ||
                                    state.latestTag.isNotBlank()
                            )
                    val showPreRelease =
                        state.showPreReleaseInfo &&
                            (
                                state.latestPreName.isNotBlank() ||
                                    state.latestPreRawTag.isNotBlank() ||
                                    state.preReleaseInfo.isNotBlank()
                            )
                    if (showStableRelease) {
                        val latestColor =
                            state.stableVersionColor(
                                neutralColor = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        val stableReleaseMeta =
                            formatReleaseMetaValue(
                                preciseInfo =
                                    state.latestStableApkVersion.takeIf {
                                        itemLookupConfig.preciseApkVersionEnabled
                                    },
                                releaseName = state.latestStableName.ifBlank { state.latestTag },
                                rawTag = state.latestStableRawTag,
                            )
                        val stableExpanded =
                            expansionState.trackedStableVersionExpanded[item.id] == true
                        GitHubReleaseVersionCard(
                            label =
                                stringResource(
                                    if (item.isDirectApkTrack()) {
                                        R.string.github_item_label_remote_stable_version
                                    } else {
                                        R.string.github_item_label_stable_version
                                    },
                                ),
                            value =
                                formatApkVersionValue(
                                    preciseInfo =
                                        state.latestStableApkVersion.takeIf {
                                            itemLookupConfig.preciseApkVersionEnabled
                                        },
                                    releaseName = state.latestStableName.ifBlank { state.latestTag },
                                    rawTag = state.latestStableRawTag,
                                ),
                            textColor = latestColor,
                            expanded = stableExpanded,
                            emphasized = state.hasUpdate == true && !state.recommendsPreRelease,
                            valueMaxLines = 2,
                            onExpandedChange = { expanded ->
                                actions.onStableVersionExpandedChange(item.id, expanded)
                            },
                        )
                        AnimatedVisibility(
                            visible = stableExpanded && stableReleaseMeta.isNotBlank(),
                            enter = appExpandIn(),
                            exit = appExpandOut(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
                            ) {
                                GitHubLinkedInfoCard(
                                    label =
                                        stringResource(
                                            if (item.isDirectApkTrack()) {
                                                R.string.github_item_label_remote_stable_release
                                            } else {
                                                R.string.github_item_label_stable_release
                                            },
                                        ),
                                    value = stableReleaseMeta,
                                    valueColor = MiuixTheme.colorScheme.primary,
                                    valueMaxLines = 2,
                                    onClick = {
                                        actions.onOpenExternalUrl(
                                            state.githubStableReleaseLinkUrl(
                                                item.owner,
                                                item.repo,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (showPreRelease) {
                        val preReleaseCardTextColor = gitHubPreReleaseCardTextColor()
                        val preReleaseMeta =
                            formatReleaseMetaValue(
                                preciseInfo =
                                    state.latestPreApkVersion.takeIf {
                                        itemLookupConfig.preciseApkVersionEnabled
                                    },
                                releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                                rawTag = state.latestPreRawTag,
                            )
                        val preExpanded =
                            expansionState.trackedPreReleaseVersionExpanded[item.id] == true
                        GitHubReleaseVersionCard(
                            label =
                                stringResource(
                                    if (item.isDirectApkTrack()) {
                                        R.string.github_item_label_remote_prerelease_version
                                    } else {
                                        R.string.github_item_label_prerelease_version
                                    },
                                ),
                            value =
                                formatApkVersionValue(
                                    preciseInfo =
                                        state.latestPreApkVersion.takeIf {
                                            itemLookupConfig.preciseApkVersionEnabled
                                        },
                                    releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                                    rawTag = state.latestPreRawTag,
                                ),
                            textColor = preReleaseCardTextColor,
                            expanded = preExpanded,
                            emphasized = state.recommendsPreRelease || state.hasPreReleaseUpdate,
                            valueMaxLines = 2,
                            onExpandedChange = { expanded ->
                                actions.onPreReleaseVersionExpandedChange(item.id, expanded)
                            },
                        )
                        AnimatedVisibility(
                            visible = preExpanded && preReleaseMeta.isNotBlank(),
                            enter = appExpandIn(),
                            exit = appExpandOut(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
                            ) {
                                GitHubLinkedInfoCard(
                                    label =
                                        stringResource(
                                            if (item.isDirectApkTrack()) {
                                                R.string.github_item_label_remote_prerelease_release
                                            } else {
                                                R.string.github_item_label_prerelease_release
                                            },
                                        ),
                                    value = preReleaseMeta,
                                    labelColor = preReleaseCardTextColor,
                                    valueColor = preReleaseCardTextColor,
                                    valueMaxLines = 2,
                                    onClick = {
                                        actions.onOpenExternalUrl(
                                            state.githubPreReleaseLinkUrl(item.owner, item.repo),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (state.releaseHint.isNotBlank()) {
                        AppSupportingBlock(
                            text = githubReleaseHintMessage(context, state.releaseHint),
                            accentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }

                    val assetBundle = assetState.apkAssetBundles[item.id]
                    val assetLoading = assetState.apkAssetLoading[item.id] == true
                    val assetError = assetState.apkAssetErrors[item.id].orEmpty()
                    val assetExpanded = assetState.apkAssetExpanded[item.id] == true
                    if (item.isGitHubRepositoryTrack() &&
                        content.lookupConfig.decisionAssistEnabled &&
                        content.lookupConfig.repositoryHealthCardEnabled
                    ) {
                        val health = buildGitHubRepositoryHealth(item, state)
                        GitHubHealthPreviewBlock(
                            health = health,
                            onClick = {
                                actions.onOpenDecisionAssistDetail(
                                    GitHubDecisionAssistDetailType.RepositoryHealth,
                                    item,
                                )
                            },
                        )
                    }
                    if (item.isGitHubRepositoryTrack()) {
                        GitHubTrackedItemAssetPanel(
                            item = item,
                            state = state,
                            lookupConfig = itemLookupConfig,
                            isDark = surfaces.isDark,
                            contentBackdrop = surfaces.contentBackdrop,
                            assetBundle = assetBundle,
                            assetLoading = assetLoading,
                            assetError = assetError,
                            assetExpanded = assetExpanded,
                            managedInstallLoading = assetState.managedInstallLoading,
                            onOpenExternalUrl = actions.onOpenExternalUrl,
                            onLoadApkAssets = actions.onLoadApkAssets,
                            onRefreshTrackedItem = actions.onRefreshTrackedItem,
                            onOpenApkInfo = actions.onOpenApkInfo,
                            onOpenApkInDownloader = actions.onOpenApkInDownloader,
                            onShareApkLink = actions.onShareApkLink,
                            context = context,
                            supportedAbis = runtime.supportedAbis,
                        )
                    }
                    if (item.isDirectApkTrack()) {
                        GitHubTrackedItemAssetPanel(
                            item = item,
                            state = state,
                            lookupConfig = itemLookupConfig,
                            isDark = surfaces.isDark,
                            contentBackdrop = surfaces.contentBackdrop,
                            assetBundle = null,
                            assetLoading = false,
                            assetError = "",
                            assetExpanded = assetExpanded,
                            managedInstallLoading = assetState.managedInstallLoading,
                            onOpenExternalUrl = actions.onOpenExternalUrl,
                            onLoadApkAssets = actions.onLoadApkAssets,
                            onRefreshTrackedItem = actions.onRefreshTrackedItem,
                            onOpenApkInfo = actions.onOpenApkInfo,
                            onOpenApkInDownloader = actions.onOpenApkInDownloader,
                            onShareApkLink = actions.onShareApkLink,
                            context = context,
                            supportedAbis = runtime.supportedAbis,
                        )
                        GitHubDirectApkRemoteHealthCard(
                            item = item,
                            state = state,
                            onOpenExternalUrl = actions.onOpenExternalUrl,
                        )
                    }
                }
            }
        }
    }
}
