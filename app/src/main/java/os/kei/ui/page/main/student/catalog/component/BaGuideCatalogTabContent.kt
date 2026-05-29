@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.page.LocalCatalogActivationCount
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabContentUiState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabListState

@Composable
internal fun BaGuideCatalogTabContent(
    tab: BaGuideCatalogTab,
    filterSortState: BaGuideCatalogFilterSortState,
    derivedState: BaGuideCatalogListDerivedState,
    favoriteCatalogEntries: Map<Long, Long>,
    loading: Boolean,
    error: String?,
    progress: Float,
    progressColor: Color,
    accent: Color,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    isPageActive: Boolean,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onListScrollInProgressChange: (Boolean) -> Unit,
    onOpenGuide: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val effectiveLoading = loading || (derivedState.deriving && derivedState.filteredEntries.isEmpty())
    val activationCount = LocalCatalogActivationCount.current
    val tabListState =
        rememberBaGuideCatalogTabListState(
            tab = tab,
            filteredEntries = derivedState.filteredEntries,
            loading = effectiveLoading,
            isPageActive = isPageActive,
            resetSignal = activationCount,
        )
    val tabContentUiState =
        rememberBaGuideCatalogTabContentUiState(
            tab = tab,
            searchQuery = filterSortState.searchQuery,
            activeFilterCount = derivedState.activeFilterCount,
            loading = effectiveLoading,
            error = error,
            filteredEntriesEmpty = tabListState.filteredEntries.isEmpty(),
        )
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(tabListState.listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        var lastScrollBounds: Pair<Boolean, Boolean>? = null
        var lastScrollInProgress: Boolean? = null
        snapshotFlowManager
            .snapshotFlow {
                Triple(
                    tabListState.listState.canScrollBackward,
                    tabListState.listState.canScrollForward,
                    tabListState.listState.isScrollInProgress,
                )
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward, scrolling) ->
                val scrollBounds = canScrollBackward to canScrollForward
                if (lastScrollBounds != scrollBounds) {
                    lastScrollBounds = scrollBounds
                    onScrollBoundsChange(canScrollBackward, canScrollForward)
                }
                if (lastScrollInProgress != scrolling) {
                    lastScrollInProgress = scrolling
                    onListScrollInProgressChange(scrolling)
                }
            }
    }
    BaGuideCatalogTabListLayout(
        listState = tabListState.listState,
        nestedScrollConnection = nestedScrollConnection,
        innerPadding = innerPadding,
        uiState = tabContentUiState,
        progress = progress,
        progressColor = progressColor,
        accent = accent,
        displayedEntries = tabListState.displayedEntries,
        hasMoreEntries = tabListState.hasMoreEntries,
        favoriteCatalogEntries = favoriteCatalogEntries,
        onOpenGuide = onOpenGuide,
        onToggleFavorite = onToggleFavorite,
    )
}
