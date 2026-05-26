@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.LocalGuideMediaGifTargetRequester
import os.kei.ui.page.main.student.LocalGuideMediaGifTargets
import os.kei.ui.page.main.student.LocalGuideMediaImageBitmaps
import os.kei.ui.page.main.student.LocalGuideMediaImageMissingKeys
import os.kei.ui.page.main.student.LocalGuideMediaImageRequester
import os.kei.ui.page.main.student.page.component.BaStudentGuideBottomBar
import os.kei.ui.page.main.student.page.component.BaStudentGuidePagerContent
import os.kei.ui.page.main.student.page.state.BaStudentGuideViewModel
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideForegroundAudioGuard
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideMediaSaveEvents
import os.kei.ui.page.main.student.page.state.BindBaStudentGuidePagerSyncEffects
import os.kei.ui.page.main.student.page.state.BindBaStudentGuidePlayerLifecycleEffects
import os.kei.ui.page.main.student.page.state.BindBaStudentGuidePrefetchEffects
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideVoiceListenerEffect
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideVoiceProgressEffect
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideBottomBarChromeState
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideMediaPackSaveAction
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideMediaSaveAction
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuidePageActions
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideTabSelectCoordinator
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideTopBarActionItems
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideVoicePlayerController
import os.kei.ui.page.main.student.page.support.rememberGuideSyncProgress
import os.kei.ui.page.main.student.page.support.resolveGuideBottomTabs
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.perf.ReportPagerPerformanceState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BaStudentGuidePage(
    liquidBottomBarEnabled: Boolean = true,
    miuixMainNavigationEnabled: Boolean = false,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    preloadingEnabled: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val preloadPolicy =
        remember(preloadingEnabled) {
            UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled)
        }
    val defaultPageTitle = stringResource(R.string.guide_page_title_default)
    val shareSourceEmptyText = stringResource(R.string.guide_share_source_empty)
    val shareSourceChooserTitle = stringResource(R.string.guide_share_source_chooser_title)
    val shareSourceFailedText = stringResource(R.string.common_share_failed)
    val openLinkFailedText = stringResource(R.string.common_open_link_failed)
    val shareSourceContentDescription = stringResource(R.string.guide_cd_share_source)
    val refreshContentDescription = stringResource(R.string.common_refresh)
    val loadFailedText = stringResource(R.string.guide_load_failed)
    val refreshFailedKeepCacheText = stringResource(R.string.guide_refresh_failed_keep_cached)
    val accent = MiuixTheme.colorScheme.primary
    val surfaceColor = MiuixTheme.colorScheme.surface
    // Keep backdrop allocation stable per page lifecycle to avoid RenderThread native crashes
    // when rapidly switching guide tabs on some HyperOS builds.
    var activationCount by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        activationCount++
        onDispose { }
    }
    // Keep top-level backdrop only for navigator/pager layer and bottom bar.
    val navBackdrop: LayerBackdrop =
        key("nav-$activationCount") {
            rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }
        }
    // Top action bar uses its own backdrop instance to avoid cross-layer recursion.
    val topBarBackdrop: LayerBackdrop =
        key("topbar-$activationCount") {
            rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }
        }
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = true)
    val scrollBehavior = MiuixScrollBehavior()

    val guideViewModel: BaStudentGuideViewModel = applicationViewModel(create = ::BaStudentGuideViewModel)
    val guideUiState by guideViewModel.uiState.collectAsStateWithLifecycle()
    val guideMediaImageState by guideViewModel.mediaImageState.collectAsStateWithLifecycle()
    val profileLinkTitleState by guideViewModel.profileLinkTitleState.collectAsStateWithLifecycle()
    val pageChromeState by guideViewModel.pageChromeState.collectAsStateWithLifecycle()
    val voiceUiState by guideViewModel.voiceUiState.collectAsStateWithLifecycle()
    LaunchedEffect(
        guideViewModel,
        transitionAnimationsEnabled,
        preloadPolicy.initialFetchDelayMs,
        loadFailedText,
        refreshFailedKeepCacheText,
    ) {
        guideViewModel.bind(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            initialFetchDelayMs = preloadPolicy.initialFetchDelayMs,
            loadFailedText = loadFailedText,
            refreshFailedKeepCacheText = refreshFailedKeepCacheText,
        )
    }
    val guideDataState = guideUiState.dataState
    val guidePrefetchState = guideUiState.prefetchState
    val bgmFavoriteAudioUrls = guideUiState.bgmFavoriteAudioUrls
    val sourceUrl = guideDataState.sourceUrl
    val info = guideDataState.info
    val loading = guideDataState.loading
    val error = guideDataState.error
    val selectedBottomTabOrdinal = pageChromeState.selectedBottomTabOrdinal
    val selectedVoiceLanguage = pageChromeState.selectedVoiceLanguage
    val playingVoiceUrl = voiceUiState.playingVoiceUrl
    val isVoicePlaying = voiceUiState.isVoicePlaying
    val voicePlayProgress = voiceUiState.voicePlayProgress
    val bottomTabsList = remember(info) { resolveGuideBottomTabs(info) }
    LaunchedEffect(bottomTabsList, selectedBottomTabOrdinal) {
        guideViewModel.coerceSelectedBottomTab(bottomTabsList)
    }
    val selectedBottomTabIndex =
        bottomTabsList
            .indexOfFirst { tab ->
                tab.ordinal == selectedBottomTabOrdinal
            }.takeIf { it >= 0 } ?: 0
    val pagerState =
        rememberPagerState(
            initialPage = selectedBottomTabIndex,
            pageCount = { bottomTabsList.size },
        )
    ReportPagerPerformanceState(
        scope = "guide_detail_pager",
        currentPage = bottomTabsList.getOrElse(pagerState.currentPage) { GuideBottomTab.Archive }.name,
        targetPage = bottomTabsList.getOrElse(pagerState.targetPage) { GuideBottomTab.Archive }.name,
        scrolling = pagerState.isScrollInProgress,
    )
    val activeBottomTab = bottomTabsList.getOrElse(pagerState.currentPage) { GuideBottomTab.Archive }
    val settledBottomTab = bottomTabsList.getOrElse(pagerState.settledPage) { GuideBottomTab.Archive }
    val guideStaticPrefetchEnabled = info != null && !loading && error == null
    val syncProgress =
        rememberGuideSyncProgress(
            loading = loading,
            animationsEnabled = transitionAnimationsEnabled,
        )
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarChromeState = rememberBaStudentGuideBottomBarChromeState()
    val farJumpAlpha = remember { Animatable(1f) }
    val selectBottomTabAction =
        rememberBaStudentGuideTabSelectCoordinator(
            bottomTabs = bottomTabsList,
            pagerState = pagerState,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            farJumpAlpha = farJumpAlpha,
            onShowBottomBarChange = bottomBarChromeState::updateVisible,
            onSelectedBottomTabIndexChange = { selectedIndex ->
                val selectedTab =
                    bottomTabsList
                        .getOrNull(selectedIndex)
                        ?: GuideBottomTab.Archive
                guideViewModel.updateSelectedBottomTab(selectedTab)
            },
        )
    LaunchedEffect(guideUiState.requestedInitialBottomTab, bottomTabsList, selectBottomTabAction) {
        val targetTab = guideUiState.requestedInitialBottomTab ?: return@LaunchedEffect
        val targetIndex = bottomTabsList.indexOf(targetTab)
        if (targetIndex >= 0) {
            guideViewModel.requestInitialBottomTabHandled()
            selectBottomTabAction(targetIndex)
        }
    }
    val density = LocalDensity.current
    val bottomBarVisibilityThresholdPx = remember(density) { with(density) { 22.dp.toPx() } }
    val bottomBarVisibilityController =
        remember(bottomBarVisibilityThresholdPx) {
            ScrollChromeVisibilityController(bottomBarVisibilityThresholdPx)
        }
    val currentBottomBarChromeState by rememberUpdatedState(bottomBarChromeState)
    val bottomBarNestedScrollConnection =
        remember(bottomBarVisibilityController) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val chromeState = currentBottomBarChromeState
                    bottomBarVisibilityController.updateWithinScrollBounds(
                        deltaY = consumed.y,
                        visible = chromeState.visible,
                        canScrollBackward = chromeState.activePageCanScrollBackward,
                        canScrollForward = chromeState.activePageCanScrollForward,
                        onVisibleChange = chromeState::updateVisible,
                    )
                    return Offset.Zero
                }
            }
        }
    LaunchedEffect(sourceUrl, activeBottomTab) {
        bottomBarChromeState.showNow(bottomBarVisibilityController)
    }
    LaunchedEffect(
        bottomBarChromeState.activePageCanScrollBackward,
        bottomBarChromeState.activePageCanScrollForward,
        bottomBarVisibilityController,
    ) {
        bottomBarChromeState.showForStaticContent(bottomBarVisibilityController)
    }
    val pageTitle = info?.title?.ifBlank { defaultPageTitle } ?: defaultPageTitle
    val voicePlayerController = rememberBaStudentGuideVoicePlayerController(sourceUrl)
    val saveGuideMediaAction =
        rememberBaStudentGuideMediaSaveAction(
            guideViewModel = guideViewModel,
            currentStudentNamePrefix = { info?.title?.trim().orEmpty() },
        )
    val saveGuideMediaPackAction =
        rememberBaStudentGuideMediaPackSaveAction(
            guideViewModel = guideViewModel,
            currentStudentNamePrefix = { info?.title?.trim().orEmpty() },
        )

    BindBaStudentGuideMediaSaveEvents(guideViewModel = guideViewModel)
    BindBaStudentGuidePlayerLifecycleEffects(
        sourceUrl = sourceUrl,
        voicePlayerController = voicePlayerController,
    )
    BindBaStudentGuideForegroundAudioGuard(
        sourceUrl = sourceUrl,
        voicePlayerController = voicePlayerController,
        onPlayingVoiceUrlChange = guideViewModel::updatePlayingVoiceUrl,
        onIsVoicePlayingChange = guideViewModel::updateIsVoicePlaying,
        onVoicePlayProgressChange = guideViewModel::updateVoicePlayProgress,
    )
    BindBaStudentGuideVoiceListenerEffect(
        context = context,
        voicePlayer = voicePlayerController.player,
        playingVoiceUrl = playingVoiceUrl,
        onPlayingVoiceUrlChange = guideViewModel::updatePlayingVoiceUrl,
        onIsVoicePlayingChange = guideViewModel::updateIsVoicePlaying,
        onVoicePlayProgressChange = guideViewModel::updateVoicePlayProgress,
    )
    val pageActions =
        rememberBaStudentGuidePageActions(
            info = info,
            sourceUrl = sourceUrl,
            shareSourceEmptyText = shareSourceEmptyText,
            shareSourceChooserTitle = shareSourceChooserTitle,
            shareSourceFailedText = shareSourceFailedText,
            openLinkFailedText = openLinkFailedText,
            voicePlayerController = voicePlayerController,
            playingVoiceUrl = playingVoiceUrl,
            onPlayingVoiceUrlChange = guideViewModel::updatePlayingVoiceUrl,
            onIsVoicePlayingChange = guideViewModel::updateIsVoicePlaying,
            onVoicePlayProgressChange = guideViewModel::updateVoicePlayProgress,
            onOpenGuideInPage = guideViewModel::openGuide,
            onRefresh = guideViewModel::requestRefresh,
            saveGuideMedia = saveGuideMediaAction,
            saveGuideMediaPack = saveGuideMediaPackAction,
        )

    BindBaStudentGuidePagerSyncEffects(
        sourceUrl = sourceUrl,
        bottomTabsSize = bottomTabsList.size,
        selectedBottomTabIndex = selectedBottomTabIndex,
        pagerState = pagerState,
        onSelectedBottomTabIndexChange = { selectedIndex ->
            val selectedTab =
                bottomTabsList
                    .getOrNull(selectedIndex)
                    ?: GuideBottomTab.Archive
            guideViewModel.updateSelectedBottomTab(selectedTab)
        },
    )
    BindBaStudentGuideVoiceProgressEffect(
        activeBottomTab = activeBottomTab,
        isVoicePlaying = isVoicePlaying,
        playingVoiceUrl = playingVoiceUrl,
        voicePlayer = voicePlayerController.player,
        onVoicePlayProgressChange = guideViewModel::updateVoicePlayProgress,
    )
    BindBaStudentGuidePrefetchEffects(
        info = info,
        prefetchBottomTab = settledBottomTab,
        prefetchEnabled = guideStaticPrefetchEnabled,
        initialPrefetchCount = preloadPolicy.guideStaticPrefetchInitialCount,
        galleryExtraPrefetchCount = preloadPolicy.guideStaticPrefetchGalleryExtraCount,
        onSyncPrefetch = guideViewModel::syncStaticImagePrefetch,
    )
    val shareIcon = appLucideShareIcon()
    val refreshIcon = appLucideRefreshIcon()
    val actionItems =
        rememberBaStudentGuideTopBarActionItems(
            shareIcon = shareIcon,
            refreshIcon = refreshIcon,
            shareSourceContentDescription = shareSourceContentDescription,
            refreshContentDescription = refreshContentDescription,
            onShareSource = pageActions.shareSource,
            onRefresh = pageActions.requestRefresh,
        )
    CompositionLocalProvider(
        LocalGuideMediaImageBitmaps provides guideMediaImageState.bitmaps,
        LocalGuideMediaImageMissingKeys provides guideMediaImageState.missingKeys,
        LocalGuideMediaGifTargets provides guideMediaImageState.resolvedGifTargets,
        LocalGuideMediaImageRequester provides guideViewModel::requestGuideMediaImages,
        LocalGuideMediaGifTargetRequester provides guideViewModel::requestGuideMediaGifTargets,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppScaffold(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background)
                        .nestedScroll(bottomBarNestedScrollConnection),
                topBar = {
                    AppTopBarSection(
                        title = pageTitle,
                        largeTitle = pageTitle,
                        scrollBehavior = scrollBehavior,
                        color = topBarMaterialBackdrop,
                        titleBackdrop = topBarBackdrop,
                        titleEndReserve = AppChromeTokens.topBarTitleActionReserve,
                        onTitleClick = {
                            bottomBarChromeState.showNow(bottomBarVisibilityController)
                        },
                        navigationIcon = {
                            AppLiquidNavigationButton(
                                icon = appLucideBackIcon(),
                                contentDescription = pageTitle,
                                onClick = onBack,
                                backdrop = topBarBackdrop,
                            )
                        },
                    )
                },
                bottomBar = {
                    BaStudentGuideBottomBar(
                        visible = bottomBarChromeState.visible,
                        navigationBarBottom = navigationBarBottom,
                        bottomTabs = bottomTabsList,
                        selectedPage = pagerState.targetPage,
                        selectedPagePosition = pagerState.targetPage.toFloat(),
                        selectedPagePositionProvider = {
                            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, bottomTabsList.lastIndex.coerceAtLeast(0).toFloat())
                        },
                        selectedPageProvider = { pagerState.targetPage },
                        backdrop = navBackdrop,
                        isLiquidEffectEnabled = liquidBottomBarEnabled,
                        miuixMainNavigationEnabled = miuixMainNavigationEnabled,
                        onSelectTab = selectBottomTabAction,
                    )
                },
            ) { innerPadding ->
                BaStudentGuidePagerContent(
                    sourceUrl = sourceUrl,
                    info = info,
                    error = error,
                    pagerState = pagerState,
                    bottomTabs = bottomTabsList,
                    syncProgress = syncProgress,
                    activationCount = activationCount,
                    surfaceColor = surfaceColor,
                    accent = accent,
                    innerPadding = innerPadding,
                    farJumpAlpha = farJumpAlpha.value,
                    navBackdrop = navBackdrop,
                    topBarBackdrop = topBarBackdrop,
                    galleryCacheRevision = guidePrefetchState.galleryCacheRevision,
                    selectedVoiceLanguage = selectedVoiceLanguage,
                    playingVoiceUrl = playingVoiceUrl,
                    isVoicePlaying = isVoicePlaying,
                    voicePlayProgress = voicePlayProgress,
                    bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                    profileLinkTitles = profileLinkTitleState.titles,
                    profileLinkMissingLinks = profileLinkTitleState.missingLinks,
                    isNpcSatelliteGuide = guideUiState.isNpcSatelliteGuide,
                    mediaAdaptiveRotationEnabled = guideUiState.mediaSettings.mediaAdaptiveRotationEnabled,
                    includeTargetPageInHeavyRender = preloadPolicy.includeTargetPageInHeavyRender,
                    guidePagerBeyondViewportPageCount = preloadPolicy.guidePagerBeyondViewportPageCount,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    onOpenExternal = pageActions.openExternal,
                    onOpenGuide = pageActions.openGuideInPage,
                    onSaveMedia = pageActions.saveGuideMedia,
                    onSaveMediaPack = pageActions.saveGuideMediaPack,
                    onToggleBgmFavorite = guideViewModel::requestToggleBgmFavorite,
                    onRequestProfileLinkTitles = guideViewModel::requestProfileLinkTitles,
                    onToggleVoicePlayback = pageActions.toggleVoicePlayback,
                    onScrollBoundsChange = { canScrollBackward, canScrollForward ->
                        bottomBarChromeState.updateScrollBounds(canScrollBackward, canScrollForward)
                    },
                    onListScrollInProgressChange = {},
                    onSelectedVoiceLanguageChange = guideViewModel::updateSelectedVoiceLanguage,
                )
            }
            AppTopEndActionBarOverlay {
                LiquidActionBar(
                    backdrop = topBarBackdrop,
                    layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                    items = actionItems,
                )
            }
        }
    }
}
