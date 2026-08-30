@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.widget.chrome.LocalAppManagedSceneBackdrop
import os.kei.ui.page.main.widget.chrome.appPageBackdropBaseColor
import os.kei.ui.page.main.widget.glass.AppScrollEdgeEffect
import os.kei.ui.page.main.widget.glass.AppScrollEdgeSide
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmDockTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideMemoryLobbyListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListDerivedState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLiftState
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogPageContent(
    pageTitle: String,
    accent: Color,
    isDark: Boolean,
    panelBackground: Color,
    pageChromeBackdrop: LayerBackdrop,
    bottomChromeBackdrop: LayerBackdrop,
    pagerState: MainLoadedPagerState,
    tabs: List<BaGuideCatalogPageTab>,
    pageState: BaGuideCatalogPageStateHolder,
    filterSortState: BaGuideCatalogFilterSortState,
    catalogFavoriteEntries: Map<Long, Long>,
    catalogDataState: BaGuideCatalogDataUiState,
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    memoryLobbyListDerivedState: BaGuideMemoryLobbyListDerivedState,
    studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    pendingBgmFavoriteUndo: GuideBgmFavoriteItem?,
    favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackUiState: BaGuideBgmPlaybackUiState,
    chromeScrollState: BaGuideBgmBottomChromeScrollState,
    chromeTabs: List<BaGuideBgmDockTab>,
    chromePresentation: BaGuideCatalogChromePresentation,
    transferExportAction: BaGuideCatalogJsonExportAction,
    importActions: BaGuideCatalogImportActions,
    bgmCacheState: BaGuideCatalogBgmCacheState,
    nativeBgmMediaNotificationEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    allExportSuccessText: String,
    studentExportSuccessText: String,
    bgmExportSuccessText: String,
    mediaAdaptiveRotationEnabled: Boolean,
    transitionAnimationsEnabled: Boolean,
    searchAutoFocusEnabled: Boolean,
    enableSearchBar: Boolean,
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
    onRequestVisibleCatalogImages: (List<String>) -> Unit,
    pageActions: BaGuideCatalogPageActions,
    onRequestNotificationPermission: () -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topScrimHeight =
        maxOf(
            CATALOG_MUSIC_CONTENT_TOP_PADDING,
            statusTop + AppChromeTokens.liquidActionBarOuterHeight + AppChromeTokens.topBarToHeaderGap,
        )
    val keyboardLiftState =
        rememberAppFloatingKeyboardLiftState(
            focusedLift = 18.dp,
            restingBottomGap = navigationBottom,
        )
    val keyboardLiftProvider = remember(keyboardLiftState) { { keyboardLiftState.value } }
    val catalogSortMode = filterSortState.sortMode
    val catalogSelectedFilterOptions = filterSortState.selectedFilterOptions
    val requestVisibleCatalogImages by rememberUpdatedState(onRequestVisibleCatalogImages)
    val pagerSwitchMotion = rememberBaGuideCatalogPagerSwitchMotion()
    val playbackForegroundImageUrls =
        remember(
            chromePresentation.artworkImageUrl,
            chromePresentation.playbackFavorite,
        ) {
            buildBaGuidePlaybackForegroundImageUrls(
                artworkImageUrl = chromePresentation.artworkImageUrl,
                playbackFavorite = chromePresentation.playbackFavorite,
            )
        }

    BackHandler(enabled = pageState.searchVisible) {
        pageState.closeSearch()
    }
    val initialContentCanReveal =
        remember(
            chromePresentation.activeTab,
            chromePresentation.activeCatalogTab,
            catalogDataState,
            catalogListDerivedStates,
            studentBgmListDerivedState,
            memoryLobbyListDerivedState,
            favoriteBgmListDerivedState,
        ) {
            isBaGuideCatalogInitialContentReady(
                activeTab = chromePresentation.activeTab,
                activeCatalogTab = chromePresentation.activeCatalogTab,
                catalogDataState = catalogDataState,
                catalogListDerivedStates = catalogListDerivedStates,
                studentBgmListDerivedState = studentBgmListDerivedState,
                memoryLobbyListDerivedState = memoryLobbyListDerivedState,
                favoriteBgmListDerivedState = favoriteBgmListDerivedState,
            )
        }
    var initialContentRevealed by remember {
        mutableStateOf(initialContentCanReveal)
    }
    LaunchedEffect(initialContentCanReveal) {
        if (initialContentCanReveal) {
            initialContentRevealed = true
        }
    }
    LaunchedEffect(initialContentRevealed, playbackForegroundImageUrls) {
        if (initialContentRevealed) {
            requestVisibleCatalogImages(playbackForegroundImageUrls)
        }
    }
    val initialContentFadeMs =
        resolvedMotionDuration(CatalogInitialContentCrossfadeMs, transitionAnimationsEnabled)
    val catalogSceneBackdrop = rememberBaGuideCatalogSceneBackdrop()

    // What the scroll edges blur: the wallpaper composite under the list that slides beneath them.
    // `pageChromeBackdrop` records the pager, so there is no feedback loop — the edges are siblings drawn
    // after it. Sampling `bottomChromeBackdrop` would include the edges themselves.
    val managedSceneBackdrop = LocalAppManagedSceneBackdrop.current
    val scrollEdgeBackdrop: Backdrop =
        if (managedSceneBackdrop != null) {
            rememberCombinedBackdrop(managedSceneBackdrop, pageChromeBackdrop)
        } else {
            pageChromeBackdrop
        }
    // The page's real base, never `panelBackground` — that is transparent while a background paints, and
    // tinting with it is what turned both edges black.
    val scrollEdgeTint = appPageBackdropBaseColor()

    Box(modifier = Modifier.fillMaxSize().pageRootTestTag(KeiOsTestTags.BaGuideCatalogPageRoot)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .layerBackdrop(catalogSceneBackdrop),
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(panelBackground)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        accent.copy(alpha = if (isDark) 0.20f else 0.08f),
                                        MiuixTheme.colorScheme.surface.copy(alpha = if (isDark) 0.10f else 0.55f),
                                        // Same RGB, zero alpha: ending on `panelBackground` while it is
                                        // `Color.Transparent` walks the gradient's colour toward black.
                                        MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                    ),
                            ),
                        ),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(bottomChromeBackdrop),
        ) {
            CompositionLocalProvider(LocalLiquidParentBackdrop provides catalogSceneBackdrop) {
                Crossfade(
                    targetState = initialContentRevealed,
                    animationSpec = tween(durationMillis = initialContentFadeMs),
                    label = "BaGuideCatalogInitialContent",
                ) { contentReady ->
                    if (contentReady) {
                        BaGuideCatalogPagePager(
                            pagerState = pagerState,
                            tabs = tabs,
                            pageState = pageState,
                            filterSortState = filterSortState,
                            catalogDataState = catalogDataState,
                            catalogListDerivedStates = catalogListDerivedStates,
                            catalogFavoriteEntries = catalogFavoriteEntries,
                            studentBgmListDerivedState = studentBgmListDerivedState,
                            memoryLobbyListDerivedState = memoryLobbyListDerivedState,
                            studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
                            favoriteBgmListDerivedState = favoriteBgmListDerivedState,
                            favoriteBgms = favoriteBgms,
                            pendingBgmFavoriteUndo = pendingBgmFavoriteUndo,
                            favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
                            pageActions = pageActions,
                            playbackCoordinator = playbackCoordinator,
                            playbackUiState = playbackUiState,
                            chromeScrollState = chromeScrollState,
                            pageChromeBackdrop = pageChromeBackdrop,
                            catalogSceneBackdrop = catalogSceneBackdrop,
                            transitionAnimationsEnabled = transitionAnimationsEnabled,
                            mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                            accent = accent,
                            onOpenGuide = onOpenGuide,
                            onRequestVisibleCatalogImages = onRequestVisibleCatalogImages,
                            modifier =
                                Modifier
                                    .drawWithContent {
                                        drawContent()
                                        val veilAlpha = pagerSwitchMotion.veilAlpha
                                        if (veilAlpha > 0f) {
                                            drawRect(scrollEdgeTint.copy(alpha = veilAlpha))
                                        }
                                    },
                        )
                    } else {
                        BaGuideCatalogInitialLoadingContent(accent = accent)
                    }
                }
            }
            AppScrollEdgeEffect(
                backdrop = scrollEdgeBackdrop,
                side = AppScrollEdgeSide.Top,
                height = topScrimHeight,
                tint = scrollEdgeTint,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            BaGuideCatalogMusicTopBar(
                title = pageTitle,
                accent = accent,
                onBack = onBack,
                sortMode = catalogSortMode,
                showFilterPopup = filterSortState.showFilterPopup,
                filterEnabled = chromePresentation.filterEnabled,
                filterDefinitions = chromePresentation.filterDefinitions,
                selectedFilterOptions = catalogSelectedFilterOptions,
                showMorePopup = pageState.showMorePopup,
                incrementalRefreshIntervalHours = pageState.catalogIncrementalRefreshIntervalHours,
                selectedStudentCatalogTab = pageState.selectedStudentCatalogTab,
                studentCatalogSwitchEnabled = chromePresentation.activeTab == BaGuideCatalogPageTab.Student,
                catalogListActionsEnabled = chromePresentation.activeCatalogTab != null,
                onSelectSortMode = { mode ->
                    filterSortState.selectSortMode(mode)
                    pageState.closeMorePopup()
                },
                onSelectStudentCatalogTab = { tab ->
                    pageState.updateSelectedStudentCatalogTab(tab)
                    filterSortState.showFilterPopup = false
                    pageState.closeMorePopup()
                },
                onFilter = {
                    pageState.closeMorePopup()
                    filterSortState.openFilterPopup()
                },
                onDismissFilter = { filterSortState.showFilterPopup = false },
                onToggleFilterOption = filterSortState::toggleFilterOption,
                onClearFilters = filterSortState::clearFilters,
                onMore = {
                    filterSortState.showFilterPopup = false
                    pageState.toggleMorePopup()
                },
                onDismissMore = pageState::closeMorePopup,
                onTransfer = {
                    pageState.closeMorePopup()
                    pageState.openTransferSheet()
                },
                onSelectIncrementalRefreshIntervalHours = pageState::updateCatalogIncrementalRefreshIntervalHours,
                onRefresh = {
                    pageState.closeMorePopup()
                    filterSortState.showFilterPopup = false
                    pageActions.onRefresh()
                },
                backdrop = pageChromeBackdrop,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            BaGuideCatalogTransferSheetRoute(
                pageState = pageState,
                pageActions = pageActions,
                playbackCoordinator = playbackCoordinator,
                transferExportAction = transferExportAction,
                importActions = importActions,
                bgmCacheState = bgmCacheState,
                nativeBgmMediaNotificationEnabled = nativeBgmMediaNotificationEnabled,
                notificationPermissionGranted = notificationPermissionGranted,
                allExportSuccessText = allExportSuccessText,
                studentExportSuccessText = studentExportSuccessText,
                bgmExportSuccessText = bgmExportSuccessText,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
            BaGuideCatalogImportPreviewSheet(
                state = pageState.importPreviewState,
                onDismissRequest = { pageState.updateImportPreviewState(null) },
                onConfirm = {
                    pageState.importPreviewState?.let(importActions.confirmFavoritesImport)
                },
            )
            AppScrollEdgeEffect(
                backdrop = scrollEdgeBackdrop,
                side = AppScrollEdgeSide.Bottom,
                height = CATALOG_BOTTOM_SCROLL_EDGE_HEIGHT,
                tint = scrollEdgeTint,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        AnimatedVisibility(
            visible = initialContentRevealed,
            enter = fadeIn(animationSpec = tween(durationMillis = initialContentFadeMs)),
            exit = fadeOut(animationSpec = tween(durationMillis = initialContentFadeMs)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BaGuideCatalogBottomChromeRoute(
                accent = accent,
                scrollState = chromeScrollState,
                dockTabs = chromeTabs,
                playbackFavorite = chromePresentation.playbackFavorite,
                currentTitle = chromePresentation.currentTitle,
                artworkImageUrl = chromePresentation.artworkImageUrl,
                playbackUiState = playbackUiState,
                searchEnabled = enableSearchBar,
                pageState = pageState,
                searchQuery = chromePresentation.searchQuery,
                searchPlaceholder = chromePresentation.searchPlaceholder,
                activeTab = chromePresentation.activeTab,
                tabs = tabs,
                pagerState = pagerState,
                transitionAnimationsEnabled = transitionAnimationsEnabled,
                searchAutoFocusEnabled = searchAutoFocusEnabled,
                playbackCoordinator = playbackCoordinator,
                pagerSwitchMotion = pagerSwitchMotion,
                backdrop = bottomChromeBackdrop,
                modifier =
                    Modifier
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = navigationBottom + 12.dp,
                        ).offset {
                            val lift = if (pageState.searchInputActive) keyboardLiftProvider() else 0.dp
                            IntOffset(x = 0, y = -lift.roundToPx())
                        },
            )
        }
    }
}

@Composable
internal fun rememberBaGuideCatalogSceneBackdrop(): LayerBackdrop = rememberLayerBackdrop()

@Composable
private fun BaGuideCatalogInitialLoadingContent(accent: Color) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    top = CATALOG_MUSIC_CONTENT_TOP_PADDING + AppChromeTokens.topBarToHeaderGap,
                    bottom = CATALOG_MUSIC_CONTENT_BOTTOM_PADDING,
                    start = appPageEdgePaddingStart(),
                    end = appPageEdgePaddingEnd(),
                ),
        contentAlignment = Alignment.TopCenter,
    ) {
        AppAronaLoadingPanel(accent = accent)
    }
}

private fun isBaGuideCatalogInitialContentReady(
    activeTab: BaGuideCatalogPageTab,
    activeCatalogTab: BaGuideCatalogTab?,
    catalogDataState: BaGuideCatalogDataUiState,
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    memoryLobbyListDerivedState: BaGuideMemoryLobbyListDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
): Boolean {
    if (!catalogDataState.error.isNullOrBlank()) return true
    val catalog = catalogDataState.catalog
    return when {
        activeCatalogTab != null -> {
            val derivedState = catalogListDerivedStates[activeCatalogTab]
            catalog.entries(activeCatalogTab).isNotEmpty() &&
                derivedState != null &&
                !derivedState.deriving
        }

        activeTab.specialTab == BaGuideCatalogSpecialTab.MemoryLobby ->
            catalog.hasAnyGuideCatalogEntry() &&
                memoryLobbyListDerivedState.allStudentEntries.isNotEmpty() &&
                !memoryLobbyListDerivedState.deriving

        activeTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm ->
            catalog.hasAnyGuideCatalogEntry() &&
                studentBgmListDerivedState.allStudentEntries.isNotEmpty() &&
                !studentBgmListDerivedState.deriving

        activeTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm ->
            catalog.hasAnyGuideCatalogEntry() && !favoriteBgmListDerivedState.deriving

        else -> catalog.hasAnyGuideCatalogEntry()
    }
}

private fun BaGuideCatalogBundle.hasAnyGuideCatalogEntry(): Boolean =
    entriesByTab.values.any { entries -> entries.isNotEmpty() }

private val CATALOG_BOTTOM_SCROLL_EDGE_HEIGHT = 196.dp

private const val CatalogInitialContentCrossfadeMs = 140
