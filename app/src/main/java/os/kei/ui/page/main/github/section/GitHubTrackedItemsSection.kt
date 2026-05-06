package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseNotesMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isKeiOsSelfTrack
import os.kei.ui.page.main.github.AppIcon
import os.kei.ui.page.main.github.GitHubCompactInfoRow
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.GitHubRepositoryHealthReason
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.VersionValueRow
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.buildGitHubReleaseNotesLines
import os.kei.ui.page.main.github.buildGitHubRepositoryHealth
import os.kei.ui.page.main.github.formatApkVersionValue
import os.kei.ui.page.main.github.formatReleaseMetaValue
import os.kei.ui.page.main.github.formatReleaseValue
import os.kei.ui.page.main.github.githubReleaseHintMessage
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.github.preReleaseVersionColor
import os.kei.ui.page.main.github.stableVersionColor
import os.kei.ui.page.main.github.statusActionUrl
import os.kei.ui.page.main.github.statusColor
import os.kei.ui.page.main.github.statusIcon
import os.kei.ui.page.main.github.statusMessage
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppInfoListBody
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSupportingBlock
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
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
    trackedCardExpanded: SnapshotStateMap<String, Boolean>,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenTrackSheetForEdit: (GitHubTrackedApp) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    onClearApkAssetUiState: (String) -> Unit,
    onCollapseApkAssetPanel: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean) -> Unit,
    onOpenDecisionAssistDetail: (GitHubDecisionAssistDetailType, GitHubTrackedApp) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenApkInfo: (GitHubReleaseAssetFile) -> Unit,
    onOpenApkInDownloader: (GitHubReleaseAssetFile) -> Unit,
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
            AppLiquidAccordionCard(
                backdrop = contentBackdrop,
                title = item.appLabel,
                subtitle = item.packageName,
                expanded = expanded,
                onExpandedChange = {
                    trackedCardExpanded[item.id] = it
                    if (!it) {
                        val collapseState = checkStates[item.id] ?: VersionCheckUi()
                        if (apkAssetExpanded[item.id] == true) {
                            onCollapseApkAssetPanel(item, collapseState)
                        } else {
                            onClearApkAssetUiState(item.id)
                        }
                    }
                },
                headerStartAction = {
                    AppIcon(packageName = item.packageName, size = 24.dp)
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
                headerActions = {
                    val state = checkStates[item.id] ?: VersionCheckUi()
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
                    val canLoadApkAssets = alwaysLatestReleaseDownload ||
                        state.hasUpdate == true ||
                        state.recommendsPreRelease ||
                        state.hasPreReleaseUpdate
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
                            contentAlignment = androidx.compose.ui.Alignment.Center
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
                            onRefreshTrackedItem = onRefreshTrackedItem,
                            onOpenActionsSheet = onOpenActionsSheet,
                            onRequestDeleteTrackedItem = onRequestDeleteTrackedItem
                        )
                    }
                }
            ) {
                val state = checkStates[item.id] ?: VersionCheckUi()
                AppInfoListBody(
                    modifier = Modifier.fillMaxWidth(),
                    verticalSpacing = CardLayoutRhythm.denseSectionGap
                ) {
                    GitHubCompactInfoRow(
                        label = stringResource(R.string.github_item_label_repo),
                        value = "${item.owner}/${item.repo}",
                        valueColor = MiuixTheme.colorScheme.primary,
                        titleColor = MiuixTheme.colorScheme.primary,
                        onClick = {
                            onOpenExternalUrl(GitHubVersionUtils.buildReleaseUrl(item.owner, item.repo))
                        }
                    )
                    val localText = formatLocalVersionText(context, state)
                    if (localText != null) {
                        VersionValueRow(
                            label = stringResource(R.string.github_item_label_local_version),
                            value = localText,
                            valueColor = if (state.isLocalAppUninstalled()) {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            } else {
                                MiuixTheme.colorScheme.primary
                            }
                        )
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
                        VersionValueRow(
                            label = stringResource(R.string.github_item_label_stable_version),
                            value = formatApkVersionValue(
                                preciseInfo = state.latestStableApkVersion.takeIf {
                                    lookupConfig.preciseApkVersionEnabled
                                },
                                releaseName = state.latestStableName.ifBlank { state.latestTag },
                                rawTag = state.latestStableRawTag
                            ),
                            valueColor = latestColor,
                            emphasized = state.hasUpdate == true && !state.recommendsPreRelease
                        )
                    }
                    if (showPreRelease) {
                        val preColor = state.preReleaseVersionColor(
                            neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        VersionValueRow(
                            label = stringResource(R.string.github_item_label_prerelease_version),
                            value = formatApkVersionValue(
                                preciseInfo = state.latestPreApkVersion.takeIf {
                                    lookupConfig.preciseApkVersionEnabled
                                },
                                releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                                rawTag = state.latestPreRawTag
                            ),
                            valueColor = preColor,
                            emphasized = state.recommendsPreRelease || state.hasPreReleaseUpdate
                        )
                    }
                    if (lookupConfig.preciseApkVersionEnabled) {
                        val releaseMetaValues = buildList {
                            if (showStableRelease) {
                                formatReleaseMetaValue(
                                    preciseInfo = state.latestStableApkVersion,
                                    releaseName = state.latestStableName.ifBlank { state.latestTag },
                                    rawTag = state.latestStableRawTag
                                ).takeIf { it.isNotBlank() }?.let { label ->
                                    add("${context.getString(R.string.github_release_meta_prefix_stable)}: $label")
                                }
                            }
                            if (showPreRelease) {
                                formatReleaseMetaValue(
                                    preciseInfo = state.latestPreApkVersion,
                                    releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                                    rawTag = state.latestPreRawTag
                                ).takeIf { it.isNotBlank() }?.let { label ->
                                    add("${context.getString(R.string.github_release_meta_prefix_prerelease)}: $label")
                                }
                            }
                        }
                        if (releaseMetaValues.isNotEmpty()) {
                            VersionValueRow(
                                label = stringResource(R.string.github_item_label_releases),
                                value = releaseMetaValues.joinToString(" / "),
                                valueColor = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                    }
                    val appUpdatedAtLabel = formatReleaseUpdatedAtCompact(
                        appLastUpdatedAtByTrackId[item.id]?.takeIf { it > 0L }
                    ) ?: stringResource(R.string.common_unknown)
                    VersionValueRow(
                        label = stringResource(R.string.github_item_label_updated_at),
                        value = appUpdatedAtLabel,
                        valueColor = GitHubStatusPalette.Active
                    )
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
                    if (lookupConfig.decisionAssistEnabled &&
                        lookupConfig.repositoryHealthCardEnabled
                    ) {
                        val health = buildGitHubRepositoryHealth(item, state)
                        AppSupportingBlock(
                            text = buildGitHubRepositoryHealthText(
                                context = context,
                                score = health.score,
                                reasons = health.reasons
                            ),
                            accentColor = health.level.toStatusColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onClick = {
                                onOpenDecisionAssistDetail(
                                    GitHubDecisionAssistDetailType.RepositoryHealth,
                                    item
                                )
                            }
                        )
                    }
                    if (lookupConfig.decisionAssistEnabled &&
                        lookupConfig.releaseNotesMode != GitHubReleaseNotesMode.Off
                    ) {
                        val releaseNotesLines = buildGitHubReleaseNotesLines(
                            item = item,
                            state = state,
                            assetBundle = assetBundle,
                            expanded = lookupConfig.releaseNotesMode == GitHubReleaseNotesMode.Expanded
                        )
                        if (releaseNotesLines.isNotEmpty()) {
                            AppSupportingBlock(
                                text = buildString {
                                    append(stringResource(R.string.github_release_notes_title))
                                    append('\n')
                                    append(releaseNotesLines.joinToString("\n"))
                                },
                                accentColor = GitHubStatusPalette.Active,
                                maxLines = if (lookupConfig.releaseNotesMode == GitHubReleaseNotesMode.Expanded) {
                                    6
                                } else {
                                    3
                                },
                                overflow = TextOverflow.Ellipsis,
                                onClick = {
                                    onOpenDecisionAssistDetail(
                                        GitHubDecisionAssistDetailType.ReleaseNotes,
                                        item
                                    )
                                }
                            )
                        }
                    }
                    GitHubTrackedItemAssetPanel(
                        item = item,
                        state = state,
                        lookupConfig = lookupConfig,
                        isDark = isDark,
                        contentBackdrop = contentBackdrop,
                        assetBundle = assetBundle,
                        assetLoading = assetLoading,
                        assetError = assetError,
                        assetExpanded = assetExpanded,
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

private fun buildGitHubRepositoryHealthText(
    context: Context,
    score: Int,
    reasons: List<GitHubRepositoryHealthReason>
): String {
    val reasonText = reasons
        .take(4)
        .joinToString(" / ") { reason -> context.getString(reason.labelRes()) }
    return if (reasonText.isBlank()) {
        context.getString(R.string.github_health_score_value, score)
    } else {
        context.getString(R.string.github_health_score_with_reasons, score, reasonText)
    }
}

private fun GitHubDecisionLevel.toStatusColor(): Color {
    return when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }
}

private fun GitHubRepositoryHealthReason.labelRes(): Int {
    return when (this) {
        GitHubRepositoryHealthReason.UpdateAvailable -> R.string.github_health_reason_update_available
        GitHubRepositoryHealthReason.PreReleaseRecommended -> R.string.github_health_reason_prerelease
        GitHubRepositoryHealthReason.CheckFailed -> R.string.github_health_reason_check_failed
        GitHubRepositoryHealthReason.MissingPackageName -> R.string.github_health_reason_missing_package
        GitHubRepositoryHealthReason.MissingStableRelease -> R.string.github_health_reason_missing_stable
        GitHubRepositoryHealthReason.LocalMissing -> R.string.github_health_reason_local_missing
        GitHubRepositoryHealthReason.StableDetected -> R.string.github_health_reason_stable_detected
        GitHubRepositoryHealthReason.FreshRelease -> R.string.github_health_reason_fresh_release
    }
}

@Composable
private fun GitHubTrackedItemMoreActions(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    iconTint: Color,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var menuAnchorBounds by remember(item.id) { mutableStateOf<IntRect?>(null) }
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
                LiquidGlassDropdownColumn {
                    GitHubTrackedItemMenuAction(
                        text = stringResource(R.string.common_refresh),
                        index = 0,
                        optionSize = 3,
                        onClick = {
                            menuExpanded = false
                            onRefreshTrackedItem(item)
                        }
                    )
                    GitHubTrackedItemMenuAction(
                        text = stringResource(R.string.github_actions_menu),
                        index = 1,
                        optionSize = 3,
                        onClick = {
                            menuExpanded = false
                            onOpenActionsSheet(item)
                        }
                    )
                    GitHubTrackedItemMenuAction(
                        text = stringResource(R.string.github_track_sheet_btn_delete),
                        index = 2,
                        optionSize = 3,
                        variant = GlassVariant.SheetDangerAction,
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
    index: Int,
    optionSize: Int,
    onClick: () -> Unit,
    variant: GlassVariant = GlassVariant.SheetAction
) {
    LiquidGlassDropdownActionItem(
        text = text,
        onClick = onClick,
        index = index,
        optionSize = optionSize,
        variant = variant
    )
}

internal fun formatLocalVersionText(
    context: Context,
    state: VersionCheckUi
): String? {
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

@Composable
internal fun GitHubAssetCountBubble(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
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
                blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, GlassVariant.Compact),
                lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, GlassVariant.Compact),
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
