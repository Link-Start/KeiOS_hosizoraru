package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
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
            contentType = "ba_guide_bgm_empty_track_result"
        ) {
            BaGuideBgmEmptyTrackResult(
                accent = accent,
                backdrop = backdrop
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
            contentType = "ba_guide_bgm_track_chunk"
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
                onTrackShareClick = onTrackShareClick
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    onShareClick = { onTrackShareClick(track) }
                )
            }
        }
    }
}

@Composable
private fun BaGuideBgmEmptyTrackResult(
    accent: Color,
    backdrop: Backdrop
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidSurface(
                backdrop = iconBackdrop,
                shape = CircleShape,
                tint = accent.copy(alpha = 0.16f),
                surfaceColor = accent.copy(alpha = 0.08f),
                chromaticAberration = true,
                isInteractive = false,
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = appLucideMusicIcon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = stringResource(R.string.ba_catalog_bgm_search_empty_title),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.ba_catalog_bgm_search_empty_subtitle),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BaGuideBgmTrackRow(
    index: Int,
    track: BaGuideBgmTrack,
    active: Boolean,
    isPlaying: Boolean,
    favorite: Boolean,
    offlineSaved: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var moreExpanded by remember(track.id) { mutableStateOf(false) }
    var moreAnchorBounds by remember(track.id) { mutableStateOf<IntRect?>(null) }
    val rowShape = RoundedCornerShape(14.dp)
    val offlineBadgeLabel = stringResource(R.string.ba_catalog_bgm_track_badge_offline)
    val rowStatusDescription = if (offlineSaved) {
        "${track.durationLabel}, $offlineBadgeLabel"
    } else {
        track.durationLabel
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "${track.title}, $rowStatusDescription" }
            .clip(rowShape)
            .background(
                color = if (active) accent.copy(alpha = 0.08f) else Color.Transparent,
                shape = rowShape
            )
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BaGuideBgmTrackIndex(
            index = index,
            active = active,
            isPlaying = isPlaying,
            accent = accent
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = track.durationLabel,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End
        )
        Box(
            modifier = Modifier.capturePopupAnchor { moreAnchorBounds = it },
            contentAlignment = Alignment.Center
        ) {
            BaGuideBgmInlineIcon(
                icon = appLucideMoreIcon(),
                contentDescription = stringResource(R.string.ba_catalog_bgm_action_more),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                size = 40.dp,
                iconSize = 22.dp,
                onClick = { moreExpanded = true }
            )
            BaGuideBgmTrackMorePopup(
                show = moreExpanded,
                anchorBounds = moreAnchorBounds,
                favorite = favorite,
                offlineSaved = offlineSaved,
                onDismissRequest = { moreExpanded = false },
                onPlayClick = {
                    moreExpanded = false
                    onClick()
                },
                onFavoriteClick = {
                    moreExpanded = false
                    onFavoriteClick()
                },
                onOfflineClick = {
                    moreExpanded = false
                    onOfflineClick()
                },
                onOpenGuideClick = {
                    moreExpanded = false
                    onShareClick()
                }
            )
        }
    }
}

@Composable
private fun BaGuideBgmTrackMorePopup(
    show: Boolean,
    anchorBounds: IntRect?,
    favorite: Boolean,
    offlineSaved: Boolean,
    onDismissRequest: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onOpenGuideClick: () -> Unit
) {
    if (!show) return
    SnapshotWindowListPopup(
        show = true,
        alignment = PopupPositionProvider.Align.BottomEnd,
        anchorBounds = anchorBounds?.asTrackMenuAnchor(),
        placement = SnapshotPopupPlacement.ButtonEnd,
        enableWindowDim = false,
        onDismissRequest = onDismissRequest
    ) {
        LiquidGlassDropdownColumn {
            BaGuideBgmTrackMenuItem(
                text = stringResource(R.string.ba_catalog_bgm_action_play),
                leadingIcon = appLucidePlayIcon(),
                index = 0,
                optionSize = BaGuideBgmTrackMenuItemCount,
                onClick = onPlayClick
            )
            BaGuideBgmTrackMenuItem(
                text = stringResource(
                    if (favorite) {
                        R.string.ba_catalog_bgm_action_unfavorite
                    } else {
                        R.string.ba_catalog_bgm_action_favorite
                    }
                ),
                leadingIcon = appLucideHeartIcon(),
                index = 1,
                optionSize = BaGuideBgmTrackMenuItemCount,
                onClick = onFavoriteClick
            )
            BaGuideBgmTrackMenuItem(
                text = stringResource(
                    if (offlineSaved) {
                        R.string.ba_catalog_bgm_action_remove_offline
                    } else {
                        R.string.ba_catalog_bgm_action_save_offline
                    }
                ),
                leadingIcon = appLucideDownloadIcon(),
                index = 2,
                optionSize = BaGuideBgmTrackMenuItemCount,
                onClick = onOfflineClick
            )
            BaGuideBgmTrackMenuItem(
                text = stringResource(R.string.ba_catalog_bgm_action_open_gallery),
                leadingIcon = appLucideExternalLinkIcon(),
                index = 3,
                optionSize = BaGuideBgmTrackMenuItemCount,
                onClick = onOpenGuideClick
            )
        }
    }
}

@Composable
private fun BaGuideBgmTrackMenuItem(
    text: String,
    index: Int,
    optionSize: Int,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    LiquidGlassDropdownActionItem(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        index = index,
        optionSize = optionSize
    )
}

private fun IntRect.asTrackMenuAnchor(): IntRect {
    return IntRect(
        left = left,
        top = top,
        right = right,
        bottom = Int.MAX_VALUE / 4
    )
}

@Composable
private fun BaGuideBgmTrackIndex(
    index: Int,
    active: Boolean,
    isPlaying: Boolean,
    accent: Color
) {
    Box(
        modifier = Modifier.width(22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            BaGuideBgmPlayingBars(
                accent = accent,
                animated = isPlaying
            )
        } else {
            Text(
                text = (index + 1).toString(),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BaGuideBgmPlayingBars(
    accent: Color,
    animated: Boolean
) {
    val heights = rememberBaGuideBgmPlayingBarHeights(animated)
    Row(
        modifier = Modifier
            .width(18.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        BaGuideBgmPlayingBar(accent = accent, heightFraction = heights.first)
        BaGuideBgmPlayingBar(accent = accent, heightFraction = heights.second)
        BaGuideBgmPlayingBar(accent = accent, heightFraction = heights.third)
    }
}

@Composable
private fun rememberBaGuideBgmPlayingBarHeights(animated: Boolean): BaGuideBgmPlayingBarHeights {
    if (!animated) {
        return BaGuideBgmPlayingBarHeights(
            first = BaGuideBgmPlayingBarStaticHeight,
            second = BaGuideBgmPlayingBarStaticHeight,
            third = BaGuideBgmPlayingBarStaticHeight
        )
    }
    val transition = rememberInfiniteTransition(label = "ba_catalog_bgm_playing_bars")
    val firstHeight by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ba_catalog_bgm_playing_bar_first"
    )
    val secondHeight by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 640),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ba_catalog_bgm_playing_bar_second"
    )
    val thirdHeight by transition.animateFloat(
        initialValue = 0.56f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 580),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ba_catalog_bgm_playing_bar_third"
    )
    return BaGuideBgmPlayingBarHeights(
        first = firstHeight,
        second = secondHeight,
        third = thirdHeight
    )
}

@Composable
private fun BaGuideBgmPlayingBar(
    accent: Color,
    heightFraction: Float
) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .fillMaxHeight(heightFraction.coerceIn(0.32f, 1f))
            .clip(ContinuousCapsule)
            .background(accent)
    )
}

private data class BaGuideBgmPlayingBarHeights(
    val first: Float,
    val second: Float,
    val third: Float
)

private const val BaGuideBgmPlayingBarStaticHeight = 0.56f

@Composable
internal fun BaGuideBgmSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    backdrop: Backdrop
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        AppLiquidSearchField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.ba_catalog_bgm_search_placeholder),
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            textColor = MiuixTheme.colorScheme.onBackground,
            variant = GlassVariant.Content,
            horizontalPadding = 18.dp,
            verticalPadding = 0.dp,
            focusRequester = focusRequester
        )
    }
}

private const val BaGuideBgmTrackMenuItemCount = 4
