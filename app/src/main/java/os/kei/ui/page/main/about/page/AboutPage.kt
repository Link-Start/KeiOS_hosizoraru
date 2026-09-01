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
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.privilege.PrivilegedShell
import os.kei.core.ui.effect.rememberAppTopBarColor
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
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists
import os.kei.ui.page.main.widget.chrome.appPageAlternatingLanes
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageContentMaxWidthFor
import os.kei.ui.page.main.widget.chrome.rememberTabbedPageChromeScrollState
import os.kei.ui.page.main.widget.chrome.tabbedPageContentNestedScrollConnection
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import os.kei.core.privilege.PrivilegeStatus

@Composable
fun AboutPage(
    appLabel: String,
    notificationPermissionGranted: Boolean,
    privilegeStatus: PrivilegeStatus,
    privilegedShell: PrivilegedShell,
    onCheckPrivilege: () -> Unit,
    contentBottomPadding: Dp = 72.dp,
    scrollToTopSignal: Int = 0,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val palette = rememberAboutPageColorPalette(privilegeStatus = privilegeStatus)
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
    val overviewSecondaryState = rememberLazyListState()
    val systemSecondaryState = rememberLazyListState()
    val techSecondaryState = rememberLazyListState()
    val labSecondaryState = rememberLazyListState()
    val searchSecondaryState = rememberLazyListState()
    // Two columns on a tablet or an unfolded fold, one everywhere else. Both scroll positions are kept, so
    // the shape a reader returns to still holds the place they left.
    val aboutColumnCount = appPageColumnCount()
    val wideAboutLayout = aboutColumnCount >= 2
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val expansionState = chromeState.expansionState
    val searchExpanded = chromeState.searchExpanded
    val searchQuery = chromeState.searchQuery
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val showBottomBar = chromeState.bottomBarVisible
    val farJumpAlpha = remember { Animatable(1f) }
    val tabJumpJobHolder = remember { AboutTabJumpJobHolder() }
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            val category = categories.getOrNull(pagerState.settledPage)
            if (category != null) {
                when (category) {
                    AboutCategory.Overview -> overviewListState.animateScrollToItem(0)
                    AboutCategory.System -> systemListState.animateScrollToItem(0)
                    AboutCategory.Tech -> techListState.animateScrollToItem(0)
                    AboutCategory.Lab -> labListState.animateScrollToItem(0)
                }
                if (wideAboutLayout) {
                    // Both columns: the signal belongs to the page, not to one of its lanes.
                    when (category) {
                        AboutCategory.Overview -> overviewSecondaryState.animateScrollToItem(0)
                        AboutCategory.System -> systemSecondaryState.animateScrollToItem(0)
                        AboutCategory.Tech -> techSecondaryState.animateScrollToItem(0)
                        AboutCategory.Lab -> labSecondaryState.animateScrollToItem(0)
                    }
                }
            }
        }
    }

    LaunchedEffect(context, appLabel, notificationPermissionGranted, privilegeStatus, privilegedShell) {
        viewModel.refreshDetails(
            context = context,
            appLabel = appLabel,
            privilegeStatus = privilegeStatus,
            notificationPermissionGranted = notificationPermissionGranted,
            privilegedShell = privilegedShell,
        )
    }
    val permissionEntries = detailsState.permissionEntries
    val componentEntries = detailsState.componentEntries
    val privilegeDetailMap = detailsState.privilegeDetailMap
    val privilegeReady = privilegeStatus.isCommandReady
    val openLinkFailed = stringResource(R.string.common_open_link_failed)
    val aboutSearchPlaceholder = stringResource(R.string.about_search_placeholder)
    val searchContentDescription = stringResource(R.string.about_search_placeholder)
    val searchActive = searchQuery.trim().isNotEmpty()
    val matchingSearchTargets = searchState.matchingTargets
    val matchingSearchCards = searchState.matchingCards
    val activePageListStateProvider: () -> ScrollableState =
        remember(
            categories,
            pagerState,
            overviewListState,
            systemListState,
            techListState,
            labListState,
        ) {
            {
                val activeCategoryIndex =
                    if (pagerState.isScrollInProgress) {
                        pagerState.targetPage
                    } else {
                        pagerState.settledPage
                    }.coerceIn(0, categories.lastIndex)
                // The chrome follows the first column. Either column can move it through the shared
                // nested scroll; reading one keeps the show-again check from flapping when they disagree.
                when (categories[activeCategoryIndex]) {
                    AboutCategory.Overview -> overviewListState
                    AboutCategory.System -> systemListState
                    AboutCategory.Tech -> techListState
                    AboutCategory.Lab -> labListState
                }
            }
        }
    val activeChromeListStateProvider: () -> ScrollableState =
        remember(
            searchActive,
            searchListState,
            activePageListStateProvider,
        ) {
            {
                if (searchActive) searchListState else activePageListStateProvider()
            }
        }
    val bottomChromeScrollState =
        rememberTabbedPageChromeScrollState(
            visible = showBottomBar,
            activeListStateProvider = activeChromeListStateProvider,
            onVisibleChange = viewModel::updateBottomBarVisible,
        )
    val searchNestedScrollConnection =
        remember(searchListState, bottomChromeScrollState, scrollBehavior.nestedScrollConnection) {
            tabbedPageContentNestedScrollConnection(
                listState = searchListState,
                chrome = bottomChromeScrollState.chromeNestedScrollConnection,
                delegate = scrollBehavior.nestedScrollConnection,
            )
        }
    LaunchedEffect(pagerState.settledPage) {
        bottomChromeScrollState.showNow()
        if (chromeState.selectedCategoryIndex != pagerState.settledPage) {
            viewModel.updateSelectedCategoryIndex(pagerState.settledPage)
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
            privilegeReady = privilegeReady,
            notificationPermissionGranted = notificationPermissionGranted,
            privilegeDetailMap = privilegeDetailMap,
            permissionEntries = permissionEntries,
            componentEntries = componentEntries,
            techDetails = detailsState.techDetails,
        )
    val cardActions =
        remember(context, openLinkFailed, onCheckPrivilege, viewModel) {
            AboutCardActions(
                onExpandedChange = viewModel::updateSectionExpanded,
                onCheckPrivilege = onCheckPrivilege,
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
                .pageRootTestTag(KeiOsTestTags.AboutPageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = topBarBackdrop,
        // The whole page, chrome included, centres on this.
        contentMaxWidth = appPageContentMaxWidthFor(aboutColumnCount),
        onTitleClick = {
            scope.launch {
                (activeChromeListStateProvider() as? LazyListState)?.animateScrollToItem(0)
            }
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
                onSelectCategory = selectAboutCategory,
                onExpandDock = {
                    bottomChromeScrollState.showNow()
                },
            )
        },
    ) { innerPadding ->
        if (searchActive) {
            val searchModifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(searchNestedScrollConnection)
                    .layerBackdrop(topBarBackdrop)
                    .layerBackdrop(bottomBarBackdrop)
            val searchBottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight
            if (matchingSearchTargets.isNotEmpty() && wideAboutLayout) {
                val (left, right) =
                    appPageAlternatingLanes(matchingSearchTargets.map { target -> target.card })
                AppPageTwoColumnLists(
                    innerPadding = innerPadding,
                    primaryState = searchListState,
                    secondaryState = searchSecondaryState,
                    modifier = searchModifier,
                    bottomExtra = searchBottomExtra,
                    sectionSpacing = 14.dp,
                    primary = { aboutCardLane(left, cardRenderState, cardActions) },
                    secondary = { aboutCardLane(right, cardRenderState, cardActions) },
                )
            } else {
                AppPageLazyColumn(
                    innerPadding = innerPadding,
                    state = searchListState,
                    modifier = searchModifier,
                    bottomExtra = searchBottomExtra,
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
                val pageNestedScrollConnection =
                    remember(pageListState, bottomChromeScrollState, scrollBehavior.nestedScrollConnection) {
                        tabbedPageContentNestedScrollConnection(
                            listState = pageListState,
                            chrome = bottomChromeScrollState.chromeNestedScrollConnection,
                            delegate = scrollBehavior.nestedScrollConnection,
                        )
                    }
                val pageModifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(pageNestedScrollConnection)
                val pageBottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight
                if (wideAboutLayout) {
                    val (left, right) =
                        appPageAlternatingLanes(
                            aboutCategoryCardList(
                                category = category,
                                matchingCards = matchingSearchCards,
                                state = cardRenderState,
                            ),
                        )
                    AppPageTwoColumnLists(
                        innerPadding = innerPadding,
                        primaryState = pageListState,
                        secondaryState =
                            when (category) {
                                AboutCategory.Overview -> overviewSecondaryState
                                AboutCategory.System -> systemSecondaryState
                                AboutCategory.Tech -> techSecondaryState
                                AboutCategory.Lab -> labSecondaryState
                            },
                        modifier = pageModifier,
                        bottomExtra = pageBottomExtra,
                        sectionSpacing = 14.dp,
                        primary = { aboutCardLane(left, cardRenderState, cardActions) },
                        secondary = { aboutCardLane(right, cardRenderState, cardActions) },
                    )
                } else {
                    AppPageLazyColumn(
                        innerPadding = innerPadding,
                        state = pageListState,
                        modifier = pageModifier,
                        bottomExtra = pageBottomExtra,
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
}

private fun aboutPagerSwitchDurationMillis(distance: Int): Int = (100 * distance.coerceAtLeast(1) + 100).coerceIn(180, 420)

private class AboutTabJumpJobHolder {
    var job: Job? = null
}
