@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmTrackRow(
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
    onShareClick: () -> Unit,
) {
    var moreExpanded by remember(track.id) { mutableStateOf(false) }
    var moreAnchorBounds by remember(track.id) { mutableStateOf<IntRect?>(null) }
    val rowShape = RoundedCornerShape(14.dp)
    val offlineBadgeLabel = stringResource(R.string.ba_catalog_bgm_track_badge_offline)
    val rowStatusDescription =
        if (offlineSaved) {
            "${track.durationLabel}, $offlineBadgeLabel"
        } else {
            track.durationLabel
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = "${track.title}, $rowStatusDescription" }
                .clip(rowShape)
                .background(
                    color = if (active) accent.copy(alpha = 0.08f) else Color.Transparent,
                    shape = rowShape,
                ).clickable(onClick = onClick)
                .padding(start = 4.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BaGuideBgmTrackIndex(
            index = index,
            active = active,
            isPlaying = isPlaying,
            accent = accent,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = track.durationLabel,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
        Box(
            modifier = Modifier.capturePopupAnchor { moreAnchorBounds = it },
            contentAlignment = Alignment.Center,
        ) {
            BaGuideBgmInlineIcon(
                icon = appLucideMoreIcon(),
                contentDescription = stringResource(R.string.ba_catalog_bgm_action_more),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                size = 40.dp,
                iconSize = 22.dp,
                onClick = { moreExpanded = true },
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
                },
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
    onOpenGuideClick: () -> Unit,
) {
    if (!show) return
    SnapshotWindowListPopup(
        show = true,
        alignment = PopupPositionProvider.Align.BottomEnd,
        anchorBounds = anchorBounds?.asTrackMenuAnchor(),
        placement = SnapshotPopupPlacement.ButtonEnd,
        enableWindowDim = false,
        onDismissRequest = onDismissRequest,
    ) {
        LiquidGlassDropdownColumn {
            BaGuideBgmTrackMenuItem(
                text = stringResource(R.string.ba_catalog_bgm_action_play),
                leadingIcon = appLucidePlayIcon(),
                index = 0,
                optionSize = BA_GUIDE_BGM_TRACK_MENU_ITEM_COUNT,
                onClick = onPlayClick,
            )
            BaGuideBgmTrackMenuItem(
                text =
                    stringResource(
                        if (favorite) {
                            R.string.ba_catalog_bgm_action_unfavorite
                        } else {
                            R.string.ba_catalog_bgm_action_favorite
                        },
                    ),
                leadingIcon = appLucideHeartIcon(),
                index = 1,
                optionSize = BA_GUIDE_BGM_TRACK_MENU_ITEM_COUNT,
                onClick = onFavoriteClick,
            )
            BaGuideBgmTrackMenuItem(
                text =
                    stringResource(
                        if (offlineSaved) {
                            R.string.ba_catalog_bgm_action_remove_offline
                        } else {
                            R.string.ba_catalog_bgm_action_save_offline
                        },
                    ),
                leadingIcon = appLucideDownloadIcon(),
                index = 2,
                optionSize = BA_GUIDE_BGM_TRACK_MENU_ITEM_COUNT,
                onClick = onOfflineClick,
            )
            BaGuideBgmTrackMenuItem(
                text = stringResource(R.string.ba_catalog_bgm_action_open_gallery),
                leadingIcon = appLucideExternalLinkIcon(),
                index = 3,
                optionSize = BA_GUIDE_BGM_TRACK_MENU_ITEM_COUNT,
                onClick = onOpenGuideClick,
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
    onClick: () -> Unit,
) {
    LiquidGlassDropdownActionItem(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        index = index,
        optionSize = optionSize,
    )
}

private fun IntRect.asTrackMenuAnchor(): IntRect =
    IntRect(
        left = left,
        top = top,
        right = right,
        bottom = Int.MAX_VALUE / 4,
    )

@Composable
private fun BaGuideBgmTrackIndex(
    index: Int,
    active: Boolean,
    isPlaying: Boolean,
    accent: Color,
) {
    Box(
        modifier = Modifier.width(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            BaGuideBgmPlayingBars(
                accent = accent,
                animated = isPlaying,
            )
        } else {
            Text(
                text = (index + 1).toString(),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BaGuideBgmPlayingBars(
    accent: Color,
    animated: Boolean,
) {
    val heights = rememberBaGuideBgmPlayingBarHeights(animated)
    Row(
        modifier =
            Modifier
                .width(18.dp)
                .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
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
            first = BA_GUIDE_BGM_PLAYING_BAR_STATIC_HEIGHT,
            second = BA_GUIDE_BGM_PLAYING_BAR_STATIC_HEIGHT,
            third = BA_GUIDE_BGM_PLAYING_BAR_STATIC_HEIGHT,
        )
    }
    val transition = rememberInfiniteTransition(label = "ba_catalog_bgm_playing_bars")
    val firstHeight by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.88f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 520),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "ba_catalog_bgm_playing_bar_first",
    )
    val secondHeight by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.48f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 640),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "ba_catalog_bgm_playing_bar_second",
    )
    val thirdHeight by transition.animateFloat(
        initialValue = 0.56f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 580),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "ba_catalog_bgm_playing_bar_third",
    )
    return BaGuideBgmPlayingBarHeights(
        first = firstHeight,
        second = secondHeight,
        third = thirdHeight,
    )
}

@Composable
private fun BaGuideBgmPlayingBar(
    accent: Color,
    heightFraction: Float,
) {
    Box(
        modifier =
            Modifier
                .width(3.dp)
                .fillMaxHeight(heightFraction.coerceIn(0.32f, 1f))
                .clip(ContinuousCapsule)
                .background(accent),
    )
}

private data class BaGuideBgmPlayingBarHeights(
    val first: Float,
    val second: Float,
    val third: Float,
)

private const val BA_GUIDE_BGM_PLAYING_BAR_STATIC_HEIGHT = 0.56f
private const val BA_GUIDE_BGM_TRACK_MENU_ITEM_COUNT = 4
