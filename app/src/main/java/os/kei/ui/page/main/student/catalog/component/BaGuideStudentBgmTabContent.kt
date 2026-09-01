@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedInput
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.visibleStudentBgmEntriesWithFavoriteVisibility
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import os.kei.ui.testing.KeiOsTestTags

private const val STUDENT_BGM_ENTRY_START_INDEX = 1

@Composable
internal fun BaGuideStudentBgmTabContent(
    catalogSyncedAtMs: Long,
    catalogSceneBackdrop: Backdrop,
    favorites: List<GuideBgmFavoriteItem>,
    derivedState: BaGuideStudentBgmListDerivedState,
    displayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    onRequestDisplayedDerivedState: (BaGuideStudentBgmDisplayedInput) -> Unit,
    onRequestVisibleImages: (List<String>) -> Unit,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackState: BaGuideBgmPlaybackUiState,
    nowPlayingVisible: Boolean,
    nowPlayingExpanded: Boolean,
    seekPreviewProgress: Float?,
    sliderInteractionActive: Boolean,
    searchQuery: String,
    loading: Boolean,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    accent: Color,
    isPageActive: Boolean,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onListScrollInProgressChange: (Boolean) -> Unit,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onNowPlayingVisibleChange: (Boolean) -> Unit,
    onNowPlayingExpandedChange: (Boolean) -> Unit,
    onSeekPreviewProgressChange: (Float?) -> Unit,
    onStudentBgmSliderInteractionChanged: (Boolean) -> Unit,
    onNowPlayingVisibilityChange: (Boolean) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    onRemoveBgmFavorite: (String) -> Unit,
    showNowPlayingOverlay: Boolean = true,
    onOpenGuide: (String) -> Unit,
    onRequestGuideDetailTab: (String, GuideBottomTab) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pageScope = rememberCoroutineScope()
    val requestVisibleImages by rememberUpdatedState(onRequestVisibleImages)
    val lookupCoordinator =
        remember(pageScope) {
            BaGuideStudentBgmLookupCoordinator(scope = pageScope)
        }
    val lookupStates by lookupCoordinator.states.collectAsStateWithLifecycle()
    val tabState = rememberBaGuideStudentBgmTabStateHolder(searchQuery)
    val selectedAudioUrl = playbackState.selectedAudioUrl
    val selectedPlaybackIsPlaying by remember(playbackCoordinator, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.runtimeStateFlow
                .map { runtime -> runtime.isPlaying }
                .distinctUntilChanged()
        } else {
            emptyFlow()
        }
    }.collectAsStateWithLifecycle(initialValue = playbackCoordinator.runtimeState.isPlaying)
    val queueMode = playbackState.queueMode
    val bgmMissingText = stringResource(R.string.ba_catalog_student_bgm_toast_missing)
    val bgmResolveFailedText = stringResource(R.string.ba_catalog_student_bgm_toast_resolve_failed)

    val favoriteByNormalizedSourceUrl = derivedState.favoriteByNormalizedSourceUrl
    val favoriteAudioUrls = derivedState.favoriteAudioUrls
    val favoriteContentIds = derivedState.favoriteContentIds
    val allStudentEntries = derivedState.allStudentEntries
    val filteredEntries = derivedState.filteredEntries
    var favoritesHidden by rememberSaveable { mutableStateOf(false) }
    val visibleFilteredEntries =
        remember(filteredEntries, favoriteContentIds, favoritesHidden) {
            visibleStudentBgmEntriesWithFavoriteVisibility(
                filteredEntries = filteredEntries,
                favoriteContentIds = favoriteContentIds,
                favoritesHidden = favoritesHidden,
            )
        }
    val effectiveLoading = loading || (derivedState.deriving && allStudentEntries.isEmpty())
    val listState = rememberLazyListState()
    // Two columns on a tablet or an unfolded fold, flowing row-major so the list keeps one order and one
    // scroll. Every music row is the same height, so they simply pair up.
    val columnsPerRow = appPageColumnCount()
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(visibleFilteredEntries.size, tabState) {
        tabState.resetVisibleCount(visibleFilteredEntries.size)
    }
    LaunchedEffect(isPageActive, listState, visibleFilteredEntries.size, columnsPerRow, snapshotFlowManager, tabState) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                Triple(
                    lastVisible,
                    layoutInfo.totalItemsCount,
                    layoutInfo.visibleItemsInfo.size.coerceAtLeast(6),
                )
            }.distinctUntilChanged()
            .collect { (lastVisible, totalCount, viewportItems) ->
                if (tabState.visibleCount >= visibleFilteredEntries.size) return@collect
                if (totalCount <= 0) return@collect
                val triggerIndex = (totalCount - 1 - STUDENT_BGM_LOAD_MORE_THRESHOLD).coerceAtLeast(0)
                if (lastVisible < triggerIndex) return@collect
                tabState.appendVisibleBatch(
                    totalCount = visibleFilteredEntries.size,
                    // Items are rows here, and a batch is measured in entries: two columns halve the item
                    // count for the same amount of list on screen, so without this the page appends half
                    // as much per step and the list runs dry sooner than it used to.
                    viewportItems = viewportItems * columnsPerRow,
                )
            }
    }
    val displayedEntries =
        remember(visibleFilteredEntries, tabState.visibleCount) {
            if (tabState.visibleCount >= visibleFilteredEntries.size) {
                visibleFilteredEntries
            } else {
                visibleFilteredEntries.subList(0, tabState.visibleCount)
            }
        }

    fun setNowPlayingVisible(visible: Boolean) {
        onNowPlayingVisibleChange(visible)
        onNowPlayingVisibilityChange(visible && selectedAudioUrl.isNotBlank())
    }

    fun setSliderInteractionActive(active: Boolean) {
        onStudentBgmSliderInteractionChanged(active)
        onSliderInteractionChanged(active)
    }
    val actions =
        rememberBaGuideStudentBgmActions(
            context = context,
            lookupCoordinator = lookupCoordinator,
            lookupStates = lookupStates,
            favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl,
            selectedAudioUrl = selectedAudioUrl,
            playbackCoordinator = playbackCoordinator,
            setNowPlayingVisible = ::setNowPlayingVisible,
            onOpenGuide = onOpenGuide,
            onRequestGuideDetailTab = onRequestGuideDetailTab,
            onToggleFavorite = onToggleBgmFavorite,
            onRemoveFavorite = onRemoveBgmFavorite,
            bgmMissingText = bgmMissingText,
            bgmResolveFailedText = bgmResolveFailedText,
        )

    val displayedInput =
        remember(
            displayedEntries,
            lookupStates,
            favoriteByNormalizedSourceUrl,
            favoriteAudioUrls,
        ) {
            BaGuideStudentBgmDisplayedInput(
                displayedEntries = displayedEntries,
                lookupStates = lookupStates,
                favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl,
                favoriteAudioUrls = favoriteAudioUrls,
            )
        }
    LaunchedEffect(displayedInput, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        onRequestDisplayedDerivedState(displayedInput)
    }
    val displayedBgmModel = displayedDerivedState.model
    val displayedRows = displayedBgmModel.rows
    val entryRows =
        remember(displayedRows, columnsPerRow) {
            baGuideCatalogEntryRows(entries = displayedRows, columnsPerRow = columnsPerRow)
        }
    // Below the rows on purpose: the preload window is expressed in *item* indices, so it can only be
    // resolved once the row grouping those items come from exists. `displayedRows` and
    // `displayedEntries` are the same entries in the same order, which is what lets one row shape
    // index into the other -- the mapping this effect already relied on before there were rows.
    LaunchedEffect(isPageActive, listState, displayedEntries, entryRows, snapshotFlowManager, lookupCoordinator) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                BaGuideVisibleItemRange(
                    firstItemIndex = visibleItems.firstOrNull()?.index ?: -1,
                    lastItemIndex = visibleItems.lastOrNull()?.index ?: -1,
                    visibleItemCount = visibleItems.size,
                )
            }.distinctUntilChanged()
            .collect { visibleItemRange ->
                // Resolved through the rows: one visible item is two entries in two columns.
                val visibleEntryRange =
                    baGuideCatalogVisibleEntryRange(
                        rows = entryRows,
                        visibleItemRange = visibleItemRange,
                        entryStartIndex = STUDENT_BGM_ENTRY_START_INDEX,
                    )
                val imageUrls =
                    buildBaGuideCatalogVisibleImageRequestUrls(
                        displayedEntries = displayedEntries,
                        visibleItemRange = visibleEntryRange,
                        entryStartIndex = 0,
                    )
                requestVisibleImages(imageUrls)
                val prewarmEntries =
                    buildBaGuideStudentBgmVisiblePrewarmEntries(
                        displayedEntries = displayedEntries,
                        visibleItemRange = visibleEntryRange,
                        entryStartIndex = 0,
                    )
                lookupCoordinator.prewarmVisibleNetwork(prewarmEntries)
            }
    }
    val displayedPlayableFavorites = displayedBgmModel.playableFavorites
    LaunchedEffect(playbackCoordinator, displayedPlayableFavorites, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.updateQueue(displayedPlayableFavorites)
        }
    }
    val selectedIndex = displayedPlayableFavorites.indexOfFirst { it.audioUrl == selectedAudioUrl }
    val selectedFavorite = displayedPlayableFavorites.getOrNull(selectedIndex)
    LaunchedEffect(
        selectedFavorite?.audioUrl,
        selectedFavorite?.studentImageUrl,
        selectedFavorite?.imageUrl,
    ) {
        selectedFavorite?.let { favorite ->
            requestVisibleImages(
                listOf(
                    favorite.studentImageUrl,
                    favorite.imageUrl,
                ),
            )
        }
    }
    val showNowPlaying = showNowPlayingOverlay && selectedFavorite != null && nowPlayingVisible
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomChromePadding =
        if (showNowPlaying) {
            navigationBarBottom
        } else {
            innerPadding.calculateBottomPadding()
        }
    val nowPlayingBottomPadding = navigationBarBottom + AppChromeTokens.pageSectionGap

    LaunchedEffect(catalogSyncedAtMs) {
        lookupCoordinator.clear()
    }
    LaunchedEffect(displayedBgmModel.contentIds, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        lookupCoordinator.prewarmCached(displayedEntries)
    }
    LaunchedEffect(selectedFavorite?.audioUrl) {
        onSeekPreviewProgressChange(null)
    }
    LaunchedEffect(showNowPlaying) {
        onNowPlayingVisibilityChange(showNowPlaying)
    }
    LaunchedEffect(listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        var lastScrollBounds: Pair<Boolean, Boolean>? = null
        var lastScrollInProgress: Boolean? = null
        snapshotFlowManager
            .snapshotFlow {
                Triple(
                    listState.canScrollBackward,
                    listState.canScrollForward,
                    listState.isScrollInProgress,
                )
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward, scrolling) ->
                val nextScrollBounds = canScrollBackward to canScrollForward
                if (lastScrollBounds != nextScrollBounds) {
                    lastScrollBounds = nextScrollBounds
                    onScrollBoundsChange(canScrollBackward, canScrollForward)
                }
                if (lastScrollInProgress != scrolling) {
                    lastScrollInProgress = scrolling
                    onListScrollInProgressChange(scrolling)
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose { setNowPlayingVisible(false) }
    }
    DisposableEffect(Unit) {
        onDispose { setSliderInteractionActive(false) }
    }
    DisposableEffect(lifecycleOwner, selectedFavorite?.audioUrl) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    if (!playbackCoordinator.keepsPlaybackAfterPageStop) {
                        selectedFavorite?.let { favorite ->
                            playbackCoordinator.pause(favorite)
                        }
                    }
                    setNowPlayingVisible(false)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(selectedFavorite?.audioUrl, queueMode, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        playbackCoordinator.prepareSelected()
    }
    val showEmptyStatus = !effectiveLoading && visibleFilteredEntries.isEmpty()
    val entryListGap = rememberBaGuideCatalogEntryListGap()

    val edgeStackState =
        rememberAppEdgeStackState(stackLine = innerPadding.calculateTopPadding())
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
        // Wraps only the list: the now-playing bar below is a Box sibling and must keep its own
        // bottom alignment, so it stays outside the shifted, clipped region.
        AppEdgeStackKeepAlive(
            state = edgeStackState,
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !sliderInteractionActive,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = appEdgeStackKeepAliveTopPadding(innerPadding.calculateTopPadding()),
                    bottom =
                        listBottomChromePadding +
                            AppChromeTokens.pageSectionGap +
                            if (showNowPlaying) {
                                if (nowPlayingExpanded) 210.dp else 96.dp
                            } else {
                                0.dp
                            },
                    start = appPageEdgePaddingStart(),
                    end = appPageEdgePaddingEnd(),
                ),
            verticalArrangement = Arrangement.spacedBy(entryListGap),
        ) {
            if (effectiveLoading && allStudentEntries.isEmpty()) {
                item(
                    key = "student-bgm-loading",
                    contentType = "student_bgm_status",
                ) {
                    AppAronaLoadingPanel(accent = accent)
                }
            } else {
                item(
                    key = "student-bgm-header",
                    contentType = "student_bgm_header",
                ) {
                    BaGuideStudentBgmHeader(
                        totalCount = allStudentEntries.size,
                        displayedCount = visibleFilteredEntries.size,
                        resolvedCount = displayedBgmModel.resolvedCount,
                        favoriteCount = favoriteContentIds.size,
                        searchActive = searchQuery.isNotBlank(),
                        favoritesHidden = favoritesHidden,
                        accent = accent,
                        onToggleFavoritesHidden = {
                            if (favoriteContentIds.isNotEmpty()) {
                                favoritesHidden = !favoritesHidden
                            }
                        },
                    )
                }
            }

            if (showEmptyStatus) {
                item(
                    key = "student-bgm-empty",
                    contentType = "student_bgm_status",
                ) {
                    LiquidInfoBlock(
                        backdrop = catalogSceneBackdrop,
                        title = stringResource(R.string.ba_catalog_empty_title),
                        subtitle = stringResource(R.string.ba_catalog_empty_subtitle_search),
                        accent = accent,
                    )
                }
            } else {
                items(
                    items = entryRows,
                    key = { it.entries.first().entry.contentId },
                    contentType = { "student_bgm_entry" },
                ) { entryRow ->
                    BaGuideCatalogEntryRowLayout(
                        row = entryRow,
                        columnsPerRow = columnsPerRow,
                        horizontalGap = entryListGap,
                    ) { row, _ ->
                    val entry = row.entry
                    val selected = row.readyAudioUrl == selectedAudioUrl
                    BaGuideStudentBgmCard(
                        // Tapping this card is what loads androidx.media3 at all. Only the first one is
                        // tagged: one handle is all a journey needs, and every row would put a resource
                        // id on a list that can run to hundreds. Keyed off the first row's id rather than
                        // an index, so `items` keeps the key and contentType spelling
                        // `BaGuideCatalogPageBackdropTest` pins.
                        modifier =
                            if (displayedRows.firstOrNull()?.entry?.contentId == entry.contentId) {
                                Modifier.testTag(KeiOsTestTags.BaGuideCatalogStudentBgmFirst)
                            } else {
                                Modifier
                            },
                        entry = entry,
                        lookupState = row.displayState,
                        selected = selected,
                        playing = selected && selectedPlaybackIsPlaying,
                        favorite = row.favorite,
                        accent = accent,
                        onOpenGuide = { actions.openStudentGuide(entry) },
                        onPlay = { actions.playEntry(entry) },
                        onToggleFavorite = { actions.toggleEntryFavorite(entry) },
                    )
                    }
                }
            }
        }
        }
        }

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = appPageEdgePaddingStart(),
                        end = appPageEdgePaddingEnd(),
                        bottom = nowPlayingBottomPadding,
                    ),
        ) {
            selectedFavorite?.let { favorite ->
                BaGuideStudentBgmNowPlayingMiniPlayer(
                    playbackCoordinator = playbackCoordinator,
                    favorite = favorite,
                    seekPreviewProgress = seekPreviewProgress,
                    queueIndex = selectedIndex.coerceAtLeast(0),
                    queueSize = displayedPlayableFavorites.size,
                    queueMode = queueMode,
                    accent = accent,
                    expanded = nowPlayingExpanded,
                    onExpandedChange = onNowPlayingExpandedChange,
                    onOpenQueue = {
                        pageScope.launch { listState.animateScrollToItem(0) }
                    },
                    onPrevious = {
                        actions.selectQueueOffset(-1, true, true)
                    },
                    onTogglePlayback = {
                        actions.togglePlayback(favorite)
                    },
                    onNext = {
                        actions.selectQueueOffset(1, true, true)
                    },
                    onSeekChanged = { progress ->
                        onSeekPreviewProgressChange(progress)
                    },
                    onSeekFinished = {
                        val seekProgress =
                            seekPreviewProgress
                                ?: playbackCoordinator.runtimeState.progress
                        playbackCoordinator.seek(favorite, seekProgress)
                        onSeekPreviewProgressChange(null)
                    },
                    onVolumeChanged = { volume ->
                        playbackCoordinator.updateVolume(favorite, volume.coerceIn(0f, 1f))
                    },
                    onSliderInteractionChanged = ::setSliderInteractionActive,
                    onToggleQueueMode = {
                        playbackCoordinator.toggleQueueMode()
                    },
                    onOpenGuide = { actions.openFavoriteGuide(favorite) },
                    runtimeActive = isPageActive,
                )
            }
        }
    }
}
