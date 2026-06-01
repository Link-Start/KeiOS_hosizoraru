@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmQueueMode
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmAlbumContent
import os.kei.ui.page.main.student.catalog.component.resolveStudentArtworkImageUrl
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState
import os.kei.ui.page.main.student.section.gallery.formatAudioDuration

@Composable
internal fun BaGuideFavoriteBgmMusicContent(
    catalog: BaGuideCatalogBundle,
    favorites: List<GuideBgmFavoriteItem>,
    derivedState: BaGuideFavoriteBgmListDerivedState,
    offlineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackState: BaGuideBgmPlaybackUiState,
    volumeControlVisible: Boolean,
    lastAudibleVolume: Float,
    accent: Color,
    bottomBarScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    bottomPadding: Dp,
    isPageActive: Boolean,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onVolumeControlVisibleChange: (Boolean) -> Unit,
    onLastAudibleVolumeChange: (Float) -> Unit,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onRemoveBgmFavorite: (String) -> Unit,
    onRequestOfflineCache: (List<GuideBgmFavoriteItem>, Boolean, Boolean) -> Unit,
    onToggleFavoriteCache: (GuideBgmFavoriteItem, List<GuideBgmFavoriteItem>) -> Unit,
    onOpenGuide: (String) -> Unit,
    onRequestGuideDetailTab: (String, GuideBottomTab) -> Unit,
) {
    val contentBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    val playbackRuntimeState by remember(playbackCoordinator, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.runtimeStateFlow
        } else {
            emptyFlow()
        }
    }.collectAsStateWithLifecycle(
        initialValue = playbackCoordinator.runtimeState,
    )
    val playbackChromeState =
        remember(playbackRuntimeState) {
            BaGuideFavoriteBgmPlaybackChromeState(
                isPlaying = playbackRuntimeState.isPlaying,
                volume = playbackRuntimeState.volume,
            )
        }
    val selectedAudioUrl = playbackState.selectedAudioUrl
    val displayedTracks =
        remember(
            derivedState.tracks,
            selectedAudioUrl,
            playbackRuntimeState.durationMs,
        ) {
            val durationMs = playbackRuntimeState.durationMs
            if (durationMs <= 0L || selectedAudioUrl.isBlank()) {
                derivedState.tracks
            } else {
                derivedState.tracks.map { track ->
                    if (track.id == selectedAudioUrl) {
                        track.copy(durationLabel = formatAudioDuration(durationMs))
                    } else {
                        track
                    }
                }
            }
        }
    val displayedFavorites = derivedState.displayedFavorites
    val favoritesByTrackId = derivedState.favoritesByTrackId
    val favoriteOfflineCacheState =
        rememberBaGuideFavoriteBgmOfflineCacheState(
            uiState = offlineCacheState,
            onToggleFavoriteCache = { favorite ->
                onToggleFavoriteCache(favorite, displayedFavorites)
            },
        )
    LaunchedEffect(displayedFavorites, isPageActive) {
        onRequestOfflineCache(displayedFavorites, isPageActive, false)
    }
    LaunchedEffect(playbackCoordinator, displayedFavorites, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.updateQueue(displayedFavorites)
        }
    }
    LaunchedEffect(listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                listState.canScrollBackward to listState.canScrollForward
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                onScrollBoundsChange(canScrollBackward, canScrollForward)
            }
    }
    val selectedFavorite =
        remember(
        playbackState.selectedAudioUrl,
        playbackState.queue,
        playbackState.favorites,
            displayedFavorites,
        ) {
            playbackState.selectedFavorite
                ?: displayedFavorites.firstOrNull { it.audioUrl == playbackState.selectedAudioUrl }
            ?: displayedFavorites.firstOrNull()
    }
    DisposableEffect(lifecycleOwner, selectedFavorite?.audioUrl, playbackCoordinator) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && !playbackCoordinator.keepsPlaybackAfterPageStop) {
                    selectedFavorite?.let { favorite ->
                        playbackCoordinator.pause(favorite)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val sectionTitle =
        selectedFavorite
            ?.studentTitle
            ?.ifBlank { stringResource(R.string.ba_catalog_bgm_track_fallback) }
            ?: stringResource(R.string.ba_catalog_bgm_empty_title)
    val sectionMeta =
        if (selectedFavorite != null) {
            ""
        } else {
            stringResource(
                R.string.ba_catalog_bgm_library_summary,
                favorites.size,
                favoriteOfflineCacheState.offlineAudioUrls.size,
            )
        }

    fun playFavorite(
        favorite: GuideBgmFavoriteItem,
        restart: Boolean = false,
    ) {
        playbackCoordinator.play(favorite, restart = restart)
    }

    LaunchedEffect(selectedFavorite?.audioUrl, playbackCoordinator.queueMode, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        playbackCoordinator.prepareSelected()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .layerBackdrop(contentBackdrop),
        )
        BaGuideBgmAlbumContent(
            accent = accent,
            tracks = displayedTracks,
            currentTrackId = selectedFavorite?.audioUrl.orEmpty(),
            isPlaying = playbackChromeState.isPlaying,
            repeatEnabled = playbackState.queueMode == BaGuideBgmQueueMode.SingleLoop,
            playbackVolume = playbackChromeState.volume,
            volumeControlVisible = volumeControlVisible,
            lastAudibleVolume = lastAudibleVolume,
            isTrackFavorite = { id -> favoritesByTrackId.containsKey(id) },
            onRepeatClick = { playbackCoordinator.toggleQueueMode() },
            onPlayPauseClick = {
                val favorite =
                    selectedFavorite ?: displayedFavorites.firstOrNull()
                        ?: return@BaGuideBgmAlbumContent
                playbackCoordinator.toggle(favorite)
            },
            onVolumeChange = { volume ->
                selectedFavorite?.let { favorite ->
                    playbackCoordinator.updateVolume(
                        favorite,
                        volume,
                    )
                }
            },
            onVolumeChangeFinished = { volume ->
                selectedFavorite?.let { favorite ->
                    playbackCoordinator.updateVolume(
                        favorite,
                        volume,
                    )
                }
            },
            onVolumeControlVisibleChange = onVolumeControlVisibleChange,
            onLastAudibleVolumeChange = onLastAudibleVolumeChange,
            onSliderInteractionChanged = onSliderInteractionChanged,
            onTrackClick = { id ->
                favoritesByTrackId[id]?.let { favorite ->
                    playFavorite(favorite, restart = id == playbackState.selectedAudioUrl)
                }
            },
            onTrackFavoriteClick = { id ->
                onRemoveBgmFavorite(id)
                if (playbackState.selectedAudioUrl == id) playbackCoordinator.select("")
            },
            onTrackOfflineClick = { id ->
                favoritesByTrackId[id]?.let(favoriteOfflineCacheState.onToggleFavoriteCache)
            },
            onTrackShareClick = { track ->
                favoritesByTrackId[track.id]?.let { favorite ->
                    onRequestGuideDetailTab(favorite.sourceUrl, GuideBottomTab.Gallery)
                    onOpenGuide(favorite.sourceUrl)
                }
            },
            isTrackOfflineSaved = { id ->
                id in favoriteOfflineCacheState.offlineAudioUrls
            },
            sectionTitle = sectionTitle,
            sectionMeta = sectionMeta,
            sectionFooterTitle = stringResource(R.string.ba_catalog_tab_bgm),
            offlineTrackCount = favoriteOfflineCacheState.offlineAudioUrls.size,
            showFooter = false,
            listState = listState,
            collapseProgress = 0f,
            bottomBarScrollConnection = bottomBarScrollConnection,
            userScrollEnabled = true,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            contentBackdrop = contentBackdrop,
            artworkImageUrl =
                selectedFavorite
                    ?.resolveStudentArtworkImageUrl(catalog)
                    .orEmpty(),
            showAlbumTitle = false,
            promoteSectionTitle = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private data class BaGuideFavoriteBgmPlaybackChromeState(
    val isPlaying: Boolean,
    val volume: Float,
)
