@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideDatabaseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.LocalGuideMediaGifTargetRequester
import os.kei.ui.page.main.student.LocalGuideMediaGifTargets
import os.kei.ui.page.main.student.LocalGuideMediaImageBitmaps
import os.kei.ui.page.main.student.LocalGuideMediaImageMissingKeys
import os.kei.ui.page.main.student.LocalGuideMediaImageRequester
import os.kei.ui.page.main.student.page.component.BaStudentGuideBottomBar
import os.kei.ui.page.main.student.page.state.BaStudentGuideUiPreferencesStore
import os.kei.ui.page.main.widget.chrome.appSidebarAvailableAt
import os.kei.ui.page.main.widget.chrome.AppSidebarToggleSize
import os.kei.ui.page.main.student.page.component.GuideSidebarToggleTestTag
import os.kei.ui.page.main.student.page.component.GuideSidebarToggleGap
import os.kei.ui.page.main.host.pager.MainPagerSidebarToggle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import os.kei.ui.page.main.student.page.component.BaStudentGuideSidebar
import os.kei.ui.page.main.widget.chrome.appNavigationPlacementFor
import os.kei.ui.page.main.widget.chrome.AppNavigationPlacement
import os.kei.ui.page.main.widget.chrome.LocalAppNavigationPlacement
import os.kei.core.prefs.UiPrefs
import androidx.compose.ui.platform.LocalConfiguration
import os.kei.ui.page.main.student.page.component.BaStudentGuidePagerContent
import os.kei.ui.page.main.student.page.state.BaStudentGuideViewModel
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideForegroundAudioGuard
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideMediaSaveEvents
import os.kei.ui.page.main.student.page.state.BindBaStudentGuidePagerSyncEffects
import os.kei.ui.page.main.student.page.state.BindBaStudentGuidePlayerLifecycleEffects
import os.kei.ui.page.main.student.page.state.BindBaStudentGuidePrefetchEffects
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideVoiceListenerEffect
import os.kei.ui.page.main.student.page.state.BindBaStudentGuideVoiceProgressEffect
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideMediaPackSaveAction
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideMediaSaveAction
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuidePageActions
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideTabSelectCoordinator
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideTopBarActionItems
import os.kei.ui.page.main.student.page.state.rememberBaStudentGuideVoicePlayerController
import os.kei.ui.page.main.student.page.support.rememberGuideSyncProgress
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.appManagedPageBackgroundActive
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.chrome.LiquidToolbar
import os.kei.ui.page.main.widget.chrome.rememberAppPageBackdrop
import os.kei.ui.page.main.widget.chrome.rememberTabbedPageChromeScrollState
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.perf.ReportPagerPerformanceState
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BaStudentGuidePage(
    warmStartId: Long = 0L,
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
    val cacheStatusContentDescription = stringResource(R.string.guide_cd_cache_status)
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
    val navBackdrop = rememberAppPageBackdrop("nav-$activationCount")
    val guideWindowWidth = LocalConfiguration.current.screenWidthDp.dp
    // Top action bar uses its own backdrop instance to avoid cross-layer recursion.
    val topBarBackdrop = rememberAppPageBackdrop("topbar-$activationCount")
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = true)
    val scrollBehavior = MiuixScrollBehavior()

    val guideViewModel: BaStudentGuideViewModel =
        applicationViewModel { application ->
            BaStudentGuideViewModel(
                application = application,
                warmStartId = warmStartId,
            )
        }
    val guideUiState by guideViewModel.uiState.collectAsStateWithLifecycle()
    val guideMediaImageState by guideViewModel.mediaImageState.collectAsStateWithLifecycle()
    val profileLinkTitleState by guideViewModel.profileLinkTitleState.collectAsStateWithLifecycle()
    val pageChromeState by guideViewModel.pageChromeState.collectAsStateWithLifecycle()
    val voiceUiState by guideViewModel.voiceUiState.collectAsStateWithLifecycle()
    val contentPresentationState by guideViewModel.contentPresentationState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(guideViewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            guideViewModel.reloadIfStoredUrlChanged()
        }
    }
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
    val bottomTabsList =
        if (contentPresentationState.matches(info)) {
            contentPresentationState.bottomTabs
        } else {
            remember { GuideBottomTab.entries.toList() }
        }
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
    // Six sections is one past the five a bottom bar can label on a phone, so on a panel this page takes
    // the same converted shape the pager offers: a leading rail with the labels down the side.
    //
    // Its *own* stored answer, in the guide's own preferences file rather than the app's settings
    // surface -- see BaStudentGuideUiPreferencesStore. The two pages differ in the thing the choice
    // turns on, six sections against five, so wanting a rail here says nothing about wanting one there.
    // Below AppSidebarMinWindowWidth the preference is kept but not applied, exactly as
    // appNavigationPlacementFor does for the pager.
    var guideSidebarPreferred by remember { mutableStateOf(BaStudentGuideUiPreferencesStore.isSidebarPreferred()) }
    val guideNavigationPlacement =
        remember(guideWindowWidth, guideSidebarPreferred) {
            appNavigationPlacementFor(
                availableWidth = guideWindowWidth,
                sidebarPreferred = guideSidebarPreferred,
            ).let { placement ->
                // Only the converted shape is adopted here. A route has no app-level tab bar to move to
                // the top row, so `Top` would only strip this page's own top-row gutter.
                if (placement == AppNavigationPlacement.Sidebar) placement else AppNavigationPlacement.Bottom
            }
        }
    val guideUsesSidebar = guideNavigationPlacement == AppNavigationPlacement.Sidebar
    // The adaptable style keeps the convert button in *both* shapes. The rail carries its own; this is
    // the other half, in the top row where the pager puts it, so the rail is reachable from the page
    // that offers it rather than only from a preference set somewhere else. Hidden below the width a
    // rail may appear in at all -- offering a shape the window cannot hold is worse than not offering it.
    val guideSidebarOfferable = appSidebarAvailableAt(guideWindowWidth)
    val showGuideSidebarToggle = guideSidebarOfferable && !guideUsesSidebar
    val onGuideConvertToBottomBar: () -> Unit =
        remember {
            {
                guideSidebarPreferred = false
                BaStudentGuideUiPreferencesStore.setSidebarPreferred(false)
            }
        }
    val onGuideConvertToSidebar: () -> Unit =
        remember {
            {
                guideSidebarPreferred = true
                BaStudentGuideUiPreferencesStore.setSidebarPreferred(true)
            }
        }
    var bottomBarVisible by rememberSaveable { mutableStateOf(true) }
    val guidePageListStates = remember { mutableStateMapOf<Int, LazyListState>() }
    val fallbackGuideListState = remember { LazyListState() }
    val activeGuideListStateProvider =
        remember(pagerState, guidePageListStates, fallbackGuideListState) {
            {
                guidePageListStates[pagerState.currentPage] ?: fallbackGuideListState
            }
        }
    val bottomChromeScrollState =
        rememberTabbedPageChromeScrollState(
            visible = bottomBarVisible,
            activeListStateProvider = activeGuideListStateProvider,
            onVisibleChange = { bottomBarVisible = it },
        )
    val farJumpAlpha = remember { Animatable(1f) }
    var scrollToTopSignal by remember { mutableIntStateOf(0) }
    val selectBottomTabAction =
        rememberBaStudentGuideTabSelectCoordinator(
            bottomTabs = bottomTabsList,
            pagerState = pagerState,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            farJumpAlpha = farJumpAlpha,
            onShowBottomBarChange = { bottomBarVisible = it },
            onSelectedBottomTabIndexChange = { selectedIndex ->
                val selectedTab =
                    bottomTabsList
                        .getOrNull(selectedIndex)
                        ?: GuideBottomTab.Archive
                guideViewModel.updateSelectedBottomTab(selectedTab)
            },
            onScrollToTop = { scrollToTopSignal++ },
        )
    LaunchedEffect(guideUiState.requestedInitialBottomTab, bottomTabsList, selectBottomTabAction) {
        val targetTab = guideUiState.requestedInitialBottomTab ?: return@LaunchedEffect
        val targetIndex = bottomTabsList.indexOf(targetTab)
        if (targetIndex >= 0) {
            guideViewModel.requestInitialBottomTabHandled()
            selectBottomTabAction(targetIndex)
        }
    }
    LaunchedEffect(sourceUrl, activeBottomTab) {
        bottomChromeScrollState.showNow()
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
    val cacheStatusIcon = appLucideDatabaseIcon()
    var showCacheStatusSheet by rememberSaveable { mutableStateOf(false) }
    val actionItems =
        rememberBaStudentGuideTopBarActionItems(
            shareIcon = shareIcon,
            refreshIcon = refreshIcon,
            cacheStatusIcon = cacheStatusIcon,
            shareSourceContentDescription = shareSourceContentDescription,
            refreshContentDescription = refreshContentDescription,
            cacheStatusContentDescription = cacheStatusContentDescription,
            onShareSource = pageActions.shareSource,
            onRefresh = pageActions.requestRefresh,
            onOpenCacheStatus = {
                showCacheStatusSheet = true
            },
        )
    CompositionLocalProvider(
        LocalGuideMediaImageBitmaps provides guideMediaImageState.bitmaps,
        // Published so the page insets *itself* past the rail. The rail floats over the content rather
        // than narrowing it -- the same arrangement the pager uses, and what lets a managed background
        // still run the full width -- so every container that centres on the content column adds
        // AppSidebarWidth to its leading gutter off the back of this. Bottom is the default and a no-op.
        LocalAppNavigationPlacement provides guideNavigationPlacement,
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
                        .pageRootTestTag(KeiOsTestTags.BaStudentGuidePageRoot)
                        // Transparent while a non-Home background is painting, otherwise this opaque
                        // plate covers it and the setting does nothing on this page.
                        .background(
                            if (appManagedPageBackgroundActive()) {
                                Color.Transparent
                            } else {
                                MiuixTheme.colorScheme.background
                            },
                        ),
                topBar = {
                    AppTopBarSection(
                        title = pageTitle,
                        largeTitle = pageTitle,
                        scrollBehavior = scrollBehavior,
                        color = topBarMaterialBackdrop,
                        titleBackdrop = topBarBackdrop,
                        titleEndReserve = AppChromeTokens.topBarTitleActionReserve,
                        onTitleClick = {
                            scrollToTopSignal++
                        },
                        titleStartReserve =
                            if (showGuideSidebarToggle) {
                                AppChromeTokens.topBarTitleNavigationReserve +
                                    AppSidebarToggleSize +
                                    GuideSidebarToggleGap
                            } else {
                                null
                            },
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppLiquidNavigationButton(
                                    icon = appLucideBackIcon(),
                                    contentDescription = pageTitle,
                                    onClick = onBack,
                                    backdrop = topBarBackdrop,
                                )
                                if (showGuideSidebarToggle) {
                                    Spacer(modifier = Modifier.width(GuideSidebarToggleGap))
                                    MainPagerSidebarToggle(
                                        backdrop = topBarBackdrop,
                                        expanded = false,
                                        onClick = onGuideConvertToSidebar,
                                        testTag = GuideSidebarToggleTestTag,
                                    )
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    // Nothing to switch down here while the rail is up, and a bar plus a rail would be
                    // the same control twice.
                    if (!guideUsesSidebar) {
                    BaStudentGuideBottomBar(
                        visible = bottomBarVisible,
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
                        isLiquidEffectEnabled = true,
                        onSelectTab = selectBottomTabAction,
                        onExpand = {
                            bottomChromeScrollState.showNow()
                        },
                    )
                    }
                },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
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
                    farJumpAlphaProvider = { farJumpAlpha.value },
                    navBackdrop = navBackdrop.producer,
                    topBarBackdrop = topBarBackdrop.producer,
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
                    contentPresentationState = contentPresentationState,
                    guidePagerBeyondViewportPageCount = preloadPolicy.guidePagerBeyondViewportPageCount,
                    chromeNestedScrollConnection = bottomChromeScrollState.chromeNestedScrollConnection,
                    topBarNestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    onPageListStateChange = { pageIndex, listState ->
                        guidePageListStates[pageIndex] = listState
                    },
                    onOpenExternal = pageActions.openExternal,
                    onOpenGuide = pageActions.openGuideInPage,
                    onSaveMedia = pageActions.saveGuideMedia,
                    onSaveMediaPack = pageActions.saveGuideMediaPack,
                    onToggleBgmFavorite = guideViewModel::requestToggleBgmFavorite,
                    onRequestProfileLinkTitles = guideViewModel::requestProfileLinkTitles,
                    onToggleVoicePlayback = pageActions.toggleVoicePlayback,
                    scrollToTopSignal = scrollToTopSignal,
                    onScrollBoundsChange = { _, _ -> },
                    onListScrollInProgressChange = {},
                    onSelectedVoiceLanguageChange = guideViewModel::updateSelectedVoiceLanguage,
                )
                if (guideUsesSidebar) {
                    BaStudentGuideSidebar(
                        title = pageTitle,
                        tabs = bottomTabsList,
                        selectedIndex = pagerState.targetPage.coerceIn(bottomTabsList.indices),
                        backdrop = navBackdrop,
                        topInset = innerPadding.calculateTopPadding(),
                        bottomInset = innerPadding.calculateBottomPadding(),
                        onSelected = selectBottomTabAction,
                        onConvertToBottomBar = onGuideConvertToBottomBar,
                    )
                }
                }
            }
            AppTopEndActionBarOverlay {
                LiquidToolbar(
                    backdrop = topBarBackdrop,
                    actions = actionItems,
                )
            }
            BaStudentGuideCacheStatusSheet(
                show = showCacheStatusSheet,
                cacheStatus = guideDataState.cacheStatus,
                backdrop = topBarBackdrop,
                onDismissRequest = {
                    showCacheStatusSheet = false
                },
                onRefreshCurrentStudent = {
                    showCacheStatusSheet = false
                    pageActions.requestRefresh()
                },
                onClearCurrentStudentCache = {
                    showCacheStatusSheet = false
                    guideViewModel.requestClearCurrentGuideCache()
                },
            )
        }
    }
}
