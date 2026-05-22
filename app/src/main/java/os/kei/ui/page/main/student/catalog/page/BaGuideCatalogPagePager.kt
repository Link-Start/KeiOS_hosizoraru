@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.host.pager.rememberPagerTargetWarmDataActive
import os.kei.ui.page.main.host.pager.shouldRenderStablePageContent
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogV2ListContent
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmTabContent
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListDerivedState

@Composable
internal fun BaGuideCatalogPagePager(
    pagerState: MainLoadedPagerState,
    tabs: List<BaGuideCatalogPageTab>,
    pageState: BaGuideCatalogPageStateHolder,
    filterSortState: BaGuideCatalogFilterSortState,
    catalogDataState: BaGuideCatalogDataUiState,
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    catalogFavoriteEntries: Map<Long, Long>,
    studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    pageActions: BaGuideCatalogPageActions,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackUiState: BaGuideBgmPlaybackUiState,
    chromeScrollState: BaGuideBgmBottomChromeScrollState,
    pageChromeBackdrop: LayerBackdrop,
    transitionAnimationsEnabled: Boolean,
    includeTargetPageInHeavyRender: Boolean,
    accent: Color,
    onOpenGuide: (String) -> Unit,
) {
    val targetWarmDataActive =
        rememberPagerTargetWarmDataActive(
            pagerState = pagerState,
            activationDistance = CATALOG_PAGER_TARGET_WARM_DATA_DISTANCE,
        ).value
    MainLoadedPager(
        state = pagerState,
        userScrollEnabled = !pageState.sliderInteractionActive,
        animationsEnabled = transitionAnimationsEnabled,
        modifier =
            Modifier
                .fillMaxSize()
                .layerBackdrop(pageChromeBackdrop),
    ) { pageIndex ->
        val pageTab = tabs.getOrElse(pageIndex) { BaGuideCatalogPageTab.Student }
        val renderHeavyContent =
            pagerState.shouldRenderStablePageContent(
                pageIndex = pageIndex,
                includeTargetPageInHeavyRender = includeTargetPageInHeavyRender,
                targetWarmDataActive = targetWarmDataActive,
            )
        val pageSearchQuery = pageState.searchQueryFor(pageTab)
        key(pageTab.name) {
            if (!renderHeavyContent) {
                BaGuideCatalogMusicPlaceholder(
                    label =
                        androidx.compose.ui.res
                            .stringResource(pageTab.labelRes),
                    topPadding = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                    bottomPadding = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                )
            } else {
                BaGuideCatalogPageTabContent(
                    pageTab = pageTab,
                    pageIndex = pageIndex,
                    pagerState = pagerState,
                    pageSearchQuery = pageSearchQuery,
                    filterSortState = filterSortState,
                    catalogDataState = catalogDataState,
                    catalogListDerivedStates = catalogListDerivedStates,
                    catalogFavoriteEntries = catalogFavoriteEntries,
                    studentBgmListDerivedState = studentBgmListDerivedState,
                    studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
                    favoriteBgmListDerivedState = favoriteBgmListDerivedState,
                    favoriteBgms = favoriteBgms,
                    favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
                    pageActions = pageActions,
                    playbackCoordinator = playbackCoordinator,
                    playbackUiState = playbackUiState,
                    chromeScrollState = chromeScrollState,
                    accent = accent,
                    onOpenGuide = onOpenGuide,
                    onSliderInteractionChanged = pageState::updateSliderInteractionActive,
                )
            }
        }
    }
}

@Composable
private fun BaGuideCatalogPageTabContent(
    pageTab: BaGuideCatalogPageTab,
    pageIndex: Int,
    pagerState: MainLoadedPagerState,
    pageSearchQuery: String,
    filterSortState: BaGuideCatalogFilterSortState,
    catalogDataState: BaGuideCatalogDataUiState,
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    catalogFavoriteEntries: Map<Long, Long>,
    studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    pageActions: BaGuideCatalogPageActions,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackUiState: BaGuideBgmPlaybackUiState,
    chromeScrollState: BaGuideBgmBottomChromeScrollState,
    accent: Color,
    onOpenGuide: (String) -> Unit,
    onSliderInteractionChanged: (Boolean) -> Unit,
) {
    when {
        pageTab.catalogTab != null -> {
            val catalogTab = pageTab.catalogTab
            BaGuideCatalogV2ListContent(
                tab = catalogTab,
                filterSortState = filterSortState,
                derivedState = catalogListDerivedStates[catalogTab] ?: BaGuideCatalogListDerivedState.Empty,
                favoriteCatalogEntries = catalogFavoriteEntries,
                searchQuery = pageSearchQuery,
                loading = catalogDataState.loading,
                error = catalogDataState.error,
                accent = accent,
                innerPadding =
                    PaddingValues(
                        top = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                        bottom = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                    ),
                nestedScrollConnection = chromeScrollState,
                isPageActive = pageIndex == pagerState.settledPage,
                onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                onOpenGuide = onOpenGuide,
                onToggleFavorite = pageActions.onToggleCatalogFavorite,
            )
        }

        pageTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm -> {
            BaGuideStudentBgmTabContent(
                catalogSyncedAtMs = catalogDataState.catalog.syncedAtMs,
                favorites = favoriteBgms,
                derivedState = studentBgmListDerivedState,
                displayedDerivedState = studentBgmDisplayedDerivedState,
                onRequestDisplayedDerivedState = pageActions.onRequestStudentBgmDisplayedState,
                playbackCoordinator = playbackCoordinator,
                playbackState = playbackUiState,
                searchQuery = pageSearchQuery,
                loading = catalogDataState.loading,
                innerPadding =
                    PaddingValues(
                        top = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                        bottom = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                    ),
                nestedScrollConnection = chromeScrollState,
                accent = accent,
                isPageActive = pageIndex == pagerState.settledPage,
                onSliderInteractionChanged = onSliderInteractionChanged,
                onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                onListScrollInProgressChange = {},
                onNowPlayingVisibilityChange = {},
                onToggleBgmFavorite = pageActions.onToggleBgmFavorite,
                onRemoveBgmFavorite = pageActions.onRemoveBgmFavoriteWithToast,
                showNowPlayingOverlay = false,
                onOpenGuide = onOpenGuide,
                onRequestGuideDetailTab = pageActions.onRequestGuideDetailTab,
            )
        }

        pageTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm -> {
            BaGuideFavoriteBgmMusicContent(
                catalog = catalogDataState.catalog,
                favorites = favoriteBgms,
                derivedState = favoriteBgmListDerivedState,
                offlineCacheState = favoriteBgmOfflineCacheState,
                playbackCoordinator = playbackCoordinator,
                playbackState = playbackUiState,
                accent = accent,
                bottomBarScrollConnection = chromeScrollState,
                topPadding = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                bottomPadding = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                isPageActive = pageIndex == pagerState.settledPage,
                onSliderInteractionChanged = onSliderInteractionChanged,
                onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                onRemoveBgmFavorite = pageActions.onRemoveBgmFavorite,
                onRequestOfflineCache = pageActions.onRequestFavoriteBgmOfflineCache,
                onToggleFavoriteCache = pageActions.onToggleFavoriteBgmOfflineCache,
                onOpenGuide = onOpenGuide,
                onRequestGuideDetailTab = pageActions.onRequestGuideDetailTab,
            )
        }
    }
}
