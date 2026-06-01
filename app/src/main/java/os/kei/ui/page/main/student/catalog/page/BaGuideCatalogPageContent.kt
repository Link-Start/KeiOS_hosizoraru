@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmBottomChromeScrollState
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmDockTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListDerivedState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLiftState
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
    studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    favoriteBgms: List<GuideBgmFavoriteItem>,
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
    transitionAnimationsEnabled: Boolean,
    searchAutoFocusEnabled: Boolean,
    enableSearchBar: Boolean,
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
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
            BaGuideCatalogPagePager(
                pagerState = pagerState,
                tabs = tabs,
                pageState = pageState,
                filterSortState = filterSortState,
                catalogDataState = catalogDataState,
                catalogListDerivedStates = catalogListDerivedStates,
                catalogFavoriteEntries = catalogFavoriteEntries,
                studentBgmListDerivedState = studentBgmListDerivedState,
                studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
                favoriteBgmListDerivedState = favoriteBgmListDerivedState,
                favoriteBgms = favoriteBgms,
                favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
                pageActions = pageActions,
                playbackCoordinator = playbackCoordinator,
                playbackUiState = playbackUiState,
                chromeScrollState = chromeScrollState,
                pageChromeBackdrop = pageChromeBackdrop,
                transitionAnimationsEnabled = transitionAnimationsEnabled,
                accent = accent,
                onOpenGuide = onOpenGuide,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topScrimHeight)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        panelBackground.copy(alpha = if (isDark) 0.96f else 0.94f),
                                        panelBackground.copy(alpha = if (isDark) 0.78f else 0.72f),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )
            BaGuideCatalogMusicTopBar(
                title = pageTitle,
                accent = accent,
                onBack = onBack,
                showSortPopup = filterSortState.showSortPopup,
                sortMode = catalogSortMode,
                showFilterPopup = filterSortState.showFilterPopup,
                filterEnabled = chromePresentation.filterEnabled,
                filterDefinitions = chromePresentation.filterDefinitions,
                selectedFilterOptions = catalogSelectedFilterOptions,
                onSort = filterSortState::openSortPopup,
                onDismissSort = { filterSortState.showSortPopup = false },
                onSelectSortMode = filterSortState::selectSortMode,
                onFilter = filterSortState::openFilterPopup,
                onDismissFilter = { filterSortState.showFilterPopup = false },
                onToggleFilterOption = filterSortState::toggleFilterOption,
                onClearFilters = filterSortState::clearFilters,
                onTransfer = pageState::openTransferSheet,
                onRefresh = pageActions.onRefresh,
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
            backdrop = bottomChromeBackdrop,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
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
