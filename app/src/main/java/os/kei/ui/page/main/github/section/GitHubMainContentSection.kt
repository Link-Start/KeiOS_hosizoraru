package os.kei.ui.page.main.github.section

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingRefreshStatus
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalSearchActionDock
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun GitHubMainContent(
    contentBottomPadding: Dp,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    addButtonScrollConnection: NestedScrollConnection,
    topBarBackdrop: LayerBackdrop,
    contentBackdrop: LayerBackdrop,
    topBarColor: Color,
    bottomBarVisible: Boolean,
    liquidActionBarLayeredStyleEnabled: Boolean,
    searchExpanded: Boolean,
    trackedSearch: String,
    sortMode: GitHubSortMode,
    refreshIntervalHours: Int,
    showFailedOnly: Boolean,
    showActionMenuPopup: Boolean,
    floatingDockSide: AppFloatingDockSide,
    deleteInProgress: Boolean,
    isDark: Boolean,
    overviewRefreshState: OverviewRefreshState,
    overviewExpanded: Boolean,
    refreshProgress: Float,
    lastRefreshMs: Long,
    lookupConfig: GitHubLookupConfig,
    overviewVisibleEntries: Set<GitHubOverviewEntry>,
    overviewMetrics: GitHubOverviewMetrics,
    trackedItems: List<GitHubTrackedApp>,
    filteredTracked: List<GitHubTrackedApp>,
    sortedTracked: List<GitHubTrackedApp>,
    appLastUpdatedAtByTrackId: Map<String, Long>,
    checkStates: SnapshotStateMap<String, VersionCheckUi>,
    itemRefreshLoading: SnapshotStateMap<String, Boolean>,
    apkAssetBundles: SnapshotStateMap<String, GitHubReleaseAssetBundle>,
    apkAssetLoading: SnapshotStateMap<String, Boolean>,
    apkAssetErrors: SnapshotStateMap<String, String>,
    apkAssetExpanded: SnapshotStateMap<String, Boolean>,
    actionsRecommendedRunSnapshots: SnapshotStateMap<String, GitHubActionsRecommendedRunSnapshot>,
    trackedCardExpanded: SnapshotStateMap<String, Boolean>,
    trackedLocalVersionExpanded: SnapshotStateMap<String, Boolean>,
    trackedStableVersionExpanded: SnapshotStateMap<String, Boolean>,
    trackedPreReleaseVersionExpanded: SnapshotStateMap<String, Boolean>,
    pendingShareImportPreview: GitHubShareImportPreview?,
    pendingShareImportTrack: GitHubPendingShareImportTrack?,
    pendingShareImportAttachCandidate: GitHubPendingShareImportAttachCandidate?,
    pendingShareImportResult: GitHubShareImportResult?,
    showPendingShareImportCard: Boolean,
    pendingShareImportRepoOverlapCount: Int,
    onTrackedSearchChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onShowActionMenuPopupChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubSortMode) -> Unit,
    onRefreshIntervalHoursChange: (Int) -> Unit,
    onOpenStrategySheet: () -> Unit,
    onOpenCheckLogicSheet: () -> Unit,
    onOverviewExpandedChange: (Boolean) -> Unit,
    onLocalVersionExpandedChange: (String, Boolean) -> Unit,
    onStableVersionExpandedChange: (String, Boolean) -> Unit,
    onPreReleaseVersionExpandedChange: (String, Boolean) -> Unit,
    onOpenOverviewEntrySheet: () -> Unit,
    onRefreshAllTracked: () -> Unit,
    onRetryFailedTracked: () -> Unit,
    onShowFailedOnlyChange: (Boolean) -> Unit,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenTrackSheetForAdd: () -> Unit,
    onOpenTrackSheetForEdit: (GitHubTrackedApp) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    onCollapseTrackedCard: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onCollapseApkAssetPanel: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean) -> Unit,
    onOpenDecisionAssistDetail: (GitHubDecisionAssistDetailType, GitHubTrackedApp) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenApkInfo: (GitHubReleaseAssetFile) -> Unit,
    onOpenApkInDownloader: (GitHubReleaseAssetFile) -> Unit,
    onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
    onOpenShareImportFlow: () -> Unit,
    onOpenShareImportResult: () -> Unit,
    onCancelActiveShareImportFlow: () -> Unit,
    onCancelPendingShareImportTrack: () -> Unit,
    onDismissShareImportResult: () -> Unit,
    onActionBarInteractingChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
    val bottomBarOffset = if (bottomBarVisible) 0.dp else AppChromeTokens.floatingBottomBarOuterHeight
    val searchDockBottom by animateDpAsState(
        targetValue = contentBottomPadding - 24.dp - bottomBarOffset,
        label = "github_floating_search_bottom"
    )
    val floatingKeyboardLift = rememberAppFloatingKeyboardLift(
        restingBottomGap = searchDockBottom,
        label = "github_floating_keyboard_lift"
    )
    val dockAlignment = if (floatingDockSide == AppFloatingDockSide.Start) {
        androidx.compose.ui.Alignment.BottomStart
    } else {
        androidx.compose.ui.Alignment.BottomEnd
    }
    val dockStartPadding = if (floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val refreshStatus = when (overviewRefreshState) {
        OverviewRefreshState.Refreshing -> AppFloatingRefreshStatus.Refreshing
        OverviewRefreshState.Completed -> AppFloatingRefreshStatus.Success
        OverviewRefreshState.Failed -> AppFloatingRefreshStatus.Danger
        OverviewRefreshState.Cached -> AppFloatingRefreshStatus.Cached
        OverviewRefreshState.Idle -> AppFloatingRefreshStatus.Idle
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                GitHubTopBarSection(
                    topBarColor = topBarColor,
                    scrollBehavior = scrollBehavior,
                    titleBackdrop = topBarBackdrop,
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(addButtonScrollConnection)
            ) {
                AppPageLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    innerPadding = innerPadding,
                    bottomExtra = appPageBottomPaddingWithFloatingOverlay(contentBottomPadding),
                    sectionSpacing = CardLayoutRhythm.denseSectionGap
                ) {
                    item(
                        key = "github_overview_card",
                        contentType = "github_overview"
                    ) {
                        GitHubOverviewCard(
                            backdrop = contentBackdrop,
                            isDark = isDark,
                            lookupConfig = lookupConfig,
                            overviewRefreshState = overviewRefreshState,
                            expanded = overviewExpanded,
                            onExpandedChange = onOverviewExpandedChange,
                            refreshProgress = refreshProgress,
                            lastRefreshMs = lastRefreshMs,
                            visibleEntries = overviewVisibleEntries,
                            metrics = overviewMetrics,
                            showFailedOnly = showFailedOnly,
                            onEditVisibleEntries = onOpenOverviewEntrySheet,
                            onRetryFailedTracked = onRetryFailedTracked,
                            onShowFailedOnlyChange = onShowFailedOnlyChange
                        )
                    }
                    if (showPendingShareImportCard && pendingShareImportTrack != null) {
                        item(
                            key = "github_pending_share_import_track",
                            contentType = "github_share_import"
                        ) {
                            GitHubPendingShareImportCard(
                                pending = pendingShareImportTrack,
                                repoOverlapCount = pendingShareImportRepoOverlapCount,
                                onOpen = onOpenShareImportFlow,
                                onCancel = onCancelPendingShareImportTrack
                            )
                        }
                    }
                    pendingShareImportAttachCandidate?.let { candidate ->
                        item(
                            key = "github_pending_share_import_attach",
                            contentType = "github_share_import"
                        ) {
                            GitHubShareImportAttachCandidateCard(
                                candidate = candidate,
                                onOpen = onOpenShareImportFlow,
                                onCancel = onCancelActiveShareImportFlow
                            )
                        }
                    }
                    if (pendingShareImportTrack == null && pendingShareImportAttachCandidate == null) {
                        pendingShareImportPreview?.let { preview ->
                            item(
                                key = "github_share_import_preview",
                                contentType = "github_share_import"
                            ) {
                                GitHubShareImportPreviewCard(
                                    preview = preview,
                                    onOpen = onOpenShareImportFlow,
                                    onCancel = onCancelActiveShareImportFlow
                                )
                            }
                        }
                    }
                    if (
                        pendingShareImportPreview == null &&
                        pendingShareImportTrack == null &&
                        pendingShareImportAttachCandidate == null
                    ) {
                        pendingShareImportResult?.let { result ->
                            item(
                                key = "github_share_import_result",
                                contentType = "github_share_import"
                            ) {
                                GitHubShareImportResultCard(
                                    result = result,
                                    onOpen = onOpenShareImportResult,
                                    onDismiss = onDismissShareImportResult
                                )
                            }
                        }
                    }
                    GitHubTrackedItemsSection(
                        lookupConfig = lookupConfig,
                        trackedItems = trackedItems,
                        filteredTracked = filteredTracked,
                        sortedTracked = sortedTracked,
                        appLastUpdatedAtByTrackId = appLastUpdatedAtByTrackId,
                        checkStates = checkStates,
                        itemRefreshLoading = itemRefreshLoading,
                        contentBackdrop = contentBackdrop,
                        isDark = isDark,
                        apkAssetBundles = apkAssetBundles,
                        apkAssetLoading = apkAssetLoading,
                        apkAssetErrors = apkAssetErrors,
                        apkAssetExpanded = apkAssetExpanded,
                        actionsRecommendedRunSnapshots = actionsRecommendedRunSnapshots,
                        trackedCardExpanded = trackedCardExpanded,
                        trackedLocalVersionExpanded = trackedLocalVersionExpanded,
                        trackedStableVersionExpanded = trackedStableVersionExpanded,
                        trackedPreReleaseVersionExpanded = trackedPreReleaseVersionExpanded,
                        onRefreshTrackedItem = onRefreshTrackedItem,
                        onOpenActionsSheet = onOpenActionsSheet,
                        onOpenTrackSheetForEdit = onOpenTrackSheetForEdit,
                        onRequestDeleteTrackedItem = onRequestDeleteTrackedItem,
                        onCollapseTrackedCard = onCollapseTrackedCard,
                        onLocalVersionExpandedChange = onLocalVersionExpandedChange,
                        onStableVersionExpandedChange = onStableVersionExpandedChange,
                        onPreReleaseVersionExpandedChange = onPreReleaseVersionExpandedChange,
                        onCollapseApkAssetPanel = onCollapseApkAssetPanel,
                        onLoadApkAssets = onLoadApkAssets,
                        onOpenDecisionAssistDetail = onOpenDecisionAssistDetail,
                        onOpenExternalUrl = onOpenExternalUrl,
                        onOpenApkInfo = onOpenApkInfo,
                        onOpenApkInDownloader = onOpenApkInDownloader,
                        onShareApkLink = onShareApkLink,
                        context = context,
                        supportedAbis = supportedAbis
                    )
                }

                AppFloatingVerticalSearchActionDock(
                    backdrop = contentBackdrop,
                    expanded = searchExpanded,
                    query = trackedSearch,
                    onQueryChange = onTrackedSearchChange,
                    onExpandedChange = onSearchExpandedChange,
                    searchIcon = appLucideSearchIcon(),
                    searchContentDescription = stringResource(R.string.github_topbar_search_label),
                    placeholder = stringResource(R.string.github_topbar_search_label),
                    addIcon = appLucideAddIcon(),
                    addContentDescription = stringResource(R.string.github_cd_add_track),
                    onAddClick = onOpenTrackSheetForAdd,
                    refreshIcon = appLucideRefreshIcon(),
                    refreshContentDescription = stringResource(R.string.github_topbar_cd_check),
                    onRefreshClick = onRefreshAllTracked,
                    showAddAction = true,
                    refreshEnabled = !deleteInProgress,
                    refreshStatus = refreshStatus,
                    dockSide = floatingDockSide,
                    keyboardLift = floatingKeyboardLift,
                    modifier = Modifier
                        .align(dockAlignment)
                        .padding(
                            start = dockStartPadding,
                            end = dockEndPadding,
                            bottom = searchDockBottom
                        )
                )
            }
        }
        AppTopEndActionBarOverlay {
            GitHubTopBarActions(
                backdrop = topBarBackdrop,
                liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                sortMode = sortMode,
                refreshIntervalHours = refreshIntervalHours,
                showActionMenuPopup = showActionMenuPopup,
                onOpenStrategySheet = onOpenStrategySheet,
                onOpenCheckLogicSheet = onOpenCheckLogicSheet,
                onShowActionMenuPopupChange = onShowActionMenuPopupChange,
                onSortModeChange = onSortModeChange,
                onRefreshIntervalHoursChange = onRefreshIntervalHoursChange,
                onActionBarInteractingChanged = onActionBarInteractingChanged
            )
        }
    }
}
