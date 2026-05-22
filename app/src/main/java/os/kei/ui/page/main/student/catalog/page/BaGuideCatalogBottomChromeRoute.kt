@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmDockTab

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
    BaGuideCatalogBottomChromePlaybackSurface(
        accent = accent,
        scrollState = scrollState,
        dockTabs = dockTabs,
        playbackFavorite = playbackFavorite,
        currentTitle = currentTitle,
        artworkImageUrl = artworkImageUrl,
        searchEnabled = searchEnabled,
        pageState = pageState,
        searchQuery = searchQuery,
        searchPlaceholder = searchPlaceholder,
        activeTab = activeTab,
        tabs = tabs,
        pagerState = pagerState,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        searchAutoFocusEnabled = searchAutoFocusEnabled,
        playbackCoordinator = playbackCoordinator,
        backdrop = backdrop,
        modifier = modifier,
    )
}
