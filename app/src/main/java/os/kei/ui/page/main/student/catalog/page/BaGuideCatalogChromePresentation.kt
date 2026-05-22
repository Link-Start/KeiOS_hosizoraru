@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.resolvePlaybackArtworkImageUrl
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState

@Stable
internal data class BaGuideCatalogChromePresentation(
    val activePageIndex: Int,
    val activeTab: BaGuideCatalogPageTab,
    val currentTitle: String,
    val filterDefinitions: List<BaGuideCatalogFilterDefinition>,
    val filterEnabled: Boolean,
    val searchQuery: String,
    val searchPlaceholder: String,
    val playbackFavorite: GuideBgmFavoriteItem?,
    val artworkImageUrl: String,
)

@Composable
internal fun rememberBaGuideCatalogChromePresentation(
    pagerState: MainLoadedPagerState,
    pageState: BaGuideCatalogPageStateHolder,
    tabs: List<BaGuideCatalogPageTab>,
    catalogDataState: BaGuideCatalogDataUiState,
    playbackUiState: BaGuideBgmPlaybackUiState,
): BaGuideCatalogChromePresentation {
    val activePageIndex by remember(pagerState, pageState, tabs) {
        derivedStateOf {
            val rawIndex =
                if (pagerState.isScrollInProgress) {
                    pagerState.targetPage
                } else {
                    pageState.selectedTabIndex
                }
            rawIndex.coerceIn(0, tabs.lastIndex)
        }
    }
    val activeTab = tabs.getOrElse(activePageIndex) { BaGuideCatalogPageTab.Student }
    val currentTitle = stringResource(id = activeTab.labelRes)
    val filterDefinitions =
        remember(catalogDataState.catalog, activeTab) {
            activeTab.catalogTab
                ?.let { tab ->
                    catalogDataState.catalog.filterDefinitions(tab).filter { it.type == 0 }
                }.orEmpty()
        }
    val searchQuery = pageState.searchQueryFor(activeTab)
    val searchPlaceholder = stringResource(activeTab.searchPlaceholderRes)
    val playbackFavorite =
        remember(
            playbackUiState.selectedAudioUrl,
            playbackUiState.queue,
            playbackUiState.favorites,
        ) {
            playbackUiState.selectedFavorite
        }
    val artworkImageUrl =
        remember(playbackFavorite) {
            playbackFavorite
                ?.resolvePlaybackArtworkImageUrl()
                .orEmpty()
        }
    return remember(
        activePageIndex,
        activeTab,
        currentTitle,
        filterDefinitions,
        searchQuery,
        searchPlaceholder,
        playbackFavorite,
        artworkImageUrl,
    ) {
        BaGuideCatalogChromePresentation(
            activePageIndex = activePageIndex,
            activeTab = activeTab,
            currentTitle = currentTitle,
            filterDefinitions = filterDefinitions,
            filterEnabled = filterDefinitions.isNotEmpty(),
            searchQuery = searchQuery,
            searchPlaceholder = searchPlaceholder,
            playbackFavorite = playbackFavorite,
            artworkImageUrl = artworkImageUrl,
        )
    }
}
