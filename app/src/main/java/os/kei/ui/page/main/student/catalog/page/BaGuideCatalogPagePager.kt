@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.R
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogV2ListContent
import os.kei.ui.page.main.student.catalog.component.BaGuideMemoryLobbyTabContent
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmTabContent
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.widget.chrome.LocalAppPageContentMaxWidth
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageContentMaxWidthFor
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideMemoryLobbyListDerivedState
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
    memoryLobbyListDerivedState: BaGuideMemoryLobbyListDerivedState,
    studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    pendingBgmFavoriteUndo: GuideBgmFavoriteItem?,
    favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    pageActions: BaGuideCatalogPageActions,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackUiState: BaGuideBgmPlaybackUiState,
    chromeScrollState: BaGuideBgmBottomChromeScrollState,
    pageChromeBackdrop: LayerBackdrop,
    catalogSceneBackdrop: Backdrop,
    transitionAnimationsEnabled: Boolean,
    mediaAdaptiveRotationEnabled: Boolean,
    accent: Color,
    onOpenGuide: (String) -> Unit,
    onRequestVisibleCatalogImages: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    MainLoadedPager(
        state = pagerState,
        userScrollEnabled = !pageState.sliderInteractionActive,
        animationsEnabled = transitionAnimationsEnabled,
        modifier =
            modifier
                .fillMaxSize()
                .layerBackdrop(pageChromeBackdrop),
    ) { pageIndex ->
        val pageTab = tabs.getOrElse(pageIndex) { BaGuideCatalogPageTab.Student }
        val pageSearchQuery = pageState.searchQueryFor(pageTab)
        val resolvedCatalogTab = pageTab.resolvedCatalogTab(pageState.selectedStudentCatalogTab)
        key(pageTab.name, resolvedCatalogTab) {
            BaGuideCatalogPageTabContent(
                pageTab = pageTab,
                resolvedCatalogTab = resolvedCatalogTab,
                pageIndex = pageIndex,
                pagerState = pagerState,
                pageState = pageState,
                pageSearchQuery = pageSearchQuery,
                filterSortState = filterSortState,
                catalogDataState = catalogDataState,
                catalogListDerivedStates = catalogListDerivedStates,
                catalogFavoriteEntries = catalogFavoriteEntries,
                studentBgmListDerivedState = studentBgmListDerivedState,
                memoryLobbyListDerivedState = memoryLobbyListDerivedState,
                studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
                favoriteBgmListDerivedState = favoriteBgmListDerivedState,
                favoriteBgms = favoriteBgms,
                pendingBgmFavoriteUndo = pendingBgmFavoriteUndo,
                favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
                pageActions = pageActions,
                playbackCoordinator = playbackCoordinator,
                playbackUiState = playbackUiState,
                chromeScrollState = chromeScrollState,
                catalogSceneBackdrop = catalogSceneBackdrop,
                accent = accent,
                mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                onOpenGuide = onOpenGuide,
                onRequestGuideDetailTab = pageActions.onRequestGuideDetailTab,
                onRequestVisibleCatalogImages = onRequestVisibleCatalogImages,
                onSliderInteractionChanged = pageState::updateSliderInteractionActive,
            )
        }
    }
}

@Composable
private fun BaGuideCatalogPageTabContent(
    pageTab: BaGuideCatalogPageTab,
    resolvedCatalogTab: BaGuideCatalogTab?,
    pageIndex: Int,
    pagerState: MainLoadedPagerState,
    pageState: BaGuideCatalogPageStateHolder,
    pageSearchQuery: String,
    filterSortState: BaGuideCatalogFilterSortState,
    catalogDataState: BaGuideCatalogDataUiState,
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    catalogFavoriteEntries: Map<Long, Long>,
    studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    memoryLobbyListDerivedState: BaGuideMemoryLobbyListDerivedState,
    studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    pendingBgmFavoriteUndo: GuideBgmFavoriteItem?,
    favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    pageActions: BaGuideCatalogPageActions,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackUiState: BaGuideBgmPlaybackUiState,
    chromeScrollState: BaGuideBgmBottomChromeScrollState,
    catalogSceneBackdrop: Backdrop,
    accent: Color,
    mediaAdaptiveRotationEnabled: Boolean,
    onOpenGuide: (String) -> Unit,
    onRequestGuideDetailTab: (String, GuideBottomTab) -> Unit,
    onRequestVisibleCatalogImages: (List<String>) -> Unit,
    onSliderInteractionChanged: (Boolean) -> Unit,
) {
    // Published here rather than at the page root because the bottom chrome -- four tabs, a search dock
    // and the now-playing bar -- is a sibling of these and must keep the single-column width: a bar
    // stretched to a two-column page puts its first tab and its last a panel apart.
    CompositionLocalProvider(
        LocalAppPageContentMaxWidth provides appPageContentMaxWidthFor(appPageColumnCount()),
    ) {
    when {
        resolvedCatalogTab != null -> {
            val catalogTab = resolvedCatalogTab
            BaGuideCatalogV2ListContent(
                tab = catalogTab,
                catalogSceneBackdrop = catalogSceneBackdrop,
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
                scrollToTopSignal = pageState.scrollToTopSignal,
                onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                onRequestVisibleImages = onRequestVisibleCatalogImages,
                onOpenGuide = onOpenGuide,
                onToggleFavorite = pageActions.onToggleCatalogFavorite,
            )
        }

        pageTab.specialTab == BaGuideCatalogSpecialTab.MemoryLobby -> {
            BaGuideMemoryLobbyTabContent(
                catalogSyncedAtMs = catalogDataState.catalog.syncedAtMs,
                catalogSceneBackdrop = catalogSceneBackdrop,
                derivedState = memoryLobbyListDerivedState,
                searchQuery = pageSearchQuery,
                loading = catalogDataState.loading,
                error = catalogDataState.error,
                innerPadding =
                    PaddingValues(
                        top = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                        bottom = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                    ),
                nestedScrollConnection = chromeScrollState,
                accent = accent,
                isPageActive = pageIndex == pagerState.settledPage,
                scrollToTopSignal = pageState.scrollToTopSignal,
                mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                onRequestVisibleImages = onRequestVisibleCatalogImages,
                onOpenGuide = onOpenGuide,
                onRequestGuideDetailTab = onRequestGuideDetailTab,
                onToggleFavorite = pageActions.onToggleCatalogFavorite,
            )
        }

        pageTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm -> {
            BaGuideStudentBgmTabContent(
                catalogSyncedAtMs = catalogDataState.catalog.syncedAtMs,
                catalogSceneBackdrop = catalogSceneBackdrop,
                favorites = favoriteBgms,
                derivedState = studentBgmListDerivedState,
                displayedDerivedState = studentBgmDisplayedDerivedState,
                onRequestDisplayedDerivedState = pageActions.onRequestStudentBgmDisplayedState,
                onRequestVisibleImages = onRequestVisibleCatalogImages,
                playbackCoordinator = playbackCoordinator,
                playbackState = playbackUiState,
                nowPlayingVisible = pageState.studentBgmNowPlayingVisible,
                nowPlayingExpanded = pageState.studentBgmNowPlayingExpanded,
                seekPreviewProgress = pageState.studentBgmSeekPreviewProgress,
                sliderInteractionActive = pageState.studentBgmSliderInteractionActive,
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
                onNowPlayingVisibleChange = pageState::updateStudentBgmNowPlayingVisible,
                onNowPlayingExpandedChange = pageState::updateStudentBgmNowPlayingExpanded,
                onSeekPreviewProgressChange = pageState::updateStudentBgmSeekPreviewProgress,
                onStudentBgmSliderInteractionChanged = pageState::updateStudentBgmSliderInteractionActive,
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
                volumeControlVisible = pageState.bgmVolumeControlVisible,
                lastAudibleVolume = pageState.bgmLastAudibleVolume,
                accent = accent,
                bottomBarScrollConnection = chromeScrollState,
                topPadding = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                bottomPadding = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                isPageActive = pageIndex == pagerState.settledPage,
                onSliderInteractionChanged = onSliderInteractionChanged,
                onVolumeControlVisibleChange = pageState::updateBgmVolumeControlVisible,
                onLastAudibleVolumeChange = pageState::updateBgmLastAudibleVolume,
                onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                onRemoveBgmFavorite = pageActions.onRemoveBgmFavorite,
                pendingUndoFavorite = pendingBgmFavoriteUndo,
                onUndoRemoveBgmFavorite = pageActions.onUndoRemoveBgmFavorite,
                onRequestOfflineCache = pageActions.onRequestFavoriteBgmOfflineCache,
                onToggleFavoriteCache = pageActions.onToggleFavoriteBgmOfflineCache,
                onRequestVisibleImages = onRequestVisibleCatalogImages,
                onOpenGuide = onOpenGuide,
                onRequestGuideDetailTab = pageActions.onRequestGuideDetailTab,
            )
        }
    }
    }
}
