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
import androidx.compose.foundation.lazy.LazyListScope
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
    val secondaryListState = rememberLazyListState()
    // Two lanes on a tablet or an unfolded fold, scrolling independently and alternating so each lane
    // stays sorted -- see `baGuideCatalogEntryLanes`. A status-only state has no entries to split.
    val pageColumnCount = appPageColumnCount()
    val columnCount = if (effectiveLoading && allStudentEntries.isEmpty()) 1 else pageColumnCount
    val laneStates =
        if (columnCount >= 2) listOf(listState, secondaryListState) else listOf(listState)
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(visibleFilteredEntries.size, tabState) {
        tabState.resetVisibleCount(visibleFilteredEntries.size)
    }
    LaunchedEffect(isPageActive, laneStates, visibleFilteredEntries.size, snapshotFlowManager, tabState) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                // Both lanes, each judged against its own end: a lane holds every other entry, so its own
                // item count is the right yardstick for "nearly through it". An empty second lane never
                // triggers, which is what keeps the single-lane condition unchanged.
                val nearEnd = laneStates.any { state -> state.reachedStudentBgmLoadMoreTrigger() }
                // Items across both lanes, because a batch is measured in entries and two lanes show
                // twice as many for the same amount of scrolling.
                val viewportItems =
                    laneStates
                        .sumOf { state -> state.layoutInfo.visibleItemsInfo.size }
                        .coerceAtLeast(6)
                nearEnd to viewportItems
            }.distinctUntilChanged()
            .collect { (nearEnd, viewportItems) ->
                if (tabState.visibleCount >= visibleFilteredEntries.size) return@collect
                if (!nearEnd) return@collect
                tabState.appendVisibleBatch(
                    totalCount = visibleFilteredEntries.size,
                    viewportItems = viewportItems,
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
    val laneRows =
        remember(displayedRows, columnCount) {
            baGuideCatalogEntryLanes(entries = displayedRows, columnCount = columnCount)
        }
    val laneEntryStartIndices =
        remember(columnCount) {
            List(columnCount) { lane -> if (lane == 0) STUDENT_BGM_ENTRY_START_INDEX else 0 }
        }
    // `displayedRows` and `displayedEntries` are the same entries in the same order, which is what lets a
    // lane index computed from one address the other -- the mapping this effect already relied on.
    LaunchedEffect(
        isPageActive,
        laneStates,
        displayedEntries,
        columnCount,
        laneEntryStartIndices,
        snapshotFlowManager,
        lookupCoordinator,
    ) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                // Both lanes at once, resolved back to flat entry indices: preloading follows the screen,
                // and with two lanes the screen is both of them at whatever offsets they have drifted to.
                baGuideCatalogVisibleLaneEntryIndices(
                    laneVisibleItemIndices = laneStates.baGuideCatalogLaneVisibleItemIndices(),
                    laneEntryStartIndices = laneEntryStartIndices,
                    columnCount = columnCount,
                    entryCount = displayedEntries.size,
                )
            }.distinctUntilChanged()
            .collect { visibleEntryIndices ->
                val imageUrls =
                    buildBaGuideCatalogVisibleImageRequestUrls(
                        displayedEntries = displayedEntries,
                        visibleItemIndices = visibleEntryIndices,
                        entryStartIndex = 0,
                    )
                requestVisibleImages(imageUrls)
                val prewarmEntries =
                    buildBaGuideStudentBgmVisiblePrewarmEntries(
                        displayedEntries = displayedEntries,
                        visibleItemIndices = visibleEntryIndices,
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
                    laneStates.baGuideCatalogAnyLaneCanScrollBackward(),
                    laneStates.baGuideCatalogAnyLaneCanScrollForward(),
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
        val laneContents =
            laneRows.mapIndexed { lane, rows ->
                val laneContent: LazyListScope.() -> Unit = {
                    // The loading panel and the header belong to the list, not to a column: emitted once,
                    // in the leading lane, rather than mirrored into both.
                    if (effectiveLoading && allStudentEntries.isEmpty()) {
                        if (lane == 0) {
                            item(
                                key = "student-bgm-loading",
                                contentType = "student_bgm_status",
                            ) {
                                AppAronaLoadingPanel(accent = accent)
                            }
                        }
                    } else if (lane == 0) {
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
                        if (lane == 0) {
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
                        }
                    } else {
                        items(
                            items = rows,
                            key = { it.entry.contentId },
                            contentType = { "student_bgm_entry" },
                        ) { row ->
                            val entry = row.entry
                            val selected = row.readyAudioUrl == selectedAudioUrl
                            BaGuideStudentBgmCard(
                                // Tapping this card is what loads androidx.media3 at all. Only the first
                                // one is tagged: one handle is all a journey needs, and every row would
                                // put a resource id on a list that can run to hundreds. Keyed off the
                                // list's first entry rather than an index, which with alternating lanes
                                // is still the first card of the leading lane.
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
                laneContent
            }
        BaGuideCatalogLaneLists(
            laneStates = laneStates,
            startPadding = appPageEdgePaddingStart(),
            endPadding = appPageEdgePaddingEnd(),
            topPadding = appEdgeStackKeepAliveTopPadding(innerPadding.calculateTopPadding()),
            bottomPadding =
                listBottomChromePadding +
                    AppChromeTokens.pageSectionGap +
                    if (showNowPlaying) {
                        if (nowPlayingExpanded) 210.dp else 96.dp
                    } else {
                        0.dp
                    },
            horizontalGap = entryListGap,
            verticalGap = entryListGap,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            userScrollEnabled = !sliderInteractionActive,
            lanes = laneContents,
        )
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
                        pageScope.launch { laneStates.forEach { state -> state.animateScrollToItem(0) } }
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

/**
 * Whether this lane is within [STUDENT_BGM_LOAD_MORE_THRESHOLD] of its own last item.
 *
 * An empty lane never triggers: with one lane the second has no items, and a `lastVisible` of -1 against
 * a trigger index of 0 would otherwise read as "at the end" and append forever.
 */
private fun androidx.compose.foundation.lazy.LazyListState.reachedStudentBgmLoadMoreTrigger(): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount <= 0) return false
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= (layoutInfo.totalItemsCount - 1 - STUDENT_BGM_LOAD_MORE_THRESHOLD).coerceAtLeast(0)
}
