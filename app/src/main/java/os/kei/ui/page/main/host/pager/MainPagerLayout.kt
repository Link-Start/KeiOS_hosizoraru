package os.kei.ui.page.main.host.pager

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.core.system.ShizukuApiUtils
import os.kei.mcp.server.McpServerManager
import os.kei.ui.navigation.KeiosRoute
import os.kei.ui.navigation.Navigator
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.appGripAwareDockTouchObserver
import os.kei.ui.page.main.widget.glass.rememberAppGripAwareDockState
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainPagerLayout(
    navigator: Navigator,
    settingsReturnToken: Int,
    liquidBottomBarEnabled: Boolean,
    liquidActionBarLayeredStyleEnabled: Boolean,
    gripAwareFloatingDockEnabled: Boolean,
    homeIconHdrEnabled: Boolean,
    homeDynamicFullEffectEnabled: Boolean,
    preloadingEnabled: Boolean,
    nonHomeBackgroundEnabled: Boolean,
    nonHomeBackgroundUri: String,
    nonHomeBackgroundOpacity: Float,
    visibleBottomPageNames: Set<String>,
    onVisibleBottomPageNamesChange: (Set<String>) -> Unit,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    mcpServerManager: McpServerManager,
    onOpenGuideDetail: (String) -> Unit,
    onOpenBaGuideCatalog: () -> Unit,
    requestedBottomPage: String?,
    requestedBottomPageToken: Int,
    requestedGitHubRefreshToken: Int,
    onRequestedBottomPageConsumed: () -> Unit
) {
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val context = LocalContext.current
    val insets = rememberMainPagerInsets()
    val floatingDockState = rememberAppGripAwareDockState(gripAwareFloatingDockEnabled)
    val floatingDockSide = if (gripAwareFloatingDockEnabled) {
        floatingDockState.side
    } else {
        AppFloatingDockSide.End
    }
    val onOpenSettings = remember(navigator) {
        { navigator.pushSingleTop(KeiosRoute.Settings) }
    }
    val onOpenAbout = remember(navigator) {
        { navigator.pushSingleTop(KeiosRoute.About) }
    }
    val openBaGuideCatalog = remember(navigator) {
        { navigator.pushSingleTop(KeiosRoute.BaGuideCatalog) }
    }
    val onOpenMcpSkill = remember(navigator) {
        { navigator.pushSingleTop(KeiosRoute.McpSkill) }
    }
    val coordinator = rememberMainPagerCoordinator(
        settingsReturnToken = settingsReturnToken,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        preloadingEnabled = preloadingEnabled,
        nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
        nonHomeBackgroundUri = nonHomeBackgroundUri,
        visibleBottomPageNames = visibleBottomPageNames,
        onVisibleBottomPageNamesChange = onVisibleBottomPageNamesChange,
        mcpServerManager = mcpServerManager,
        requestedBottomPage = requestedBottomPage,
        requestedBottomPageToken = requestedBottomPageToken,
        onRequestedBottomPageConsumed = onRequestedBottomPageConsumed
    )
    DisposableEffect(
        context,
        homeIconHdrEnabled,
        coordinator.tabs,
        coordinator.pagerState.currentPage,
        coordinator.pagerState.targetPage,
        coordinator.pagerState.settledPage
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onDispose { }
        } else {
            val activity = context as? Activity
            val homeVisibleInPager = listOf(
                coordinator.pagerState.currentPage,
                coordinator.pagerState.targetPage,
                coordinator.pagerState.settledPage
            ).any { pageIndex ->
                coordinator.tabs.getOrElse(pageIndex) { BottomPage.Home } == BottomPage.Home
            }
            runCatching {
                activity?.window?.colorMode = if (homeIconHdrEnabled && homeVisibleInPager) {
                    ActivityInfo.COLOR_MODE_HDR
                } else {
                    ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
            onDispose {
                runCatching {
                    activity?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .appGripAwareDockTouchObserver(
                enabled = gripAwareFloatingDockEnabled,
                onDockSideTouch = floatingDockState::recordTouchSide
            )
            .background(MiuixTheme.colorScheme.background)
            .nestedScroll(coordinator.nestedScrollConnection),
        bottomBar = {
            val safeSelectedPageIndex = coordinator.selectedPageIndex.coerceIn(
                0,
                (coordinator.tabs.size - 1).coerceAtLeast(0)
            )
            val pagerSelectionPosition = when {
                coordinator.pagerState.isScrollInProgress && !coordinator.navigationActive ->
                    coordinator.pagerState.pagePosition.coerceIn(
                    0f,
                    (coordinator.tabs.size - 1).coerceAtLeast(0).toFloat()
                )
                else -> null
            }
            MainPagerBottomBar(
                visible = coordinator.showBottomBar,
                navigationBarBottom = insets.navigationBarBottom,
                tabs = coordinator.tabs,
                selectedPageIndex = safeSelectedPageIndex,
                selectedPagePosition = pagerSelectionPosition,
                backdrop = coordinator.backdrop,
                liquidBottomBarEnabled = liquidBottomBarEnabled,
                onPageSelected = coordinator.onPageSelected
            )
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            MainLoadedPager(
                state = coordinator.pagerState,
                userScrollEnabled = coordinator.pagerScrollEnabled,
                animationsEnabled = transitionAnimationsEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coordinator.farJumpAlpha }
                    .layerBackdrop(coordinator.backdrop)
            ) { pageIndex ->
                val pageType = coordinator.tabs[pageIndex]
                val pageRuntime = coordinator.pagerRuntime.pageRuntime(
                    pageIndex = pageIndex,
                    contentTopPadding = if (pageType == BottomPage.Home) insets.homeTopInset else 0.dp,
                    contentBottomPadding = if (pageType == BottomPage.Home) {
                        insets.homeBottomInset
                    } else {
                        insets.bottomOverlayPadding
                    },
                    bottomBarVisible = coordinator.showBottomBar,
                    floatingDockSide = floatingDockSide,
                    scrollToTopSignal = when (pageType) {
                        BottomPage.Home -> 0
                        BottomPage.Os -> coordinator.osScrollToTopSignal
                        BottomPage.Ba -> coordinator.baScrollToTopSignal
                        BottomPage.Mcp -> coordinator.mcpScrollToTopSignal
                        BottomPage.GitHub -> coordinator.githubScrollToTopSignal
                    }
                )
                key(pageType.name) {
                    MainPagerPageHost(
                        pageType = pageType,
                        runtime = pageRuntime,
                        visibleBottomPages = coordinator.visibleTabsSnapshot,
                        shizukuStatus = shizukuStatus,
                        shizukuApiUtils = shizukuApiUtils,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        homeIconHdrEnabled = homeIconHdrEnabled,
                        homeDynamicFullEffectEnabled = homeDynamicFullEffectEnabled,
                        preloadingEnabled = preloadingEnabled,
                        mcpServerManager = mcpServerManager,
                        homeMcpOverview = coordinator.homeMcpOverview,
                        homeGitHubOverview = coordinator.homeGitHubOverview,
                        homeBaOverview = coordinator.homeBaOverview,
                        visibleOverviewCards = coordinator.visibleOverviewCards,
                        requestedGitHubRefreshToken = requestedGitHubRefreshToken,
                        onBottomPageVisibilityChange = coordinator.onBottomPageVisibilityChange,
                        onOverviewCardVisibilityChange = coordinator.onOverviewCardVisibilityChange,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onOpenPoolGuideDetail = onOpenGuideDetail,
                        onOpenBaGuideCatalog = openBaGuideCatalog,
                        onOpenMcpSkill = onOpenMcpSkill,
                        onActionBarInteractingChanged = coordinator.onActionBarInteractingChanged
                    )
                }
            }

            if (coordinator.pagerRuntime.shouldRenderNonHomeBackground) {
                NonHomePageBackground(
                    enabled = coordinator.hasNonHomeBackground,
                    imageUri = coordinator.effectiveNonHomeBackgroundUri,
                    opacity = nonHomeBackgroundOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun NonHomePageBackground(
    enabled: Boolean,
    imageUri: String,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    if (!enabled || imageUri.isBlank()) return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val (targetWidthPx, targetHeightPx) = remember(configuration, density) {
        with(density) {
            val width = configuration.screenWidthDp.dp.roundToPx().coerceAtLeast(1)
            val height = configuration.screenHeightDp.dp.roundToPx().coerceAtLeast(1)
            width to height
        }
    }
    val request = remember(imageUri, targetWidthPx, targetHeightPx) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .size(targetWidthPx, targetHeightPx)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        alpha = opacity.coerceIn(0f, 1f),
        modifier = modifier
    )
}
