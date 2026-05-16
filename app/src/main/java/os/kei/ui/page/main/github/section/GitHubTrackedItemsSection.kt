package os.kei.ui.page.main.github.section

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.isKeiOsSelfTrack
import os.kei.ui.page.main.github.AppIcon
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.githubReleaseHintMessage
import os.kei.ui.page.main.github.githubTrackedDisplaySubtitle
import os.kei.ui.page.main.github.githubTrackedDisplayTitle
import os.kei.ui.page.main.widget.core.AppInfoListBody
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSupportingBlock
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
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
                    GitHubTrackedItemHeaderActions(
                        item = item,
                        state = state,
                        displayTitle = displayTitle,
                        lookupConfig = content.lookupConfig,
                        checkState = checkState,
                        assetState = assetState,
                        actions = actions,
                        context = context,
                    )
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
                    GitHubTrackedItemVersionSections(
                        item = item,
                        state = state,
                        itemLookupConfig = itemLookupConfig,
                        appUpdatedAtLabel = appUpdatedAtLabel,
                        checkState = checkState,
                        expansionState = expansionState,
                        actions = actions,
                        context = context,
                    )
                    if (state.releaseHint.isNotBlank()) {
                        AppSupportingBlock(
                            text = githubReleaseHintMessage(context, state.releaseHint),
                            accentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    GitHubTrackedItemAssetSections(
                        item = item,
                        state = state,
                        itemLookupConfig = itemLookupConfig,
                        content = content,
                        surfaces = surfaces,
                        assetState = assetState,
                        runtime = runtime,
                        actions = actions,
                    )
                }
            }
        }
    }
}
