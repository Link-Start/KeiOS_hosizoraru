package os.kei.ui.page.main.github.section

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingRefreshStatus
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalSearchActionDock
import os.kei.ui.page.main.widget.glass.appFloatingDockBottomTarget
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import os.kei.ui.testing.KeiOsTestTags

@Suppress("FunctionName")
@Composable
internal fun GitHubMainContent(
    layout: GitHubMainContentLayout,
    surfaces: GitHubMainContentSurfaces,
    controls: GitHubMainContentControls,
    overview: GitHubMainContentOverview,
    tracked: GitHubMainContentTracked,
    shareImport: GitHubMainContentShareImport,
    actions: GitHubMainContentActions,
) {
    val context = LocalContext.current
    val supportedAbis = remember { Build.SUPPORTED_ABIS?.toList().orEmpty() }
    val installedAppLabelsByPackage =
        remember(tracked.appList) {
            tracked.appList
                .asSequence()
                .map { it.packageName.trim() to it.label.trim() }
                .filter { (packageName, label) -> packageName.isNotBlank() && label.isNotBlank() }
                .toMap()
        }
    val searchDockBottomTarget =
        appFloatingDockBottomTarget(
            contentBottomPadding = layout.contentBottomPadding,
            bottomBarVisible = layout.bottomBarVisible,
        )
    val searchDockBottomState =
        rememberAppFloatingDockBottomState(
            contentBottomPadding = layout.contentBottomPadding,
            bottomBarVisible = layout.bottomBarVisible,
            label = "github_floating_search_bottom",
        )
    val floatingKeyboardLift =
        rememberAppFloatingKeyboardLift(
            restingBottomGap = searchDockBottomTarget,
            label = "github_floating_keyboard_lift",
        )
    val dockAlignment =
        if (layout.floatingDockSide == AppFloatingDockSide.Start) {
            androidx.compose.ui.Alignment.BottomStart
        } else {
            androidx.compose.ui.Alignment.BottomEnd
        }
    val dockStartPadding = if (layout.floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (layout.floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val refreshStatus =
        when (overview.refreshState) {
            OverviewRefreshState.Refreshing -> AppFloatingRefreshStatus.Refreshing
            OverviewRefreshState.Completed -> AppFloatingRefreshStatus.Success
            OverviewRefreshState.Failed -> AppFloatingRefreshStatus.Danger
            OverviewRefreshState.Cached -> AppFloatingRefreshStatus.Cached
            OverviewRefreshState.Idle -> AppFloatingRefreshStatus.Idle
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(KeiOsTestTags.GitHubPageRoot),
    ) {
        AppScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                GitHubTopBarSection(
                    topBarColor = surfaces.topBarColor,
                    scrollBehavior = layout.scrollBehavior,
                    titleBackdrop = surfaces.topBarBackdrop,
                    onTitleClick = layout.onShowBottomBar,
                )
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(layout.addButtonScrollConnection),
            ) {
                AppPageLazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(layout.scrollBehavior.nestedScrollConnection),
                    state = layout.listState,
                    innerPadding = innerPadding,
                    bottomExtra = appPageBottomPaddingWithFloatingOverlay(layout.contentBottomPadding),
                    sectionSpacing = CardLayoutRhythm.denseSectionGap,
                ) {
                    item(
                        key = "github_overview_card",
                        contentType = "github_overview",
                    ) {
                        GitHubOverviewCard(
                            backdrop = surfaces.contentBackdrop,
                            isDark = surfaces.isDark,
                            lookupConfig = overview.lookupConfig,
                            overviewRefreshState = overview.refreshState,
                            expanded = overview.expanded,
                            onExpandedChange = actions.onOverviewExpandedChange,
                            refreshProgress = overview.refreshProgress,
                            lastRefreshMs = overview.lastRefreshMs,
                            visibleEntries = overview.visibleEntries,
                            metrics = overview.metrics,
                            failedFilterActive = controls.trackedFilterMode == GitHubTrackedFilterMode.FailedChecks,
                            onEditVisibleEntries = actions.onOpenOverviewEntrySheet,
                            onRetryFailedTracked = actions.onRetryFailedTracked,
                            onFailedFilterToggle = actions.onFailedFilterToggle,
                        )
                    }
                    if (shareImport.showPendingCard && shareImport.pendingTrack != null) {
                        item(
                            key = "github_pending_share_import_track",
                            contentType = "github_share_import",
                        ) {
                            GitHubPendingShareImportCard(
                                pending = shareImport.pendingTrack,
                                repoOverlapCount = shareImport.pendingRepoOverlapCount,
                                onOpen = actions.onOpenShareImportFlow,
                                onCancel = actions.onCancelPendingShareImportTrack,
                            )
                        }
                    }
                    shareImport.pendingAttachCandidate?.let { candidate ->
                        item(
                            key = "github_pending_share_import_attach",
                            contentType = "github_share_import",
                        ) {
                            GitHubShareImportAttachCandidateCard(
                                candidate = candidate,
                                onOpen = actions.onOpenShareImportFlow,
                                onCancel = actions.onCancelActiveShareImportFlow,
                            )
                        }
                    }
                    if (shareImport.pendingTrack == null && shareImport.pendingAttachCandidate == null) {
                        shareImport.pendingPreview?.let { preview ->
                            item(
                                key = "github_share_import_preview",
                                contentType = "github_share_import",
                            ) {
                                GitHubShareImportPreviewCard(
                                    preview = preview,
                                    onOpen = actions.onOpenShareImportFlow,
                                    onCancel = actions.onCancelActiveShareImportFlow,
                                )
                            }
                        }
                    }
                    if (
                        shareImport.pendingPreview == null &&
                        shareImport.pendingTrack == null &&
                        shareImport.pendingAttachCandidate == null
                    ) {
                        shareImport.pendingResult?.let { result ->
                            item(
                                key = "github_share_import_result",
                                contentType = "github_share_import",
                            ) {
                                GitHubShareImportResultCard(
                                    result = result,
                                    onOpen = actions.onOpenShareImportResult,
                                    onDismiss = actions.onDismissShareImportResult,
                                )
                            }
                        }
                    }
                    GitHubTrackedItemsSection(
                        content =
                            GitHubTrackedItemsContent(
                                lookupConfig = overview.lookupConfig,
                                trackedItems = tracked.trackedItems,
                                filteredTracked = tracked.filteredTracked,
                                sortedTracked = tracked.sortedTracked,
                                installedAppLabelsByPackage = installedAppLabelsByPackage,
                                appLastUpdatedAtByTrackId = tracked.appLastUpdatedAtByTrackId,
                            ),
                        surfaces =
                            GitHubTrackedItemsSurfaces(
                                contentBackdrop = surfaces.contentBackdrop,
                                isDark = surfaces.isDark,
                            ),
                        checkState =
                            GitHubTrackedItemsCheckState(
                                checkStates = tracked.checkStates,
                                itemRefreshLoading = tracked.itemRefreshLoading,
                                actionsRecommendedRunSnapshots = tracked.actionsRecommendedRunSnapshots,
                            ),
                        assetState =
                            GitHubTrackedItemsAssetState(
                                apkAssetBundles = tracked.apkAssetBundles,
                                apkAssetLoading = tracked.apkAssetLoading,
                                apkAssetErrors = tracked.apkAssetErrors,
                                apkAssetExpanded = tracked.apkAssetExpanded,
                                managedInstallLoading = tracked.managedInstallLoading,
                            ),
                        expansionState =
                            GitHubTrackedItemsExpansionState(
                                trackedCardExpanded = tracked.trackedCardExpanded,
                                trackedLocalVersionExpanded = tracked.trackedLocalVersionExpanded,
                                trackedStableVersionExpanded = tracked.trackedStableVersionExpanded,
                                trackedPreReleaseVersionExpanded = tracked.trackedPreReleaseVersionExpanded,
                            ),
                        runtime =
                            GitHubTrackedItemsRuntime(
                                context = context,
                                supportedAbis = supportedAbis,
                            ),
                        actions =
                            GitHubTrackedItemsActions(
                                onRefreshTrackedItem = actions.onRefreshTrackedItem,
                                onOpenActionsSheet = actions.onOpenActionsSheet,
                                onOpenTrackSheetForEdit = actions.onOpenTrackSheetForEdit,
                                onRequestDeleteTrackedItem = actions.onRequestDeleteTrackedItem,
                                onCollapseTrackedCard = actions.onCollapseTrackedCard,
                                onLocalVersionExpandedChange = actions.onLocalVersionExpandedChange,
                                onStableVersionExpandedChange = actions.onStableVersionExpandedChange,
                                onPreReleaseVersionExpandedChange = actions.onPreReleaseVersionExpandedChange,
                                onCollapseApkAssetPanel = actions.onCollapseApkAssetPanel,
                                onLoadApkAssets = actions.onLoadApkAssets,
                                onOpenDecisionAssistDetail = actions.onOpenDecisionAssistDetail,
                                onOpenExternalUrl = actions.onOpenExternalUrl,
                                onOpenApkInfo = actions.onOpenApkInfo,
                                onInstallApk = actions.onInstallApk,
                                onOpenApkInDownloader = actions.onOpenApkInDownloader,
                                onShareApkLink = actions.onShareApkLink,
                            ),
                    )
                }

                AppFloatingVerticalSearchActionDock(
                    backdrop = surfaces.contentBackdrop,
                    expanded = controls.searchExpanded,
                    query = controls.trackedSearch,
                    onQueryChange = actions.onTrackedSearchChange,
                    onExpandedChange = actions.onSearchExpandedChange,
                    searchIcon = appLucideSearchIcon(),
                    searchContentDescription = stringResource(R.string.github_topbar_search_label),
                    placeholder = stringResource(R.string.github_topbar_search_label),
                    addIcon = appLucideAddIcon(),
                    addContentDescription = stringResource(R.string.github_cd_add_track),
                    onAddClick = actions.onOpenTrackSheetForAdd,
                    refreshIcon = appLucideRefreshIcon(),
                    refreshContentDescription = stringResource(R.string.github_topbar_cd_check),
                    onRefreshClick = actions.onRefreshVisibleTracked,
                    showAddAction = true,
                    refreshEnabled = !controls.deleteInProgress,
                    refreshStatus = refreshStatus,
                    dockSide = layout.floatingDockSide,
                    keyboardLift = floatingKeyboardLift,
                    modifier =
                        Modifier
                            .align(dockAlignment)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = -searchDockBottomState.value.roundToPx(),
                                )
                            }.padding(
                                start = dockStartPadding,
                                end = dockEndPadding,
                            ),
                )
            }
        }
        AppTopEndActionBarOverlay {
            GitHubTopBarActions(
                backdrop = surfaces.topBarBackdrop,
                liquidActionBarLayeredStyleEnabled = surfaces.liquidActionBarLayeredStyleEnabled,
                sortMode = controls.sortMode,
                sortDirection = controls.sortDirection,
                trackedFilterMode = controls.trackedFilterMode,
                refreshIntervalHours = controls.refreshIntervalHours,
                showActionMenuPopup = controls.showActionMenuPopup,
                tracksExporting = controls.tracksExporting,
                tracksImporting = controls.tracksImporting,
                onOpenStrategySheet = actions.onOpenStrategySheet,
                onOpenCheckLogicSheet = actions.onOpenCheckLogicSheet,
                onShowActionMenuPopupChange = actions.onShowActionMenuPopupChange,
                onSortModeChange = actions.onSortModeChange,
                onSortDirectionChange = actions.onSortDirectionChange,
                onTrackedFilterModeChange = actions.onTrackedFilterModeChange,
                onRefreshIntervalHoursChange = actions.onRefreshIntervalHoursChange,
                onExportTrackedItems = actions.onExportTrackedItems,
                onImportTrackedItems = actions.onImportTrackedItems,
                onOpenStarImport = actions.onOpenStarImport,
                onActionBarInteractingChanged = actions.onActionBarInteractingChanged,
            )
        }
    }
}
