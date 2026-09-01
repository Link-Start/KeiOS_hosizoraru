@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabContentUiState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabListState
import os.kei.ui.page.main.student.catalog.state.visibleCatalogEntriesWithFavoriteVisibility
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import androidx.compose.foundation.lazy.LazyListScope
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.AppEdgeStackListTopInset
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogV2ListContent(
    tab: BaGuideCatalogTab,
    catalogSceneBackdrop: Backdrop,
    filterSortState: BaGuideCatalogFilterSortState,
    derivedState: BaGuideCatalogListDerivedState,
    favoriteCatalogEntries: Map<Long, Long>,
    searchQuery: String,
    loading: Boolean,
    error: String?,
    accent: Color,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    isPageActive: Boolean,
    scrollToTopSignal: Int,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onRequestVisibleImages: (List<String>) -> Unit,
    onOpenGuide: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    var favoritesHidden by rememberSaveable(tab) { mutableStateOf(false) }
    val studentFavoriteHeaderVisible = tab == BaGuideCatalogTab.Student
    val visibleFilteredEntries =
        remember(tab, derivedState.filteredEntries, favoriteCatalogEntries, favoritesHidden) {
            if (studentFavoriteHeaderVisible) {
                visibleCatalogEntriesWithFavoriteVisibility(
                    filteredEntries = derivedState.filteredEntries,
                    favoriteCatalogEntries = favoriteCatalogEntries,
                    favoritesHidden = favoritesHidden,
                )
            } else {
                derivedState.filteredEntries
            }
        }
    val favoriteCount =
        remember(derivedState.filteredEntries, favoriteCatalogEntries, studentFavoriteHeaderVisible) {
            if (studentFavoriteHeaderVisible) {
                derivedState.filteredEntries.count { entry -> favoriteCatalogEntries.containsKey(entry.contentId) }
            } else {
                0
            }
        }
    val effectiveLoading = loading || (derivedState.deriving && derivedState.filteredEntries.isEmpty())
    val tabListState =
        rememberBaGuideCatalogTabListState(
            tab = tab,
            filteredEntries = visibleFilteredEntries,
            loading = effectiveLoading,
            isPageActive = isPageActive,
        )
    val uiState =
        rememberBaGuideCatalogTabContentUiState(
            tab = tab,
            searchQuery = searchQuery,
            activeFilterCount = derivedState.activeFilterCount,
            loading = effectiveLoading,
            error = error,
            filteredEntriesEmpty = tabListState.filteredEntries.isEmpty(),
        )
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    // Two lanes on a tablet or an unfolded fold, scrolling independently. Entries alternate between them,
    // so each lane is still the list in order -- see `baGuideCatalogEntryLanes`. A loading or empty state
    // has no entries to split, and a lone status card off to the left of an empty second lane reads as a
    // layout bug, so those states stay on one lane.
    val pageColumnCount = appPageColumnCount()
    val columnCount = if (uiState.showLoading || uiState.showEmpty) 1 else pageColumnCount
    val laneStates = tabListState.laneStates(columnCount)
    val laneEntries =
        remember(tabListState.displayedEntries, columnCount) {
            baGuideCatalogEntryLanes(entries = tabListState.displayedEntries, columnCount = columnCount)
        }
    val requestVisibleImages by rememberUpdatedState(onRequestVisibleImages)
    val consumedScrollToTopSignal = remember(tab) { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > consumedScrollToTopSignal.intValue && isPageActive) {
            consumedScrollToTopSignal.intValue = scrollToTopSignal
            laneStates.forEach { state -> state.animateScrollToItem(0) }
        } else {
            consumedScrollToTopSignal.intValue = scrollToTopSignal
        }
    }
    LaunchedEffect(laneStates, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                // Either lane, because the chrome expands for content that cannot scroll at all, and a
                // page with one short lane beside a long one is not that.
                laneStates.baGuideCatalogAnyLaneCanScrollBackward() to
                    laneStates.baGuideCatalogAnyLaneCanScrollForward()
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                onScrollBoundsChange(canScrollBackward, canScrollForward)
            }
    }
    // The error card sits in the leading lane only, so only that lane's entries start one item down.
    val entryStartIndex = if (uiState.showError) 1 else 0
    val laneEntryStartIndices =
        remember(columnCount, entryStartIndex) {
            List(columnCount) { lane -> if (lane == 0) entryStartIndex else 0 }
        }
    LaunchedEffect(
        laneStates,
        tabListState.displayedEntries,
        columnCount,
        laneEntryStartIndices,
        uiState.showError,
        uiState.showLoading,
        uiState.showEmpty,
        isPageActive,
        snapshotFlowManager,
    ) {
        if (!isPageActive || uiState.showLoading || uiState.showEmpty || tabListState.displayedEntries.isEmpty()) {
            return@LaunchedEffect
        }
        snapshotFlowManager
            .snapshotFlow {
                // Both lanes at once, resolved back to flat entry indices: preloading follows the screen,
                // and with two lanes the screen is both of them at whatever offsets they have drifted to.
                buildBaGuideCatalogVisibleImageRequestUrls(
                    displayedEntries = tabListState.displayedEntries,
                    visibleItemIndices =
                        baGuideCatalogVisibleLaneEntryIndices(
                            laneVisibleItemIndices = laneStates.baGuideCatalogLaneVisibleItemIndices(),
                            laneEntryStartIndices = laneEntryStartIndices,
                            columnCount = columnCount,
                            entryCount = tabListState.displayedEntries.size,
                        ),
                    entryStartIndex = 0,
                )
            }.distinctUntilChanged()
            .collect { imageUrls ->
                if (imageUrls.isNotEmpty()) {
                    requestVisibleImages(imageUrls)
                }
            }
    }
    val entryListGap = rememberBaGuideCatalogEntryListGap()
    val showPinnedFavoritesHeader = studentFavoriteHeaderVisible && !uiState.showLoading
    val listTopPadding =
        if (showPinnedFavoritesHeader) {
            AppEdgeStackListTopInset
        } else {
            innerPadding.calculateTopPadding()
        }
    val edgeStackState = rememberAppEdgeStackState(stackLine = listTopPadding)
    Column(modifier = Modifier.fillMaxSize()) {
    if (showPinnedFavoritesHeader) {
        // The favorites summary is the catalog's status hub: pinned above the list so
        // the entry pile always forms beneath it.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = appPageEdgePaddingStart(),
                        end = appPageEdgePaddingEnd(),
                        top = innerPadding.calculateTopPadding(),
                    ),
        ) {
            BaGuideCatalogFavoriteVisibilityHeader(
                totalCount = derivedState.filteredEntries.size,
                favoriteCount = favoriteCount,
                favoritesHidden = favoritesHidden,
                onToggleFavoritesHidden = {
                    if (favoriteCount > 0) {
                        favoritesHidden = !favoritesHidden
                    }
                },
            )
        }
    }
    CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
    // No PullToRefresh on this tab, so the keep-alive box takes the list's own weight and the list
    // fills it. The headroom lands in `contentPadding.top` rather than a `topExtra`.
    AppEdgeStackKeepAlive(
        state = edgeStackState,
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
    ) {
    val laneContents =
        laneEntries.mapIndexed { lane, entries ->
            val laneContent: LazyListScope.() -> Unit = {
                // Status cards belong to the list, not to a column, so they are emitted once -- in the
                // leading lane -- rather than mirrored into both.
                if (lane == 0 && uiState.showError) {
                    item(
                        key = "ba-guide-catalog-error-${tab.name}",
                        contentType = "ba_guide_catalog_status",
                    ) {
                        LiquidInfoBlock(
                            backdrop = catalogSceneBackdrop,
                            title = uiState.syncStatusTitle,
                            subtitle = uiState.errorText,
                            body = uiState.syncStatusBody,
                            accent = Color(0xFFEF4444),
                        )
                    }
                }
                if (lane == 0 && uiState.showLoading) {
                    item(
                        key = "ba-guide-catalog-loading-${tab.name}",
                        contentType = "ba_guide_catalog_loading",
                    ) {
                        AppAronaLoadingPanel(accent = accent)
                    }
                }
                if (uiState.showEmpty) {
                    if (lane == 0) {
                        item(
                            key = "ba-guide-catalog-empty-${tab.name}",
                            contentType = "ba_guide_catalog_status",
                        ) {
                            LiquidInfoBlock(
                                backdrop = catalogSceneBackdrop,
                                title = uiState.emptyTitle,
                                subtitle = uiState.emptySubtitle,
                                accent = accent,
                            )
                        }
                    }
                } else if (!uiState.showLoading) {
                    renderBaGuideCatalogEntryListAdapter(
                        laneEntries = entries,
                        hasMoreEntries = tabListState.hasMoreEntries,
                        favoriteCatalogEntries = favoriteCatalogEntries,
                        accent = accent,
                        loadingMoreText = uiState.loadingMoreText,
                        laneIndex = lane,
                        onOpenGuide = onOpenGuide,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
            laneContent
        }
    BaGuideCatalogLaneLists(
        laneStates = laneStates,
        startPadding = appPageEdgePaddingStart(),
        endPadding = appPageEdgePaddingEnd(),
        topPadding = appEdgeStackKeepAliveTopPadding(listTopPadding),
        bottomPadding = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
        horizontalGap = entryListGap,
        verticalGap = entryListGap,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        lanes = laneContents,
    )
    }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaGuideCatalogFavoriteVisibilityHeader(
    totalCount: Int,
    favoriteCount: Int,
    favoritesHidden: Boolean,
    onToggleFavoritesHidden: () -> Unit,
) {
    val hasFavorites = favoriteCount > 0
    AppSurfaceCard(
        containerColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.62f),
        borderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.16f),
        showIndication = hasFavorites,
        onClick = if (hasFavorites) onToggleFavoritesHidden else null,
    ) {
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            maxItemsInEachRow = 2,
        ) {
            BaGuideCatalogFavoriteMetricPill(
                label = stringResource(R.string.ba_catalog_student_bgm_metric_students),
                value = totalCount,
                color = Color(0xFF6366F1),
            )
            BaGuideCatalogFavoriteMetricPill(
                label = stringResource(R.string.ba_catalog_student_bgm_metric_favorites),
                value = favoriteCount,
                color =
                    if (favoritesHidden) {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    } else {
                        Color(0xFFEC4899)
                    },
            )
        }
    }
}

@Composable
private fun BaGuideCatalogFavoriteMetricPill(
    label: String,
    value: Int,
    color: Color,
) {
    StatusPill(
        label = "$label ${value.coerceAtLeast(0)}",
        color = color,
        size = AppStatusPillSize.Compact,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
    )
}
