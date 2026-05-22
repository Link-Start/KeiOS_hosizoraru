@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmDockTab
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmFloatingBottomChrome

@Composable
internal fun BaGuideCatalogBottomChromeRoute(
    accent: Color,
    scrollState: BaGuideBgmBottomChromeScrollState,
    dockTabs: List<BaGuideBgmDockTab>,
    playbackFavorite: GuideBgmFavoriteItem?,
    currentTitle: String,
    artworkImageUrl: String,
    playbackUiState: BaGuideBgmPlaybackUiState,
    searchEnabled: Boolean,
    pageState: BaGuideCatalogPageStateHolder,
    searchQuery: String,
    searchPlaceholder: String,
    activeTab: BaGuideCatalogPageTab,
    tabs: List<BaGuideCatalogPageTab>,
    pagerState: MainLoadedPagerState,
    transitionAnimationsEnabled: Boolean,
    searchAutoFocusEnabled: Boolean,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val pageScope = rememberCoroutineScope()
    val playbackRuntimeState by playbackCoordinator.runtimeStateFlow.collectAsStateWithLifecycle()
    val latestPlaybackProgress =
        rememberUpdatedState(pageState.playbackSliderPreview ?: playbackRuntimeState.progress)
    BaGuideBgmFloatingBottomChrome(
        accent = accent,
        scrollState = scrollState,
        dockTabs = dockTabs,
        currentTrackTitle =
            playbackFavorite
                ?.studentTitle
                ?.ifBlank { currentTitle }
                ?: currentTitle,
        artworkImageUrl = artworkImageUrl,
        isPlaying = playbackRuntimeState.isPlaying,
        playbackProgress = { latestPlaybackProgress.value },
        onPlaybackProgressChange = pageState::updatePlaybackSliderPreview,
        onPlaybackProgressChangeFinished = { progress ->
            val favorite = playbackFavorite ?: return@BaGuideBgmFloatingBottomChrome
            pageState.updatePlaybackSliderPreview(null)
            playbackCoordinator.seek(favorite, progress)
        },
        onPlaybackSliderInteractionChanged = pageState::updateSliderInteractionActive,
        onPlayPauseClick = {
            val favorite = playbackFavorite ?: return@BaGuideBgmFloatingBottomChrome
            playbackCoordinator.toggle(favorite)
        },
        onPreviousClick = {
            playbackCoordinator.selectOffset(offset = -1)
        },
        onNextClick = {
            playbackCoordinator.selectOffset(offset = 1)
        },
        searchVisible = searchEnabled && pageState.searchVisible,
        searchInputActive = searchEnabled && pageState.searchInputActive,
        searchQuery = searchQuery,
        searchPlaceholder = searchPlaceholder,
        onSearchQueryChange = { query ->
            pageState.updateSearchQuery(activeTab, query)
        },
        onSearchInputActiveChange = pageState::updateSearchInputActive,
        selectedDockKey = activeTab.name,
        onSelectedDockKeyChange = { keyName ->
            pageState.closeSearch()
            tabs
                .indexOfFirst { it.name == keyName }
                .takeIf { it >= 0 }
                ?.let { index ->
                    pageState.updateSelectedTabIndex(index)
                    pageScope.launch {
                        if (transitionAnimationsEnabled) {
                            pagerState.animateToPage(
                                target = index,
                                animationsEnabled = true,
                                durationMillis =
                                    catalogPagerSwitchDurationMillis(
                                        kotlin.math.abs(index - pagerState.settledPage),
                                    ),
                            )
                        } else {
                            pagerState.scrollToPage(index)
                        }
                    }
                }
        },
        onCompactDockClick = {
            pageState.closeSearch()
            scrollState.expand()
        },
        onSearchClick = {
            if (searchEnabled) {
                pageState.openSearch(searchAutoFocusEnabled)
                scrollState.expand()
            }
        },
        backdrop = backdrop,
        modifier = modifier,
    )
}
