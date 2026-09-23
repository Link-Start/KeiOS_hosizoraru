package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.HomeWebDavOverview
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.chrome.AppNavigationPlacement
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.perf.ReportPagerPerformanceState

internal data class MainPagerInsets(
    val navigationBarBottom: Dp,
    val homeTopInset: Dp,
    val homeBottomInset: Dp,
    val bottomOverlayPadding: Dp,
)

internal data class MainPagerCoordinatorState(
    val tabs: List<BottomPage>,
    val visibleTabsSnapshot: Set<BottomPage>,
    val pagerState: MainPagerStateContract,
    val pagerRuntime: MainPagerRuntimeSnapshot,
    val homeAppOverview: HomeAppOverview,
    val homeMcpOverview: HomeMcpOverview,
    val homeGitHubOverview: HomeGitHubOverview,
    val homeWebDavOverview: HomeWebDavOverview,
    val homeBaOverview: HomeBaOverview,
    val homeRuntimeNowMs: Long,
    val visibleOverviewCards: Set<HomeOverviewCard>,
    val showCacheFreshnessInCards: Boolean,
    val showHomeBottomPageEditor: Boolean,
    val pagerScrollEnabled: Boolean,
    val showBottomBar: Boolean,
    val selectedPageIndex: Int,
    val navigationActive: Boolean,
    val nestedScrollConnection: NestedScrollConnection,
    val backdrop: LayerBackdrop,
    val hasNonHomeBackground: Boolean,
    val effectiveNonHomeBackgroundUri: String,
    val onPageSelected: (Int) -> Unit,
    val onBottomPageVisibilityChange: (BottomPage, Boolean) -> Unit,
    val onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    val onCacheFreshnessVisibilityChange: (Boolean) -> Unit,
    val onHomeBottomPageEditorVisibleChange: (Boolean) -> Unit,
    val onShowBottomBar: () -> Unit,
    val onPageScrollBoundsChange: (pageIndex: Int, canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    val osScrollToTopSignal: Int,
    val baScrollToTopSignal: Int,
    val mcpScrollToTopSignal: Int,
    val githubScrollToTopSignal: Int,
)

@Composable
internal fun rememberMainPagerInsets(placement: AppNavigationPlacement): MainPagerInsets {
    val density = LocalDensity.current
    val navigationBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val systemInsets = WindowInsets.safeDrawing.union(WindowInsets.navigationBars).asPaddingValues()
    return remember(navigationBarBottom, systemInsets, placement) {
        MainPagerInsets(
            navigationBarBottom = navigationBarBottom,
            homeTopInset = systemInsets.calculateTopPadding(),
            homeBottomInset = systemInsets.calculateBottomPadding(),
            // 112dp is the room the floating bottom bar needs to hover in. Once the bar has moved to the top
            // row nothing floats down there, and leaving the reservation behind would strand every page above
            // a band of empty panel — the same "phone layout, more of it" failure the whole round is about.
            //
            // No matching top reservation is needed, and that is the point of the iPadOS arrangement: the bar
            // shares the row the title and the actions already occupy, so it costs no vertical space at all.
            bottomOverlayPadding =
                when (placement) {
                    AppNavigationPlacement.Bottom -> 112.dp + navigationBarBottom
                    AppNavigationPlacement.Top, AppNavigationPlacement.Sidebar -> 24.dp + navigationBarBottom
                },
        )
    }
}

@Composable
internal fun rememberMainPagerCoordinator(
    settingsReturnToken: Int,
    transitionAnimationsEnabled: Boolean,
    preloadingEnabled: Boolean,
    nonHomeBackgroundEnabled: Boolean,
    nonHomeBackgroundUri: String,
    visibleBottomPageNames: Set<String>,
    onVisibleBottomPageNamesChange: (Set<String>) -> Unit,
    mcpServerManager: McpServerManager,
    requestedBottomPage: String?,
    requestedBottomPageToken: Int,
    onRequestedBottomPageConsumed: () -> Unit,
): MainPagerCoordinatorState {
    val preloadPolicy =
        remember(preloadingEnabled) {
            UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled)
        }
    val backgroundState =
        rememberMainPagerBackgroundState(
            nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
            nonHomeBackgroundUri = nonHomeBackgroundUri,
        )
    val tabsState =
        rememberMainPagerTabsState(
            visibleBottomPageNames = visibleBottomPageNames,
            requestedBottomPage = requestedBottomPage,
            requestedBottomPageToken = requestedBottomPageToken,
        )
    val tabs = tabsState.tabs
    val visibleTabsSnapshot = tabsState.visibleTabsSnapshot
    val pageKeys = remember(tabs) { tabs.map { page -> page.name } }
    val pagerState: MainPagerStateContract =
        rememberMainLoadedPagerState(
            initialPage = tabsState.initialPageIndex,
            pageCount = tabs.size,
            pageKeys = pageKeys,
        )
    val backdrop =
        rememberMainPagerBackdropLifecycle(
            sceneActive = backgroundState.hasNonHomeBackground,
        )
    val targetWarmDataActive = rememberPagerTargetWarmDataActive(pagerState).value

    val pagerRuntime =
        remember(
            tabs,
            pagerState.currentPage,
            pagerState.targetPage,
            pagerState.settledPage,
            pagerState.isScrollInProgress,
            preloadPolicy,
            backgroundState.hasNonHomeBackground,
            targetWarmDataActive,
        ) {
            buildMainPagerRuntimeSnapshot(
                tabs = tabs,
                currentPageIndex = pagerState.currentPage,
                targetPageIndex = pagerState.targetPage,
                settledPageIndex = pagerState.settledPage,
                isPagerScrollInProgress = pagerState.isScrollInProgress,
                preloadPolicy = preloadPolicy,
                hasNonHomeBackground = backgroundState.hasNonHomeBackground,
                targetWarmDataActive = targetWarmDataActive,
            )
        }
    val homePageIndex = tabs.indexOf(BottomPage.Home).coerceAtLeast(0)
    val homeRuntime =
        pagerRuntime.pageRuntime(
            pageIndex = homePageIndex,
            bottomBarVisible = pagerRuntime.homePageBottomBarPinned,
        )
    val homeOverviewState =
        rememberMainPagerHomeOverviewState(
            mcpServerManager = mcpServerManager,
            settingsReturnToken = settingsReturnToken,
            homeRuntime = homeRuntime,
        )
    val pageScrollBoundsState = remember { MainPagerPageScrollBoundsState() }
    BindMainPagerCoordinatorEffects(
        tabsSize = tabs.size,
        pagerState = pagerState,
    )
    val tabJumpController =
        rememberMainPagerTabJumpController(
            tabs = tabs,
            pagerState = pagerState,
            pagerRuntime = pagerRuntime,
            pageScrollBoundsState = pageScrollBoundsState,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            requestedBottomPage = requestedBottomPage,
            requestedBottomPageToken = requestedBottomPageToken,
            onRequestedBottomPageConsumed = onRequestedBottomPageConsumed,
        )
    val scrollSignalController =
        rememberMainPagerScrollSignalController(
            tabs = tabs,
            pagerRuntime = pagerRuntime,
            onPageSelected = tabJumpController.onPageSelected,
        )
    val onBottomPageVisibilityChange =
        remember(
            visibleBottomPageNames,
            onVisibleBottomPageNamesChange,
            homeOverviewState.onOverviewCardVisibilityChange,
        ) {
            buildMainPagerVisibilityChangeAction(
                visibleBottomPageNames = visibleBottomPageNames,
                onVisibleBottomPageNamesChange = onVisibleBottomPageNamesChange,
                onOverviewCardVisibilityChange = homeOverviewState.onOverviewCardVisibilityChange,
            )
        }

    val pageBackdropProducerCount =
        tabs.indices.count { pageIndex ->
            tabs[pageIndex] != BottomPage.Home && pagerRuntime.isWarmActive(pageIndex)
        }
    val pageFullBackdropEffectCount =
        if (pagerState.isScrollInProgress) {
            0
        } else {
            pageBackdropProducerCount
        }

    ReportPagerPerformanceState(
        scope = "main_pager",
        currentPage = tabs.getOrElse(pagerState.currentPage) { BottomPage.Home }.name,
        targetPage = tabs.getOrElse(pagerState.targetPage) { BottomPage.Home }.name,
        scrolling = pagerState.isScrollInProgress,
        currentPageIndex = pagerState.currentPage,
        targetPageIndex = pagerState.targetPage,
        settledPageIndex = pagerState.settledPage,
        programmaticNavigation = pagerState.isProgrammaticNavigationInProgress,
        navigationActive = tabJumpController.navigationActive,
        pageBackdropProducerCount = pageBackdropProducerCount,
        pageFullBackdropEffectCount = pageFullBackdropEffectCount,
    )

    return remember(
        tabs,
        visibleTabsSnapshot,
        pagerState,
        pagerRuntime,
        homeOverviewState,
        tabJumpController,
        scrollSignalController,
        backdrop,
        tabJumpController.selectedPageIndex,
        tabJumpController.navigationActive,
        backgroundState.hasNonHomeBackground,
        backgroundState.effectiveNonHomeBackgroundUri,
        onBottomPageVisibilityChange,
        pageScrollBoundsState,
    ) {
        MainPagerCoordinatorState(
            tabs = tabs,
            visibleTabsSnapshot = visibleTabsSnapshot,
            pagerState = pagerState,
            pagerRuntime = pagerRuntime,
            homeAppOverview = homeOverviewState.homeAppOverview,
            homeMcpOverview = homeOverviewState.homeMcpOverview,
            homeGitHubOverview = homeOverviewState.homeGitHubOverview,
            homeWebDavOverview = homeOverviewState.homeWebDavOverview,
            homeBaOverview = homeOverviewState.homeBaOverview,
            homeRuntimeNowMs = homeOverviewState.runtimeNowMs,
            visibleOverviewCards = homeOverviewState.visibleOverviewCards,
            showCacheFreshnessInCards = homeOverviewState.showCacheFreshnessInCards,
            showHomeBottomPageEditor = homeOverviewState.showBottomPageEditor,
            pagerScrollEnabled = tabJumpController.pagerScrollEnabled,
            showBottomBar = tabJumpController.showBottomBar,
            selectedPageIndex = tabJumpController.selectedPageIndex,
            navigationActive = tabJumpController.navigationActive,
            nestedScrollConnection = tabJumpController.nestedScrollConnection,
            backdrop = backdrop,
            hasNonHomeBackground = backgroundState.hasNonHomeBackground,
            effectiveNonHomeBackgroundUri = backgroundState.effectiveNonHomeBackgroundUri,
            onPageSelected = scrollSignalController.onPageSelected,
            onBottomPageVisibilityChange = onBottomPageVisibilityChange,
            onOverviewCardVisibilityChange = homeOverviewState.onOverviewCardVisibilityChange,
            onCacheFreshnessVisibilityChange = homeOverviewState.onCacheFreshnessVisibilityChange,
            onHomeBottomPageEditorVisibleChange = homeOverviewState.onBottomPageEditorVisibleChange,
            onShowBottomBar = tabJumpController.onShowBottomBar,
            onPageScrollBoundsChange = pageScrollBoundsState::update,
            osScrollToTopSignal = scrollSignalController.osScrollToTopSignal,
            baScrollToTopSignal = scrollSignalController.baScrollToTopSignal,
            mcpScrollToTopSignal = scrollSignalController.mcpScrollToTopSignal,
            githubScrollToTopSignal = scrollSignalController.githubScrollToTopSignal,
        )
    }
}
