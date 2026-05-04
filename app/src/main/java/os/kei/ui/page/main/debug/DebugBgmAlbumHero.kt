package os.kei.ui.page.main.debug

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideRepeatIcon
import os.kei.ui.page.main.os.appLucideVolume2Icon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LiquidVolumeSlider
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DebugBgmAlbumHero(
    accent: Color,
    collapseProgress: Float,
    repeatEnabled: Boolean,
    isPlaying: Boolean,
    playbackVolume: Float,
    sectionTitle: String,
    sectionMeta: String,
    onRepeatClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    onVolumeSliderInteractionChanged: (Boolean) -> Unit,
    contentBackdrop: Backdrop,
    artworkImageUrl: String = "",
    showAlbumTitle: Boolean = true,
    promoteSectionTitle: Boolean = false
) {
    var volumeControlVisible by rememberSaveable { mutableStateOf(true) }
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val density = LocalDensity.current
    val volumeTransition = updateTransition(
        targetState = volumeControlVisible,
        label = "debug_bgm_volume_control"
    )
    val volumeMotionDuration = resolvedMotionDuration(DebugBgmVolumeControlMotionMs, animationsEnabled)
    val volumeHeight by volumeTransition.animateDp(
        transitionSpec = {
            tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
        },
        label = "debug_bgm_volume_height"
    ) { visible ->
        if (visible) DebugBgmVolumeControlHeight else 0.dp
    }
    val volumeAlpha by volumeTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
        },
        label = "debug_bgm_volume_alpha"
    ) { visible ->
        if (visible) 1f else 0f
    }
    val volumeOffsetY by volumeTransition.animateDp(
        transitionSpec = {
            tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
        },
        label = "debug_bgm_volume_offset"
    ) { visible ->
        if (visible) 0.dp else (-6).dp
    }
    val volumeScale by volumeTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
        },
        label = "debug_bgm_volume_scale"
    ) { visible ->
        if (visible) 1f else 0.98f
    }
    val volumeSpacing by volumeTransition.animateDp(
        transitionSpec = {
            tween(durationMillis = volumeMotionDuration, easing = FastOutSlowInEasing)
        },
        label = "debug_bgm_volume_spacing"
    ) { visible ->
        if (visible) 12.dp else 0.dp
    }
    val volumeOffsetPx = with(density) { volumeOffsetY.toPx() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = 1f - collapseProgress * 0.04f
                scaleY = 1f - collapseProgress * 0.04f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DebugBgmAlbumArtwork(
            accent = accent,
            backdrop = contentBackdrop,
            imageUrl = artworkImageUrl
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showAlbumTitle) {
                Text(
                    text = stringResource(R.string.debug_component_lab_album_title),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = sectionTitle,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = if (promoteSectionTitle) 25.sp else AppTypographyTokens.SectionTitle.fontSize,
                lineHeight = if (promoteSectionTitle) 30.sp else AppTypographyTokens.SectionTitle.lineHeight,
                fontWeight = if (promoteSectionTitle) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sectionMeta.isNotBlank()) {
                Text(
                    text = sectionMeta,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(volumeSpacing)
        ) {
            DebugBgmAlbumPrimaryActions(
                accent = accent,
                repeatEnabled = repeatEnabled,
                isPlaying = isPlaying,
                volumeControlVisible = volumeControlVisible,
                onRepeatClick = onRepeatClick,
                onPlayPauseClick = onPlayPauseClick,
                onVolumeClick = { volumeControlVisible = !volumeControlVisible }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(volumeHeight)
                    .clipToBounds()
            ) {
                DebugBgmAlbumVolumeControl(
                    accent = accent,
                    volume = playbackVolume,
                    onVolumeChange = onVolumeChange,
                    onVolumeChangeFinished = onVolumeChangeFinished,
                    onInteractionChanged = onVolumeSliderInteractionChanged,
                    backdrop = contentBackdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = volumeAlpha
                            translationY = volumeOffsetPx
                            scaleX = volumeScale
                            scaleY = volumeScale
                        }
                )
            }
        }
    }
}

@Composable
private fun DebugBgmAlbumArtwork(
    accent: Color,
    backdrop: Backdrop,
    imageUrl: String = ""
) {
    val shape = RoundedCornerShape(28.dp)
    val innerShape = RoundedCornerShape(23.dp)
    LiquidSurface(
        backdrop = backdrop,
        shape = shape,
        tint = if (imageUrl.isBlank()) accent.copy(alpha = 0.14f) else Color.Transparent,
        surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.18f),
        chromaticAberration = true,
        isInteractive = false,
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(12.dp)
                .clip(innerShape)
                .background(defaultAlbumArtworkBrush(accent))
        )
        if (imageUrl.isNotBlank()) {
            DebugBgmArtworkImage(
                imageUrl = imageUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .padding(12.dp)
                    .clip(innerShape)
            )
        } else {
            Icon(
                imageVector = appLucideMusicIcon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun DebugBgmAlbumPrimaryActions(
    accent: Color,
    repeatEnabled: Boolean,
    isPlaying: Boolean,
    volumeControlVisible: Boolean,
    onRepeatClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeClick: () -> Unit
) {
    val neutralTint = MiuixTheme.colorScheme.onBackground
    val actionsBackdrop = rememberLayerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(actionsBackdrop)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DebugBgmRoundAction(
                icon = appLucideRepeatIcon(),
                contentDescription = stringResource(R.string.debug_component_lab_action_repeat),
                accent = accent,
                neutralTint = neutralTint,
                onClick = onRepeatClick,
                backdrop = actionsBackdrop
            )
            DebugBgmPlayAction(
                accent = accent,
                neutralTint = neutralTint,
                isPlaying = isPlaying,
                onClick = onPlayPauseClick,
                backdrop = actionsBackdrop
            )
            DebugBgmRoundAction(
                icon = appLucideVolume2Icon(),
                contentDescription = stringResource(R.string.debug_component_lab_liquid_volume_slider_label),
                accent = accent,
                neutralTint = neutralTint,
                onClick = onVolumeClick,
                backdrop = actionsBackdrop
            )
        }
    }
}

@Composable
private fun DebugBgmAlbumVolumeControl(
    accent: Color,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var sliderActive by rememberSaveable { mutableStateOf(false) }
    val volumeBackdrop = rememberLayerBackdrop()
    val sliderBackdrop = rememberCombinedBackdrop(backdrop, volumeBackdrop)
    val neutralTint = MiuixTheme.colorScheme.onBackgroundVariant
    val activeTint = if (sliderActive) accent.copy(alpha = 0.95f) else neutralTint
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DebugBgmVolumeControlHeight)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(volumeBackdrop)
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = appLucideVolume2Icon(),
                contentDescription = stringResource(R.string.debug_component_lab_liquid_volume_slider_label),
                tint = activeTint,
                modifier = Modifier.size(22.dp)
            )
            LiquidVolumeSlider(
                value = { volume.coerceIn(0f, 1f) },
                onValueChange = onVolumeChange,
                onValueChangeFinished = onVolumeChangeFinished,
                onInteractionChanged = { active ->
                    sliderActive = active
                    onInteractionChanged(active)
                },
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                backdrop = sliderBackdrop,
                activeColor = activeTint,
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
            )
            Text(
                text = stringResource(R.string.debug_component_lab_volume_value, (volume * 100).toInt()),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DebugBgmRoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color,
    neutralTint: Color,
    onClick: () -> Unit = {},
    backdrop: Backdrop
) {
    var pressed by rememberSaveable { mutableStateOf(false) }
    val contentTint = if (pressed) accent.copy(alpha = 0.98f) else neutralTint
    AppLiquidIconButton(
        backdrop = backdrop,
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        width = 52.dp,
        height = 52.dp,
        shape = CircleShape,
        iconTint = contentTint,
        variant = GlassVariant.Floating,
        onPressedChange = { pressed = it }
    )
}

@Composable
private fun DebugBgmPlayAction(
    accent: Color,
    neutralTint: Color,
    isPlaying: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop
) {
    var pressed by rememberSaveable { mutableStateOf(false) }
    val contentTint = if (pressed) accent.copy(alpha = 0.98f) else neutralTint
    AppLiquidTextButton(
        backdrop = backdrop,
        text = stringResource(
            if (isPlaying) R.string.debug_component_lab_action_pause else R.string.debug_component_lab_action_play
        ),
        onClick = onClick,
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = 116.dp),
        textColor = contentTint,
        leadingIcon = if (isPlaying) appLucidePauseIcon() else appLucidePlayIcon(),
        iconTint = contentTint,
        variant = GlassVariant.Floating,
        minHeight = 52.dp,
        horizontalPadding = 24.dp,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis,
        onPressedChange = { pressed = it }
    )
}

private fun defaultAlbumArtworkBrush(accent: Color): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFFF4D6D),
            Color(0xFFFFC857),
            Color(0xFF35C2FF),
            accent,
            Color(0xFF2ED573)
        )
    )
}

private val DebugBgmVolumeControlHeight = 34.dp
private const val DebugBgmVolumeControlMotionMs = 220
