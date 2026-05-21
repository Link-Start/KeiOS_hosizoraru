package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackSnapshot
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmQueueMode
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmAlbumContent
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmTrack
import os.kei.ui.page.main.student.catalog.component.resolvePlaybackArtworkImageUrl
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.page.state.GuideDetailTabRequestStore
import os.kei.ui.page.main.student.section.gallery.formatAudioDuration

@Composable
internal fun BaGuideFavoriteBgmMusicContent(
    catalog: BaGuideCatalogBundle,
    favorites: List<GuideBgmFavoriteItem>,
    derivedState: BaGuideFavoriteBgmListDerivedState,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackState: BaGuideBgmPlaybackUiState,
    accent: Color,
    bottomBarScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    bottomPadding: Dp,
    isPageActive: Boolean,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onOpenGuide: (String) -> Unit
) {
    val context = LocalContext.current
    val contentBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    val displayedFavorites = derivedState.displayedFavorites
    val offlineCacheState = rememberBaGuideFavoriteBgmOfflineCacheState(
        context = context,
        favorites = displayedFavorites,
        isPageActive = isPageActive
    )
    LaunchedEffect(playbackCoordinator, displayedFavorites, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.updateQueue(displayedFavorites)
        }
    }
    LaunchedEffect(listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager.snapshotFlow {
            listState.canScrollBackward to listState.canScrollForward
        }
            .distinctUntilChanged()
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
    val playbackSnapshot = remember(displayedFavorites) {
        GuideBgmFavoritePlaybackStore.snapshot()
    }
    val tracks = remember(displayedFavorites) {
        displayedFavorites.map { favorite ->
            favorite.toBaGuideBgmTrack(playbackSnapshot)
        }
    }
    val favoritesByTrackId = remember(displayedFavorites) {
        displayedFavorites.associateBy { it.audioUrl }
    }
    val sectionTitle = selectedFavorite
        ?.studentTitle
        ?.ifBlank { stringResource(R.string.ba_catalog_bgm_track_fallback) }
        ?: stringResource(R.string.ba_catalog_bgm_empty_title)
    val sectionMeta = if (selectedFavorite != null) {
        ""
    } else {
        stringResource(
            R.string.ba_catalog_bgm_library_summary,
            favorites.size,
            offlineCacheState.offlineAudioUrls.size
        )
    }

    fun playFavorite(favorite: GuideBgmFavoriteItem, restart: Boolean = false) {
        playbackCoordinator.play(favorite, restart = restart)
    }

    LaunchedEffect(selectedFavorite?.audioUrl, playbackCoordinator.queueMode) {
        playbackCoordinator.prepareSelected()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(contentBackdrop)
        )
        BaGuideBgmAlbumContent(
            accent = accent,
            tracks = tracks,
            currentTrackId = selectedFavorite?.audioUrl.orEmpty(),
            isPlaying = playbackState.runtimeState.isPlaying,
            repeatEnabled = playbackState.queueMode == BaGuideBgmQueueMode.SingleLoop,
            playbackVolume = playbackState.runtimeState.volume,
            isTrackFavorite = { id -> favoritesByTrackId.containsKey(id) },
            onRepeatClick = { playbackCoordinator.toggleQueueMode() },
            onPlayPauseClick = {
                val favorite = selectedFavorite ?: displayedFavorites.firstOrNull()
                ?: return@BaGuideBgmAlbumContent
                playbackCoordinator.toggle(favorite)
            },
            onVolumeChange = { volume ->
                selectedFavorite?.let { favorite ->
                    playbackCoordinator.updateVolume(
                        favorite,
                        volume
                    )
                }
            },
            onVolumeChangeFinished = { volume ->
                selectedFavorite?.let { favorite ->
                    playbackCoordinator.updateVolume(
                        favorite,
                        volume
                    )
                }
            },
            onSliderInteractionChanged = onSliderInteractionChanged,
            onTrackClick = { id ->
                favoritesByTrackId[id]?.let { favorite ->
                    playFavorite(favorite, restart = id == playbackState.selectedAudioUrl)
                }
            },
            onTrackFavoriteClick = { id ->
                GuideBgmFavoriteStore.removeFavorite(id)
                if (playbackState.selectedAudioUrl == id) playbackCoordinator.select("")
            },
            onTrackOfflineClick = { id ->
                favoritesByTrackId[id]?.let(offlineCacheState.onToggleFavoriteCache)
            },
            onTrackShareClick = { track ->
                favoritesByTrackId[track.id]?.let { favorite ->
                    GuideDetailTabRequestStore.request(favorite.sourceUrl, GuideBottomTab.Gallery)
                    onOpenGuide(favorite.sourceUrl)
                }
            },
            isTrackOfflineSaved = { id ->
                id in offlineCacheState.offlineAudioUrls
            },
            sectionTitle = sectionTitle,
            sectionMeta = sectionMeta,
            sectionFooterTitle = stringResource(R.string.ba_catalog_tab_bgm),
            offlineTrackCount = offlineCacheState.offlineAudioUrls.size,
            showFooter = false,
            listState = listState,
            collapseProgress = 0f,
            bottomBarScrollConnection = bottomBarScrollConnection,
            userScrollEnabled = true,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            contentBackdrop = contentBackdrop,
            artworkImageUrl = selectedFavorite
                ?.resolvePlaybackArtworkImageUrl()
                .orEmpty(),
            showAlbumTitle = false,
            promoteSectionTitle = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun GuideBgmFavoriteItem.toBaGuideBgmTrack(
    playbackSnapshot: GuideBgmFavoritePlaybackSnapshot
): BaGuideBgmTrack {
    val durationMs = playbackSnapshot.progressFor(audioUrl)?.durationMs ?: 0L
    return BaGuideBgmTrack(
        id = audioUrl,
        title = studentTitle.ifBlank { title }.ifBlank { audioUrl },
        subtitle = title.ifBlank { note }.ifBlank { sourceUrl },
        durationLabel = if (durationMs > 0L) formatAudioDuration(durationMs) else "",
        searchAlias = listOf(title, studentTitle, note, sourceUrl, audioUrl)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )
}
