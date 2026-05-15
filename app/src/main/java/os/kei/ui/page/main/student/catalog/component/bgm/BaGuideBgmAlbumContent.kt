package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
internal fun BaGuideBgmAlbumContent(
    accent: Color,
    tracks: List<BaGuideBgmTrack>,
    currentTrackId: String,
    isPlaying: Boolean,
    repeatEnabled: Boolean,
    playbackVolume: Float,
    isTrackFavorite: (String) -> Boolean,
    onRepeatClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onTrackClick: (String) -> Unit,
    onTrackFavoriteClick: (String) -> Unit,
    onTrackOfflineClick: (String) -> Unit,
    onTrackShareClick: (BaGuideBgmTrack) -> Unit,
    isTrackOfflineSaved: (String) -> Boolean,
    sectionTitle: String,
    sectionMeta: String,
    sectionFooterTitle: String,
    offlineTrackCount: Int,
    showFooter: Boolean = true,
    listState: LazyListState,
    collapseProgress: Float,
    bottomBarScrollConnection: NestedScrollConnection,
    userScrollEnabled: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    contentBackdrop: Backdrop,
    artworkImageUrl: String = "",
    showAlbumTitle: Boolean = true,
    promoteSectionTitle: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sliderLockedScrollConnection = remember(userScrollEnabled, bottomBarScrollConnection) {
        if (userScrollEnabled) {
            bottomBarScrollConnection
        } else {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    return available
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    return available
                }
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(sliderLockedScrollConnection),
        userScrollEnabled = userScrollEnabled,
        contentPadding = PaddingValues(start = 16.dp, top = topPadding, end = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(
            key = "ba-guide-bgm-album-hero",
            contentType = "ba_guide_bgm_album_hero"
        ) {
            BaGuideBgmAlbumHero(
                accent = accent,
                collapseProgress = collapseProgress,
                repeatEnabled = repeatEnabled,
                isPlaying = isPlaying,
                playbackVolume = playbackVolume,
                sectionTitle = sectionTitle,
                sectionMeta = sectionMeta,
                onRepeatClick = onRepeatClick,
                onPlayPauseClick = onPlayPauseClick,
                onVolumeChange = onVolumeChange,
                onVolumeChangeFinished = onVolumeChangeFinished,
                onVolumeSliderInteractionChanged = onSliderInteractionChanged,
                contentBackdrop = contentBackdrop,
                artworkImageUrl = artworkImageUrl,
                showAlbumTitle = showAlbumTitle,
                promoteSectionTitle = promoteSectionTitle
            )
        }
        item(
            key = "ba-guide-bgm-track-list",
            contentType = "ba_guide_bgm_track_list"
        ) {
            BaGuideBgmTrackList(
                tracks = tracks,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                accent = accent,
                backdrop = contentBackdrop,
                isTrackFavorite = isTrackFavorite,
                isTrackOfflineSaved = isTrackOfflineSaved,
                onTrackClick = onTrackClick,
                onTrackFavoriteClick = onTrackFavoriteClick,
                onTrackOfflineClick = onTrackOfflineClick,
                onTrackShareClick = onTrackShareClick
            )
        }
        if (showFooter) {
            item(
                key = "ba-guide-bgm-album-footer",
                contentType = "ba_guide_bgm_album_footer"
            ) {
                BaGuideBgmAlbumFooter(
                    sectionTitle = sectionFooterTitle,
                    trackCount = tracks.size,
                    offlineTrackCount = offlineTrackCount
                )
            }
        }
    }
}
