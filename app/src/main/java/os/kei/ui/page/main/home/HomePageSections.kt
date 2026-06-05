@file:Suppress("FunctionName")

package os.kei.ui.page.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.appSquircleSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val HOME_CARD_HORIZONTAL_PADDING_DP = 12

internal fun Modifier.homeKeiHdrAccent(
    enabled: Boolean,
    sweepProgress: () -> Float,
    sweepAlpha: Float = 0.62f,
    radialAlpha: Float = 0.20f,
    radialRadiusScale: Float = 0.72f,
    radialCenterX: Float = 0.5f,
    radialCenterY: Float = 0.5f,
): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithCache {
            val radialBrush =
                Brush.radialGradient(
                    colors = HOME_KEI_HDR_RADIAL_COLORS,
                    center = Offset(size.width * radialCenterX, size.height * radialCenterY),
                    radius = size.minDimension * radialRadiusScale,
                )
            onDrawWithContent {
                drawContent()
                val currentSweepProgress = sweepProgress()
                val sweepVisibility = homeKeiHdrSweepVisibility(currentSweepProgress)
                if (sweepVisibility > 0f) {
                    val visibleSweepAlpha = sweepAlpha * sweepVisibility
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.Transparent,
                                        (currentSweepProgress - HOME_KEI_HDR_SWEEP_HALF_WIDTH).coerceIn(0f, 1f) to Color.Transparent,
                                        currentSweepProgress.coerceIn(0f, 1f) to Color.White.copy(alpha = visibleSweepAlpha),
                                        (currentSweepProgress + HOME_KEI_HDR_SWEEP_HALF_WIDTH).coerceIn(0f, 1f) to Color.Transparent,
                                        1f to Color.Transparent,
                                    ),
                            ),
                        blendMode = BlendMode.SrcAtop,
                    )
                    drawRect(
                        brush = radialBrush,
                        alpha = radialAlpha * sweepVisibility,
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            }
        }
}

internal fun homeKeiHdrSweepVisibility(sweepProgress: Float): Float {
    if (sweepProgress <= 0f || sweepProgress >= 1f) return 0f
    val fadeIn = (sweepProgress / HOME_KEI_HDR_EDGE_FADE_WIDTH).coerceIn(0f, 1f)
    val fadeOut = ((1f - sweepProgress) / HOME_KEI_HDR_EDGE_FADE_WIDTH).coerceIn(0f, 1f)
    return minOf(fadeIn, fadeOut)
}

private const val HOME_KEI_HDR_SWEEP_HALF_WIDTH = 0.16f
private const val HOME_KEI_HDR_EDGE_FADE_WIDTH = 0.18f
private val HOME_KEI_HDR_RADIAL_COLORS = listOf(Color.White, Color.Transparent)

@Composable
internal fun Modifier.homeHeroForegroundBlur(
    backdrop: LayerBackdrop?,
    enabled: Boolean,
    shape: Shape,
    blurRadiusDp: Float,
): Modifier {
    if (!enabled || backdrop == null) return this
    val isDark = isSystemInDarkTheme()
    val logoBlend =
        remember(isDark) {
            if (isDark) {
                listOf(
                    BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                    BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xFFFF73AD), BlurBlendMode.Lab),
                )
            } else {
                listOf(
                    BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                    BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xFFFF5C96), BlurBlendMode.Lab),
                )
            }
        }
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = blurRadiusDp,
        colors = BlurColors(blendColors = logoBlend),
        contentBlendMode = BlendMode.DstIn,
    )
}

@Composable
internal fun HomeInfoCard(
    backdrop: Backdrop?,
    blurEnabled: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val blurRadius = resolvedGlassBlurDp(8.dp, GlassVariant.Content)
    val lensRadius = resolvedGlassLensDp(24.dp, GlassVariant.Content)
    val containerColor =
        if (blurEnabled) {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
        } else {
            MiuixTheme.colorScheme.surfaceContainer
        }
    val interactionSource = remember { MutableInteractionSource() }
    val clickAction = onClick
    val clickable = clickAction != null
    val clickModifier =
        if (clickAction != null) {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = clickAction,
            )
        } else {
            Modifier
        }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScaleState =
        appMotionFloatState(
            targetValue = if (clickable && isPressed) 0.992f else 1f,
            durationMillis = 120,
            label = "home_info_card_press_scale",
        )
    val pressedScaleProvider = remember(pressedScaleState) { { pressedScaleState.value } }

    val cardModifier =
        Modifier
            .padding(horizontal = HOME_CARD_HORIZONTAL_PADDING_DP.dp)
            .padding(bottom = 6.dp)
            .graphicsLayer {
                val scale = pressedScaleProvider()
                scaleX = scale
                scaleY = scale
            }

    if (backdrop != null && blurEnabled) {
        LiquidSurface(
            backdrop = backdrop,
            modifier =
                cardModifier
                    .fillMaxWidth()
                    .then(clickModifier),
            shape = RoundedRectangle(20.dp),
            isInteractive = clickable,
            surfaceColor = containerColor,
            blurRadius = blurRadius,
            lensRadius = lensRadius,
            interactionSource = interactionSource,
        ) {
            HomeInfoCardContent(content)
        }
    } else {
        Box(
            modifier =
                cardModifier
                    .fillMaxWidth()
                    .then(clickModifier)
                    .appSquircleSurface(MiuixTheme.colorScheme.surfaceContainer, 20.dp),
        ) {
            HomeInfoCardContent(content)
        }
    }
}

@Composable
private fun HomeInfoCardContent(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
internal fun HomeBottomPageLabel(
    page: BottomPage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier =
            Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = page.iconScale
                    scaleY = page.iconScale
                }
        if (page.iconRes != null) {
            Icon(
                painter = painterResource(id = page.iconRes),
                contentDescription = page.label,
                tint = if (page.keepOriginalColors) Color.Unspecified else MiuixTheme.colorScheme.onBackground,
                modifier = iconModifier,
            )
        } else {
            page.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = page.label,
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = iconModifier,
                )
            }
        }
        Text(
            text = page.label,
            color = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
internal fun HomeInfoGridCard(
    title: String,
    stats: List<HomeCardStatItem>,
    naText: String,
    columns: Int = 2,
) {
    val summaryColor =
        if (isSystemInDarkTheme()) {
            Color(0xFF8AB8FF)
        } else {
            Color(0xFF1E63D6)
        }
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val rows =
        remember(stats, columns) {
            homeInfoGridRows(stats, columns)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        rows.forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowStats.forEach { stat ->
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stat.label,
                            color = labelColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stat.value.ifBlank { naText },
                            color = if (stat.emphasize) summaryColor else MiuixTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (stat.emphasize) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = stat.valueMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat((columns - rowStats.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun homeInfoGridRows(
    stats: List<HomeCardStatItem>,
    columns: Int,
): List<List<HomeCardStatItem>> {
    val safeColumns = columns.coerceAtLeast(1)
    if (stats.size <= safeColumns) return listOf(stats)

    val rows = stats.chunked(safeColumns).map { it.toMutableList() }.toMutableList()
    val lastIndex = rows.lastIndex
    if (safeColumns >= 3 && rows[lastIndex].size == 1 && lastIndex > 0) {
        val previous = rows[lastIndex - 1]
        if (previous.size > 2) {
            rows[lastIndex].add(0, previous.removeAt(previous.lastIndex))
        }
    }
    return rows
}

@Immutable
internal data class HomeCardStatItem(
    val label: String,
    val value: String,
    val emphasize: Boolean = false,
    val valueMaxLines: Int = 1,
)
