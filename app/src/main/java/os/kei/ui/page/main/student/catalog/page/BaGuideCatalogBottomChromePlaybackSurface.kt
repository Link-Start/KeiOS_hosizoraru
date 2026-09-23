@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmDockTab
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmFloatingBottomChrome
import kotlin.math.abs

@Composable
internal fun BaGuideCatalogBottomChromePlaybackSurface(
    accent: Color,
    scrollState: BaGuideBgmBottomChromeScrollState,
    dockTabs: List<BaGuideBgmDockTab>,
    playbackFavorite: GuideBgmFavoriteItem?,
    currentTitle: String,
    artworkImageUrl: String,
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
    pagerSwitchMotion: BaGuideCatalogPagerSwitchMotion,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val pageScope = rememberCoroutineScope()
    val pageSwitchJobHolder = remember { BaGuideCatalogPageSwitchJobHolder() }
    val playbackRuntimeState by remember(playbackCoordinator) {
        playbackCoordinator.runtimeStateFlow
    }.collectAsStateWithLifecycle(initialValue = playbackCoordinator.runtimeState)
    val selectedDockPositionProvider =
        remember(pagerState, dockTabs) {
            {
                pagerState.pagePosition
                    .takeIf(Float::isFinite)
                    ?.coerceIn(0f, dockTabs.lastIndex.coerceAtLeast(0).toFloat())
            }
        }

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
        playbackProgress = {
            pageState.playbackSliderPreview ?: playbackRuntimeState.progress
        },
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
        selectedDockPositionProvider = selectedDockPositionProvider,
        onSelectedDockKeyChange = { keyName ->
            pageState.closeSearch()
            tabs
                .indexOfFirst { it.name == keyName }
                .takeIf { it >= 0 }
                ?.let { index ->
                    if (index == pagerState.settledPage) {
                        pageState.emitScrollToTop()
                    } else {
                        val distance = abs(index - pagerState.settledPage)
                        pageSwitchJobHolder.job?.cancel()
                        pageSwitchJobHolder.job =
                            pageScope.launch {
                                if (transitionAnimationsEnabled) {
                                    withFrameNanos { }
                                }
                                pageState.updateSelectedTabIndex(index)
                                pagerSwitchMotion.runSwitch(
                                    distance = distance,
                                    animationsEnabled = transitionAnimationsEnabled,
                                ) {
                                    if (transitionAnimationsEnabled && distance <= 1) {
                                        pagerState.animateToPage(
                                            target = index,
                                            animationsEnabled = true,
                                        )
                                    } else if (transitionAnimationsEnabled) {
                                        pagerState.animateToPageViaAdjacent(
                                            target = index,
                                            animationsEnabled = true,
                                        )
                                    } else {
                                        pagerState.scrollToPage(index)
                                    }
                                }
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

private class BaGuideCatalogPageSwitchJobHolder {
    var job: Job? = null
}
