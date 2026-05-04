package os.kei.ui.page.main.student.catalog.page

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.debug.DebugBgmAlbumContent
import os.kei.ui.page.main.debug.DebugBgmDockTab
import os.kei.ui.page.main.debug.DebugBgmFloatingBottomChrome
import os.kei.ui.page.main.debug.DebugBgmTrack
import os.kei.ui.page.main.debug.rememberDebugBgmBottomChromeScrollState
import os.kei.ui.page.main.os.appLucideChevronLeftIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackRuntimeState
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmQueueMode
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogV2ListContent
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmTabContent
import os.kei.ui.page.main.student.catalog.component.applyFavoriteBgmQueueMode
import os.kei.ui.page.main.student.catalog.component.favoriteBgmRuntimeState
import os.kei.ui.page.main.student.catalog.component.filterAndSortBgmFavorites
import os.kei.ui.page.main.student.catalog.component.isFavoriteBgmCached
import os.kei.ui.page.main.student.catalog.component.playFavoriteBgm
import os.kei.ui.page.main.student.catalog.component.prepareFavoriteBgmPlayback
import os.kei.ui.page.main.student.catalog.component.seekFavoriteBgmPlayback
import os.kei.ui.page.main.student.catalog.component.toggleFavoriteBgmPlayback
import os.kei.ui.page.main.student.catalog.component.updateFavoriteBgmVolume
import os.kei.ui.page.main.student.catalog.component.favoriteCacheScope
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogSortMode
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogViewModel
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.rememberCatalogSyncProgress
import os.kei.ui.page.main.student.page.state.GuideDetailTabRequestStore
import os.kei.ui.page.main.student.section.gallery.formatAudioDuration
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.perf.ReportPagerPerformanceState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class BaGuideCatalogPageTab(
    val labelRes: Int,
    val compactLabelRes: Int,
    val catalogTab: BaGuideCatalogTab?,
    val specialTab: BaGuideCatalogSpecialTab? = null
) {
    Student(
        labelRes = R.string.ba_catalog_tab_student,
        compactLabelRes = R.string.ba_catalog_tab_student_short,
        catalogTab = BaGuideCatalogTab.Student
    ),
    NpcSatellite(
        labelRes = R.string.ba_catalog_tab_npc_satellite,
        compactLabelRes = R.string.ba_catalog_tab_npc_satellite_short,
        catalogTab = BaGuideCatalogTab.NpcSatellite
    ),
    StudentBgm(
        labelRes = R.string.ba_catalog_tab_student_bgm,
        compactLabelRes = R.string.ba_catalog_tab_student_bgm_short,
        catalogTab = null,
        specialTab = BaGuideCatalogSpecialTab.StudentBgm
    ),
    Bgm(
        labelRes = R.string.ba_catalog_tab_bgm,
        compactLabelRes = R.string.ba_catalog_tab_bgm,
        catalogTab = null,
        specialTab = BaGuideCatalogSpecialTab.FavoriteBgm
    )
}

private enum class BaGuideCatalogSpecialTab {
    StudentBgm,
    FavoriteBgm
}

@Composable
fun BaGuideCatalogPage(
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
    liquidBottomBarEnabled: Boolean = true,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    preloadingEnabled: Boolean = false,
    enableSearchBar: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val pageScope = rememberCoroutineScope()
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val preloadPolicy = remember(preloadingEnabled) {
        UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled)
    }
    val pageTitle = stringResource(R.string.ba_catalog_page_title)
    val searchLabel = stringResource(R.string.ba_catalog_search_label)
    val accent = MiuixTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val panelBackground = if (isDark) Color(0xFF10141B) else MiuixTheme.colorScheme.background
    var activationCount by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        activationCount++
        onDispose { }
    }
    val pageChromeBackdrop: LayerBackdrop = key("ba-catalog-page-$activationCount-$isDark") {
        rememberLayerBackdrop {
            drawRect(panelBackground)
            drawContent()
        }
    }
    val bottomChromeBackdrop: LayerBackdrop = key("ba-catalog-bottom-$activationCount-$isDark") {
        rememberLayerBackdrop {
            drawRect(panelBackground)
            drawContent()
        }
    }

    val loadFailedText = stringResource(R.string.ba_catalog_load_failed)
    val refreshFailedKeepCacheText = stringResource(R.string.ba_catalog_refresh_failed_keep_cached)
    val catalogViewModel: BaGuideCatalogViewModel = viewModel()
    val catalogDataState by catalogViewModel.dataState.collectAsState()
    LaunchedEffect(
        catalogViewModel,
        transitionAnimationsEnabled,
        preloadPolicy.initialFetchDelayMs,
        loadFailedText,
        refreshFailedKeepCacheText
    ) {
        catalogViewModel.bind(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            initialFetchDelayMs = preloadPolicy.initialFetchDelayMs,
            loadFailedText = loadFailedText,
            refreshFailedKeepCacheText = refreshFailedKeepCacheText
        )
    }

    val tabs = BaGuideCatalogPageTab.entries
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0)),
        pageCount = { tabs.size }
    )
    val filterSortState = rememberBaGuideCatalogFilterSortState()
    val chromeTabs = rememberBaGuideCatalogChromeTabs()
    val chromeScrollState = rememberDebugBgmBottomChromeScrollState(scrollThreshold = 56.dp)
    val favoriteBgms by GuideBgmFavoriteStore.favoritesFlow().collectAsState()
    var playbackSnapshot by remember { mutableStateOf(GuideBgmFavoritePlaybackStore.snapshot()) }
    var chromePlaybackState by remember {
        mutableStateOf(BaGuideBgmPlaybackRuntimeState(volume = playbackSnapshot.volume))
    }
    var playbackSliderPreview by remember { mutableStateOf<Float?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchInputActive by remember { mutableStateOf(false) }
    var sliderInteractionActive by remember { mutableStateOf(false) }
    var currentPanelHeightPx by remember { mutableIntStateOf(0) }
    var largestPanelHeightPx by remember { mutableIntStateOf(0) }

    val chromeActivePageIndex = if (pagerState.isScrollInProgress) {
        pagerState.targetPage
    } else {
        selectedTabIndex
    }.coerceIn(0, tabs.lastIndex)
    val chromeActiveTab = tabs.getOrElse(chromeActivePageIndex) { BaGuideCatalogPageTab.Student }
    val chromeCurrentTitle = stringResource(id = chromeActiveTab.labelRes)
    val chromeCurrentMeta = filterSortState.searchQuery.ifBlank { searchLabel }
    val chromePlaybackFavorite = favoriteBgms.firstOrNull { it.audioUrl == playbackSnapshot.selectedAudioUrl }
        ?: favoriteBgms.firstOrNull()
    val chromeQueueMode = remember(playbackSnapshot.queueModeName) {
        BaGuideBgmQueueMode.entries.firstOrNull { it.name == playbackSnapshot.queueModeName }
            ?: BaGuideBgmQueueMode.Continuous
    }
    val chromePlaybackProgress = playbackSliderPreview ?: chromePlaybackState.progress

    LaunchedEffect(Unit) {
        while (true) {
            playbackSnapshot = GuideBgmFavoritePlaybackStore.snapshot()
            val favorite = favoriteBgms.firstOrNull { it.audioUrl == playbackSnapshot.selectedAudioUrl }
            chromePlaybackState = favorite
                ?.let { favoriteBgmRuntimeState(appContext, it) }
                ?: BaGuideBgmPlaybackRuntimeState(volume = playbackSnapshot.volume)
            delay(500L)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTabIndex != pagerState.settledPage) {
            selectedTabIndex = pagerState.settledPage
        }
    }
    ReportPagerPerformanceState(
        scope = "guide_catalog_music_pager",
        currentPage = tabs.getOrElse(pagerState.currentPage) { BaGuideCatalogPageTab.Student }.name,
        targetPage = tabs.getOrElse(pagerState.targetPage) { BaGuideCatalogPageTab.Student }.name,
        scrolling = pagerState.isScrollInProgress
    )

    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val imeVisible = imeBottom > navigationBottom + 24.dp
    val panelResizeDeltaPx = if (imeVisible && largestPanelHeightPx > 0 && currentPanelHeightPx > 0) {
        (largestPanelHeightPx - currentPanelHeightPx).coerceAtLeast(0)
    } else {
        0
    }
    val panelResizeDelta = with(density) { panelResizeDeltaPx.toDp() }
    val consumedKeyboardInset = if (panelResizeDelta > 48.dp) panelResizeDelta else 0.dp
    val keyboardAvoidanceBottom = if (imeVisible) {
        maxOf(imeBottom - consumedKeyboardInset, navigationBottom)
    } else {
        navigationBottom
    }
    val bottomChromeTargetPadding = if (searchInputActive) keyboardAvoidanceBottom else navigationBottom

    BackHandler(enabled = searchVisible) {
        searchInputActive = false
        searchVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(panelBackground)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = if (isDark) 0.20f else 0.08f),
                        MiuixTheme.colorScheme.surface.copy(alpha = if (isDark) 0.10f else 0.55f),
                        panelBackground
                    )
                )
            )
            .onSizeChanged { size ->
                currentPanelHeightPx = size.height
                largestPanelHeightPx = maxOf(largestPanelHeightPx, size.height)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(bottomChromeBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(pageChromeBackdrop)
            )
            val progress = rememberCatalogSyncProgress(
                loading = catalogDataState.loading,
                animationsEnabled = transitionAnimationsEnabled
            )
            val progressColor = when {
                catalogDataState.loading -> Color(0xFF3B82F6)
                !catalogDataState.error.isNullOrBlank() -> Color(0xFFEF4444)
                else -> Color(0xFF22C55E)
            }
            HorizontalPager(
                state = pagerState,
                key = { index -> tabs[index].name },
                userScrollEnabled = !sliderInteractionActive,
                beyondViewportPageCount = preloadPolicy.catalogPagerBeyondViewportPageCount,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageTab = tabs.getOrElse(pageIndex) { BaGuideCatalogPageTab.Student }
                val renderHeavyContent = pageIndex == pagerState.currentPage ||
                    pageIndex == pagerState.settledPage ||
                    (preloadPolicy.includeTargetPageInHeavyRender && pageIndex == pagerState.targetPage)
                if (!renderHeavyContent) {
                    BaGuideCatalogMusicPlaceholder(
                        label = stringResource(pageTab.labelRes),
                        topPadding = CatalogMusicContentTopPadding,
                        bottomPadding = CatalogMusicContentBottomPadding
                    )
                } else {
                    when {
                        pageTab.catalogTab != null -> BaGuideCatalogV2ListContent(
                            tab = pageTab.catalogTab,
                            catalog = catalogDataState.catalog,
                            filterSortState = filterSortState,
                            loading = catalogDataState.loading,
                            error = catalogDataState.error,
                            accent = accent,
                            innerPadding = PaddingValues(
                                top = CatalogMusicContentTopPadding,
                                bottom = CatalogMusicContentBottomPadding
                            ),
                            nestedScrollConnection = chromeScrollState,
                            isPageActive = pageIndex == pagerState.currentPage,
                            onOpenGuide = onOpenGuide
                        )
                        pageTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm -> BaGuideStudentBgmTabContent(
                            catalog = catalogDataState.catalog,
                            searchQuery = filterSortState.searchQuery,
                            innerPadding = PaddingValues(
                                top = CatalogMusicContentTopPadding,
                                bottom = CatalogMusicContentBottomPadding
                            ),
                            nestedScrollConnection = chromeScrollState,
                            accent = accent,
                            isPageActive = pageIndex == pagerState.currentPage,
                            onSliderInteractionChanged = { sliderInteractionActive = it },
                            onScrollBoundsChange = { _, _ -> },
                            onListScrollInProgressChange = {},
                            onNowPlayingVisibilityChange = {},
                            showNowPlayingOverlay = false,
                            onOpenGuide = onOpenGuide
                        )
                        pageTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm -> BaGuideFavoriteBgmMusicContent(
                            searchQuery = filterSortState.searchQuery,
                            accent = accent,
                            listBackdrop = pageChromeBackdrop,
                            bottomBarScrollConnection = chromeScrollState,
                            topPadding = CatalogMusicContentTopPadding,
                            bottomPadding = CatalogMusicContentBottomPadding,
                            onSliderInteractionChanged = { sliderInteractionActive = it },
                            onOpenGuide = onOpenGuide
                        )
                    }
                }
            }
            BaGuideCatalogMusicTopBar(
                title = pageTitle,
                accent = accent,
                onBack = onBack,
                onSort = { filterSortState.selectSortMode(filterSortState.sortMode.next()) },
                onRefresh = catalogViewModel::requestRefresh,
                backdrop = pageChromeBackdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(196.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                panelBackground.copy(alpha = if (isDark) 0.86f else 0.88f),
                                panelBackground.copy(alpha = if (isDark) 0.96f else 0.98f)
                            )
                        )
                    )
            )
        }
        DebugBgmFloatingBottomChrome(
            accent = accent,
            scrollState = chromeScrollState,
            dockTabs = chromeTabs,
            currentTrackTitle = chromePlaybackFavorite
                ?.title
                ?.ifBlank { chromeCurrentTitle }
                ?: chromeCurrentTitle,
            isPlaying = chromePlaybackState.isPlaying,
            playbackProgress = chromePlaybackProgress,
            onPlaybackProgressChange = { progress ->
                playbackSliderPreview = progress
            },
            onPlaybackProgressChangeFinished = { progress ->
                val favorite = chromePlaybackFavorite ?: return@DebugBgmFloatingBottomChrome
                playbackSliderPreview = null
                chromePlaybackState = seekFavoriteBgmPlayback(
                    context = appContext,
                    favorite = favorite,
                    queueMode = chromeQueueMode,
                    progress = progress
                )
            },
            onPlaybackSliderInteractionChanged = { active ->
                sliderInteractionActive = active
                if (!active) playbackSliderPreview = null
            },
            onPlayPauseClick = {
                val favorite = chromePlaybackFavorite ?: return@DebugBgmFloatingBottomChrome
                val resumePosition = GuideBgmFavoritePlaybackStore
                    .progressFor(favorite.audioUrl)
                    ?.resumePositionMs
                    ?: 0L
                toggleFavoriteBgmPlayback(
                    context = appContext,
                    favorite = favorite,
                    queueMode = chromeQueueMode,
                    startPositionMs = resumePosition
                )
            },
            onPreviousClick = {
                selectChromeFavoriteOffset(
                    favorites = favoriteBgms,
                    selectedAudioUrl = playbackSnapshot.selectedAudioUrl,
                    offset = -1,
                    appContext = appContext,
                    queueMode = chromeQueueMode
                )
            },
            onNextClick = {
                selectChromeFavoriteOffset(
                    favorites = favoriteBgms,
                    selectedAudioUrl = playbackSnapshot.selectedAudioUrl,
                    offset = 1,
                    appContext = appContext,
                    queueMode = chromeQueueMode
                )
            },
            searchVisible = enableSearchBar && searchVisible,
            searchInputActive = enableSearchBar && searchInputActive,
            searchQuery = filterSortState.searchQuery,
            onSearchQueryChange = { filterSortState.searchQuery = it },
            onSearchInputActiveChange = { active ->
                searchInputActive = active
                if (active) searchVisible = true
            },
            selectedDockKey = chromeActiveTab.name,
            onSelectedDockKeyChange = { keyName ->
                searchInputActive = false
                searchVisible = false
                tabs.indexOfFirst { it.name == keyName }
                    .takeIf { it >= 0 }
                    ?.let { index ->
                        selectedTabIndex = index
                        pageScope.launch {
                            if (transitionAnimationsEnabled) {
                                pagerState.animateScrollToPage(index)
                            } else {
                                pagerState.scrollToPage(index)
                            }
                        }
                    }
            },
            onCompactDockClick = {
                searchInputActive = false
                searchVisible = false
                chromeScrollState.expand()
            },
            onSearchClick = {
                if (enableSearchBar) {
                    searchVisible = true
                    chromeScrollState.expand()
                }
            },
            backdrop = bottomChromeBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = bottomChromeTargetPadding + 12.dp
                )
        )
    }
}

@Composable
private fun rememberBaGuideCatalogChromeTabs(): List<DebugBgmDockTab> {
    val studentLabel = stringResource(R.string.ba_catalog_tab_student_short)
    val npcLabel = stringResource(R.string.ba_catalog_tab_npc_satellite_short)
    val studentBgmLabel = stringResource(R.string.ba_catalog_tab_student_bgm_short)
    val bgmLabel = stringResource(R.string.ba_catalog_tab_bgm)
    val studentIcon = ImageVector.vectorResource(R.drawable.ba_tab_profile_vector)
    val npcIcon = ImageVector.vectorResource(R.drawable.ba_tab_skill_vector)
    val musicIcon = ImageVector.vectorResource(R.drawable.ba_tab_bgm)
    val playbackIcon = ImageVector.vectorResource(R.drawable.ba_tab_play)
    return remember(
        studentLabel,
        npcLabel,
        studentBgmLabel,
        bgmLabel,
        studentIcon,
        npcIcon,
        musicIcon,
        playbackIcon
    ) {
        listOf(
            DebugBgmDockTab(BaGuideCatalogPageTab.Student.name, studentIcon, studentLabel),
            DebugBgmDockTab(BaGuideCatalogPageTab.NpcSatellite.name, npcIcon, npcLabel),
            DebugBgmDockTab(BaGuideCatalogPageTab.StudentBgm.name, musicIcon, studentBgmLabel),
            DebugBgmDockTab(BaGuideCatalogPageTab.Bgm.name, playbackIcon, bgmLabel)
        )
    }
}

private fun selectChromeFavoriteOffset(
    favorites: List<GuideBgmFavoriteItem>,
    selectedAudioUrl: String,
    offset: Int,
    appContext: Context,
    queueMode: BaGuideBgmQueueMode
) {
    if (favorites.isEmpty()) return
    val currentIndex = favorites.indexOfFirst { it.audioUrl == selectedAudioUrl }
        .takeIf { it >= 0 }
        ?: 0
    val nextIndex = (currentIndex + offset + favorites.size) % favorites.size
    val favorite = favorites[nextIndex]
    val resumePosition = GuideBgmFavoritePlaybackStore
        .progressFor(favorite.audioUrl)
        ?.resumePositionMs
        ?: 0L
    GuideBgmFavoritePlaybackStore.saveSelection(
        audioUrl = favorite.audioUrl,
        queueModeName = queueMode.name
    )
    playFavoriteBgm(
        context = appContext,
        favorite = favorite,
        queueMode = queueMode,
        startPositionMs = resumePosition,
        restart = offset != 0
    )
}

@Composable
private fun BaGuideFavoriteBgmMusicContent(
    searchQuery: String,
    accent: Color,
    listBackdrop: Backdrop,
    bottomBarScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    topPadding: Dp,
    bottomPadding: Dp,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onOpenGuide: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val favorites by GuideBgmFavoriteStore.favoritesFlow().collectAsState()
    val savedPlayback = remember { GuideBgmFavoritePlaybackStore.snapshot() }
    var selectedAudioUrl by rememberSaveable { mutableStateOf(savedPlayback.selectedAudioUrl) }
    var queueModeName by rememberSaveable {
        mutableStateOf(
            savedPlayback.queueModeName
                .takeIf { saved -> BaGuideBgmQueueMode.entries.any { it.name == saved } }
                ?: BaGuideBgmQueueMode.Continuous.name
        )
    }
    val queueMode = remember(queueModeName) {
        BaGuideBgmQueueMode.entries.firstOrNull { it.name == queueModeName }
            ?: BaGuideBgmQueueMode.Continuous
    }
    var runtimeState by remember {
        mutableStateOf(BaGuideBgmPlaybackRuntimeState(volume = savedPlayback.volume))
    }
    var cacheRevision by remember { mutableIntStateOf(0) }
    var cachingAudioUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    val displayedFavorites = remember(favorites, searchQuery) {
        filterAndSortBgmFavorites(
            favorites = favorites,
            searchQuery = searchQuery,
            sortMode = os.kei.ui.page.main.student.catalog.component.BaGuideBgmFavoriteSortMode.Recent
        )
    }
    val selectedFavorite = displayedFavorites.firstOrNull { it.audioUrl == selectedAudioUrl }
        ?: displayedFavorites.firstOrNull()
    val tracks = remember(displayedFavorites, cacheRevision) {
        displayedFavorites.map { favorite -> favorite.toDebugTrack() }
    }
    val favoritesByTrackId = remember(displayedFavorites) {
        displayedFavorites.associateBy { it.audioUrl }
    }
    val sectionTitle = selectedFavorite
        ?.title
        ?.ifBlank { stringResource(R.string.ba_catalog_bgm_track_fallback) }
        ?: stringResource(R.string.ba_catalog_bgm_empty_title)
    val sectionMeta = selectedFavorite
        ?.studentTitle
        ?.ifBlank { stringResource(R.string.ba_catalog_bgm_student_unknown) }
        ?: stringResource(
            R.string.ba_catalog_bgm_library_summary,
            favorites.size,
            displayedFavorites.count { isFavoriteBgmCached(appContext, it) }
        )
    val cacheSuccessText = stringResource(R.string.ba_catalog_bgm_cache_success)
    val cacheFailedText = stringResource(R.string.ba_catalog_bgm_cache_failed)

    fun playFavorite(favorite: GuideBgmFavoriteItem, restart: Boolean = false) {
        selectedAudioUrl = favorite.audioUrl
        GuideBgmFavoritePlaybackStore.saveSelection(favorite.audioUrl, queueMode.name)
        playFavoriteBgm(
            context = appContext,
            favorite = favorite,
            queueMode = queueMode,
            startPositionMs = GuideBgmFavoritePlaybackStore
                .progressFor(favorite.audioUrl)
                ?.resumePositionMs
                ?: 0L,
            restart = restart
        )
        runtimeState = favoriteBgmRuntimeState(appContext, favorite)
    }

    fun cacheFavorite(favorite: GuideBgmFavoriteItem) {
        if (favorite.audioUrl.isBlank() || favorite.audioUrl in cachingAudioUrls) return
        cachingAudioUrls = cachingAudioUrls + favorite.audioUrl
        scope.launch {
            val success = runCatching {
                BaGuideTempMediaCache.prefetchForGuide(
                    context = appContext,
                    sourceUrl = favoriteCacheScope(favorite),
                    rawUrls = listOf(favorite.audioUrl)
                )
                isFavoriteBgmCached(appContext, favorite)
            }.getOrDefault(false)
            cachingAudioUrls = cachingAudioUrls - favorite.audioUrl
            cacheRevision += 1
            Toast.makeText(
                context,
                if (success) cacheSuccessText else cacheFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(displayedFavorites) {
        selectedAudioUrl = when {
            displayedFavorites.isEmpty() -> ""
            selectedAudioUrl.isBlank() -> displayedFavorites.first().audioUrl
            displayedFavorites.none { it.audioUrl == selectedAudioUrl } -> displayedFavorites.first().audioUrl
            else -> selectedAudioUrl
        }
    }
    LaunchedEffect(selectedFavorite?.audioUrl, queueMode) {
        val favorite = selectedFavorite ?: return@LaunchedEffect
        prepareFavoriteBgmPlayback(
            context = appContext,
            favorite = favorite,
            queueMode = queueMode,
            startPositionMs = GuideBgmFavoritePlaybackStore
                .progressFor(favorite.audioUrl)
                ?.resumePositionMs
                ?: 0L
        )
        GuideBgmFavoritePlaybackStore.saveSelection(favorite.audioUrl, queueMode.name)
    }
    LaunchedEffect(selectedFavorite?.audioUrl, queueMode, displayedFavorites) {
        val favorite = selectedFavorite ?: return@LaunchedEffect
        while (true) {
            val state = favoriteBgmRuntimeState(appContext, favorite)
            runtimeState = state
            saveFavoriteProgress(favorite, state)
            if (
                state.isEnded &&
                queueMode == BaGuideBgmQueueMode.Continuous &&
                displayedFavorites.size > 1
            ) {
                val index = displayedFavorites.indexOfFirst { it.audioUrl == favorite.audioUrl }.coerceAtLeast(0)
                playFavorite(displayedFavorites[(index + 1) % displayedFavorites.size], restart = true)
                return@LaunchedEffect
            }
            delay(500L)
        }
    }

    DebugBgmAlbumContent(
        accent = accent,
        tracks = tracks,
        currentTrackId = selectedFavorite?.audioUrl.orEmpty(),
        isPlaying = runtimeState.isPlaying,
        repeatEnabled = queueMode == BaGuideBgmQueueMode.SingleLoop,
        playbackVolume = runtimeState.volume,
        isTrackFavorite = { id -> favoritesByTrackId.containsKey(id) },
        onRepeatClick = {
            val favorite = selectedFavorite ?: return@DebugBgmAlbumContent
            val nextMode = if (queueMode == BaGuideBgmQueueMode.Continuous) {
                BaGuideBgmQueueMode.SingleLoop
            } else {
                BaGuideBgmQueueMode.Continuous
            }
            queueModeName = nextMode.name
            GuideBgmFavoritePlaybackStore.saveSelection(favorite.audioUrl, nextMode.name)
            applyFavoriteBgmQueueMode(appContext, favorite, nextMode)
        },
        onPlayPauseClick = {
            val favorite = selectedFavorite ?: displayedFavorites.firstOrNull() ?: return@DebugBgmAlbumContent
            selectedAudioUrl = favorite.audioUrl
            toggleFavoriteBgmPlayback(
                context = appContext,
                favorite = favorite,
                queueMode = queueMode,
                startPositionMs = GuideBgmFavoritePlaybackStore
                    .progressFor(favorite.audioUrl)
                    ?.resumePositionMs
                    ?: 0L
            )
        },
        onVolumeChange = { volume ->
            selectedFavorite?.let { favorite ->
                runtimeState = updateFavoriteBgmVolume(appContext, favorite, volume)
            }
        },
        onVolumeChangeFinished = { volume ->
            selectedFavorite?.let { favorite ->
                runtimeState = updateFavoriteBgmVolume(appContext, favorite, volume)
            }
        },
        onSliderInteractionChanged = onSliderInteractionChanged,
        onTrackClick = { id ->
            favoritesByTrackId[id]?.let { favorite ->
                playFavorite(favorite, restart = id == selectedAudioUrl)
            }
        },
        onTrackFavoriteClick = { id ->
            GuideBgmFavoriteStore.removeFavorite(id)
            if (selectedAudioUrl == id) selectedAudioUrl = ""
        },
        onTrackOfflineClick = { id ->
            favoritesByTrackId[id]?.let(::cacheFavorite)
        },
        onTrackShareClick = { track ->
            favoritesByTrackId[track.id]?.let { favorite ->
                GuideDetailTabRequestStore.request(favorite.sourceUrl, GuideBottomTab.Gallery)
                onOpenGuide(favorite.sourceUrl)
            }
        },
        isTrackOfflineSaved = { id ->
            favoritesByTrackId[id]?.let { isFavoriteBgmCached(appContext, it) } == true
        },
        sectionTitle = sectionTitle,
        sectionMeta = sectionMeta,
        sectionFooterTitle = stringResource(R.string.ba_catalog_tab_bgm),
        offlineTrackCount = displayedFavorites.count { isFavoriteBgmCached(appContext, it) },
        listState = rememberLazyListState(),
        collapseProgress = 0f,
        bottomBarScrollConnection = bottomBarScrollConnection,
        userScrollEnabled = true,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        contentBackdrop = listBackdrop,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BaGuideCatalogMusicTopBar(
    title: String,
    accent: Color,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val sortContentDescription = stringResource(R.string.ba_catalog_action_sort)
    val refreshContentDescription = stringResource(R.string.ba_catalog_action_refresh)
    val moreContentDescription = stringResource(R.string.debug_component_lab_action_more)
    val sortIcon = appLucideSortIcon()
    val refreshIcon = appLucideRefreshIcon()
    val moreIcon = appLucideMoreIcon()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppChromeTokens.liquidActionBarOuterHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BaGuideCatalogTopIconButton(
                icon = appLucideChevronLeftIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = backdrop
            )
            LiquidActionBar(
                modifier = Modifier.height(AppChromeTokens.liquidActionBarOuterHeight),
                backdrop = backdrop,
                selectedIndex = 0,
                items = remember(
                    sortContentDescription,
                    refreshContentDescription,
                    moreContentDescription,
                    sortIcon,
                    refreshIcon,
                    moreIcon,
                    onSort,
                    onRefresh
                ) {
                    listOf(
                        LiquidActionItem(
                            icon = sortIcon,
                            contentDescription = sortContentDescription,
                            onClick = onSort
                        ),
                        LiquidActionItem(
                            icon = refreshIcon,
                            contentDescription = refreshContentDescription,
                            onClick = onRefresh
                        ),
                        LiquidActionItem(
                            icon = moreIcon,
                            contentDescription = moreContentDescription,
                            onClick = {}
                        )
                    )
                }
            )
        }
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 150.dp)
                .graphicsLayer { alpha = 0.92f }
        )
    }
}

@Composable
private fun BaGuideCatalogTopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backdrop: Backdrop
) {
    AppLiquidIconButton(
        backdrop = backdrop,
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = Modifier.height(AppChromeTokens.liquidActionBarOuterHeight),
        width = AppChromeTokens.liquidActionBarOuterHeight,
        height = AppChromeTokens.liquidActionBarOuterHeight,
        shape = CircleShape,
        iconTint = MiuixTheme.colorScheme.onBackground,
        variant = GlassVariant.Floating
    )
}

@Composable
private fun BaGuideCatalogMusicPlaceholder(
    label: String,
    topPadding: Dp,
    bottomPadding: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = topPadding,
                bottom = bottomPadding,
                start = AppChromeTokens.pageHorizontalPadding,
                end = AppChromeTokens.pageHorizontalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight
        )
    }
}

private fun BaGuideCatalogSortMode.next(): BaGuideCatalogSortMode {
    val modes = BaGuideCatalogSortMode.entries
    return modes[(modes.indexOf(this).coerceAtLeast(0) + 1) % modes.size]
}

private fun GuideBgmFavoriteItem.toDebugTrack(): DebugBgmTrack {
    val durationMs = GuideBgmFavoritePlaybackStore.progressFor(audioUrl)?.durationMs ?: 0L
    return DebugBgmTrack(
        id = audioUrl,
        title = studentTitle.ifBlank { title }.ifBlank { audioUrl },
        subtitle = title.ifBlank { note }.ifBlank { sourceUrl },
        durationLabel = if (durationMs > 0L) formatAudioDuration(durationMs) else "",
        searchAlias = listOf(title, studentTitle, note, sourceUrl, audioUrl)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )
}

private fun saveFavoriteProgress(
    favorite: GuideBgmFavoriteItem,
    state: BaGuideBgmPlaybackRuntimeState
) {
    if (state.durationMs > 0L || state.positionMs > 0L || state.isPlaying) {
        GuideBgmFavoritePlaybackStore.saveProgress(
            audioUrl = favorite.audioUrl,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying
        )
    }
}

private val CatalogMusicContentTopPadding = 136.dp
private val CatalogMusicContentBottomPadding = 224.dp
