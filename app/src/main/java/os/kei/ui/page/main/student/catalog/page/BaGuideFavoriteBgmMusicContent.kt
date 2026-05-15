package os.kei.ui.page.main.student.catalog.page

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackCoordinator
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmPlaybackUiState
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmQueueMode
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmAlbumContent
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmTrack
import os.kei.ui.page.main.student.catalog.component.filterAndSortBgmFavorites
import os.kei.ui.page.main.student.catalog.component.resolvePlaybackArtworkImageUrl
import os.kei.ui.page.main.student.page.state.GuideDetailTabRequestStore
import os.kei.ui.page.main.student.section.gallery.formatAudioDuration

@Composable
internal fun BaGuideFavoriteBgmMusicContent(
    catalog: BaGuideCatalogBundle,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    playbackState: BaGuideBgmPlaybackUiState,
    searchQuery: String,
    accent: Color,
    bottomBarScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    bottomPadding: Dp,
    isPageActive: Boolean,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onOpenGuide: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val favorites by GuideBgmFavoriteStore.favoritesFlow().collectAsStateWithLifecycle()
    var cacheRevision by remember { mutableIntStateOf(0) }
    var cachingAudioUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var offlineAudioUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    val contentBackdrop = rememberLayerBackdrop()
    val displayedFavorites = remember(favorites, searchQuery) {
        filterAndSortBgmFavorites(
            favorites = favorites,
            searchQuery = searchQuery,
            sortMode = os.kei.ui.page.main.student.catalog.component.BaGuideBgmFavoriteSortMode.Recent
        )
    }
    LaunchedEffect(playbackCoordinator, displayedFavorites, isPageActive) {
        if (isPageActive) {
            playbackCoordinator.updateQueue(displayedFavorites)
        }
    }
    LaunchedEffect(displayedFavorites, cacheRevision, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        offlineAudioUrls = loadFavoriteBgmCachedAudioUrlsAsync(
            context = appContext,
            favorites = displayedFavorites
        )
    }
    val selectedFavorite = playbackState.selectedFavorite
        ?: displayedFavorites.firstOrNull { it.audioUrl == playbackState.selectedAudioUrl }
        ?: displayedFavorites.firstOrNull()
    val tracks = remember(displayedFavorites, cacheRevision) {
        displayedFavorites.map { favorite -> favorite.toBaGuideBgmTrack() }
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
            offlineAudioUrls.size
        )
    }
    val cacheSuccessText = stringResource(R.string.ba_catalog_bgm_cache_success)
    val cacheFailedText = stringResource(R.string.ba_catalog_bgm_cache_failed)
    val cacheRemovedText = stringResource(R.string.ba_catalog_bgm_cache_removed)

    fun playFavorite(favorite: GuideBgmFavoriteItem, restart: Boolean = false) {
        playbackCoordinator.play(favorite, restart = restart)
    }

    fun cacheFavorite(favorite: GuideBgmFavoriteItem) {
        if (favorite.audioUrl.isBlank() || favorite.audioUrl in cachingAudioUrls) return
        cachingAudioUrls = cachingAudioUrls + favorite.audioUrl
        scope.launch {
            val success = try {
                cacheFavoriteBgmAsync(appContext, favorite)
            } catch (error: CancellationException) {
                cachingAudioUrls = cachingAudioUrls - favorite.audioUrl
                throw error
            } catch (_: Throwable) {
                false
            }
            cachingAudioUrls = cachingAudioUrls - favorite.audioUrl
            if (success) {
                offlineAudioUrls = offlineAudioUrls + favorite.audioUrl
            }
            cacheRevision += 1
            Toast.makeText(
                context,
                if (success) cacheSuccessText else cacheFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleFavoriteCache(favorite: GuideBgmFavoriteItem) {
        if (favorite.audioUrl in offlineAudioUrls) {
            scope.launch {
                clearFavoriteBgmCacheAsync(appContext, favorite)
                offlineAudioUrls = offlineAudioUrls - favorite.audioUrl
                cacheRevision += 1
                Toast.makeText(context, cacheRemovedText, Toast.LENGTH_SHORT).show()
            }
        } else {
            cacheFavorite(favorite)
        }
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
                favoritesByTrackId[id]?.let(::toggleFavoriteCache)
            },
            onTrackShareClick = { track ->
                favoritesByTrackId[track.id]?.let { favorite ->
                    GuideDetailTabRequestStore.request(favorite.sourceUrl, GuideBottomTab.Gallery)
                    onOpenGuide(favorite.sourceUrl)
                }
            },
            isTrackOfflineSaved = { id ->
                id in offlineAudioUrls
            },
            sectionTitle = sectionTitle,
            sectionMeta = sectionMeta,
            sectionFooterTitle = stringResource(R.string.ba_catalog_tab_bgm),
            offlineTrackCount = offlineAudioUrls.size,
            showFooter = false,
            listState = rememberLazyListState(),
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

private fun GuideBgmFavoriteItem.toBaGuideBgmTrack(): BaGuideBgmTrack {
    val durationMs = GuideBgmFavoritePlaybackStore.progressFor(audioUrl)?.durationMs ?: 0L
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
