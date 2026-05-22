@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmFavoriteSortMode
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListInput
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogViewModel
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListInput
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListInput

@Composable
internal fun BaGuideCatalogPageBindEffects(
    catalogViewModel: BaGuideCatalogViewModel,
    transitionAnimationsEnabled: Boolean,
    initialFetchDelayMs: Int,
    loadFailedText: String,
    refreshFailedKeepCacheText: String,
) {
    LaunchedEffect(
        catalogViewModel,
        transitionAnimationsEnabled,
        initialFetchDelayMs,
        loadFailedText,
        refreshFailedKeepCacheText,
    ) {
        catalogViewModel.bind(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            initialFetchDelayMs = initialFetchDelayMs,
            loadFailedText = loadFailedText,
            refreshFailedKeepCacheText = refreshFailedKeepCacheText,
        )
    }
}

@Composable
internal fun BaGuideCatalogPageDerivedEffects(
    catalogViewModel: BaGuideCatalogViewModel,
    catalogDataState: BaGuideCatalogDataUiState,
    filterSortState: BaGuideCatalogFilterSortState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    pageState: BaGuideCatalogPageStateHolder,
) {
    val catalogSortMode = filterSortState.sortMode
    val catalogFavoriteEntries = filterSortState.favoriteCatalogEntries
    val catalogSelectedFilterOptions = filterSortState.selectedFilterOptions
    LaunchedEffect(
        catalogViewModel,
        catalogDataState.catalog,
        catalogSortMode,
        catalogFavoriteEntries,
        catalogSelectedFilterOptions,
        pageState.studentSearchQuery,
        pageState.npcSearchQuery,
    ) {
        BaGuideCatalogTab.entries.forEach { tab ->
            catalogViewModel.requestCatalogListDerivedState(
                BaGuideCatalogListInput(
                    catalog = catalogDataState.catalog,
                    tab = tab,
                    sortMode = catalogSortMode,
                    favoriteCatalogEntries = catalogFavoriteEntries,
                    selectedFilterOptions = catalogSelectedFilterOptions,
                    searchQuery = pageState.searchQueries.catalogSearchQueryFor(tab),
                ),
            )
        }
    }
    LaunchedEffect(
        catalogViewModel,
        catalogDataState.catalog,
        favoriteBgms,
        pageState.studentBgmSearchQuery,
    ) {
        catalogViewModel.requestStudentBgmListDerivedState(
            BaGuideStudentBgmListInput(
                catalog = catalogDataState.catalog,
                favorites = favoriteBgms,
                searchQuery = pageState.studentBgmSearchQuery,
            ),
        )
    }
    LaunchedEffect(
        catalogViewModel,
        favoriteBgms,
        pageState.favoriteBgmSearchQuery,
    ) {
        catalogViewModel.requestFavoriteBgmListDerivedState(
            BaGuideFavoriteBgmListInput(
                favorites = favoriteBgms,
                searchQuery = pageState.favoriteBgmSearchQuery,
                sortMode = BaGuideBgmFavoriteSortMode.Recent,
            ),
        )
    }
}
