@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import os.kei.ui.page.main.back.BackNavigationSource
import os.kei.ui.page.main.back.KeiOSBackNavigationHandler
import os.kei.ui.page.main.back.LocalBackNavigationRuntimeState
import os.kei.ui.page.main.back.MainBackNavigationAction
import os.kei.ui.page.main.back.resolveMainBackNavigationAction
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.appWindowSizeDp
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.appGripAwareDockTouchObserver
import os.kei.ui.page.main.widget.glass.rememberAppGripAwareDockState
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainPagerLayout(
    rootBackHandlersEnabled: Boolean,
    navigator: Navigator,
    settingsReturnToken: Int,
    liquidBottomBarEnabled: Boolean,
    miuixMainNavigationEnabled: Boolean,
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
    requestedGitHubManagedInstallConfirmToken: Int,
    requestedGitHubActionsTrackId: String?,
    requestedGitHubActionsSheetToken: Int,
    transientExternalLaunchActive: Boolean,
    onRequestedBottomPageConsumed: () -> Unit,
) {
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val backNavigationRuntime = LocalBackNavigationRuntimeState.current
    val context = LocalContext.current
    val insets = rememberMainPagerInsets()
    val floatingDockState = rememberAppGripAwareDockState(gripAwareFloatingDockEnabled)
    val floatingDockSide =
        if (gripAwareFloatingDockEnabled) {
            floatingDockState.side
        } else {
            AppFloatingDockSide.End
        }
    val onOpenSettings =
        remember(navigator) {
            { navigator.pushSingleTop(KeiosRoute.Settings) }
        }
    val onOpenAbout =
        remember(navigator) {
            { navigator.pushSingleTop(KeiosRoute.About) }
        }
    val openBaGuideCatalog =
        remember(navigator) {
            { navigator.pushSingleTop(KeiosRoute.BaGuideCatalog()) }
        }
    val onOpenMcpSkill =
        remember(navigator) {
            { navigator.pushSingleTop(KeiosRoute.McpSkill) }
        }
    val coordinator =
        rememberMainPagerCoordinator(
            settingsReturnToken = settingsReturnToken,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            miuixMainNavigationEnabled = miuixMainNavigationEnabled,
            preloadingEnabled = preloadingEnabled,
            nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
            nonHomeBackgroundUri = nonHomeBackgroundUri,
            visibleBottomPageNames = visibleBottomPageNames,
            onVisibleBottomPageNamesChange = onVisibleBottomPageNamesChange,
            mcpServerManager = mcpServerManager,
            requestedBottomPage = requestedBottomPage,
            requestedBottomPageToken = requestedBottomPageToken,
            onRequestedBottomPageConsumed = onRequestedBottomPageConsumed,
        )
    val onOpenGitHubPage =
        remember(coordinator.tabs, coordinator.onPageSelected) {
            {
                val index = coordinator.tabs.indexOf(BottomPage.GitHub)
                if (index >= 0) {
                    coordinator.onPageSelected(index)
                }
            }
        }
    // Derive the Home-visible boolean OUTSIDE the DisposableEffect. During a swipe
    // currentPage/targetPage/settledPage change every frame, but the boolean only flips
    // when one of them crosses into / out of the Home slot. Keying the effect on the
    // boolean instead of the raw indices avoids running the JNI `Window.setColorMode`
    // call on every frame of every page swipe.
    val homeVisibleInPager =
        remember(
            coordinator.tabs,
            coordinator.pagerState.currentPage,
            coordinator.pagerState.targetPage,
            coordinator.pagerState.settledPage,
        ) {
            val homeIndex = coordinator.tabs.indexOf(BottomPage.Home)
            homeIndex >= 0 &&
                (
                    coordinator.pagerState.currentPage == homeIndex ||
                        coordinator.pagerState.targetPage == homeIndex ||
                        coordinator.pagerState.settledPage == homeIndex
                )
        }
    DisposableEffect(context, homeIconHdrEnabled, homeVisibleInPager) {
        val activity = context as? Activity
        runCatching {
            activity?.window?.colorMode =
                if (homeIconHdrEnabled && homeVisibleInPager) {
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
    val homePageIndex =
        coordinator.tabs
            .indexOf(BottomPage.Home)
            .takeIf { it >= 0 }
            ?: 0
    val mainBackAction =
        resolveMainBackNavigationAction(
            backStackSize = 1,
            targetPageIndex =
                coordinator.pagerState.targetPage.coerceIn(
                    0,
                    (coordinator.tabs.size - 1).coerceAtLeast(0),
                ),
            homePageIndex = homePageIndex,
        )
    KeiOSBackNavigationHandler(
        enabled = rootBackHandlersEnabled && mainBackAction == MainBackNavigationAction.NavigateHome,
        source = BackNavigationSource.MainPager,
    ) {
        coordinator.onPageSelected(homePageIndex)
    }

    AppScaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .appGripAwareDockTouchObserver(
                    enabled = gripAwareFloatingDockEnabled,
                    onDockSideTouch = floatingDockState::recordTouchSide,
                ).background(MiuixTheme.colorScheme.background)
                .nestedScroll(coordinator.nestedScrollConnection),
        bottomBar = {
            val safeSelectedPageIndex =
                coordinator.pagerState.targetPage.coerceIn(
                    0,
                    (coordinator.tabs.size - 1).coerceAtLeast(0),
                )
            if (miuixMainNavigationEnabled) {
                MainMiuixBottomBar(
                    visible = coordinator.showBottomBar,
                    navigationBarBottom = insets.navigationBarBottom,
                    tabs = coordinator.tabs,
                    selectedPageIndex = safeSelectedPageIndex,
                    backdrop = coordinator.backdrop,
                    onPageSelected = coordinator.onPageSelected,
                )
            } else {
                val lastPagePosition = (coordinator.tabs.size - 1).coerceAtLeast(0).toFloat()
                val pagerSelectionPosition =
                    coordinator.pagerState.pagePosition.coerceIn(
                        0f,
                        lastPagePosition,
                    )
                MainPagerBottomBar(
                    visible = coordinator.showBottomBar,
                    navigationBarBottom = insets.navigationBarBottom,
                    tabs = coordinator.tabs,
                    selectedPageIndex = safeSelectedPageIndex,
                    selectedPagePosition = pagerSelectionPosition,
                    backdrop = coordinator.backdrop,
                    liquidBottomBarEnabled = liquidBottomBarEnabled,
                    onPageSelected = coordinator.onPageSelected,
                )
            }
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            val pagerModifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coordinator.farJumpAlpha }
                    .layerBackdrop(coordinator.backdrop)
            val activationState =
                rememberMainPageActivationState(
                    tabs = coordinator.tabs,
                    settledPageIndex = coordinator.pagerState.settledPage,
                )
            val pageContent: @Composable (Int) -> Unit = { pageIndex ->
                val pageType = coordinator.tabs[pageIndex]
                key(pageType.name) {
                    // Memoize per-page MainPageRuntime so we don't allocate a fresh
                    // @Immutable instance on every recomposition. During a swipe with
                    // beyondViewportPageCount = 1, ~3 pages recompose every frame —
                    // without this remember, that's ~3 allocations/frame plus broken
                    // equality skip checks downstream into each page composable.
                    // Inputs cover everything pageRuntime() reads.
                    val contentTopPadding =
                        if (pageType == BottomPage.Home) insets.homeTopInset else 0.dp
                    val contentBottomPadding =
                        if (pageType == BottomPage.Home) {
                            insets.homeBottomInset
                        } else {
                            insets.bottomOverlayPadding
                        }
                    val scrollToTopSignal =
                        when (pageType) {
                            BottomPage.Home -> 0
                            BottomPage.Os -> coordinator.osScrollToTopSignal
                            BottomPage.Ba -> coordinator.baScrollToTopSignal
                            BottomPage.Mcp -> coordinator.mcpScrollToTopSignal
                            BottomPage.GitHub -> coordinator.githubScrollToTopSignal
                        }
                    val hasActivated = activationState.hasActivated(pageType)
                    val contentReady = activationState.contentReady(pageType)
                    val contentWorkAllowed =
                        backNavigationRuntime.contentWorkAllowed &&
                            !transientExternalLaunchActive
                    val pageRuntime =
                        remember(
                            coordinator.pagerRuntime,
                            pageIndex,
                            contentTopPadding,
                            contentBottomPadding,
                            coordinator.showBottomBar,
                            floatingDockSide,
                            scrollToTopSignal,
                            hasActivated,
                            contentReady,
                            contentWorkAllowed,
                        ) {
                            coordinator.pagerRuntime.pageRuntime(
                                pageIndex = pageIndex,
                                contentTopPadding = contentTopPadding,
                                contentBottomPadding = contentBottomPadding,
                                bottomBarVisible = coordinator.showBottomBar,
                                floatingDockSide = floatingDockSide,
                                scrollToTopSignal = scrollToTopSignal,
                                hasActivated = hasActivated,
                                contentReady = contentReady,
                                contentWorkAllowed = contentWorkAllowed,
                            )
                        }
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
                        showCacheFreshnessInCards = coordinator.showCacheFreshnessInCards,
                        requestedGitHubRefreshToken = requestedGitHubRefreshToken,
                        requestedGitHubManagedInstallConfirmToken =
                        requestedGitHubManagedInstallConfirmToken,
                        requestedGitHubActionsTrackId = requestedGitHubActionsTrackId,
                        requestedGitHubActionsSheetToken = requestedGitHubActionsSheetToken,
                        onBottomPageVisibilityChange = coordinator.onBottomPageVisibilityChange,
                        onOverviewCardVisibilityChange = coordinator.onOverviewCardVisibilityChange,
                        onCacheFreshnessVisibilityChange = coordinator.onCacheFreshnessVisibilityChange,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onOpenGitHubPage = onOpenGitHubPage,
                        onOpenPoolGuideDetail = onOpenGuideDetail,
                        onOpenBaGuideCatalog = openBaGuideCatalog,
                        onOpenMcpSkill = onOpenMcpSkill,
                        onActionBarInteractingChanged = coordinator.onActionBarInteractingChanged,
                    )
                }
            }
            when (val pagerState = coordinator.pagerState) {
                is MainMiuixPagerState -> {
                    MainMiuixPager(
                        state = pagerState,
                        userScrollEnabled = coordinator.pagerScrollEnabled,
                        modifier = pagerModifier,
                        pageContent = pageContent,
                    )
                }

                is MainFoundationPagerState -> {
                    MainFoundationPager(
                        state = pagerState,
                        userScrollEnabled = coordinator.pagerScrollEnabled,
                        modifier = pagerModifier,
                        pageContent = pageContent,
                    )
                }

                else -> {
                    error("Unsupported main pager state: ${pagerState::class.java.name}")
                }
            }

            if (coordinator.pagerRuntime.shouldRenderNonHomeBackground) {
                NonHomePageBackground(
                    enabled = coordinator.hasNonHomeBackground,
                    imageUri = coordinator.effectiveNonHomeBackgroundUri,
                    opacity = nonHomeBackgroundOpacity,
                    modifier = Modifier.fillMaxSize(),
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
    modifier: Modifier = Modifier,
) {
    if (!enabled || imageUri.isBlank()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowSize = appWindowSizeDp()
    val (targetWidthPx, targetHeightPx) =
        remember(windowSize, density) {
            with(density) {
                val width =
                    windowSize.width
                        .roundToPx()
                        .coerceAtLeast(1)
                val height =
                    windowSize.height
                        .roundToPx()
                        .coerceAtLeast(1)
                width to height
            }
        }
    val request =
        remember(imageUri, targetWidthPx, targetHeightPx) {
            ImageRequest
                .Builder(context)
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
        modifier = modifier,
    )
}
