@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import os.kei.R
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideSkipBackIcon
import os.kei.ui.page.main.os.appLucideSkipForwardIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.LiquidMusicProgressSlider
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.shape.appSquircleClip
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmChromeMiniPlayer(
    accent: Color,
    currentTrackTitle: String,
    artworkImageUrl: String,
    isPlaying: Boolean,
    playbackProgress: () -> Float,
    onPlaybackProgressChange: (Float) -> Unit,
    onPlaybackProgressChangeFinished: (Float) -> Unit,
    onPlaybackSliderInteractionChanged: (Boolean) -> Unit,
    expandedProgress: () -> Float,
    compactProgress: () -> Float,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    controlInteractionSource: MutableInteractionSource? = null,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val expanded = expandedProgress().coerceIn(0f, 1f)
    val expandedProvider = { expandedProgress().coerceIn(0f, 1f) }
    val compact = compactProgress().coerceIn(0f, 1f)
    val titleMotionProgress =
        (expanded / MINI_PLAYER_PROGRESS_REVEAL_START).coerceIn(0f, 1f)
    val progressReveal =
        (
            (expanded - MINI_PLAYER_PROGRESS_REVEAL_START) /
                (1f - MINI_PLAYER_PROGRESS_REVEAL_START)
        ).coerceIn(0f, 1f)
    val artworkSize = debugBgmLerpDp(38.dp, 42.dp, expanded)
    val artworkCornerRadius = debugBgmLerpDp(10.dp, 11.dp, expanded)
    val contentPadding = PaddingValues(horizontal = debugBgmLerpDp(10.dp, 14.dp, expanded))
    val titleFontSize = debugBgmLerpSp(12f, AppTypographyTokens.Supporting.fontSize.value, expanded)
    val titleLineHeight = debugBgmLerpSp(14f, AppTypographyTokens.Supporting.lineHeight.value, expanded)
    val playIconSize = debugBgmLerpDp(27.dp, 25.dp, expanded)
    val itemGap = debugBgmLerpDp(8.dp, 10.dp, expanded)
    // The 62dp pill is fully saturated: the dragged progress thumb balloons upward to
    // ~33dp from the pill top and the drag-clearance contract pins the expanded title
    // to the top slot - lowering it further would put the thumb into the title
    // (BaGuideBgmChromeMiniPlayerTest drag-clearance cases). A visibly lower title
    // needs a taller BGM pill or a downward-biased thumb expansion first.
    val titleVerticalOffset = debugBgmLerpDp(21.dp, 0.dp, titleMotionProgress)
    val sideControlSlotWidth =
        debugBgmLerpDp(
            0.dp,
            BaGuideBgmMiniPlayerTransportControlSlotSize,
            expanded,
        )
    val playButtonScale = 1f - compact * 0.02f
    val playPauseTint =
        if (isPlaying) {
            accent.copy(alpha = 0.98f)
        } else {
            MiuixTheme.colorScheme.onBackground
        }

    Row(
        modifier =
            modifier
                .then(if (isPlaying) Modifier.testTag(KeiOsTestTags.BaGuideCatalogBgmPlaying) else Modifier)
                .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidSurface(
            backdrop = backdrop,
            shape = RoundedRectangle(artworkCornerRadius),
            tint = if (artworkImageUrl.isBlank()) accent.copy(alpha = 0.14f) else Color.Transparent,
            surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.12f),
            chromaticAberration = true,
            isInteractive = false,
            modifier =
                Modifier
                    .size(artworkSize)
                    .testTag(BaGuideBgmMiniPlayerArtworkSlotTestTag),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .appSquircleClip((artworkCornerRadius - 2.dp).coerceAtLeast(8.dp))
                        .background(defaultMiniArtworkBrush(accent)),
            )
            if (artworkImageUrl.isNotBlank()) {
                BaGuideBgmArtworkImage(
                    imageUrl = artworkImageUrl,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .appSquircleClip((artworkCornerRadius - 2.dp).coerceAtLeast(8.dp)),
                )
            } else {
                Icon(
                    imageVector = appLucideMusicIcon(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(debugBgmLerpDp(21.dp, 23.dp, expanded)),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(BaGuideBgmMiniPlayerTitleSlotTestTag),
        ) {
            Text(
                text = currentTrackTitle,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.graphicsLayer {
                        translationY = titleVerticalOffset.toPx()
                    },
            )
            if (progressReveal > MINI_PLAYER_EXPANDED_CONTENT_THRESHOLD) {
                LiquidMusicProgressSlider(
                    value = { playbackProgress().coerceIn(0f, 1f) },
                    onValueChange = onPlaybackProgressChange,
                    onValueChangeFinished = onPlaybackProgressChangeFinished,
                    onInteractionChanged = onPlaybackSliderInteractionChanged,
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.001f,
                    backdrop = backdrop,
                    contentDescription = stringResource(R.string.ba_catalog_bgm_seekbar),
                    visualVerticalOffset = BaGuideBgmMiniPlayerProgressVisualOffset,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(MINI_PLAYER_PROGRESS_TOUCH_HEIGHT)
                            .padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = progressReveal },
                )
            }
        }
        Row(
            modifier = Modifier.testTag(BaGuideBgmMiniPlayerTransportGroupTestTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BaGuideBgmChromeMiniPlayerSideControl(
                width = sideControlSlotWidth,
                progress = expandedProvider,
            ) {
                BaGuideBgmInlineIcon(
                    icon = appLucideSkipBackIcon(),
                    contentDescription = stringResource(R.string.ba_catalog_bgm_action_previous),
                    tint = MiuixTheme.colorScheme.onBackground,
                    size = 32.dp,
                    iconSize = 22.dp,
                    interactionSource = controlInteractionSource,
                    onClick = onPreviousClick,
                )
            }
            val playInteractionSource = controlInteractionSource ?: remember { MutableInteractionSource() }
            val playContentDescription =
                stringResource(
                    if (isPlaying) R.string.ba_catalog_bgm_action_pause else R.string.ba_catalog_bgm_action_play,
                )
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(
                            minWidth = BaGuideBgmMiniPlayerTransportControlSlotSize,
                            minHeight = BaGuideBgmMiniPlayerTransportControlSlotSize,
                        ).size(36.dp)
                        .semantics { contentDescription = playContentDescription }
                        .graphicsLayer {
                            scaleX = playButtonScale
                            scaleY = playButtonScale
                        }.clickable(
                            interactionSource = playInteractionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onPlayPauseClick,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) appLucidePauseIcon() else appLucidePlayIcon(),
                    contentDescription = null,
                    tint = playPauseTint,
                    modifier = Modifier.size(playIconSize),
                )
            }
            BaGuideBgmChromeMiniPlayerSideControl(
                width = sideControlSlotWidth,
                progress = expandedProvider,
            ) {
                BaGuideBgmInlineIcon(
                    icon = appLucideSkipForwardIcon(),
                    contentDescription = stringResource(R.string.ba_catalog_bgm_action_next),
                    tint = MiuixTheme.colorScheme.onBackground,
                    size = 32.dp,
                    iconSize = 22.dp,
                    interactionSource = controlInteractionSource,
                    onClick = onNextClick,
                )
            }
        }
    }
}

@Composable
private fun BaGuideBgmChromeMiniPlayerSideControl(
    width: androidx.compose.ui.unit.Dp,
    progress: () -> Float,
    content: @Composable () -> Unit,
) {
    val resolvedProgress = progress().coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier
                .width(width)
                .clipToBounds()
                .graphicsLayer {
                    alpha = resolvedProgress
                },
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedProgress > MINI_PLAYER_EXPANDED_CONTENT_THRESHOLD) {
            content()
        }
    }
}

private fun debugBgmLerpDp(
    start: androidx.compose.ui.unit.Dp,
    end: androidx.compose.ui.unit.Dp,
    fraction: Float,
) = (start.value + (end.value - start.value) * fraction.coerceIn(0f, 1f)).dp

private fun debugBgmLerpSp(
    start: Float,
    end: Float,
    fraction: Float,
) = (start + (end - start) * fraction.coerceIn(0f, 1f)).sp

private fun defaultMiniArtworkBrush(accent: Color): Brush =
    Brush.linearGradient(
        colors = listOf(Color(0xFFFFC857), accent, Color(0xFFFF4D6D)),
    )

internal val BaGuideBgmMiniPlayerTransportControlSlotSize = 48.dp

internal val BaGuideBgmMiniPlayerTransportControlGroupWidth =
    BaGuideBgmMiniPlayerTransportControlSlotSize * 3f

internal const val BaGuideBgmMiniPlayerArtworkSlotTestTag = "ba_bgm_mini_player_artwork_slot"

internal const val BaGuideBgmMiniPlayerTitleSlotTestTag = "ba_bgm_mini_player_title_slot"

internal const val BaGuideBgmMiniPlayerTransportGroupTestTag = "ba_bgm_mini_player_transport_group"

private val MINI_PLAYER_PROGRESS_TOUCH_HEIGHT = 48.dp

internal val BaGuideBgmMiniPlayerProgressVisualOffset = 10.dp

private const val MINI_PLAYER_PROGRESS_REVEAL_START = 0.5f

private const val MINI_PLAYER_EXPANDED_CONTENT_THRESHOLD = 0.001f
