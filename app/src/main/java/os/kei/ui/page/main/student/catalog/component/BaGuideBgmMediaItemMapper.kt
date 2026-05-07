package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import os.kei.R
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.normalizeGuideMediaSource

internal fun GuideBgmFavoriteItem.toBaGuideBgmMediaItem(
    context: Context,
    playbackUrlResolver: (Context, GuideBgmFavoriteItem) -> String = ::resolveFavoriteBgmPlaybackUrl,
    artworkUrlResolver: (GuideBgmFavoriteItem) -> String = { favorite ->
        favorite.resolvePlaybackArtworkImageUrl()
    }
): MediaItem? {
    val mediaId = normalizeGuideMediaSource(audioUrl)
    if (mediaId.isBlank()) return null
    val playbackUrl = playbackUrlResolver(context, this)
    if (playbackUrl.isBlank()) return null
    val titleText = title.trim().ifBlank {
        context.getString(R.string.ba_catalog_bgm_track_fallback)
    }
    val artistText = studentTitle.trim().ifBlank {
        context.getString(R.string.ba_catalog_bgm_student_unknown)
    }
    val artworkUri = artworkUrlResolver(this)
        .takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playbackUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(titleText)
                .setArtist(artistText)
                .setAlbumTitle(context.getString(R.string.ba_catalog_bgm_album_title))
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()
}

internal fun List<GuideBgmFavoriteItem>.toBaGuideBgmMediaItems(context: Context): List<MediaItem> {
    return filter { it.audioUrl.isNotBlank() }
        .distinctBy { normalizeGuideMediaSource(it.audioUrl) }
        .mapNotNull { favorite -> favorite.toBaGuideBgmMediaItem(context) }
}
