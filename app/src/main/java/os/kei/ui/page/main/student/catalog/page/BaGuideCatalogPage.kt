@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.host.pager.rememberPagerTargetWarmDataActive
import os.kei.ui.page.main.host.pager.shouldRenderStablePageContent
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogV2ListContent
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmTabContent
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmFloatingBottomChrome
import os.kei.ui.page.main.student.catalog.component.bgm.rememberBaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.component.rememberBaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.resolvePlaybackArtworkImageUrl
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogViewModel
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogFilterSortState
import os.kei.ui.page.main.widget.chrome.LocalSearchAutoFocusEnabled
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.perf.ReportPagerPerformanceState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BaGuideCatalogPage(
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    liquidBottomBarEnabled: Boolean = true,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    preloadingEnabled: Boolean = false,
    enableSearchBar: Boolean = true,
    openBgmPlaybackToken: Long = 0L,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val pageScope = rememberCoroutineScope()
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val preloadPolicy =
        remember(preloadingEnabled) {
            UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled)
        }
    val pageTitle = stringResource(R.string.ba_catalog_page_title)
    val accent = MiuixTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val panelBackground = if (isDark) Color(0xFF10141B) else MiuixTheme.colorScheme.background
    var activationCount by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        activationCount++
        onDispose { }
    }
    val pageChromeBackdrop: LayerBackdrop =
        key("ba-catalog-page-$activationCount-$isDark") {
            rememberLayerBackdrop()
        }
    val bottomChromeBackdrop: LayerBackdrop =
        key("ba-catalog-bottom-$activationCount-$isDark") {
            rememberLayerBackdrop()
        }

    val loadFailedText = stringResource(R.string.ba_catalog_load_failed)
    val refreshFailedKeepCacheText = stringResource(R.string.ba_catalog_refresh_failed_keep_cached)
    val exportDoneText = stringResource(R.string.ba_catalog_transfer_export_done)
    val exportFailedText = stringResource(R.string.ba_catalog_transfer_export_failed)
    val studentExportSuccessText = stringResource(R.string.ba_catalog_transfer_student_export_success)
    val allExportSuccessText = stringResource(R.string.ba_catalog_transfer_all_export_success)
    val bgmExportSuccessText = stringResource(R.string.ba_catalog_bgm_export_success)
    val catalogViewModel: BaGuideCatalogViewModel = viewModel()
    val catalogDataState by catalogViewModel.dataState.collectAsStateWithLifecycle()
    val catalogListDerivedStates by catalogViewModel.catalogListDerivedStates.collectAsStateWithLifecycle()
    val studentBgmListDerivedState by catalogViewModel.studentBgmListDerivedState.collectAsStateWithLifecycle()
    val favoriteBgmListDerivedState by catalogViewModel.favoriteBgmListDerivedState.collectAsStateWithLifecycle()
    val studentBgmDisplayedDerivedState by catalogViewModel.studentBgmDisplayedDerivedState.collectAsStateWithLifecycle()
    val favoriteBgms by catalogViewModel.favoriteBgms.collectAsStateWithLifecycle()
    BaGuideCatalogPageBindEffects(
        catalogViewModel = catalogViewModel,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        initialFetchDelayMs = preloadPolicy.initialFetchDelayMs,
        loadFailedText = loadFailedText,
        refreshFailedKeepCacheText = refreshFailedKeepCacheText,
    )

    val tabs = BaGuideCatalogPageTab.entries
    val pageState = rememberBaGuideCatalogPageStateHolder()
    val pagerState =
        rememberMainLoadedPagerState(
            initialPage = pageState.selectedTabIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0)),
            pageCount = tabs.size,
        )
    val filterSortState = rememberBaGuideCatalogFilterSortState()
    val catalogSortMode = filterSortState.sortMode
    val catalogFavoriteEntries = filterSortState.favoriteCatalogEntries
    val catalogSelectedFilterOptions = filterSortState.selectedFilterOptions
    BaGuideCatalogPageDerivedEffects(
        catalogViewModel = catalogViewModel,
        catalogDataState = catalogDataState,
        filterSortState = filterSortState,
        favoriteBgms = favoriteBgms,
        pageState = pageState,
    )
    val chromeTabs = rememberBaGuideCatalogChromeTabs()
    val chromeScrollState = rememberBaGuideBgmBottomChromeScrollState(scrollThreshold = 56.dp)
    val nativeBgmMediaNotificationEnabled by
        catalogViewModel.nativeBgmMediaNotificationEnabled.collectAsStateWithLifecycle()
    val playbackCoordinator =
        rememberBaGuideBgmPlaybackCoordinator(
            context = appContext,
            favorites = favoriteBgms,
            nativeMediaNotificationEnabled = nativeBgmMediaNotificationEnabled,
        )
    val playbackUiState by playbackCoordinator.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(openBgmPlaybackToken) {
        if (openBgmPlaybackToken <= 0L) return@LaunchedEffect
        val playbackTabIndex = tabs.indexOf(BaGuideCatalogPageTab.Bgm)
        if (playbackTabIndex < 0) return@LaunchedEffect
        pageState.activatePlaybackTab(playbackTabIndex)
        pagerState.scrollToPage(playbackTabIndex)
        chromeScrollState.expand()
    }

    val chromeActivePageIndex =
        if (pagerState.isScrollInProgress) {
            pagerState.targetPage
        } else {
            pageState.selectedTabIndex
        }.coerceIn(0, tabs.lastIndex)
    val chromeActiveTab = tabs.getOrElse(chromeActivePageIndex) { BaGuideCatalogPageTab.Student }
    LaunchedEffect(chromeActiveTab) {
        chromeScrollState.expand()
        if (chromeActiveTab.catalogTab == null) {
            filterSortState.showFilterPopup = false
        }
    }
    val chromeCurrentTitle = stringResource(id = chromeActiveTab.labelRes)
    val chromeFilterDefinitions =
        remember(catalogDataState.catalog, chromeActiveTab) {
            chromeActiveTab.catalogTab
                ?.let { tab ->
                    catalogDataState.catalog.filterDefinitions(tab).filter { it.type == 0 }
                }.orEmpty()
        }
    val chromeFilterEnabled = chromeFilterDefinitions.isNotEmpty()
    val chromeSearchQuery = pageState.searchQueryFor(chromeActiveTab)
    val chromeSearchPlaceholder =
        stringResource(
            chromeActiveTab.searchPlaceholderRes,
        )
    val chromePlaybackFavorite =
        remember(
            playbackUiState.selectedAudioUrl,
            playbackUiState.queue,
            playbackUiState.favorites,
        ) {
            playbackUiState.selectedFavorite
        }
    val chromeArtworkImageUrl =
        remember(chromePlaybackFavorite) {
            chromePlaybackFavorite
                ?.resolvePlaybackArtworkImageUrl()
                .orEmpty()
        }
    val chromePlaybackProgress = pageState.playbackSliderPreview ?: playbackUiState.runtimeState.progress
    val bgmCacheState =
        rememberBaGuideCatalogBgmCacheState(
            context = context,
            favorites = favoriteBgms,
            refreshWhenVisible = pageState.showTransferSheet,
        )
    val transferExportAction =
        rememberBaGuideCatalogJsonExportAction(
            context,
            pageScope,
            exportDoneText,
            exportFailedText,
        )
    val importActions =
        rememberBaGuideCatalogImportActions(
            context = context,
            pageScope = pageScope,
            filterSortState = filterSortState,
            onPreviewStateChange = pageState::updateImportPreviewState,
        )

    LaunchedEffect(pagerState.settledPage) {
        pageState.updateSettledPage(pagerState.settledPage)
    }
    ReportPagerPerformanceState(
        scope = "guide_catalog_music_pager",
        currentPage = tabs.getOrElse(pagerState.currentPage) { BaGuideCatalogPageTab.Student }.name,
        targetPage = tabs.getOrElse(pagerState.targetPage) { BaGuideCatalogPageTab.Student }.name,
        scrolling = pagerState.isScrollInProgress,
    )

    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val keyboardLift =
        rememberAppFloatingKeyboardLift(
            focusedLift = 18.dp,
            restingBottomGap = navigationBottom,
        )
    val searchAutoFocusEnabled = LocalSearchAutoFocusEnabled.current
    val bottomChromeTargetPadding = navigationBottom + if (pageState.searchInputActive) keyboardLift else 0.dp

    BackHandler(enabled = pageState.searchVisible) {
        pageState.closeSearch()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(panelBackground)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accent.copy(alpha = if (isDark) 0.20f else 0.08f),
                                MiuixTheme.colorScheme.surface.copy(alpha = if (isDark) 0.10f else 0.55f),
                                panelBackground,
                            ),
                    ),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(bottomChromeBackdrop),
        ) {
            val targetWarmDataActive =
                rememberPagerTargetWarmDataActive(
                    pagerState = pagerState,
                    activationDistance = CATALOG_PAGER_TARGET_WARM_DATA_DISTANCE,
                ).value
            MainLoadedPager(
                state = pagerState,
                userScrollEnabled = !pageState.sliderInteractionActive,
                animationsEnabled = transitionAnimationsEnabled,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageChromeBackdrop),
            ) { pageIndex ->
                val pageTab = tabs.getOrElse(pageIndex) { BaGuideCatalogPageTab.Student }
                val renderHeavyContent =
                    pagerState.shouldRenderStablePageContent(
                        pageIndex = pageIndex,
                        includeTargetPageInHeavyRender = preloadPolicy.includeTargetPageInHeavyRender,
                        targetWarmDataActive = targetWarmDataActive,
                    )
                val pageSearchQuery = pageState.searchQueryFor(pageTab)
                key(pageTab.name) {
                    if (!renderHeavyContent) {
                        BaGuideCatalogMusicPlaceholder(
                            label = stringResource(pageTab.labelRes),
                            topPadding = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                            bottomPadding = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                        )
                    } else {
                        when {
                            pageTab.catalogTab != null -> {
                                val catalogTab = pageTab.catalogTab
                                BaGuideCatalogV2ListContent(
                                    tab = catalogTab,
                                    filterSortState = filterSortState,
                                    derivedState =
                                        catalogListDerivedStates[catalogTab]
                                            ?: BaGuideCatalogListDerivedState.Empty,
                                    searchQuery = pageSearchQuery,
                                    loading = catalogDataState.loading,
                                    error = catalogDataState.error,
                                    accent = accent,
                                    innerPadding =
                                        PaddingValues(
                                            top = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                                            bottom = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                                        ),
                                    nestedScrollConnection = chromeScrollState,
                                    isPageActive = pageIndex == pagerState.settledPage,
                                    onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                                    onOpenGuide = onOpenGuide,
                                )
                            }

                            pageTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm -> {
                                BaGuideStudentBgmTabContent(
                                    catalogSyncedAtMs = catalogDataState.catalog.syncedAtMs,
                                    favorites = favoriteBgms,
                                    derivedState = studentBgmListDerivedState,
                                    displayedDerivedState = studentBgmDisplayedDerivedState,
                                    onRequestDisplayedDerivedState = catalogViewModel::requestStudentBgmDisplayedDerivedState,
                                    playbackCoordinator = playbackCoordinator,
                                    playbackState = playbackUiState,
                                    searchQuery = pageSearchQuery,
                                    loading = catalogDataState.loading,
                                    innerPadding =
                                        PaddingValues(
                                            top = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                                            bottom = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                                        ),
                                    nestedScrollConnection = chromeScrollState,
                                    accent = accent,
                                    isPageActive = pageIndex == pagerState.settledPage,
                                    onSliderInteractionChanged = pageState::updateSliderInteractionActive,
                                    onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                                    onListScrollInProgressChange = {},
                                    onNowPlayingVisibilityChange = {},
                                    onToggleBgmFavorite = catalogViewModel::toggleBgmFavorite,
                                    onRemoveBgmFavorite = catalogViewModel::removeBgmFavorite,
                                    showNowPlayingOverlay = false,
                                    onOpenGuide = onOpenGuide,
                                )
                            }

                            pageTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm -> {
                                BaGuideFavoriteBgmMusicContent(
                                    catalog = catalogDataState.catalog,
                                    favorites = favoriteBgms,
                                    derivedState = favoriteBgmListDerivedState,
                                    playbackCoordinator = playbackCoordinator,
                                    playbackState = playbackUiState,
                                    accent = accent,
                                    bottomBarScrollConnection = chromeScrollState,
                                    topPadding = CATALOG_MUSIC_CONTENT_TOP_PADDING,
                                    bottomPadding = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                                    isPageActive = pageIndex == pagerState.settledPage,
                                    onSliderInteractionChanged = pageState::updateSliderInteractionActive,
                                    onScrollBoundsChange = chromeScrollState::expandForStaticContent,
                                    onRemoveBgmFavorite = catalogViewModel::removeBgmFavorite,
                                    onOpenGuide = onOpenGuide,
                                )
                            }
                        }
                    }
                }
            }
            BaGuideCatalogMusicTopBar(
                title = pageTitle,
                accent = accent,
                onBack = onBack,
                showSortPopup = filterSortState.showSortPopup,
                sortMode = catalogSortMode,
                showFilterPopup = filterSortState.showFilterPopup,
                filterEnabled = chromeFilterEnabled,
                filterDefinitions = chromeFilterDefinitions,
                selectedFilterOptions = catalogSelectedFilterOptions,
                onSort = filterSortState::openSortPopup,
                onDismissSort = { filterSortState.showSortPopup = false },
                onSelectSortMode = filterSortState::selectSortMode,
                onFilter = filterSortState::openFilterPopup,
                onDismissFilter = { filterSortState.showFilterPopup = false },
                onToggleFilterOption = filterSortState::toggleFilterOption,
                onClearFilters = filterSortState::clearFilters,
                onTransfer = pageState::openTransferSheet,
                onRefresh = catalogViewModel::requestRefresh,
                backdrop = pageChromeBackdrop,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            BaGuideCatalogTransferSheet(
                show = pageState.showTransferSheet,
                onDismissRequest = pageState::closeTransferSheet,
                mediaSaveCustomEnabled = transferExportAction.saveLocationState.mediaSaveCustomEnabled,
                mediaSaveFixedTreeUri = transferExportAction.saveLocationState.mediaSaveFixedTreeUri,
                playbackSettingsState =
                    BaGuideCatalogPlaybackSettingsState(
                        nativeBgmMediaNotificationEnabled = nativeBgmMediaNotificationEnabled,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onNativeBgmMediaNotificationChange = { enabled ->
                            if (enabled && !notificationPermissionGranted) {
                                onRequestNotificationPermission()
                            }
                            catalogViewModel.setNativeBgmMediaNotificationEnabled(enabled)
                            playbackCoordinator.updateNativeMediaNotificationEnabled(enabled)
                        },
                    ),
                onMediaSaveCustomEnabledChange = transferExportAction.saveLocationState.onMediaSaveCustomEnabledChange,
                onPickMediaSaveLocation = transferExportAction.saveLocationState.onPickMediaSaveLocation,
                onExportAllFavorites = {
                    pageState.closeTransferSheet()
                    transferExportAction.exportJsonFrom(
                        { buildCatalogAllFavoritesExportJsonAsync(filterSortState.favoriteCatalogEntries) },
                        "keios-ba-favorites.json",
                        allExportSuccessText,
                    )
                },
                onImportAllFavorites = {
                    pageState.closeTransferSheet()
                    importActions.importAllFavoritesLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/*",
                            "*/*",
                        ),
                    )
                },
                onExportStudentFavorites = {
                    pageState.closeTransferSheet()
                    transferExportAction.exportJsonFrom(
                        { buildCatalogFavoritesExportJsonAsync(filterSortState.favoriteCatalogEntries) },
                        "keios-ba-student-favorites.json",
                        studentExportSuccessText,
                    )
                },
                onImportStudentFavorites = {
                    pageState.closeTransferSheet()
                    importActions.importStudentFavoritesLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/*",
                            "*/*",
                        ),
                    )
                },
                onExportBgmFavorites = {
                    pageState.closeTransferSheet()
                    transferExportAction.exportJsonFrom(
                        { buildBgmFavoritesExportJsonAsync() },
                        "keios-ba-bgm-favorites.json",
                        bgmExportSuccessText,
                    )
                },
                onImportBgmFavorites = {
                    pageState.closeTransferSheet()
                    importActions.importBgmFavoritesLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/*",
                            "*/*",
                        ),
                    )
                },
                bgmCacheSummary = bgmCacheState.summary,
                onCacheAllBgm = {
                    pageState.closeTransferSheet()
                    bgmCacheState.onCacheAllBgm()
                },
                onCleanInvalidBgmCache = {
                    pageState.closeTransferSheet()
                    bgmCacheState.onCleanInvalidBgmCache()
                },
            )
            BaGuideCatalogImportPreviewSheet(
                state = pageState.importPreviewState,
                onDismissRequest = { pageState.updateImportPreviewState(null) },
                onConfirm = {
                    pageState.importPreviewState?.let(importActions.confirmFavoritesImport)
                },
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(196.dp)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        panelBackground.copy(alpha = if (isDark) 0.86f else 0.88f),
                                        panelBackground.copy(alpha = if (isDark) 0.96f else 0.98f),
                                    ),
                            ),
                        ),
            )
        }
        BaGuideBgmFloatingBottomChrome(
            accent = accent,
            scrollState = chromeScrollState,
            dockTabs = chromeTabs,
            currentTrackTitle =
                chromePlaybackFavorite
                    ?.studentTitle
                    ?.ifBlank { chromeCurrentTitle }
                    ?: chromeCurrentTitle,
            artworkImageUrl = chromeArtworkImageUrl,
            isPlaying = playbackUiState.runtimeState.isPlaying,
            playbackProgress = chromePlaybackProgress,
            onPlaybackProgressChange = { progress ->
                pageState.updatePlaybackSliderPreview(progress)
            },
            onPlaybackProgressChangeFinished = { progress ->
                val favorite = chromePlaybackFavorite ?: return@BaGuideBgmFloatingBottomChrome
                pageState.updatePlaybackSliderPreview(null)
                playbackCoordinator.seek(favorite, progress)
            },
            onPlaybackSliderInteractionChanged = { active ->
                pageState.updateSliderInteractionActive(active)
            },
            onPlayPauseClick = {
                val favorite = chromePlaybackFavorite ?: return@BaGuideBgmFloatingBottomChrome
                playbackCoordinator.toggle(favorite)
            },
            onPreviousClick = {
                playbackCoordinator.selectOffset(offset = -1)
            },
            onNextClick = {
                playbackCoordinator.selectOffset(offset = 1)
            },
            searchVisible = enableSearchBar && pageState.searchVisible,
            searchInputActive = enableSearchBar && pageState.searchInputActive,
            searchQuery = chromeSearchQuery,
            searchPlaceholder = chromeSearchPlaceholder,
            onSearchQueryChange = { query ->
                pageState.updateSearchQuery(chromeActiveTab, query)
            },
            onSearchInputActiveChange = { active ->
                pageState.updateSearchInputActive(active)
            },
            selectedDockKey = chromeActiveTab.name,
            onSelectedDockKeyChange = { keyName ->
                pageState.closeSearch()
                tabs
                    .indexOfFirst { it.name == keyName }
                    .takeIf { it >= 0 }
                    ?.let { index ->
                        pageState.updateSelectedTabIndex(index)
                        pageScope.launch {
                            if (transitionAnimationsEnabled) {
                                pagerState.animateToPage(
                                    target = index,
                                    animationsEnabled = true,
                                    durationMillis =
                                        catalogPagerSwitchDurationMillis(
                                            kotlin.math.abs(index - pagerState.settledPage),
                                        ),
                                )
                            } else {
                                pagerState.scrollToPage(index)
                            }
                        }
                    }
            },
            onCompactDockClick = {
                pageState.closeSearch()
                chromeScrollState.expand()
            },
            onSearchClick = {
                if (enableSearchBar) {
                    pageState.openSearch(searchAutoFocusEnabled)
                    chromeScrollState.expand()
                }
            },
            backdrop = bottomChromeBackdrop,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = bottomChromeTargetPadding + 12.dp,
                    ),
        )
    }
}
