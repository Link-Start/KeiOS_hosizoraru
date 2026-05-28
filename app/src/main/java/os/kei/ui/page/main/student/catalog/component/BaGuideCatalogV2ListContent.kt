@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabContentUiState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabListState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock

@Composable
internal fun BaGuideCatalogV2ListContent(
    tab: BaGuideCatalogTab,
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
    onOpenGuide: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val effectiveLoading = loading || (derivedState.deriving && derivedState.filteredEntries.isEmpty())
    val tabListState =
        rememberBaGuideCatalogTabListState(
            tab = tab,
            filteredEntries = derivedState.filteredEntries,
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
    var hasInitiallyScrolled by remember(tab) { mutableStateOf(false) }
    LaunchedEffect(tabListState.displayedEntries.isNotEmpty(), isPageActive) {
        if (!hasInitiallyScrolled && tabListState.displayedEntries.isNotEmpty() && isPageActive) {
            hasInitiallyScrolled = true
            tabListState.listState.scrollToItem(0)
        }
    }
    val consumedScrollToTopSignal = remember(tab) { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > consumedScrollToTopSignal.intValue && isPageActive) {
            consumedScrollToTopSignal.intValue = scrollToTopSignal
            tabListState.listState.animateScrollToItem(0)
        } else {
            consumedScrollToTopSignal.intValue = scrollToTopSignal
        }
    }
    LaunchedEffect(tabListState.listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                tabListState.listState.canScrollBackward to tabListState.listState.canScrollForward
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                onScrollBoundsChange(canScrollBackward, canScrollForward)
            }
    }
    LazyColumn(
        state = tabListState.listState,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        contentPadding =
            PaddingValues(
                top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                bottom = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
                start = AppChromeTokens.pageHorizontalPadding,
                end = AppChromeTokens.pageHorizontalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(AppChromeTokens.pageSectionGap),
    ) {
        if (uiState.showError) {
            item(
                key = "ba-guide-catalog-error-${tab.name}",
                contentType = "ba_guide_catalog_status",
            ) {
                LiquidInfoBlock(
                    backdrop = null,
                    title = uiState.syncStatusTitle,
                    subtitle = uiState.errorText,
                    body = uiState.syncStatusBody,
                    accent = Color(0xFFEF4444),
                )
            }
        }
        if (uiState.showLoading) {
            item(
                key = "ba-guide-catalog-loading-${tab.name}",
                contentType = "ba_guide_catalog_loading",
            ) {
                AppAronaLoadingPanel(accent = accent)
            }
        }
        if (uiState.showEmpty) {
            item(
                key = "ba-guide-catalog-empty-${tab.name}",
                contentType = "ba_guide_catalog_status",
            ) {
                LiquidInfoBlock(
                    backdrop = null,
                    title = uiState.emptyTitle,
                    subtitle = uiState.emptySubtitle,
                    accent = accent,
                )
            }
        } else if (!uiState.showLoading) {
            renderBaGuideCatalogEntryListAdapter(
                displayedEntries = tabListState.displayedEntries,
                hasMoreEntries = tabListState.hasMoreEntries,
                favoriteCatalogEntries = favoriteCatalogEntries,
                accent = accent,
                loadingMoreText = uiState.loadingMoreText,
                onOpenGuide = onOpenGuide,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}
