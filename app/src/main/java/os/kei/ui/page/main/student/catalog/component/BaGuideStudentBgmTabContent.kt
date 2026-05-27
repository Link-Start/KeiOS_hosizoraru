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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit

@Composable
internal fun BaGuideStudentBgmTabContent(
    catalogSyncedAtMs: Long,
    favorites: List<GuideBgmFavoriteItem>,
    derivedState: BaGuideStudentBgmListDerivedState,
    displayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    onRequestDisplayedDerivedState: (BaGuideStudentBgmDisplayedInput) -> Unit,
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
    val allStudentEntries = derivedState.allStudentEntries
    val filteredEntries = derivedState.filteredEntries
    val effectiveLoading = loading || (derivedState.deriving && allStudentEntries.isEmpty())
    val listState = rememberLazyListState()
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(filteredEntries.size, tabState) {
        tabState.resetVisibleCount(filteredEntries.size)
    }
    LaunchedEffect(isPageActive, listState, filteredEntries.size, snapshotFlowManager, tabState) {
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
                if (tabState.visibleCount >= filteredEntries.size) return@collect
                if (totalCount <= 0) return@collect
                val triggerIndex = (totalCount - 1 - STUDENT_BGM_LOAD_MORE_THRESHOLD).coerceAtLeast(0)
                if (lastVisible < triggerIndex) return@collect
                tabState.appendVisibleBatch(
                    totalCount = filteredEntries.size,
                    viewportItems = viewportItems,
                )
            }
    }
    val displayedEntries =
        remember(filteredEntries, tabState.visibleCount) {
            if (tabState.visibleCount >= filteredEntries.size) {
                filteredEntries
            } else {
                filteredEntries.subList(0, tabState.visibleCount)
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
    val displayedPlayableFavorites = displayedBgmModel.playableFavorites
    LaunchedEffect(playbackCoordinator, displayedPlayableFavorites, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.updateQueue(displayedPlayableFavorites)
        }
    }
    val selectedIndex = displayedPlayableFavorites.indexOfFirst { it.audioUrl == selectedAudioUrl }
    val selectedFavorite = displayedPlayableFavorites.getOrNull(selectedIndex)
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !sliderInteractionActive,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                    bottom =
                        listBottomChromePadding +
                            AppChromeTokens.pageSectionGap +
                            if (showNowPlaying) {
                                if (nowPlayingExpanded) 210.dp else 96.dp
                            } else {
                                0.dp
                            },
                    start = AppChromeTokens.pageHorizontalPadding,
                    end = AppChromeTokens.pageHorizontalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        displayedCount = filteredEntries.size,
                        resolvedCount = displayedBgmModel.resolvedCount,
                        favoriteCount = favorites.size,
                        loadingCount = displayedBgmModel.loadingCount,
                        searchActive = searchQuery.isNotBlank(),
                        accent = accent,
                    )
                }
            }

            if (!effectiveLoading && filteredEntries.isEmpty()) {
                item(
                    key = "student-bgm-empty",
                    contentType = "student_bgm_status",
                ) {
                    LiquidInfoBlock(
                        backdrop = null,
                        title = stringResource(R.string.ba_catalog_empty_title),
                        subtitle = stringResource(R.string.ba_catalog_empty_subtitle_search),
                        accent = accent,
                    )
                }
            } else {
                items(
                    items = displayedRows,
                    key = { it.entry.contentId },
                    contentType = { "student_bgm_entry" },
                ) { row ->
                    val entry = row.entry
                    val selected = row.readyAudioUrl == selectedAudioUrl
                    BaGuideStudentBgmCard(
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

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = AppChromeTokens.pageHorizontalPadding,
                        end = AppChromeTokens.pageHorizontalPadding,
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
                )
            }
        }
    }
}
