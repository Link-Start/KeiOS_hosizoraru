@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabContentUiState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabListState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogTabContent(
    tab: BaGuideCatalogTab,
    filterSortState: BaGuideCatalogFilterSortState,
    derivedState: BaGuideCatalogListDerivedState,
    loading: Boolean,
    error: String?,
    progress: Float,
    progressColor: Color,
    accent: Color,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    isPageActive: Boolean,
    renderHeavyContent: Boolean,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onListScrollInProgressChange: (Boolean) -> Unit,
    onOpenGuide: (String) -> Unit,
) {
    if (!renderHeavyContent) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                        bottom = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
                        start = AppChromeTokens.pageHorizontalPadding,
                        end = AppChromeTokens.pageHorizontalPadding,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(tab.labelRes),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = 13.sp,
            )
        }
        return
    }

    val effectiveLoading = loading || (derivedState.deriving && derivedState.filteredEntries.isEmpty())
    val tabListState =
        rememberBaGuideCatalogTabListState(
            tab = tab,
            filteredEntries = derivedState.filteredEntries,
            loading = effectiveLoading,
            isPageActive = isPageActive,
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
        favoriteCatalogEntries = filterSortState.favoriteCatalogEntries,
        onOpenGuide = onOpenGuide,
        onToggleFavorite = filterSortState::toggleFavorite,
    )
}
