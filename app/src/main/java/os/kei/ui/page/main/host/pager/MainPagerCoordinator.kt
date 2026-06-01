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
    val mainPagerBeyondViewportPageCount: Int,
    val homeAppOverview: HomeAppOverview,
    val homeMcpOverview: HomeMcpOverview,
    val homeGitHubOverview: HomeGitHubOverview,
    val homeWebDavOverview: HomeWebDavOverview,
    val homeBaOverview: HomeBaOverview,
    val homeRuntimeNowMs: Long,
    val visibleOverviewCards: Set<HomeOverviewCard>,
    val showCacheFreshnessInCards: Boolean,
    val homeActionBarSelectedIndex: Int,
    val showHomeBottomPageEditor: Boolean,
    val pagerScrollEnabled: Boolean,
    val showBottomBar: Boolean,
    val selectedPageIndex: Int,
    val navigationActive: Boolean,
    val nestedScrollConnection: NestedScrollConnection,
    val backdrop: LayerBackdrop,
    val farJumpAlpha: Float,
    val hasNonHomeBackground: Boolean,
    val effectiveNonHomeBackgroundUri: String,
    val onPageSelected: (Int) -> Unit,
    val onActionBarInteractingChanged: (Boolean) -> Unit,
    val onBottomPageVisibilityChange: (BottomPage, Boolean) -> Unit,
    val onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    val onCacheFreshnessVisibilityChange: (Boolean) -> Unit,
    val onHomeActionBarSelectedIndexChange: (Int) -> Unit,
    val onHomeBottomPageEditorVisibleChange: (Boolean) -> Unit,
    val onShowBottomBar: () -> Unit,
    val onPageScrollBoundsChange: (pageIndex: Int, canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    val osScrollToTopSignal: Int,
    val baScrollToTopSignal: Int,
    val mcpScrollToTopSignal: Int,
    val githubScrollToTopSignal: Int,
)

@Composable
internal fun rememberMainPagerInsets(): MainPagerInsets {
    val density = LocalDensity.current
    val navigationBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val systemInsets = WindowInsets.safeDrawing.union(WindowInsets.navigationBars).asPaddingValues()
    return remember(navigationBarBottom, systemInsets) {
        MainPagerInsets(
            navigationBarBottom = navigationBarBottom,
            homeTopInset = systemInsets.calculateTopPadding(),
            homeBottomInset = systemInsets.calculateBottomPadding(),
            bottomOverlayPadding = 112.dp + navigationBarBottom,
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
    val backdrop = rememberMainPagerBackdropLifecycle()
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

    ReportPagerPerformanceState(
        scope = "main_pager",
        currentPage = tabs.getOrElse(pagerState.currentPage) { BottomPage.Home }.name,
        targetPage = tabs.getOrElse(pagerState.targetPage) { BottomPage.Home }.name,
        scrolling = pagerState.isScrollInProgress,
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
        tabJumpController.farJumpAlpha,
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
            mainPagerBeyondViewportPageCount = preloadPolicy.mainPagerBeyondViewportPageCount,
            homeAppOverview = homeOverviewState.homeAppOverview,
            homeMcpOverview = homeOverviewState.homeMcpOverview,
            homeGitHubOverview = homeOverviewState.homeGitHubOverview,
            homeWebDavOverview = homeOverviewState.homeWebDavOverview,
            homeBaOverview = homeOverviewState.homeBaOverview,
            homeRuntimeNowMs = homeOverviewState.runtimeNowMs,
            visibleOverviewCards = homeOverviewState.visibleOverviewCards,
            showCacheFreshnessInCards = homeOverviewState.showCacheFreshnessInCards,
            homeActionBarSelectedIndex = homeOverviewState.actionBarSelectedIndex,
            showHomeBottomPageEditor = homeOverviewState.showBottomPageEditor,
            pagerScrollEnabled = tabJumpController.pagerScrollEnabled,
            showBottomBar = tabJumpController.showBottomBar,
            selectedPageIndex = tabJumpController.selectedPageIndex,
            navigationActive = tabJumpController.navigationActive,
            nestedScrollConnection = tabJumpController.nestedScrollConnection,
            backdrop = backdrop,
            farJumpAlpha = tabJumpController.farJumpAlpha,
            hasNonHomeBackground = backgroundState.hasNonHomeBackground,
            effectiveNonHomeBackgroundUri = backgroundState.effectiveNonHomeBackgroundUri,
            onPageSelected = scrollSignalController.onPageSelected,
            onActionBarInteractingChanged = tabJumpController.onActionBarInteractingChanged,
            onBottomPageVisibilityChange = onBottomPageVisibilityChange,
            onOverviewCardVisibilityChange = homeOverviewState.onOverviewCardVisibilityChange,
            onCacheFreshnessVisibilityChange = homeOverviewState.onCacheFreshnessVisibilityChange,
            onHomeActionBarSelectedIndexChange = homeOverviewState.onActionBarSelectedIndexChange,
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
