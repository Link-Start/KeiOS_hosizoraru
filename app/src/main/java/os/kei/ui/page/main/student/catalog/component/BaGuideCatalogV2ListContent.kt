package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabContentUiState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabListState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock

@Composable
internal fun BaGuideCatalogV2ListContent(
    tab: BaGuideCatalogTab,
    catalog: BaGuideCatalogBundle,
    filterSortState: BaGuideCatalogFilterSortState,
    searchQuery: String,
    loading: Boolean,
    error: String?,
    accent: Color,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    isPageActive: Boolean,
    onOpenGuide: (String) -> Unit
) {
    val tabListState = rememberBaGuideCatalogTabListState(
        tab = tab,
        catalog = catalog,
        sortMode = filterSortState.sortMode,
        favoriteCatalogEntries = filterSortState.favoriteCatalogEntries,
        searchQuery = searchQuery,
        loading = loading,
        isPageActive = isPageActive
    )
    val uiState = rememberBaGuideCatalogTabContentUiState(
        tab = tab,
        searchQuery = searchQuery,
        loading = loading,
        error = error,
        filteredEntriesEmpty = tabListState.filteredEntries.isEmpty()
    )
    LazyColumn(
        state = tabListState.listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
            start = AppChromeTokens.pageHorizontalPadding,
            end = AppChromeTokens.pageHorizontalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppChromeTokens.pageSectionGap)
    ) {
        if (uiState.showError) {
            item {
                LiquidInfoBlock(
                    backdrop = null,
                    title = uiState.syncStatusTitle,
                    subtitle = uiState.errorText,
                    body = uiState.syncStatusBody,
                    accent = Color(0xFFEF4444)
                )
            }
        }
        if (uiState.showEmpty) {
            item {
                LiquidInfoBlock(
                    backdrop = null,
                    title = uiState.emptyTitle,
                    subtitle = uiState.emptySubtitle,
                    accent = accent
                )
            }
        } else {
            renderBaGuideCatalogEntryListAdapter(
                displayedEntries = tabListState.displayedEntries,
                hasMoreEntries = tabListState.hasMoreEntries,
                favoriteCatalogEntries = filterSortState.favoriteCatalogEntries,
                accent = accent,
                loadingMoreText = uiState.loadingMoreText,
                onOpenGuide = onOpenGuide,
                onToggleFavorite = filterSortState::toggleFavorite
            )
        }
    }
}
