package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LiquidGlassDropdownContainerRadius = 26.dp
internal val LiquidGlassDropdownItemRadius = 18.dp
private val LiquidGlassDropdownMinWidth = 168.dp
private val LiquidGlassDropdownMaxWidth = 280.dp
private val LiquidGlassDropdownMaxHeight = 336.dp
private val LiquidGlassDropdownContentPadding = 8.dp
internal val LiquidGlassDropdownItemPressSafePadding =
    AppInteractiveTokens.compactLiquidPressSafePadding
internal val LiquidGlassDropdownRowMinHeight = 44.dp
internal val LiquidGlassDropdownIconSize = 18.dp
internal val LiquidGlassDropdownCheckSize = 18.dp
internal val LocalLiquidGlassDropdownSizingPass = staticCompositionLocalOf { false }
internal val LocalLiquidGlassDropdownBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalLiquidGlassDropdownMaterial =
    staticCompositionLocalOf { LiquidGlassDropdownMaterial.Default }

enum class LiquidGlassDropdownMaterial {
    Default,
    ActionMenu
}

private data class LiquidGlassDropdownMetrics(
    val containerRadius: Dp,
    val contentPadding: Dp,
    val blurRadius: Dp,
    val lensStart: Dp,
    val lensEnd: Dp,
    val shadowElevation: Dp,
    val innerShadowRadius: Dp,
    val vibrancy: Boolean,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val lightHighlightAlpha: Float,
    val darkHighlightAlpha: Float,
    val lightOuterShadowAlpha: Float,
    val darkOuterShadowAlpha: Float,
    val lightSpotShadowAlpha: Float,
    val darkSpotShadowAlpha: Float,
    val lightShadowAlpha: Float,
    val darkShadowAlpha: Float,
    val lightInnerShadowAlpha: Float,
    val darkInnerShadowAlpha: Float
)

private fun liquidGlassDropdownMetrics(material: LiquidGlassDropdownMaterial): LiquidGlassDropdownMetrics {
    return when (material) {
        LiquidGlassDropdownMaterial.Default -> LiquidGlassDropdownMetrics(
            containerRadius = LiquidGlassDropdownContainerRadius,
            contentPadding = LiquidGlassDropdownContentPadding,
            blurRadius = 8.dp,
            lensStart = 18.dp,
            lensEnd = 34.dp,
            shadowElevation = 18.dp,
            innerShadowRadius = 10.dp,
            vibrancy = true,
            chromaticAberration = true,
            depthEffect = true,
            lightHighlightAlpha = 0.86f,
            darkHighlightAlpha = 0.72f,
            lightOuterShadowAlpha = 0.12f,
            darkOuterShadowAlpha = 0.22f,
            lightSpotShadowAlpha = 0.10f,
            darkSpotShadowAlpha = 0.18f,
            lightShadowAlpha = 0.14f,
            darkShadowAlpha = 0.22f,
            lightInnerShadowAlpha = 0.12f,
            darkInnerShadowAlpha = 0.20f
        )

        LiquidGlassDropdownMaterial.ActionMenu -> LiquidGlassDropdownMetrics(
            containerRadius = 30.dp,
            contentPadding = 5.dp,
            blurRadius = 32.dp,
            lensStart = 34.dp,
            lensEnd = 64.dp,
            shadowElevation = 26.dp,
            innerShadowRadius = 14.dp,
            vibrancy = false,
            chromaticAberration = true,
            depthEffect = true,
            lightHighlightAlpha = 0.88f,
            darkHighlightAlpha = 0.62f,
            lightOuterShadowAlpha = 0.14f,
            darkOuterShadowAlpha = 0.22f,
            lightSpotShadowAlpha = 0.10f,
            darkSpotShadowAlpha = 0.18f,
            lightShadowAlpha = 0.14f,
            darkShadowAlpha = 0.22f,
            lightInnerShadowAlpha = 0.12f,
            darkInnerShadowAlpha = 0.18f
        )
    }
}

@Composable
fun LiquidGlassDropdownColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = LiquidGlassDropdownMinWidth,
    maxWidth: Dp = LiquidGlassDropdownMaxWidth,
    maxHeight: Dp = LiquidGlassDropdownMaxHeight,
    initialScrollItemIndex: Int? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    backdrop: Backdrop? = null,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val metrics = liquidGlassDropdownMetrics(material)
    val containerShape = RoundedRectangle(metrics.containerRadius)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val colors = liquidGlassDropdownContainerColors(
        isDark = isDark,
        accentColor = accentColor,
        material = material
    )
    val activeBackdrop = backdrop.takeIf { LocalLiquidControlsEnabled.current }
    LaunchedEffect(initialScrollItemIndex, scrollState.maxValue, density) {
        val itemIndex = initialScrollItemIndex?.coerceAtLeast(0) ?: return@LaunchedEffect
        if (scrollState.maxValue <= 0) return@LaunchedEffect
        val targetOffset = with(density) {
            val rowOffset = LiquidGlassDropdownRowMinHeight.toPx() * itemIndex
            val contextInset = maxHeight.toPx() * 0.34f
            (rowOffset - contextInset).toInt()
        }.coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(targetOffset)
    }

    Box(
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .shadow(
                elevation = metrics.shadowElevation,
                shape = containerShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) metrics.darkOuterShadowAlpha else metrics.lightOuterShadowAlpha),
                spotColor = Color.Black.copy(alpha = if (isDark) metrics.darkSpotShadowAlpha else metrics.lightSpotShadowAlpha)
            )
            .clip(containerShape)
            .border(
                width = 1.dp,
                color = colors.borderColor,
                shape = containerShape
            )
            .then(
                if (activeBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = activeBackdrop,
                        shape = { containerShape },
                        effects = {
                            if (metrics.vibrancy) {
                                vibrancy()
                            }
                            blur(metrics.blurRadius.toPx())
                            lens(
                                metrics.lensStart.toPx(),
                                metrics.lensEnd.toPx(),
                                chromaticAberration = metrics.chromaticAberration,
                                depthEffect = metrics.depthEffect
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (isDark) metrics.darkHighlightAlpha else metrics.lightHighlightAlpha)
                        },
                        shadow = {
                            Shadow.Default.copy(color = Color.Black.copy(alpha = if (isDark) metrics.darkShadowAlpha else metrics.lightShadowAlpha))
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = metrics.innerShadowRadius,
                                alpha = if (isDark) metrics.darkInnerShadowAlpha else metrics.lightInnerShadowAlpha
                            )
                        },
                        onDrawSurface = {
                            drawRect(colors.surfaceColor)
                            drawRect(colors.topSheen)
                        }
                    )
                } else {
                    Modifier
                        .background(colors.fallbackBaseColor, containerShape)
                        .background(colors.fallbackMiddleBrush, containerShape)
                        .background(colors.fallbackSheenBrush, containerShape)
                }
            )
    ) {
        CompositionLocalProvider(
            LocalLiquidGlassDropdownBackdrop provides activeBackdrop,
            LocalLiquidGlassDropdownMaterial provides material
        ) {
            SubcomposeLayout(
                modifier = Modifier
                    .padding(metrics.contentPadding)
                    .heightIn(max = maxHeight)
                    .verticalScroll(scrollState)
            ) { constraints ->
                val minWidthPx = minWidth.roundToPx()
                val maxWidthPx = maxWidth.roundToPx().coerceAtLeast(minWidthPx)
                val contentInsetPx = metrics.contentPadding.roundToPx() * 2
                val minContentWidth = (minWidthPx - contentInsetPx).coerceAtLeast(0)
                val maxContentWidth = (maxWidthPx - contentInsetPx).coerceAtLeast(minContentWidth)
                val probeConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = maxContentWidth,
                    minHeight = 0
                )
                val probePlaceables = subcompose("probe") {
                    CompositionLocalProvider(
                        LocalLiquidGlassDropdownSizingPass provides true,
                        content = content
                    )
                }.map { measurable ->
                    measurable.measure(probeConstraints)
                }
                val resolvedWidth = probePlaceables.maxOfOrNull { it.width }
                    ?.coerceIn(minContentWidth, maxContentWidth)
                    ?: minContentWidth
                val contentConstraints = constraints.copy(
                    minWidth = resolvedWidth,
                    maxWidth = resolvedWidth,
                    minHeight = 0
                )
                val placeables = subcompose("content", content).map { measurable ->
                    measurable.measure(contentConstraints)
                }
                val contentHeight = placeables.sumOf { it.height }
                layout(resolvedWidth, contentHeight) {
                    var currentY = 0
                    placeables.forEach { placeable ->
                        placeable.placeRelative(0, currentY)
                        currentY += placeable.height
                    }
                }
            }
        }
    }
}


@Composable
fun AppStandaloneLiquidGlassDropdownColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = LiquidGlassDropdownMinWidth,
    maxWidth: Dp = LiquidGlassDropdownMaxWidth,
    maxHeight: Dp = LiquidGlassDropdownMaxHeight,
    initialScrollItemIndex: Int? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default,
    content: @Composable () -> Unit
) {
    val localBackdrop = rememberLayerBackdrop()
    AppStandaloneBackdropHost(
        backdrop = localBackdrop,
        modifier = modifier
    ) {
        LiquidGlassDropdownColumn(
            minWidth = minWidth,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            initialScrollItemIndex = initialScrollItemIndex,
            accentColor = accentColor,
            backdrop = localBackdrop,
            material = material,
            content = content
        )
    }
}

@Composable
fun AppLiquidGlassDropdownColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = LiquidGlassDropdownMinWidth,
    maxWidth: Dp = LiquidGlassDropdownMaxWidth,
    maxHeight: Dp = LiquidGlassDropdownMaxHeight,
    initialScrollItemIndex: Int? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    backdrop: Backdrop? = null,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default,
    content: @Composable () -> Unit
) {
    if (backdrop != null) {
        LiquidGlassDropdownColumn(
            modifier = modifier,
            minWidth = minWidth,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            initialScrollItemIndex = initialScrollItemIndex,
            accentColor = accentColor,
            backdrop = backdrop,
            material = material,
            content = content
        )
    } else {
        AppStandaloneLiquidGlassDropdownColumn(
            modifier = modifier,
            minWidth = minWidth,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            initialScrollItemIndex = initialScrollItemIndex,
            accentColor = accentColor,
            material = material,
            content = content
        )
    }
}


private data class LiquidGlassDropdownContainerColors(
    val surfaceColor: Color,
    val topSheen: Color,
    val borderColor: Color,
    val fallbackBaseColor: Color,
    val fallbackMiddleBrush: Brush,
    val fallbackSheenBrush: Brush
)

@Composable
private fun liquidGlassDropdownContainerColors(
    isDark: Boolean,
    accentColor: Color,
    material: LiquidGlassDropdownMaterial
): LiquidGlassDropdownContainerColors {
    return when (material) {
        LiquidGlassDropdownMaterial.ActionMenu -> if (isDark) {
            LiquidGlassDropdownContainerColors(
                surfaceColor = Color(0xFF101113).copy(alpha = 0.82f),
                topSheen = Color.White.copy(alpha = 0.08f),
                borderColor = Color.White.copy(alpha = 0.18f),
                fallbackBaseColor = Color(0xFF101113).copy(alpha = 0.92f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.05f)
                    ),
                    start = Offset.Zero,
                    end = Offset(360f, 460f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(112f, 20f),
                    radius = 280f
                )
            )
        } else {
            LiquidGlassDropdownContainerColors(
                surfaceColor = Color.White.copy(alpha = 0.92f),
                topSheen = Color.White.copy(alpha = 0.22f),
                borderColor = Color.White.copy(alpha = 0.94f),
                fallbackBaseColor = Color.White.copy(alpha = 0.97f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        Color(0xFFF4F8FF).copy(alpha = 0.78f),
                        Color.White.copy(alpha = 0.64f)
                    ),
                    start = Offset.Zero,
                    end = Offset(360f, 460f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.84f),
                        Color(0xFFEAF4FF).copy(alpha = 0.34f),
                        Color.Transparent
                    ),
                    center = Offset(112f, 20f),
                    radius = 300f
                )
            )
        }

        LiquidGlassDropdownMaterial.Default -> if (isDark) {
            LiquidGlassDropdownContainerColors(
                surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f),
                topSheen = Color.White.copy(alpha = 0.16f),
                borderColor = Color.White.copy(alpha = 0.24f),
                fallbackBaseColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.10f)
                    ),
                    start = Offset.Zero,
                    end = Offset(320f, 420f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(96f, 24f),
                    radius = 260f
                )
            )
        } else {
            LiquidGlassDropdownContainerColors(
                surfaceColor = Color.White.copy(alpha = 0.68f),
                topSheen = Color.White.copy(alpha = 0.28f),
                borderColor = Color.White.copy(alpha = 0.90f),
                fallbackBaseColor = Color.White.copy(alpha = 0.94f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color(0xFFEAF3FF).copy(alpha = 0.58f),
                        Color.White.copy(alpha = 0.36f)
                    ),
                    start = Offset.Zero,
                    end = Offset(320f, 420f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color(0xFFE1EFFF).copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(96f, 24f),
                    radius = 260f
                )
            )
        }
    }
}

internal fun liquidGlassDropdownSelectedSurfaceColor(
    isDark: Boolean,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default
): Color {
    return when (material) {
        LiquidGlassDropdownMaterial.ActionMenu -> if (isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.48f)
        }

        LiquidGlassDropdownMaterial.Default -> if (isDark) {
            Color.White.copy(alpha = 0.20f)
        } else {
            Color(0xFFEFF4FB).copy(alpha = 0.72f)
        }
    }
}

internal fun liquidGlassDropdownPressedSurfaceColor(
    isDark: Boolean,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default
): Color {
    return when (material) {
        LiquidGlassDropdownMaterial.ActionMenu -> if (isDark) {
            Color.White.copy(alpha = 0.07f)
        } else {
            Color.White.copy(alpha = 0.42f)
        }

        LiquidGlassDropdownMaterial.Default -> if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.52f)
        }
    }
}

internal fun liquidGlassDropdownSelectedBorderColor(
    isDark: Boolean,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default
): Color {
    return when (material) {
        LiquidGlassDropdownMaterial.ActionMenu -> if (isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.60f)
        }

        LiquidGlassDropdownMaterial.Default -> if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.72f)
        }
    }
}

internal fun liquidGlassDropdownItemAccent(
    isDark: Boolean,
    accentColor: Color,
    variant: GlassVariant
): Color {
    return when (variant) {
        GlassVariant.SheetDangerAction -> Color(0xFFE25B6A)
        else -> if (accentColor == Color.Unspecified) {
            if (isDark) Color(0xFF71ADFF) else Color(0xFF3B82F6)
        } else {
            accentColor
        }
    }
}
