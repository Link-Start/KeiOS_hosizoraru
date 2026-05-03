package os.kei.ui.page.main.github.section

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingLiquidActionButton
import os.kei.ui.page.main.widget.glass.AppFloatingSearchDock
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import com.kyant.backdrop.backdrops.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
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
    reduceEffectsDuringPagerScroll: Boolean,
    reduceEffectsDuringListScroll: Boolean,
    searchExpanded: Boolean,
    trackedSearch: String,
    sortMode: GitHubSortMode,
    showSortPopup: Boolean,
    showFloatingAddButton: Boolean,
    floatingDockSide: AppFloatingDockSide,
    deleteInProgress: Boolean,
    isDark: Boolean,
    overviewRefreshState: OverviewRefreshState,
    refreshProgress: Float,
    lastRefreshMs: Long,
    lookupConfig: GitHubLookupConfig,
    overviewMetrics: GitHubOverviewMetrics,
    cardPressFeedbackEnabled: Boolean,
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
    trackedCardExpanded: SnapshotStateMap<String, Boolean>,
    pendingShareImportTrack: GitHubPendingShareImportTrack?,
    showPendingShareImportCard: Boolean,
    pendingShareImportRepoOverlapCount: Int,
    onTrackedSearchChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onShowSortPopupChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubSortMode) -> Unit,
    onOpenStrategySheet: () -> Unit,
    onOpenCheckLogicSheet: () -> Unit,
    onRefreshAllTracked: () -> Unit,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    onOpenTrackSheetForAdd: () -> Unit,
    onOpenTrackSheetForEdit: (GitHubTrackedApp) -> Unit,
    onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    onClearApkAssetUiState: (String) -> Unit,
    onCollapseApkAssetPanel: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenApkInDownloader: (GitHubReleaseAssetFile) -> Unit,
    onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
    onCancelPendingShareImportTrack: () -> Unit,
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
        label = "github_floating_keyboard_lift"
    )
    val addButtonBottom by animateDpAsState(
        targetValue = searchDockBottom +
            floatingKeyboardLift +
            AppChromeTokens.floatingBottomBarOuterHeight +
            6.dp,
        label = "github_floating_add_bottom"
    )
    val dockAlignment = if (floatingDockSide == AppFloatingDockSide.Start) {
        androidx.compose.ui.Alignment.BottomStart
    } else {
        androidx.compose.ui.Alignment.BottomEnd
    }
    val dockStartPadding = if (floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                GitHubTopBarSection(
                    topBarColor = topBarColor,
                    scrollBehavior = scrollBehavior,
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
                    topExtra = 8.dp,
                    sectionSpacing = CardLayoutRhythm.denseSectionGap
                ) {
                    item {
                        GitHubOverviewCard(
                            backdrop = contentBackdrop,
                            isDark = isDark,
                            lookupConfig = lookupConfig,
                            overviewRefreshState = overviewRefreshState,
                            refreshProgress = refreshProgress,
                            lastRefreshMs = lastRefreshMs,
                            metrics = overviewMetrics,
                            cardPressFeedbackEnabled = cardPressFeedbackEnabled,
                            onRefreshAllTracked = onRefreshAllTracked,
                            onOpenTrackSheetForAdd = onOpenTrackSheetForAdd
                        )
                    }
                    if (showPendingShareImportCard && pendingShareImportTrack != null) {
                        item {
                            GitHubPendingShareImportCard(
                                pending = pendingShareImportTrack,
                                repoOverlapCount = pendingShareImportRepoOverlapCount,
                                onCancel = onCancelPendingShareImportTrack
                            )
                        }
                    }
                    GitHubTrackedItemsSection(
                        trackedItems = trackedItems,
                        filteredTracked = filteredTracked,
                        sortedTracked = sortedTracked,
                        appLastUpdatedAtByTrackId = appLastUpdatedAtByTrackId,
                        checkStates = checkStates,
                        itemRefreshLoading = itemRefreshLoading,
                        contentBackdrop = contentBackdrop,
                        reduceEffectsDuringListScroll = reduceEffectsDuringListScroll,
                        isDark = isDark,
                        apkAssetBundles = apkAssetBundles,
                        apkAssetLoading = apkAssetLoading,
                        apkAssetErrors = apkAssetErrors,
                        apkAssetExpanded = apkAssetExpanded,
                        trackedCardExpanded = trackedCardExpanded,
                        onRefreshTrackedItem = onRefreshTrackedItem,
                        onOpenActionsSheet = onOpenActionsSheet,
                        onOpenTrackSheetForEdit = onOpenTrackSheetForEdit,
                        onRequestDeleteTrackedItem = onRequestDeleteTrackedItem,
                        onClearApkAssetUiState = onClearApkAssetUiState,
                        onCollapseApkAssetPanel = onCollapseApkAssetPanel,
                        onLoadApkAssets = onLoadApkAssets,
                        onOpenExternalUrl = onOpenExternalUrl,
                        onOpenApkInDownloader = onOpenApkInDownloader,
                        onShareApkLink = onShareApkLink,
                        context = context,
                        supportedAbis = supportedAbis
                    )
                }

                AnimatedVisibility(
                    visible = showFloatingAddButton,
                    enter = appFloatingEnter(),
                    exit = appFloatingExit(),
                    modifier = Modifier.align(dockAlignment)
                ) {
                    AppFloatingLiquidActionButton(
                        backdrop = if (reduceEffectsDuringListScroll) null else contentBackdrop,
                        icon = appLucideAddIcon(),
                        contentDescription = stringResource(R.string.github_cd_add_track),
                        onClick = onOpenTrackSheetForAdd,
                        modifier = Modifier.padding(start = dockStartPadding, end = dockEndPadding, bottom = addButtonBottom),
                    )
                }
                AppFloatingSearchDock(
                    backdrop = if (reduceEffectsDuringListScroll) null else contentBackdrop,
                    expanded = searchExpanded,
                    query = trackedSearch,
                    onQueryChange = onTrackedSearchChange,
                    onExpandedChange = onSearchExpandedChange,
                    searchIcon = appLucideSearchIcon(),
                    contentDescription = stringResource(R.string.github_topbar_search_label),
                    placeholder = stringResource(R.string.github_topbar_search_label),
                    dockSide = floatingDockSide,
                    keyboardLift = floatingKeyboardLift,
                    modifier = Modifier
                        .align(dockAlignment)
                        .padding(start = dockStartPadding, end = dockEndPadding, bottom = searchDockBottom)
                )
            }
        }
        AppTopEndActionBarOverlay {
            GitHubTopBarActions(
                backdrop = topBarBackdrop,
                liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                reduceEffectsDuringPagerScroll = reduceEffectsDuringPagerScroll,
                sortMode = sortMode,
                showSortPopup = showSortPopup,
                deleteInProgress = deleteInProgress,
                onOpenStrategySheet = onOpenStrategySheet,
                onOpenCheckLogicSheet = onOpenCheckLogicSheet,
                onShowSortPopupChange = onShowSortPopupChange,
                onSortModeChange = onSortModeChange,
                onRefreshAllTracked = onRefreshAllTracked,
                onActionBarInteractingChanged = onActionBarInteractingChanged
            )
        }
    }
}
