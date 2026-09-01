@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    volumeControlVisible: Boolean,
    lastAudibleVolume: Float,
    isTrackFavorite: (String) -> Boolean,
    onRepeatClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    onVolumeControlVisibleChange: (Boolean) -> Unit,
    onLastAudibleVolumeChange: (Float) -> Unit,
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
    trackListState: LazyListState,
    /**
     * Two panes on a tablet: the album on the left, the queue on the right.
     *
     * The Apple Music shape, and the one this screen is already built for -- it is an album hero followed
     * by its track list, which is a column of art and controls followed by a column of rows. Stacked on a
     * panel, the artwork alone fills the window and the queue is entirely below the fold.
     */
    columnCount: Int = 1,
    collapseProgress: Float,
    bottomBarScrollConnection: NestedScrollConnection,
    userScrollEnabled: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    contentBackdrop: Backdrop?,
    artworkImageUrl: String = "",
    showAlbumTitle: Boolean = true,
    promoteSectionTitle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sliderLockedScrollConnection =
        remember(userScrollEnabled, bottomBarScrollConnection) {
            if (userScrollEnabled) {
                bottomBarScrollConnection
            } else {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset = available

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset = available
                }
            }
        }
    val hero: @Composable (fillAvailableHeight: Boolean) -> Unit = { fillAvailableHeight ->
        BaGuideBgmAlbumHero(
            accent = accent,
            collapseProgress = collapseProgress,
            repeatEnabled = repeatEnabled,
            isPlaying = isPlaying,
            playbackVolume = playbackVolume,
            volumeControlVisible = volumeControlVisible,
            lastAudibleVolume = lastAudibleVolume,
            sectionTitle = sectionTitle,
            sectionMeta = sectionMeta,
            onRepeatClick = onRepeatClick,
            onPlayPauseClick = onPlayPauseClick,
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            onVolumeControlVisibleChange = onVolumeControlVisibleChange,
            onLastAudibleVolumeChange = onLastAudibleVolumeChange,
            onVolumeSliderInteractionChanged = onSliderInteractionChanged,
            contentBackdrop = contentBackdrop,
            artworkImageUrl = artworkImageUrl,
            showAlbumTitle = showAlbumTitle,
            promoteSectionTitle = promoteSectionTitle,
            fillAvailableHeight = fillAvailableHeight,
        )
    }
    val queueItems: LazyListScope.() -> Unit = {
        renderBaGuideBgmTrackList(
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
            onTrackShareClick = onTrackShareClick,
        )
        if (showFooter) {
            item(
                key = "ba-guide-bgm-album-footer",
                contentType = "ba_guide_bgm_album_footer",
            ) {
                BaGuideBgmAlbumFooter(
                    sectionTitle = sectionFooterTitle,
                    trackCount = tracks.size,
                    offlineTrackCount = offlineTrackCount,
                )
            }
        }
    }
    if (columnCount >= 2) {
        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .nestedScroll(sliderLockedScrollConnection)
                    .padding(horizontal = BaGuideBgmAlbumEdgePadding),
            horizontalArrangement = Arrangement.spacedBy(BaGuideBgmAlbumEdgePadding),
        ) {
            // Not a list: the album pane is fixed. Everything in it -- artwork, title, transport,
            // volume -- has to be reachable without scrolling, which is what makes the queue beside it
            // the only thing that moves.
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = topPadding, bottom = bottomPadding),
            ) {
                hero(true)
            }
            LazyColumn(
                state = trackListState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                userScrollEnabled = userScrollEnabled,
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = queueItems,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(sliderLockedScrollConnection),
        userScrollEnabled = userScrollEnabled,
        contentPadding =
            PaddingValues(
                start = BaGuideBgmAlbumEdgePadding,
                top = topPadding,
                end = BaGuideBgmAlbumEdgePadding,
                bottom = bottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(
            key = "ba-guide-bgm-album-hero",
            contentType = "ba_guide_bgm_album_hero",
        ) {
            hero(false)
        }
        queueItems()
    }
}

/** The album pane's page margin, and the gap between the album and its queue. */
private val BaGuideBgmAlbumEdgePadding = 16.dp
