@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmFavoriteSortMode
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListInput
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListInput
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListInput

@Composable
internal fun BaGuideCatalogPageBindEffects(
    pageActions: BaGuideCatalogPageActions,
    transitionAnimationsEnabled: Boolean,
    initialFetchDelayMs: Int,
    loadFailedText: String,
    refreshFailedKeepCacheText: String,
) {
    LaunchedEffect(
        pageActions,
        transitionAnimationsEnabled,
        initialFetchDelayMs,
        loadFailedText,
        refreshFailedKeepCacheText,
    ) {
        pageActions.bindCatalog(
            transitionAnimationsEnabled,
            initialFetchDelayMs,
            loadFailedText,
            refreshFailedKeepCacheText,
        )
    }
}

@Composable
internal fun BaGuideCatalogPageDerivedEffects(
    pageActions: BaGuideCatalogPageActions,
    catalogDataState: BaGuideCatalogDataUiState,
    filterSortState: BaGuideCatalogFilterSortState,
    catalogFavoriteEntries: Map<Long, Long>,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    pageState: BaGuideCatalogPageStateHolder,
) {
    val catalogSortMode = filterSortState.sortMode
    val catalogSelectedFilterOptions = filterSortState.selectedFilterOptions
    LaunchedEffect(
        pageActions,
        catalogDataState.catalog,
        catalogSortMode,
        catalogFavoriteEntries,
        catalogSelectedFilterOptions,
        pageState.studentSearchQuery,
        pageState.npcSearchQuery,
    ) {
        BaGuideCatalogTab.entries.forEach { tab ->
            pageActions.onRequestCatalogListState(
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
        pageActions,
        catalogDataState.catalog,
        favoriteBgms,
        pageState.studentBgmSearchQuery,
    ) {
        pageActions.onRequestStudentBgmListState(
            BaGuideStudentBgmListInput(
                catalog = catalogDataState.catalog,
                favorites = favoriteBgms,
                searchQuery = pageState.studentBgmSearchQuery,
            ),
        )
    }
    LaunchedEffect(
        pageActions,
        favoriteBgms,
        pageState.favoriteBgmSearchQuery,
    ) {
        pageActions.onRequestFavoriteBgmListState(
            BaGuideFavoriteBgmListInput(
                favorites = favoriteBgms,
                searchQuery = pageState.favoriteBgmSearchQuery,
                sortMode = BaGuideBgmFavoriteSortMode.Recent,
            ),
        )
    }
}

@Composable
internal fun BaGuideCatalogBgmCacheEffects(
    pageActions: BaGuideCatalogPageActions,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    showTransferSheet: Boolean,
) {
    val latestFavoriteBgms = rememberUpdatedState(favoriteBgms)
    val favoriteAudioUrls =
        remember(favoriteBgms) {
            favoriteBgms.map { item -> item.audioUrl }
        }
    LaunchedEffect(pageActions, favoriteAudioUrls) {
        pageActions.onRequestBgmCacheSnapshot(latestFavoriteBgms.value, false)
    }
    LaunchedEffect(pageActions, showTransferSheet, favoriteAudioUrls) {
        if (showTransferSheet) {
            pageActions.onRequestBgmCacheSnapshot(latestFavoriteBgms.value, true)
        }
    }
}
