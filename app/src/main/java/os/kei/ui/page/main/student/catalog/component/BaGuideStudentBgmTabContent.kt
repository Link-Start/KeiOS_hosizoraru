package os.kei.ui.page.main.student.catalog.component

import android.widget.Toast
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.filterByQuery
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.state.GuideDetailTabRequestStore
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import kotlin.math.max

private const val STUDENT_BGM_BATCH_SIZE = 20
private const val STUDENT_BGM_LOAD_MORE_THRESHOLD = 10

@Composable
internal fun BaGuideStudentBgmTabContent(
    catalog: BaGuideCatalogBundle,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackState: BaGuideBgmPlaybackUiState,
    searchQuery: String,
    loading: Boolean,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    accent: Color,
    isPageActive: Boolean,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onListScrollInProgressChange: (Boolean) -> Unit,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onNowPlayingVisibilityChange: (Boolean) -> Unit,
    showNowPlayingOverlay: Boolean = true,
    onOpenGuide: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pageScope = rememberCoroutineScope()
    val favorites by GuideBgmFavoriteStore.favoritesFlow().collectAsStateWithLifecycle()
    val lookupCoordinator = remember(pageScope) {
        BaGuideStudentBgmLookupCoordinator(scope = pageScope)
    }
    val lookupStates by lookupCoordinator.states.collectAsStateWithLifecycle()
    var nowPlayingVisible by rememberSaveable { mutableStateOf(false) }
    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var sliderInteractionActive by remember { mutableStateOf(false) }
    var seekPreviewProgress by remember { mutableStateOf<Float?>(null) }
    val selectedAudioUrl = playbackState.selectedAudioUrl
    val playbackRuntimeState = playbackState.runtimeState
    val queueMode = playbackState.queueMode
    val bgmMissingText = stringResource(R.string.ba_catalog_student_bgm_toast_missing)
    val bgmResolveFailedText = stringResource(R.string.ba_catalog_student_bgm_toast_resolve_failed)
    val favoriteAddedText = stringResource(R.string.guide_bgm_toast_favorite_added)
    val favoriteRemovedText = stringResource(R.string.guide_bgm_toast_favorite_removed)

    val allStudentEntries = remember(catalog) {
        catalog.entries(BaGuideCatalogTab.Student).sortedBy { it.order }
    }
    val favoriteByNormalizedSourceUrl = remember(favorites) {
        buildMap {
            favorites.forEach { favorite ->
                val normalizedSourceUrl = normalizeGuideUrl(favorite.sourceUrl)
                if (!containsKey(normalizedSourceUrl)) {
                    put(normalizedSourceUrl, favorite)
                }
            }
        }
    }
    val favoriteSourceUrls = remember(favoriteByNormalizedSourceUrl) {
        favoriteByNormalizedSourceUrl.keys
    }
    val favoriteAudioUrls = remember(favorites) {
        favorites.mapTo(mutableSetOf()) { favorite -> favorite.audioUrl }
    }
    val filteredEntries = remember(allStudentEntries, searchQuery, favoriteSourceUrls) {
        allStudentEntries
            .filterByQuery(searchQuery)
            .sortedWith(
                compareByDescending<BaGuideCatalogEntry> { entry ->
                    normalizeGuideUrl(entry.detailUrl) in favoriteSourceUrls
                }.thenBy { entry -> entry.order }
            )
    }
    val listState = rememberLazyListState()
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    var visibleCount by rememberSaveable(searchQuery) { mutableIntStateOf(0) }
    LaunchedEffect(filteredEntries.size) {
        visibleCount = minOf(filteredEntries.size, STUDENT_BGM_BATCH_SIZE)
    }
    LaunchedEffect(isPageActive, listState, filteredEntries.size, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager.snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (visibleCount >= filteredEntries.size) return@collect
                if (totalCount <= 0) return@collect
                val triggerIndex = (totalCount - 1 - STUDENT_BGM_LOAD_MORE_THRESHOLD).coerceAtLeast(0)
                if (lastVisible < triggerIndex) return@collect
                val viewportItems = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(6)
                val appendBatch = max(STUDENT_BGM_BATCH_SIZE, viewportItems * 3)
                    .coerceAtMost(STUDENT_BGM_BATCH_SIZE * 3)
                visibleCount = minOf(visibleCount + appendBatch, filteredEntries.size)
            }
    }
    val displayedEntries = remember(filteredEntries, visibleCount) {
        if (visibleCount >= filteredEntries.size) {
            filteredEntries
        } else {
            filteredEntries.subList(0, visibleCount)
        }
    }

    fun setNowPlayingVisible(visible: Boolean) {
        nowPlayingVisible = visible
        onNowPlayingVisibilityChange(visible && selectedAudioUrl.isNotBlank())
    }

    fun setSliderInteractionActive(active: Boolean) {
        sliderInteractionActive = active
        onSliderInteractionChanged(active)
    }

    fun openStudentGuide(entry: BaGuideCatalogEntry) {
        GuideDetailTabRequestStore.request(entry.detailUrl, GuideBottomTab.Gallery)
        onOpenGuide(entry.detailUrl)
    }

    fun openFavoriteGuide(favorite: GuideBgmFavoriteItem) {
        GuideDetailTabRequestStore.request(favorite.sourceUrl, GuideBottomTab.Gallery)
        onOpenGuide(favorite.sourceUrl)
    }

    fun favoriteForEntry(entry: BaGuideCatalogEntry): GuideBgmFavoriteItem? {
        return favoriteForStudentBgmEntry(
            entry = entry,
            favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl
        )
    }

    fun stateWithFavoriteFallback(
        entry: BaGuideCatalogEntry,
        lookupState: BaGuideStudentBgmLookupState
    ): BaGuideStudentBgmLookupState {
        return studentBgmStateWithFavoriteFallback(
            entry = entry,
            lookupState = lookupState,
            favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl
        )
    }

    fun resolveEntry(
        entry: BaGuideCatalogEntry,
        allowNetwork: Boolean,
        onResolved: (BaGuideStudentBgmResolvedItem?) -> Unit
    ) {
        lookupCoordinator.resolveEntry(
            entry = entry,
            allowNetwork = allowNetwork,
            onResolved = onResolved
        )
    }

    fun startPlayback(favorite: GuideBgmFavoriteItem, restart: Boolean = false) {
        playbackCoordinator.play(favorite, restart = restart)
        setNowPlayingVisible(true)
    }

    fun togglePlayback(favorite: GuideBgmFavoriteItem) {
        playbackCoordinator.toggle(favorite)
        setNowPlayingVisible(true)
    }

    fun playEntry(entry: BaGuideCatalogEntry) {
        val lookupState = lookupStates[entry.contentId] ?: BaGuideStudentBgmLookupState.Idle
        stateWithFavoriteFallback(entry, lookupState).readyFavoriteOrNull()?.let { favorite ->
            if (lookupState !is BaGuideStudentBgmLookupState.Ready) {
                lookupCoordinator.markReadyFromFavorite(
                    entry = entry,
                    item = BaGuideStudentBgmResolvedItem(
                        favorite = favorite,
                        fromCache = false,
                        fromFavorite = true
                    )
                )
            }
            if (selectedAudioUrl == favorite.audioUrl) {
                togglePlayback(favorite)
            } else {
                startPlayback(favorite)
            }
            return
        }
        resolveEntry(entry = entry, allowNetwork = true) { resolved ->
            val favorite = resolved?.favorite
            if (favorite == null) {
                Toast.makeText(context, bgmMissingText, Toast.LENGTH_SHORT).show()
            } else {
                if (selectedAudioUrl == favorite.audioUrl) {
                    togglePlayback(favorite)
                } else {
                    startPlayback(favorite)
                }
            }
        }
    }

    fun toggleEntryFavorite(entry: BaGuideCatalogEntry) {
        val lookupState = lookupStates[entry.contentId] ?: BaGuideStudentBgmLookupState.Idle
        if (lookupState !is BaGuideStudentBgmLookupState.Ready) {
            val savedFavorite = favoriteForEntry(entry)
            if (savedFavorite != null) {
                GuideBgmFavoriteStore.removeFavorite(savedFavorite.audioUrl)
                Toast.makeText(context, favoriteRemovedText, Toast.LENGTH_SHORT).show()
                return
            }
        }
        resolveEntry(entry = entry, allowNetwork = true) { resolved ->
            val favorite = resolved?.favorite
            if (favorite == null) {
                Toast.makeText(context, bgmResolveFailedText, Toast.LENGTH_SHORT).show()
                return@resolveEntry
            }
            val added = GuideBgmFavoriteStore.toggleFavorite(favorite)
            Toast.makeText(
                context,
                if (added) favoriteAddedText else favoriteRemovedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun isFavoriteEntry(
        entry: BaGuideCatalogEntry,
        lookupState: BaGuideStudentBgmLookupState
    ): Boolean {
        val readyAudioUrl = lookupState.readyFavoriteOrNull()?.audioUrl
        if (!readyAudioUrl.isNullOrBlank()) {
            return readyAudioUrl in favoriteAudioUrls
        }
        return favoriteForEntry(entry) != null
    }

    val displayedBgmModel =
        remember(displayedEntries, lookupStates, favoriteByNormalizedSourceUrl) {
            buildBaGuideStudentBgmDisplayedModel(
                displayedEntries = displayedEntries,
                lookupStates = lookupStates,
                favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl
            )
        }
    val displayedPlayableFavorites = displayedBgmModel.playableFavorites
    LaunchedEffect(playbackCoordinator, displayedPlayableFavorites, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.updateQueue(displayedPlayableFavorites)
        }
    }
    val selectedIndex = displayedPlayableFavorites.indexOfFirst { it.audioUrl == selectedAudioUrl }
    val selectedFavorite = displayedPlayableFavorites.getOrNull(selectedIndex)
    val displayedPlaybackRuntimeState = remember(playbackRuntimeState, seekPreviewProgress) {
        val previewProgress = seekPreviewProgress
        if (previewProgress != null && playbackRuntimeState.durationMs > 0L) {
            val durationMs = playbackRuntimeState.durationMs
            playbackRuntimeState.copy(
                positionMs = (durationMs * previewProgress.coerceIn(0f, 1f))
                    .toLong()
                    .coerceIn(0L, durationMs)
            )
        } else {
            playbackRuntimeState
        }
    }
    val showNowPlaying = showNowPlayingOverlay && selectedFavorite != null && nowPlayingVisible
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomChromePadding = if (showNowPlaying) {
        navigationBarBottom
    } else {
        innerPadding.calculateBottomPadding()
    }
    val nowPlayingBottomPadding = navigationBarBottom + AppChromeTokens.pageSectionGap

    fun selectQueueOffset(offset: Int, shouldStartPlayback: Boolean, restart: Boolean = false) {
        playbackCoordinator.selectOffset(
            offset = offset,
            startPlayback = shouldStartPlayback,
            restart = restart
        )
    }

    LaunchedEffect(catalog.syncedAtMs) {
        lookupCoordinator.clear()
    }
    LaunchedEffect(displayedBgmModel.contentIds, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        lookupCoordinator.prewarmCached(displayedEntries)
    }
    LaunchedEffect(selectedFavorite?.audioUrl) {
        seekPreviewProgress = null
    }
    LaunchedEffect(showNowPlaying) {
        onNowPlayingVisibilityChange(showNowPlaying)
    }
    LaunchedEffect(listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager.snapshotFlow { listState.canScrollBackward to listState.canScrollForward }
            .distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                onScrollBoundsChange(canScrollBackward, canScrollForward)
            }
    }
    LaunchedEffect(listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager.snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                onListScrollInProgressChange(scrolling)
            }
    }
    DisposableEffect(Unit) {
        onDispose { onNowPlayingVisibilityChange(false) }
    }
    DisposableEffect(Unit) {
        onDispose { onSliderInteractionChanged(false) }
    }
    DisposableEffect(lifecycleOwner, selectedFavorite?.audioUrl) {
        val observer = LifecycleEventObserver { _, event ->
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
    LaunchedEffect(selectedFavorite?.audioUrl, queueMode) {
        playbackCoordinator.prepareSelected()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !sliderInteractionActive,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                bottom = listBottomChromePadding +
                    AppChromeTokens.pageSectionGap +
                    if (showNowPlaying) {
                        if (nowPlayingExpanded) 210.dp else 96.dp
                    } else {
                        0.dp
                    },
                start = AppChromeTokens.pageHorizontalPadding,
                end = AppChromeTokens.pageHorizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading && allStudentEntries.isEmpty()) {
                item(key = "student-bgm-loading") {
                    AppAronaLoadingPanel(accent = accent)
                }
            } else {
                item(key = "student-bgm-header") {
                    BaGuideStudentBgmHeader(
                        totalCount = allStudentEntries.size,
                        displayedCount = filteredEntries.size,
                        resolvedCount = displayedBgmModel.resolvedCount,
                        favoriteCount = favorites.size,
                        loadingCount = displayedBgmModel.loadingCount,
                        searchActive = searchQuery.isNotBlank(),
                        accent = accent
                    )
                }
            }

            if (!loading && filteredEntries.isEmpty()) {
                item(key = "student-bgm-empty") {
                    LiquidInfoBlock(
                        backdrop = null,
                        title = stringResource(R.string.ba_catalog_empty_title),
                        subtitle = stringResource(R.string.ba_catalog_empty_subtitle_search),
                        accent = accent
                    )
                }
            } else {
                items(
                    items = displayedEntries,
                    key = { it.contentId },
                    contentType = { "student_bgm_entry" }
                ) { entry ->
                    val lookupState = lookupStates[entry.contentId] ?: BaGuideStudentBgmLookupState.Idle
                    val displayState = stateWithFavoriteFallback(entry, lookupState)
                    val readyFavorite = displayState.readyFavoriteOrNull()
                    val selected = readyFavorite?.audioUrl == selectedAudioUrl
                    BaGuideStudentBgmCard(
                        entry = entry,
                        lookupState = displayState,
                        selected = selected,
                        playing = selected && playbackRuntimeState.isPlaying,
                        favorite = isFavoriteEntry(entry, lookupState),
                        accent = accent,
                        onOpenGuide = { openStudentGuide(entry) },
                        onPlay = { playEntry(entry) },
                        onToggleFavorite = { toggleEntryFavorite(entry) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = AppChromeTokens.pageHorizontalPadding,
                    end = AppChromeTokens.pageHorizontalPadding,
                    bottom = nowPlayingBottomPadding
                )
        ) {
            selectedFavorite?.let { favorite ->
                BaGuideBgmMiniPlayer(
                    favorite = favorite,
                    runtimeState = displayedPlaybackRuntimeState,
                    queueIndex = selectedIndex.coerceAtLeast(0),
                    queueSize = displayedPlayableFavorites.size,
                    queueMode = queueMode,
                    accent = accent,
                    expanded = nowPlayingExpanded,
                    onExpandedChange = { nowPlayingExpanded = it },
                    onOpenQueue = {
                        pageScope.launch { listState.animateScrollToItem(0) }
                    },
                    onPrevious = {
                        selectQueueOffset(offset = -1, shouldStartPlayback = true, restart = true)
                    },
                    onTogglePlayback = {
                        setNowPlayingVisible(true)
                        playbackCoordinator.toggle(favorite)
                    },
                    onNext = {
                        selectQueueOffset(offset = 1, shouldStartPlayback = true, restart = true)
                    },
                    onSeekChanged = { progress ->
                        seekPreviewProgress = progress.coerceIn(0f, 1f)
                    },
                    onSeekFinished = {
                        val seekProgress = seekPreviewProgress
                            ?: displayedPlaybackRuntimeState.progress
                        playbackCoordinator.seek(favorite, seekProgress)
                        seekPreviewProgress = null
                    },
                    onVolumeChanged = { volume ->
                        playbackCoordinator.updateVolume(favorite, volume.coerceIn(0f, 1f))
                    },
                    onSliderInteractionChanged = ::setSliderInteractionActive,
                    onToggleQueueMode = {
                        playbackCoordinator.toggleQueueMode()
                    },
                    onOpenGuide = { openFavoriteGuide(favorite) }
                )
            }
        }
    }
}
