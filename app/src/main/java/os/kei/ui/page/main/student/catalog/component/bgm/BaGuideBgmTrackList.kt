@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.LiquidSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val BGM_TRACK_CHUNK_SIZE = 18

internal fun LazyListScope.renderBaGuideBgmTrackList(
    tracks: List<BaGuideBgmTrack>,
    currentTrackId: String,
    isPlaying: Boolean,
    accent: Color,
    backdrop: Backdrop,
    isTrackFavorite: (String) -> Boolean,
    isTrackOfflineSaved: (String) -> Boolean,
    onTrackClick: (String) -> Unit,
    onTrackFavoriteClick: (String) -> Unit,
    onTrackOfflineClick: (String) -> Unit,
    onTrackShareClick: (BaGuideBgmTrack) -> Unit,
) {
    if (tracks.isEmpty()) {
        item(
            key = "ba-guide-bgm-empty-track-result",
            contentType = "ba_guide_bgm_empty_track_result",
        ) {
            BaGuideBgmEmptyTrackResult(
                accent = accent,
                backdrop = backdrop,
            )
        }
        return
    }

    tracks.chunked(BGM_TRACK_CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
        val firstId = chunk.firstOrNull()?.id.orEmpty()
        val lastId = chunk.lastOrNull()?.id.orEmpty()
        val baseIndex = chunkIndex * BGM_TRACK_CHUNK_SIZE
        item(
            key = "ba-guide-bgm-track-chunk-$firstId-$lastId-${chunk.size}",
            contentType = "ba_guide_bgm_track_chunk",
        ) {
            BaGuideBgmTrackChunk(
                tracks = chunk,
                baseIndex = baseIndex,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                accent = accent,
                backdrop = backdrop,
                isTrackFavorite = isTrackFavorite,
                isTrackOfflineSaved = isTrackOfflineSaved,
                onTrackClick = onTrackClick,
                onTrackFavoriteClick = onTrackFavoriteClick,
                onTrackOfflineClick = onTrackOfflineClick,
                onTrackShareClick = onTrackShareClick,
            )
        }
    }
}

@Composable
private fun BaGuideBgmTrackChunk(
    tracks: List<BaGuideBgmTrack>,
    baseIndex: Int,
    currentTrackId: String,
    isPlaying: Boolean,
    accent: Color,
    backdrop: Backdrop,
    isTrackFavorite: (String) -> Boolean,
    isTrackOfflineSaved: (String) -> Boolean,
    onTrackClick: (String) -> Unit,
    onTrackFavoriteClick: (String) -> Unit,
    onTrackOfflineClick: (String) -> Unit,
    onTrackShareClick: (BaGuideBgmTrack) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val listShape = RoundedCornerShape(24.dp)
    LiquidSurface(
        backdrop = backdrop,
        shape = listShape,
        tint = Color.White.copy(alpha = if (isDark) 0.04f else 0.10f),
        surfaceColor = Color.White.copy(alpha = if (isDark) 0.12f else 0.34f),
        chromaticAberration = true,
        isInteractive = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tracks.forEachIndexed { index, track ->
                val active = track.id == currentTrackId
                BaGuideBgmTrackRow(
                    index = baseIndex + index,
                    track = track,
                    active = active,
                    isPlaying = isPlaying,
                    favorite = isTrackFavorite(track.id),
                    offlineSaved = isTrackOfflineSaved(track.id),
                    accent = accent,
                    onClick = { onTrackClick(track.id) },
                    onFavoriteClick = { onTrackFavoriteClick(track.id) },
                    onOfflineClick = { onTrackOfflineClick(track.id) },
                    onShareClick = { onTrackShareClick(track) },
                )
            }
        }
    }
}

@Composable
private fun BaGuideBgmEmptyTrackResult(
    accent: Color,
    backdrop: Backdrop,
) {
    val isDark = isSystemInDarkTheme()
    val resultSurfaceBackdrop = rememberLayerBackdrop()
    val iconBackdrop = rememberCombinedBackdrop(backdrop, resultSurfaceBackdrop)
    LiquidSurface(
        backdrop = backdrop,
        shape = RoundedCornerShape(24.dp),
        tint = accent.copy(alpha = if (isDark) 0.10f else 0.07f),
        surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.16f else 0.22f),
        chromaticAberration = true,
        isInteractive = false,
        exportedBackdrop = resultSurfaceBackdrop,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidSurface(
                backdrop = iconBackdrop,
                shape = CircleShape,
                tint = accent.copy(alpha = 0.16f),
                surfaceColor = accent.copy(alpha = 0.08f),
                chromaticAberration = true,
                isInteractive = false,
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = appLucideMusicIcon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = stringResource(R.string.ba_catalog_bgm_search_empty_title),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.ba_catalog_bgm_search_empty_subtitle),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                textAlign = TextAlign.Center,
            )
        }
    }
}
