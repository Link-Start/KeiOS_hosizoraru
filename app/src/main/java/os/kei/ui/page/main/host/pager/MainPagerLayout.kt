@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
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
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.mcp.server.McpServerManager
import os.kei.ui.navigation.KeiosRoute
import os.kei.ui.navigation.Navigator
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
    requestedGitHubActionsTrackId: String?,
    requestedGitHubActionsSheetToken: Int,
    requestedBaAccountId: String?,
    requestedBaAccountToken: Int,
    transientExternalLaunchActive: Boolean,
    onRequestedBottomPageConsumed: () -> Unit,
) {
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val backNavigationRuntime = LocalBackNavigationRuntimeState.current
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
    val onOpenGitHubActionsNotificationHistory =
        remember(navigator) {
            { navigator.pushSingleTop(KeiosRoute.GitHubActionsNotificationHistory) }
        }
    val onOpenWebDavSync =
        remember(navigator) {
            { navigator.pushSingleTop(KeiosRoute.WebDavSync) }
        }
    val coordinator =
        rememberMainPagerCoordinator(
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
            onRequestedBottomPageConsumed = onRequestedBottomPageConsumed,
        )
    // Keep HDR scoped to the settled, idle Home page so the Window color mode and
    // visual sweep follow one stable runtime gate.
    val homeIndex =
        remember(coordinator.tabs) {
            coordinator.tabs.indexOf(BottomPage.Home)
        }
    val homeHdrEffectActive =
        shouldActivateHomeHdrEffect(
            homeIconHdrEnabled = homeIconHdrEnabled,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            mainRouteActive = rootBackHandlersEnabled,
            homeIndex = homeIndex,
            settledPage = coordinator.pagerState.settledPage,
            pagerScrollInProgress = coordinator.pagerState.isScrollInProgress,
            navigationActive = coordinator.navigationActive,
        )
    HomeHdrWindowModeEffect(active = homeHdrEffectActive)
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
    val mainPagerBackGestureState =
        rememberMainPagerHomeBackGestureState(
            enabled = rootBackHandlersEnabled && mainBackAction == MainBackNavigationAction.NavigateHome,
            selectedPageIndex = coordinator.selectedPageIndex,
            homePageIndex = homePageIndex,
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
                )
                .background(MiuixTheme.colorScheme.background)
                .nestedScroll(coordinator.nestedScrollConnection),
        bottomBar = {
            val safeSelectedPageIndex =
                coordinator.pagerState.targetPage.coerceIn(
                    0,
                    (coordinator.tabs.size - 1).coerceAtLeast(0),
                )
            val lastPagePosition = (coordinator.tabs.size - 1).coerceAtLeast(0).toFloat()
            val selectedPagePositionProvider =
                remember(coordinator.pagerState, mainPagerBackGestureState, lastPagePosition) {
                    {
                        if (mainPagerBackGestureState.inProgress) {
                            mainPagerBackGestureState.selectedPagePosition()
                        } else {
                            coordinator.pagerState.pagePosition
                        }.coerceIn(0f, lastPagePosition)
                    }
                }
            MainPagerBottomBar(
                visible = coordinator.showBottomBar,
                navigationBarBottom = insets.navigationBarBottom,
                tabs = coordinator.tabs,
                selectedPageIndex = safeSelectedPageIndex,
                selectedPagePosition = null,
                selectedPagePositionProvider = selectedPagePositionProvider,
                backdrop = coordinator.backdrop,
                onPageSelected = coordinator.onPageSelected,
                onExpand = coordinator.onShowBottomBar,
            )
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (coordinator.pagerRuntime.shouldRenderNonHomeBackground) {
                NonHomePageBackground(
                    enabled = coordinator.hasNonHomeBackground,
                    imageUri = coordinator.effectiveNonHomeBackgroundUri,
                    opacity = nonHomeBackgroundOpacity,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val pagerModifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        mainPagerBackGestureState.onContainerSizeChanged(size.width, size.height)
                    }
                    .graphicsLayer {
                        val backMotion = mainPagerBackGestureState.motionValues
                        transformOrigin = TransformOrigin(backMotion.pivotX, backMotion.pivotY)
                        translationX = backMotion.translationX
                        scaleX = backMotion.scale
                        scaleY = backMotion.scale
                        alpha = coordinator.farJumpAlpha * backMotion.contentAlpha
                    }
                    .layerBackdrop(coordinator.backdrop)
            val activationState =
                rememberMainPageActivationState(
                    tabs = coordinator.tabs,
                    settledPageIndex = coordinator.pagerState.settledPage,
                    targetPageIndex = coordinator.pagerState.targetPage,
                    isPagerScrollInProgress = coordinator.pagerState.isScrollInProgress,
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
                            coordinator.onShowBottomBar,
                            coordinator.onPageScrollBoundsChange,
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
                                onShowBottomBar = coordinator.onShowBottomBar,
                                onScrollBoundsChange = { canScrollBackward, canScrollForward ->
                                    coordinator.onPageScrollBoundsChange(
                                        pageIndex,
                                        canScrollBackward,
                                        canScrollForward,
                                    )
                                },
                                scrollToTopSignal = scrollToTopSignal,
                                hasActivated = hasActivated,
                                contentReady = contentReady,
                                contentWorkAllowed = contentWorkAllowed,
                            )
                        }
                    val homePageState =
                        if (pageType == BottomPage.Home) {
                            remember(
                                shizukuStatus,
                                homeHdrEffectActive,
                                homeDynamicFullEffectEnabled,
                                coordinator.visibleTabsSnapshot,
                                coordinator.homeAppOverview,
                                coordinator.homeMcpOverview,
                                coordinator.homeGitHubOverview,
                                coordinator.homeWebDavOverview,
                                coordinator.homeBaOverview,
                                coordinator.homeRuntimeNowMs,
                                coordinator.visibleOverviewCards,
                                coordinator.showCacheFreshnessInCards,
                                coordinator.homeActionBarSelectedIndex,
                                coordinator.showHomeBottomPageEditor,
                                coordinator.onBottomPageVisibilityChange,
                                coordinator.onOverviewCardVisibilityChange,
                                coordinator.onCacheFreshnessVisibilityChange,
                                coordinator.onHomeActionBarSelectedIndexChange,
                                coordinator.onHomeBottomPageEditorVisibleChange,
                                onOpenWebDavSync,
                                onOpenSettings,
                                onOpenAbout,
                            ) {
                                MainPagerHomePageState(
                                    shizukuStatus = shizukuStatus,
                                    homeIconHdrEnabled = homeHdrEffectActive,
                                    homeDynamicFullEffectEnabled = homeDynamicFullEffectEnabled,
                                    visibleBottomPages = coordinator.visibleTabsSnapshot,
                                    homeAppOverview = coordinator.homeAppOverview,
                                    homeMcpOverview = coordinator.homeMcpOverview,
                                    homeGitHubOverview = coordinator.homeGitHubOverview,
                                    homeWebDavOverview = coordinator.homeWebDavOverview,
                                    homeBaOverview = coordinator.homeBaOverview,
                                    homeRuntimeNowMs = coordinator.homeRuntimeNowMs,
                                    visibleOverviewCards = coordinator.visibleOverviewCards,
                                    showCacheFreshnessInCards = coordinator.showCacheFreshnessInCards,
                                    homeActionBarSelectedIndex = coordinator.homeActionBarSelectedIndex,
                                    showHomeBottomPageEditor = coordinator.showHomeBottomPageEditor,
                                    onBottomPageVisibilityChange = coordinator.onBottomPageVisibilityChange,
                                    onOverviewCardVisibilityChange = coordinator.onOverviewCardVisibilityChange,
                                    onCacheFreshnessVisibilityChange = coordinator.onCacheFreshnessVisibilityChange,
                                    onHomeActionBarSelectedIndexChange = coordinator.onHomeActionBarSelectedIndexChange,
                                    onHomeBottomPageEditorVisibleChange = coordinator.onHomeBottomPageEditorVisibleChange,
                                    onOpenWebDavSync = onOpenWebDavSync,
                                    onOpenSettings = onOpenSettings,
                                    onOpenAbout = onOpenAbout,
                                )
                            }
                        } else {
                            null
                        }
                    val osPageState =
                        if (pageType == BottomPage.Os) {
                            remember(shizukuStatus, shizukuApiUtils) {
                                MainPagerOsPageState(
                                    shizukuStatus = shizukuStatus,
                                    shizukuApiUtils = shizukuApiUtils,
                                )
                            }
                        } else {
                            null
                        }
                    val baPageState =
                        if (pageType == BottomPage.Ba) {
                            remember(
                                preloadingEnabled,
                                onOpenGuideDetail,
                                openBaGuideCatalog,
                                requestedBaAccountId,
                                requestedBaAccountToken,
                            ) {
                                MainPagerBaPageState(
                                    preloadingEnabled = preloadingEnabled,
                                    onOpenPoolGuideDetail = onOpenGuideDetail,
                                    onOpenBaGuideCatalog = openBaGuideCatalog,
                                    requestedAccountId = requestedBaAccountId,
                                    requestedAccountToken = requestedBaAccountToken,
                                )
                            }
                        } else {
                            null
                        }
                    val mcpPageState =
                        if (pageType == BottomPage.Mcp) {
                            remember(mcpServerManager, onOpenMcpSkill) {
                                MainPagerMcpPageState(
                                    mcpServerManager = mcpServerManager,
                                    onOpenMcpSkill = onOpenMcpSkill,
                                )
                            }
                        } else {
                            null
                        }
                    val githubPageState =
                        if (pageType == BottomPage.GitHub) {
                            remember(
                                requestedGitHubRefreshToken,
                                requestedGitHubActionsTrackId,
                                requestedGitHubActionsSheetToken,
                                onOpenGitHubActionsNotificationHistory,
                            ) {
                                MainPagerGitHubPageState(
                                    requestedGitHubRefreshToken = requestedGitHubRefreshToken,
                                    requestedGitHubActionsTrackId = requestedGitHubActionsTrackId,
                                    requestedGitHubActionsSheetToken = requestedGitHubActionsSheetToken,
                                    onOpenActionsNotificationHistory = onOpenGitHubActionsNotificationHistory,
                                )
                            }
                        } else {
                            null
                        }
                    MainPagerPageHost(
                        pageType = pageType,
                        runtime = pageRuntime,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        homePageState = homePageState,
                        osPageState = osPageState,
                        baPageState = baPageState,
                        mcpPageState = mcpPageState,
                        githubPageState = githubPageState,
                        onActionBarInteractingChanged = coordinator.onActionBarInteractingChanged,
                    )
                }
            }
            val mainPagerPageKey =
                remember(coordinator.tabs) {
                    { index: Int -> coordinator.tabs.getOrNull(index)?.name ?: "main-page-$index" }
                }
            when (val pagerState = coordinator.pagerState) {
                is MainMiuixPagerState -> {
                    MainMiuixPager(
                        state = pagerState,
                        userScrollEnabled = coordinator.pagerScrollEnabled,
                        beyondViewportPageCount = coordinator.mainPagerBeyondViewportPageCount,
                        pageKey = mainPagerPageKey,
                        modifier = pagerModifier,
                        pageContent = pageContent,
                    )
                }

                is MainFoundationPagerState -> {
                    MainFoundationPager(
                        state = pagerState,
                        userScrollEnabled = coordinator.pagerScrollEnabled,
                        beyondViewportPageCount = coordinator.mainPagerBeyondViewportPageCount,
                        pageKey = mainPagerPageKey,
                        modifier = pagerModifier,
                        pageContent = pageContent,
                    )
                }

                is MainLoadedPagerState -> {
                    MainLoadedPager(
                        state = pagerState,
                        userScrollEnabled = coordinator.pagerScrollEnabled,
                        animationsEnabled = transitionAnimationsEnabled,
                        modifier = pagerModifier,
                        pageContent = pageContent,
                    )
                }

                else -> {
                    error("Unsupported main pager state: ${pagerState::class.java.name}")
                }
            }
        }
    }
}

@Composable
private fun HomeHdrWindowModeEffect(active: Boolean) {
    val activity = LocalContext.current.findActivity()
    val window = activity?.window
    val targetMode =
        if (active) {
            ActivityInfo.COLOR_MODE_HDR
        } else {
            ActivityInfo.COLOR_MODE_DEFAULT
        }
    LaunchedEffect(window, targetMode) {
        runCatching { window?.colorMode = targetMode }
    }
    DisposableEffect(window) {
        onDispose {
            runCatching { window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT }
        }
    }
}

internal fun shouldActivateHomeHdrEffect(
    homeIconHdrEnabled: Boolean,
    transitionAnimationsEnabled: Boolean,
    mainRouteActive: Boolean,
    homeIndex: Int,
    settledPage: Int,
    pagerScrollInProgress: Boolean,
    navigationActive: Boolean,
): Boolean =
    homeIconHdrEnabled &&
        transitionAnimationsEnabled &&
        mainRouteActive &&
        homeIndex >= 0 &&
        settledPage == homeIndex &&
        !pagerScrollInProgress &&
        !navigationActive

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
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
