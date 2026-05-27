@file:Suppress("FunctionName")

package os.kei.ui.page.main.about.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.about.state.rememberAboutPageColorPalette
import os.kei.ui.page.main.about.util.openExternalUrl
import os.kei.ui.page.main.debug.DebugComponentLabActivity
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Composable
fun AboutPage(
    appLabel: String,
    notificationPermissionGranted: Boolean,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    onCheckShizuku: () -> Unit,
    miuixMainNavigationEnabled: Boolean = false,
    contentBottomPadding: Dp = 72.dp,
    scrollToTopSignal: Int = 0,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val palette = rememberAboutPageColorPalette(shizukuStatus = shizukuStatus)
    val viewModel: AboutPageViewModel = viewModel()
    val detailsState by viewModel.detailsState.collectAsStateWithLifecycle()
    val chromeState by viewModel.chromeState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    val categories =
        remember {
            listOf(
                AboutCategory.Overview,
                AboutCategory.System,
                AboutCategory.Tech,
                AboutCategory.Lab,
            )
        }
    val pagerState =
        rememberMainLoadedPagerState(
            initialPage = chromeState.selectedCategoryIndex.coerceIn(0, categories.lastIndex),
            pageCount = categories.size,
        )
    val overviewListState = rememberLazyListState()
    val systemListState = rememberLazyListState()
    val techListState = rememberLazyListState()
    val labListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val expansionState = chromeState.expansionState
    val searchExpanded = chromeState.searchExpanded
    val searchQuery = chromeState.searchQuery
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val showBottomBar = chromeState.bottomBarVisible
    val farJumpAlpha = remember { Animatable(1f) }
    val tabJumpJobHolder = remember { AboutTabJumpJobHolder() }
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val density = LocalDensity.current
    val bottomBarVisibilityThresholdPx = remember(density) { with(density) { 22.dp.toPx() } }
    val bottomBarVisibilityController =
        remember(bottomBarVisibilityThresholdPx) {
            ScrollChromeVisibilityController(bottomBarVisibilityThresholdPx)
        }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            when (categories.getOrNull(pagerState.settledPage)) {
                AboutCategory.Overview -> overviewListState.animateScrollToItem(0)
                AboutCategory.System -> systemListState.animateScrollToItem(0)
                AboutCategory.Tech -> techListState.animateScrollToItem(0)
                AboutCategory.Lab -> labListState.animateScrollToItem(0)
                null -> Unit
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        bottomBarVisibilityController.showNow(showBottomBar, viewModel::updateBottomBarVisible)
        if (chromeState.selectedCategoryIndex != pagerState.settledPage) {
            viewModel.updateSelectedCategoryIndex(pagerState.settledPage)
        }
    }

    LaunchedEffect(context, appLabel, notificationPermissionGranted, shizukuStatus, shizukuApiUtils) {
        viewModel.refreshDetails(
            context = context,
            appLabel = appLabel,
            shizukuStatus = shizukuStatus,
            notificationPermissionGranted = notificationPermissionGranted,
            shizukuApiUtils = shizukuApiUtils,
        )
    }
    val permissionEntries = detailsState.permissionEntries
    val componentEntries = detailsState.componentEntries
    val shizukuDetailMap = detailsState.shizukuDetailMap
    val shizukuReady = shizukuStatus.contains("granted", ignoreCase = true)
    val openLinkFailed = stringResource(R.string.common_open_link_failed)
    val aboutSearchPlaceholder = stringResource(R.string.about_search_placeholder)
    val searchContentDescription = stringResource(R.string.about_search_placeholder)
    val searchActive = searchQuery.trim().isNotEmpty()
    val matchingSearchTargets = searchState.matchingTargets
    val matchingSearchCards = searchState.matchingCards
    val activeCategoryIndex =
        if (pagerState.isScrollInProgress) {
            pagerState.targetPage
        } else {
            pagerState.settledPage
        }.coerceIn(0, categories.lastIndex)
    val activeCategory = categories[activeCategoryIndex]
    val activePageListState =
        when (activeCategory) {
            AboutCategory.Overview -> overviewListState
            AboutCategory.System -> systemListState
            AboutCategory.Tech -> techListState
            AboutCategory.Lab -> labListState
        }
    val activeChromeListState = if (searchActive) searchListState else activePageListState
    val currentActiveChromeListState = rememberUpdatedState(activeChromeListState)
    val currentShowBottomBar = rememberUpdatedState(showBottomBar)
    val bottomBarNestedScrollConnection =
        remember(bottomBarVisibilityController) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val currentListState = currentActiveChromeListState.value
                    bottomBarVisibilityController.updateWithinScrollBounds(
                        deltaY = consumed.y,
                        visible = currentShowBottomBar.value,
                        canScrollBackward = currentListState.canScrollBackward,
                        canScrollForward = currentListState.canScrollForward,
                        onVisibleChange = viewModel::updateBottomBarVisible,
                    )
                    return Offset.Zero
                }
            }
        }

    LaunchedEffect(activeChromeListState, bottomBarVisibilityController) {
        snapshotFlow {
            activeChromeListState.canScrollBackward to activeChromeListState.canScrollForward
        }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                bottomBarVisibilityController.showForStaticContent(
                    visible = currentShowBottomBar.value,
                    canScrollBackward = canScrollBackward,
                    canScrollForward = canScrollForward,
                    onVisibleChange = viewModel::updateBottomBarVisible,
                )
            }
    }
    val selectAboutCategory =
        remember(
            categories,
            pagerState,
            transitionAnimationsEnabled,
            farJumpAlpha,
            scope,
            viewModel,
        ) {
            { index: Int ->
                val target = index.coerceIn(0, categories.lastIndex)
                val stablePageIndex =
                    if (pagerState.isScrollInProgress) {
                        pagerState.targetPage
                    } else {
                        pagerState.settledPage
                    }
                if (target != stablePageIndex) {
                    viewModel.updateSelectedCategoryIndex(target)
                    tabJumpJobHolder.job?.cancel()
                    tabJumpJobHolder.job =
                        scope.launch {
                            val distance = abs(target - stablePageIndex)
                            if (distance > 1) {
                                farJumpAlpha.snapTo(1f)
                                farJumpAlpha.animateTo(
                                    targetValue = 0.92f,
                                    animationSpec =
                                        tween(
                                            durationMillis =
                                                resolvedMotionDuration(
                                                    AppMotionTokens.farJumpDimMs,
                                                    transitionAnimationsEnabled,
                                                ),
                                        ),
                                )
                            }
                            pagerState.animateToPage(
                                target = target,
                                animationsEnabled = transitionAnimationsEnabled,
                                durationMillis = aboutPagerSwitchDurationMillis(distance),
                            )
                            if (distance > 1) {
                                farJumpAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec =
                                        tween(
                                            durationMillis =
                                                resolvedMotionDuration(
                                                    AppMotionTokens.farJumpRestoreMs,
                                                    transitionAnimationsEnabled,
                                                ),
                                        ),
                                )
                            }
                        }
                }
            }
        }

    BackHandler(enabled = searchExpanded) {
        viewModel.updateSearchExpanded(false)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchListState.scrollToItem(0)
        }
    }
    val cardRenderState =
        AboutCardRenderState(
            appDetails = detailsState.appDetails,
            palette = palette,
            searchActive = searchActive,
            expansionState = expansionState,
            shizukuReady = shizukuReady,
            notificationPermissionGranted = notificationPermissionGranted,
            shizukuDetailMap = shizukuDetailMap,
            permissionEntries = permissionEntries,
            componentEntries = componentEntries,
            techDetails = detailsState.techDetails,
        )
    val cardActions =
        remember(context, openLinkFailed, onCheckShizuku, viewModel) {
            AboutCardActions(
                onExpandedChange = viewModel::updateSectionExpanded,
                onCheckShizuku = onCheckShizuku,
                onOpenExternalUrl = { url ->
                    if (!openExternalUrl(context, url)) {
                        context.showToast(openLinkFailed)
                    }
                },
                onOpenComponentLab = { DebugComponentLabActivity.launch(context) },
            )
        }

    AppPageScaffold(
        title = stringResource(R.string.about_page_title),
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(bottomBarNestedScrollConnection),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
        titleBackdrop = topBarBackdrop,
        onTitleClick = {
            bottomBarVisibilityController.showNow(
                visible = currentShowBottomBar.value,
                onVisibleChange = viewModel::updateBottomBarVisible,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                AppLiquidNavigationButton(
                    icon = appLucideBackIcon(),
                    contentDescription = stringResource(R.string.common_close),
                    onClick = onBack,
                    backdrop = topBarBackdrop,
                )
            }
        },
        bottomBar = {
            AboutBottomChrome(
                visible = showBottomBar,
                navigationBarBottom = navigationBarBottom,
                categories = categories,
                selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                selectedPagePosition = null,
                selectedPagePositionProvider = {
                    if (!searchExpanded && pagerState.isScrollInProgress) {
                        pagerState.pagePosition.coerceIn(
                            0f,
                            categories.lastIndex.coerceAtLeast(0).toFloat(),
                        )
                    } else {
                        null
                    }
                },
                selectedPageProvider = { pagerState.targetPage },
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSearchExpandedChange = viewModel::updateSearchExpanded,
                searchIcon = appLucideSearchIcon(),
                searchContentDescription = searchContentDescription,
                searchPlaceholder = aboutSearchPlaceholder,
                backdrop = bottomBarBackdrop,
                isLiquidEffectEnabled = true,
                miuixMainNavigationEnabled = miuixMainNavigationEnabled,
                onSelectCategory = selectAboutCategory,
            )
        },
    ) { innerPadding ->
        if (searchActive) {
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = searchListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .layerBackdrop(topBarBackdrop)
                        .layerBackdrop(bottomBarBackdrop),
                bottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight,
                sectionSpacing = 14.dp,
            ) {
                if (matchingSearchTargets.isEmpty()) {
                    item(
                        key = "about_search_empty",
                        contentType = "about_search_empty",
                    ) {
                        Text(
                            text = stringResource(R.string.common_no_matched_results),
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = AppTypographyTokens.Body.fontSize,
                            lineHeight = AppTypographyTokens.Body.lineHeight,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppChromeTokens.pageHorizontalPadding),
                        )
                    }
                } else {
                    matchingSearchTargets.forEach { target ->
                        aboutCardItem(
                            card = target.card,
                            state = cardRenderState,
                            actions = cardActions,
                        )
                    }
                }
            }
        } else {
            MainLoadedPager(
                state = pagerState,
                userScrollEnabled = !searchExpanded,
                animationsEnabled = transitionAnimationsEnabled,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = farJumpAlpha.value }
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .layerBackdrop(topBarBackdrop)
                        .layerBackdrop(bottomBarBackdrop),
            ) { pageIndex ->
                val category = categories[pageIndex]
                val pageListState =
                    when (category) {
                        AboutCategory.Overview -> overviewListState
                        AboutCategory.System -> systemListState
                        AboutCategory.Tech -> techListState
                        AboutCategory.Lab -> labListState
                    }
                AppPageLazyColumn(
                    innerPadding = innerPadding,
                    state = pageListState,
                    modifier = Modifier.fillMaxSize(),
                    bottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight,
                    sectionSpacing = 14.dp,
                ) {
                    aboutCategoryCards(
                        category = category,
                        matchingCards = matchingSearchCards,
                        state = cardRenderState,
                        actions = cardActions,
                    )
                }
            }
        }
    }
}

private fun aboutPagerSwitchDurationMillis(distance: Int): Int = (100 * distance.coerceAtLeast(1) + 100).coerceIn(180, 420)

private class AboutTabJumpJobHolder {
    var job: Job? = null
}
