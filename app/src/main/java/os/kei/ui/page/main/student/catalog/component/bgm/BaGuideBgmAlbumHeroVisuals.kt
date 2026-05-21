@file:Suppress("FunctionName", "ktlint:standard:property-naming")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideRepeatIcon
import os.kei.ui.page.main.os.appLucideVolume2Icon
import os.kei.ui.page.main.os.appLucideVolumeOffIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppInteractiveTokens
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LiquidVolumeSlider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmAlbumArtwork(
    accent: Color,
    backdrop: Backdrop,
    imageUrl: String = "",
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
        modifier =
            Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(12.dp)
                    .clip(innerShape)
                    .background(defaultAlbumArtworkBrush(accent)),
        )
        if (imageUrl.isNotBlank()) {
            BaGuideBgmArtworkImage(
                imageUrl = imageUrl,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .padding(12.dp)
                        .clip(innerShape),
            )
        } else {
            Icon(
                imageVector = appLucideMusicIcon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
internal fun BaGuideBgmAlbumPrimaryActions(
    accent: Color,
    repeatEnabled: Boolean,
    isPlaying: Boolean,
    volumeControlVisible: Boolean,
    muted: Boolean,
    onRepeatClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeClick: () -> Unit,
) {
    val neutralTint = MiuixTheme.colorScheme.onBackground
    val actionsBackdrop = rememberLayerBackdrop()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp + AppInteractiveTokens.liquidPressSafePadding * 2),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .layerBackdrop(actionsBackdrop),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(vertical = AppInteractiveTokens.liquidPressSafePadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BaGuideBgmRoundAction(
                icon = appLucideRepeatIcon(),
                contentDescription = stringResource(R.string.ba_catalog_bgm_action_repeat),
                accent = accent,
                neutralTint = neutralTint,
                active = repeatEnabled,
                onClick = onRepeatClick,
                backdrop = actionsBackdrop,
            )
            BaGuideBgmPlayAction(
                accent = accent,
                neutralTint = neutralTint,
                isPlaying = isPlaying,
                onClick = onPlayPauseClick,
                backdrop = actionsBackdrop,
            )
            BaGuideBgmRoundAction(
                icon = if (muted) appLucideVolumeOffIcon() else appLucideVolume2Icon(),
                contentDescription = stringResource(R.string.ba_catalog_bgm_volume_slider_label),
                accent = accent,
                neutralTint = neutralTint,
                active = volumeControlVisible,
                onClick = onVolumeClick,
                backdrop = actionsBackdrop,
            )
        }
    }
}

@Composable
internal fun BaGuideBgmAlbumVolumeControl(
    accent: Color,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    onToggleMuted: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    var sliderActive by rememberSaveable { mutableStateOf(false) }
    val volumeBackdrop = rememberLayerBackdrop()
    val sliderBackdrop = rememberCombinedBackdrop(backdrop, volumeBackdrop)
    val neutralTint = MiuixTheme.colorScheme.onBackgroundVariant
    val muted = volume <= 0.001f
    val sliderTint = if (muted) neutralTint.copy(alpha = 0.58f) else accent.copy(alpha = 0.95f)
    val sliderInactiveTint =
        if (muted) {
            neutralTint.copy(alpha = 0.16f)
        } else {
            accent.copy(alpha = if (sliderActive) 0.24f else 0.18f)
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BaGuideBgmVolumeControlHeight),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .layerBackdrop(volumeBackdrop),
        )
        Row(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BaGuideBgmInlineIcon(
                icon = if (muted) appLucideVolumeOffIcon() else appLucideVolume2Icon(),
                contentDescription = stringResource(R.string.ba_catalog_bgm_volume_slider_label),
                tint = sliderTint,
                size = 32.dp,
                iconSize = 22.dp,
                onClick = onToggleMuted,
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
                activeColor = sliderTint,
                inactiveColor = sliderInactiveTint,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(30.dp),
            )
            Text(
                text = stringResource(R.string.ba_catalog_bgm_volume_value, (volume * 100).toInt()),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BaGuideBgmRoundAction(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    neutralTint: Color,
    active: Boolean,
    onClick: () -> Unit = {},
    backdrop: Backdrop,
) {
    val contentTint = if (active) accent.copy(alpha = 0.98f) else neutralTint
    val actionSurfaceColor = Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.14f else 0.34f)
    LiquidSurface(
        backdrop = backdrop,
        modifier = Modifier.size(50.dp),
        shape = CircleShape,
        tint = Color.Unspecified,
        surfaceColor = actionSurfaceColor,
        chromaticAberration = true,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun BaGuideBgmPlayAction(
    accent: Color,
    neutralTint: Color,
    isPlaying: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    val contentTint = if (isPlaying) accent.copy(alpha = 0.98f) else neutralTint
    val actionSurfaceColor = Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.14f else 0.34f)
    LiquidSurface(
        backdrop = backdrop,
        modifier =
            Modifier
                .height(50.dp)
                .widthIn(min = 108.dp, max = 128.dp),
        shape = ContinuousCapsule,
        tint = Color.Unspecified,
        surfaceColor = actionSurfaceColor,
        chromaticAberration = true,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPlaying) appLucidePauseIcon() else appLucidePlayIcon(),
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text =
                    stringResource(
                        if (isPlaying) {
                            R.string.ba_catalog_bgm_action_pause
                        } else {
                            R.string.ba_catalog_bgm_action_play_short
                        },
                    ),
                color = contentTint,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun defaultAlbumArtworkBrush(accent: Color): Brush =
    Brush.linearGradient(
        colors =
            listOf(
                Color(0xFFFF4D6D),
                Color(0xFFFFC857),
                Color(0xFF35C2FF),
                accent,
                Color(0xFF2ED573),
            ),
    )

internal val BaGuideBgmVolumeControlHeight = 34.dp
