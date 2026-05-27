@file:Suppress("FunctionName", "ktlint:standard:property-naming")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmAlbumHero(
    accent: Color,
    collapseProgress: Float,
    repeatEnabled: Boolean,
    isPlaying: Boolean,
    playbackVolume: Float,
    volumeControlVisible: Boolean,
    lastAudibleVolume: Float,
    sectionTitle: String,
    sectionMeta: String,
    onRepeatClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    onVolumeControlVisibleChange: (Boolean) -> Unit,
    onLastAudibleVolumeChange: (Float) -> Unit,
    onVolumeSliderInteractionChanged: (Boolean) -> Unit,
    contentBackdrop: Backdrop,
    artworkImageUrl: String = "",
    showAlbumTitle: Boolean = true,
    promoteSectionTitle: Boolean = false,
) {
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val density = LocalDensity.current
    val volumeTransition =
        updateTransition(
            targetState = volumeControlVisible,
            label = "ba_catalog_bgm_volume_control",
        )
    val volumeMotionDuration = resolvedMotionDuration(BaGuideBgmVolumeControlMotionMs, animationsEnabled)
    val volumeHeightState =
        volumeTransition.animateDp(
            transitionSpec = {
                tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
            },
            label = "ba_catalog_bgm_volume_height",
        ) { visible ->
            if (visible) BaGuideBgmVolumeControlHeight else 0.dp
        }
    val volumeAlphaState =
        volumeTransition.animateFloat(
            transitionSpec = {
                tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
            },
            label = "ba_catalog_bgm_volume_alpha",
        ) { visible ->
            if (visible) 1f else 0f
        }
    val volumeOffsetYState =
        volumeTransition.animateDp(
            transitionSpec = {
                tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
            },
            label = "ba_catalog_bgm_volume_offset",
        ) { visible ->
            if (visible) 0.dp else (-6).dp
        }
    val volumeScaleState =
        volumeTransition.animateFloat(
            transitionSpec = {
                tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
            },
            label = "ba_catalog_bgm_volume_scale",
        ) { visible ->
            if (visible) 1f else 0.98f
        }
    val volumeSpacingState =
        volumeTransition.animateDp(
            transitionSpec = {
                tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
            },
            label = "ba_catalog_bgm_volume_spacing",
        ) { visible ->
            if (visible) 12.dp else 0.dp
        }
    val volumeHeightProvider = remember(volumeHeightState) { { volumeHeightState.value } }
    val volumeAlphaProvider = remember(volumeAlphaState) { { volumeAlphaState.value } }
    val volumeOffsetYProvider = remember(volumeOffsetYState) { { volumeOffsetYState.value } }
    val volumeScaleProvider = remember(volumeScaleState) { { volumeScaleState.value } }
    val volumeSpacingProvider = remember(volumeSpacingState) { { volumeSpacingState.value } }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = 1f - collapseProgress * 0.04f
                    scaleY = 1f - collapseProgress * 0.04f
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BaGuideBgmAlbumArtwork(
            accent = accent,
            backdrop = contentBackdrop,
            imageUrl = artworkImageUrl,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showAlbumTitle) {
                Text(
                    text = stringResource(R.string.ba_catalog_bgm_album_title),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sectionTitle,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = if (promoteSectionTitle) 25.sp else AppTypographyTokens.SectionTitle.fontSize,
                lineHeight = if (promoteSectionTitle) 30.sp else AppTypographyTokens.SectionTitle.lineHeight,
                fontWeight = if (promoteSectionTitle) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sectionMeta.isNotBlank()) {
                Text(
                    text = sectionMeta,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            BaGuideBgmAlbumPrimaryActions(
                accent = accent,
                repeatEnabled = repeatEnabled,
                isPlaying = isPlaying,
                volumeControlVisible = volumeControlVisible,
                muted = playbackVolume <= 0.001f,
                onRepeatClick = onRepeatClick,
                onPlayPauseClick = onPlayPauseClick,
                onVolumeClick = { onVolumeControlVisibleChange(!volumeControlVisible) },
            )
            Spacer(modifier = Modifier.baGuideBgmAlbumAnimatedHeight(volumeSpacingProvider))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .baGuideBgmAlbumAnimatedHeight(volumeHeightProvider)
                        .clipToBounds(),
            ) {
                BaGuideBgmAlbumVolumeControl(
                    accent = accent,
                    volume = playbackVolume,
                    onVolumeChange = onVolumeChange,
                    onVolumeChangeFinished = { volume ->
                        if (volume > 0.01f) onLastAudibleVolumeChange(volume)
                        onVolumeChangeFinished(volume)
                    },
                    onToggleMuted = {
                        val nextVolume =
                            if (playbackVolume > 0.001f) {
                                onLastAudibleVolumeChange(playbackVolume)
                                0f
                            } else {
                                lastAudibleVolume.coerceIn(0.12f, 1f)
                            }
                        onVolumeChange(nextVolume)
                        onVolumeChangeFinished(nextVolume)
                    },
                    onInteractionChanged = onVolumeSliderInteractionChanged,
                    backdrop = contentBackdrop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = volumeAlphaProvider()
                                translationY = with(density) { volumeOffsetYProvider().toPx() }
                                val scale = volumeScaleProvider()
                                scaleX = scale
                                scaleY = scale
                            },
                )
            }
        }
    }
}

private const val BaGuideBgmVolumeControlMotionMs = 220

private fun Modifier.baGuideBgmAlbumAnimatedHeight(height: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val heightPx = height().roundToPx().coerceAtLeast(0)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = heightPx,
                    maxHeight = heightPx,
                ),
            )
        layout(placeable.width, heightPx) {
            placeable.place(0, 0)
        }
    }
