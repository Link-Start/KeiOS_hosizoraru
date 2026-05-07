package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import os.kei.R
import os.kei.ui.page.main.student.BA_GUIDE_BGM_MEDIA_METADATA_AUDIO_URL
import os.kei.ui.page.main.student.BA_GUIDE_BGM_MEDIA_METADATA_SOURCE_URL
import os.kei.ui.page.main.student.BA_GUIDE_BGM_MEDIA_METADATA_STUDENT_TITLE
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.normalizeGuideMediaSource

internal fun GuideBgmFavoriteItem.toBaGuideBgmMediaItem(
    context: Context,
    playbackUrlResolver: (Context, GuideBgmFavoriteItem) -> String = ::resolveFavoriteBgmPlaybackUrl,
    artworkUrlResolver: (GuideBgmFavoriteItem) -> String = { favorite ->
        favorite.resolvePlaybackArtworkImageUrl()
    },
    artworkData: ByteArray? = null
): MediaItem? {
    val mediaId = normalizeGuideMediaSource(audioUrl)
    if (mediaId.isBlank()) return null
    val playbackUrl = playbackUrlResolver(context, this)
    if (playbackUrl.isBlank()) return null
    val studentText = studentTitle.trim()
        .ifBlank { title.trim() }
        .ifBlank { context.getString(R.string.ba_catalog_bgm_student_unknown) }
    val subtitleText = context.getString(R.string.ba_catalog_bgm_media_subtitle)
    val normalizedSourceUrl = normalizeGuideMediaSource(this.sourceUrl)
    val metadataExtras = Bundle().apply {
        putString(BA_GUIDE_BGM_MEDIA_METADATA_AUDIO_URL, mediaId)
        putString(BA_GUIDE_BGM_MEDIA_METADATA_SOURCE_URL, normalizedSourceUrl)
        putString(BA_GUIDE_BGM_MEDIA_METADATA_STUDENT_TITLE, studentText)
    }
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(studentText)
        .setDisplayTitle(studentText)
        .setArtist(subtitleText)
        .setSubtitle(subtitleText)
        .setAlbumTitle(context.getString(R.string.ba_catalog_bgm_album_title))
        .setExtras(metadataExtras)
    if (artworkData != null) {
        metadataBuilder.setArtworkData(
            artworkData,
            MediaMetadata.PICTURE_TYPE_FRONT_COVER
        )
    }
    val artworkUri = artworkUrlResolver(this)
        .takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playbackUrl)
        .setMediaMetadata(metadataBuilder.setArtworkUri(artworkUri).build())
        .build()
}

internal fun List<GuideBgmFavoriteItem>.toBaGuideBgmMediaItems(
    context: Context,
    artworkDataByMediaId: Map<String, ByteArray> = emptyMap()
): List<MediaItem> {
    return filter { it.audioUrl.isNotBlank() }
        .distinctBy { normalizeGuideMediaSource(it.audioUrl) }
        .mapNotNull { favorite ->
            val mediaId = normalizeGuideMediaSource(favorite.audioUrl)
            favorite.toBaGuideBgmMediaItem(
                context = context,
                artworkData = artworkDataByMediaId[mediaId]
            )
        }
}
