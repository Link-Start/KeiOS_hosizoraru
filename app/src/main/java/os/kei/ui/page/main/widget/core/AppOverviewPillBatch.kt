@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.activeGlassBackdrop
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.isAppInDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.min

internal const val MaxBatchedOverviewPillCount = 40

private val BatchedOverviewPillHeight = 28.dp
private val BatchedOverviewPillHorizontalPadding = 10.dp
private val BatchedOverviewPillHorizontalGap = 10.dp
private val BatchedOverviewPillVerticalGap = 8.dp
private val BatchedOverviewPillBorderWidth = 0.8.dp

private val LocalOverviewPillBatchHost = compositionLocalOf<OverviewPillBatchHostState?> { null }

internal val LocalOverviewPillBatchTransformProvider =
    compositionLocalOf<(() -> OverviewPillBatchTransform?)?> { null }

internal data class OverviewPillBatchTransform(
    val pivot: Offset,
    val translation: Offset,
    val scaleX: Float,
    val scaleY: Float,
)

private data class OverviewPillBatchGroup(
    val coordinatesState: OverviewPillBatchCoordinatesState,
    val positionState: OverviewPillBatchPositionState,
    val boundsState: OverviewPillBatchBoundsState,
    val pillsState: OverviewPillBatchPillsState,
    val transformProviderState: OverviewPillBatchTransformProviderState,
)

private class OverviewPillBatchCoordinatesState(coordinates: LayoutCoordinates) {
    var coordinates by mutableStateOf(coordinates)
}

private class OverviewPillBatchPositionState(position: Offset) {
    var position by mutableStateOf(position)
}

private class OverviewPillBatchBoundsState(bounds: List<Rect>) {
    var bounds by mutableStateOf(bounds)
}

private class OverviewPillBatchPillsState(pills: List<AppOverviewPill>) {
    var pills by mutableStateOf(pills)
}

private class OverviewPillBatchTransformProviderState(
    provider: (() -> OverviewPillBatchTransform?)?,
) {
    var provider by mutableStateOf(provider)
}

private data class ResolvedOverviewPill(
    val bounds: Rect,
    val pill: AppOverviewPill,
)

private class OverviewPillBatchHostState {
    private var rootCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    private val groups = mutableStateMapOf<Any, OverviewPillBatchGroup>()
    private var outlineVersionState by mutableIntStateOf(0)

    val outlineVersion: Int
        get() = outlineVersionState

    fun updateRootCoordinates(coordinates: LayoutCoordinates) {
        val rootChanged = rootCoordinates !== coordinates
        rootCoordinates = coordinates
        var geometryChanged = rootChanged
        groups.values.forEach { group ->
            val position = resolvePosition(group.coordinatesState.coordinates) ?: return@forEach
            if (group.positionState.position != position) {
                group.positionState.position = position
                geometryChanged = true
            }
        }
        if (geometryChanged) outlineVersionState++
    }

    fun register(
        key: Any,
        coordinates: LayoutCoordinates,
        geometry: OverviewPillBatchGeometry,
        pills: List<AppOverviewPill>,
        transformProvider: (() -> OverviewPillBatchTransform?)?,
    ) {
        val group = groups[key]
        val position = resolvePosition(coordinates) ?: Offset.Zero
        if (group == null) {
            groups[key] =
                OverviewPillBatchGroup(
                    coordinatesState = OverviewPillBatchCoordinatesState(coordinates),
                    positionState = OverviewPillBatchPositionState(position),
                    boundsState = OverviewPillBatchBoundsState(geometry.bounds),
                    pillsState = OverviewPillBatchPillsState(pills),
                    transformProviderState = OverviewPillBatchTransformProviderState(transformProvider),
                )
            outlineVersionState++
        } else {
            val coordinatesChanged = group.coordinatesState.coordinates !== coordinates
            val positionChanged = group.positionState.position != position
            val geometryChanged = group.boundsState.bounds != geometry.bounds
            if (coordinatesChanged) {
                group.coordinatesState.coordinates = coordinates
            }
            if (positionChanged) {
                group.positionState.position = position
            }
            if (geometryChanged) {
                group.boundsState.bounds = geometry.bounds
            }
            group.pillsState.pills = pills
            group.transformProviderState.provider = transformProvider
            if (coordinatesChanged || positionChanged || geometryChanged) {
                outlineVersionState++
            }
        }
    }

    fun updatePills(key: Any, pills: List<AppOverviewPill>) {
        groups[key]?.pillsState?.pills = pills
    }

    fun updateTransformProvider(
        key: Any,
        transformProvider: (() -> OverviewPillBatchTransform?)?,
    ) {
        groups[key]?.transformProviderState?.provider = transformProvider
    }

    fun unregister(key: Any) {
        if (groups.remove(key) != null) {
            outlineVersionState++
        }
    }

    private fun resolvePosition(coordinates: LayoutCoordinates): Offset? {
        val root = rootCoordinates?.takeIf(LayoutCoordinates::isAttached) ?: return null
        if (!coordinates.isAttached) return null
        return root.localPositionOf(coordinates, Offset.Zero)
    }

    fun resolvedPills(): List<ResolvedOverviewPill> {
        rootCoordinates?.takeIf(LayoutCoordinates::isAttached) ?: return emptyList()
        val resolved = ArrayList<ResolvedOverviewPill>(MaxBatchedOverviewPillCount)
        val groupSnapshot = groups.values.toList()
        for (group in groupSnapshot) {
            if (resolved.size >= MaxBatchedOverviewPillCount) break
            if (!group.coordinatesState.coordinates.isAttached) continue
            val pills = group.pillsState.pills
            val transform = group.transformProviderState.provider?.invoke()
            for ((index, localBounds) in group.boundsState.bounds.withIndex()) {
                val pill = pills.getOrNull(index) ?: continue
                if (resolved.size >= MaxBatchedOverviewPillCount) break
                val topLeft = group.positionState.position + localBounds.topLeft
                val baseBounds = Rect(topLeft, localBounds.size)
                resolved +=
                    ResolvedOverviewPill(
                        bounds =
                            transform?.let(baseBounds::transformedBy) ?: baseBounds,
                        pill = pill,
                    )
            }
        }
        return resolved
    }
}

@Composable
internal fun AppOverviewLiquidPillBatchHost(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activeBackdrop = activeGlassBackdrop(backdrop ?: LocalLiquidParentBackdrop.current)
    if (activeBackdrop == null) {
        Box(modifier = modifier) { content() }
        return
    }

    val hostState = remember { OverviewPillBatchHostState() }
    val outlineVersion = hostState.outlineVersion
    val isDark = isAppInDarkTheme()
    val blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, GlassVariant.Compact)
    val lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, GlassVariant.Compact)
    val backgroundAlpha = if (isDark) 0.18f else 0.24f
    val borderAlpha = if (isDark) 0.35f else 0.42f
    val highlightAlpha = if (isDark) 0.42f else 0.62f

    Box(modifier = modifier.onGloballyPositioned(hostState::updateRootCoordinates)) {
        // The material's outline is the union of pill shapes. Keep its clipped layer on a
        // decorative sibling: wrapping the content in that layer also clips pointer hit testing
        // and makes a parent overview card unresponsive in the space between or beyond its pills.
        Box(
            modifier =
                Modifier.matchParentSize().drawBackdrop(
                    backdrop = activeBackdrop,
                    shape = {
                        OverviewPillBatchShape(
                            hostState.resolvedPills().map(ResolvedOverviewPill::bounds),
                            version = outlineVersion,
                        )
                    },
                    effects = {
                        vibrancy()
                        blur(blurRadius.toPx())
                        overviewPillBatchLens(
                            bounds = hostState.resolvedPills().map(ResolvedOverviewPill::bounds),
                            refractionHeight = lensRadius.toPx(),
                            refractionAmount = lensRadius.toPx(),
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = highlightAlpha,
                            style =
                                OverviewPillBatchHighlightStyle(
                                    bounds = hostState.resolvedPills().map(ResolvedOverviewPill::bounds),
                                ),
                        )
                    },
                    shadow = null,
                    innerShadow = null,
                    onDrawSurface = {
                        hostState.resolvedPills().forEach { entry ->
                            val bounds = entry.bounds
                            drawRoundRect(
                                color = entry.pill.color.copy(alpha = backgroundAlpha),
                                topLeft = bounds.topLeft,
                                size = bounds.size,
                                cornerRadius = CornerRadius(bounds.height / 2f),
                            )
                        }
                    },
                    onDrawFront = {
                        val strokeWidth = BatchedOverviewPillBorderWidth.toPx()
                        val halfStroke = strokeWidth / 2f
                        hostState.resolvedPills().forEach { entry ->
                            val bounds = entry.bounds
                            drawRoundRect(
                                color = entry.pill.color.copy(alpha = borderAlpha),
                                topLeft = bounds.topLeft + Offset(halfStroke, halfStroke),
                                size = Size(bounds.width - strokeWidth, bounds.height - strokeWidth),
                                cornerRadius = CornerRadius((bounds.height - strokeWidth) / 2f),
                                style = Stroke(strokeWidth),
                            )
                        }
                    },
                ),
        )
        CompositionLocalProvider(LocalOverviewPillBatchHost provides hostState) {
            content()
        }
    }
}

@Composable
internal fun AppOverviewBatchedLiquidPillFlow(
    pills: List<AppOverviewPill>,
    modifier: Modifier,
    backdrop: Backdrop?,
) {
    val batchHost = LocalOverviewPillBatchHost.current
    val transformProvider = LocalOverviewPillBatchTransformProvider.current
    val activeBackdrop = activeGlassBackdrop(backdrop ?: LocalLiquidParentBackdrop.current)
    if ((activeBackdrop == null && batchHost == null) || pills.isEmpty()) {
        AppOverviewLegacyPillFlow(
            pills = pills,
            modifier = modifier,
            backdrop = backdrop,
        )
        return
    }

    val isDark = isAppInDarkTheme()
    val geometry = remember { OverviewPillBatchGeometry() }
    val batchGroupKey = remember { Any() }
    DisposableEffect(batchHost, batchGroupKey) {
        onDispose { batchHost?.unregister(batchGroupKey) }
    }
    SideEffect {
        batchHost?.updatePills(batchGroupKey, pills)
        batchHost?.updateTransformProvider(batchGroupKey, transformProvider)
    }
    val blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, GlassVariant.Compact)
    val lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, GlassVariant.Compact)
    val backgroundAlpha = if (isDark) 0.18f else 0.24f
    val borderAlpha = if (isDark) 0.35f else 0.42f
    val highlightAlpha = if (isDark) 0.42f else 0.62f
    val batchShape: () -> Shape = {
        OverviewPillBatchShape(geometry.bounds)
    }

    val batchRegistrationModifier =
        if (batchHost != null) {
            Modifier.onGloballyPositioned { coordinates ->
                batchHost.register(
                    key = batchGroupKey,
                    coordinates = coordinates,
                    geometry = geometry,
                    pills = pills,
                    transformProvider = transformProvider,
                )
            }
        } else {
            Modifier
        }
    val liquidMaterialModifier =
        if (batchHost == null && activeBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = activeBackdrop,
                shape = batchShape,
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    overviewPillBatchLens(
                        bounds = geometry.bounds,
                        refractionHeight = lensRadius.toPx(),
                        refractionAmount = lensRadius.toPx(),
                    )
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = highlightAlpha,
                        style = OverviewPillBatchHighlightStyle(bounds = geometry.bounds),
                    )
                },
                shadow = null,
                innerShadow = null,
                onDrawSurface = {
                    geometry.bounds.forEachIndexed { index, bounds ->
                        drawRoundRect(
                            color = pills[index].color.copy(alpha = backgroundAlpha),
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            cornerRadius = CornerRadius(bounds.height / 2f),
                        )
                    }
                },
                onDrawFront = {
                    val strokeWidth = BatchedOverviewPillBorderWidth.toPx()
                    geometry.bounds.forEachIndexed { index, bounds ->
                        val halfStroke = strokeWidth / 2f
                        drawRoundRect(
                            color = pills[index].color.copy(alpha = borderAlpha),
                            topLeft = bounds.topLeft + Offset(halfStroke, halfStroke),
                            size = Size(bounds.width - strokeWidth, bounds.height - strokeWidth),
                            cornerRadius = CornerRadius((bounds.height - strokeWidth) / 2f),
                            style = Stroke(strokeWidth),
                        )
                    }
                },
            )
        } else {
            Modifier
        }

    Layout(
        modifier =
            modifier
                .then(batchRegistrationModifier)
                .then(liquidMaterialModifier),
        content = {
            pills.forEach { pill ->
                OverviewPillBatchLabel(
                    pill = pill,
                    isDark = isDark,
                )
            }
        },
    ) { measurables, constraints ->
        val childConstraints =
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
            )
        val placeables = measurables.map { measurable -> measurable.measure(childConstraints) }
        val flowLayout =
            calculateOverviewPillFlowLayout(
                childSizes = placeables.map { IntSize(it.width, it.height) },
                maxWidth = constraints.maxWidth,
                horizontalGap = BatchedOverviewPillHorizontalGap.roundToPx(),
                verticalGap = BatchedOverviewPillVerticalGap.roundToPx(),
            )
        val width = flowLayout.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = flowLayout.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        geometry.bounds =
            flowLayout.placements.mapIndexed { index, placement ->
                val childSize = flowLayout.childSizes[index]
                val physicalX =
                    if (layoutDirection == LayoutDirection.Ltr) {
                        placement.x
                    } else {
                        width - placement.x - childSize.width
                    }
                Rect(
                    left = physicalX.toFloat(),
                    top = placement.y.toFloat(),
                    right = (physicalX + childSize.width).toFloat(),
                    bottom = (placement.y + childSize.height).toFloat(),
                )
            }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val placement = flowLayout.placements[index]
                placeable.placeRelative(placement.x, placement.y)
            }
        }
    }
}

private fun Rect.transformedBy(transform: OverviewPillBatchTransform): Rect {
    val centerRelativeToPivot = center - transform.pivot
    val transformedCenter =
        transform.pivot +
            transform.translation +
            Offset(
                x = centerRelativeToPivot.x * transform.scaleX,
                y = centerRelativeToPivot.y * transform.scaleY,
            )
    val halfWidth = width * transform.scaleX / 2f
    val halfHeight = height * transform.scaleY / 2f
    return Rect(
        left = transformedCenter.x - halfWidth,
        top = transformedCenter.y - halfHeight,
        right = transformedCenter.x + halfWidth,
        bottom = transformedCenter.y + halfHeight,
    )
}

@Composable
private fun OverviewPillBatchLabel(
    pill: AppOverviewPill,
    isDark: Boolean,
) {
    val metrics = rememberAppStatusPillMetrics(AppStatusPillSize.Compact)
    val typography =
        MiuixTheme.textStyles.main.merge(
            TextStyle(
                fontSize = metrics.typography.fontSize,
                lineHeight = metrics.typography.lineHeight,
                fontWeight = metrics.typography.fontWeight,
            ),
        )
    val contentColor = if (isDark) pill.color else pill.color.copy(alpha = 0.96f)
    Box(
        modifier =
            Modifier
                .height(BatchedOverviewPillHeight)
                .padding(horizontal = BatchedOverviewPillHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        DisableSelection {
            Text(
                text = pill.label,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography,
            )
        }
    }
}

internal data class OverviewPillFlowLayout(
    val width: Int,
    val height: Int,
    val childSizes: List<IntSize>,
    val placements: List<IntOffset>,
)

internal fun calculateOverviewPillFlowLayout(
    childSizes: List<IntSize>,
    maxWidth: Int,
    horizontalGap: Int,
    verticalGap: Int,
): OverviewPillFlowLayout {
    if (childSizes.isEmpty()) {
        return OverviewPillFlowLayout(0, 0, emptyList(), emptyList())
    }
    val boundedMaxWidth = maxWidth.takeUnless { it == Constraints.Infinity } ?: Int.MAX_VALUE
    val placements = ArrayList<IntOffset>(childSizes.size)
    var x = 0
    var y = 0
    var rowHeight = 0
    var contentWidth = 0
    childSizes.forEach { childSize ->
        if (x > 0 && x + childSize.width > boundedMaxWidth) {
            x = 0
            y += rowHeight + verticalGap
            rowHeight = 0
        }
        placements += IntOffset(x, y)
        x += childSize.width + horizontalGap
        rowHeight = max(rowHeight, childSize.height)
        contentWidth = max(contentWidth, (x - horizontalGap).coerceAtLeast(0))
    }
    val contentHeight = y + rowHeight
    return OverviewPillFlowLayout(
        width = contentWidth.coerceAtMost(boundedMaxWidth),
        height = contentHeight,
        childSizes = childSizes,
        placements = placements,
    )
}

private class OverviewPillBatchGeometry {
    var bounds by mutableStateOf<List<Rect>>(emptyList())
}

private data class OverviewPillBatchShape(
    val bounds: List<Rect>,
    val version: Int = 0,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        bounds.forEach { bounds ->
            val radius = bounds.height / 2f
            path.addRoundRect(
                RoundRect(
                    rect = bounds,
                    cornerRadius = CornerRadius(radius),
                ),
            )
        }
        return Outline.Generic(path)
    }
}

private fun BackdropEffectScope.overviewPillBatchLens(
    bounds: List<Rect>,
    refractionHeight: Float,
    refractionAmount: Float,
) {
    val uniforms =
        resolveOverviewPillBatchUniforms(
            bounds = bounds,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
        ) ?: return
    if (padding > 0f) {
        padding = (padding - uniforms.refractionHeight).coerceAtLeast(0f)
    }
    runtimeShaderEffect(
        key = "OverviewPillBatchLens-${uniforms.pillCount}",
        shaderString = overviewPillBatchLensShader(uniforms.pillCount),
        uniformShaderName = "content",
    ) {
        setFloatUniform("pillBounds", uniforms.bounds)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("pillRadius", uniforms.pillRadius)
        setFloatUniform("refractionHeight", uniforms.refractionHeight)
        setFloatUniform("refractionAmount", -uniforms.refractionAmount)
        setFloatUniform("depthEffect", 1f)
    }
}

internal data class OverviewPillBatchUniforms(
    val pillCount: Int,
    val bounds: FloatArray,
    val pillRadius: Float,
    val refractionHeight: Float,
    val refractionAmount: Float,
)

internal fun resolveOverviewPillBatchUniforms(
    bounds: List<Rect>,
    refractionHeight: Float,
    refractionAmount: Float,
): OverviewPillBatchUniforms? {
    val validBounds =
        bounds
            .asSequence()
            .filter { pillBounds -> pillBounds.width > 0f && pillBounds.height > 0f }
            .take(MaxBatchedOverviewPillCount)
            .toList()
    val pillRadius = validBounds.minOfOrNull { it.height / 2f } ?: return null
    if (!refractionHeight.isFinite() || !refractionAmount.isFinite()) return null
    val uniformBounds = FloatArray(validBounds.size * 4)
    validBounds.forEachIndexed { index, rect ->
        val offset = index * 4
        uniformBounds[offset] = rect.left
        uniformBounds[offset + 1] = rect.top
        uniformBounds[offset + 2] = rect.right
        uniformBounds[offset + 3] = rect.bottom
    }
    return OverviewPillBatchUniforms(
        pillCount = validBounds.size,
        bounds = uniformBounds,
        pillRadius = pillRadius,
        refractionHeight = refractionHeight.coerceIn(0f, pillRadius),
        refractionAmount = refractionAmount.coerceIn(0f, validBounds.minOf { it.height }),
    ).takeIf { it.refractionHeight > 0f && it.refractionAmount > 0f }
}

private data class OverviewPillBatchHighlightStyle(
    val bounds: List<Rect>,
) : HighlightStyle {
    override val color: Color = Color.White.copy(alpha = 0.5f)
    override val blendMode: BlendMode = BlendMode.Plus

    override fun DrawScope.createShader(
        shape: Shape,
        runtimeShaderCache: RuntimeShaderCache,
    ): RuntimeShader? {
        if (!isRuntimeShaderSupported()) return null
        val uniforms =
            resolveOverviewPillBatchUniforms(
                bounds = bounds,
                refractionHeight = 1f,
                refractionAmount = 1f,
            ) ?: return null
        return runtimeShaderCache.obtainRuntimeShader(
            "OverviewPillBatchHighlight-${uniforms.pillCount}",
            overviewPillBatchHighlightShader(uniforms.pillCount),
        ).apply {
            setFloatUniform("pillBounds", uniforms.bounds)
            setFloatUniform("pillRadius", uniforms.pillRadius)
            setColorUniform("color", color.copy(alpha = 1f))
            setFloatUniform("angle", Math.toRadians(45.0).toFloat())
            setFloatUniform("falloff", 1f)
        }
    }
}

private val OverviewPillBatchLensShaderCache =
    arrayOfNulls<String>(MaxBatchedOverviewPillCount + 1)
private val OverviewPillBatchHighlightShaderCache =
    arrayOfNulls<String>(MaxBatchedOverviewPillCount + 1)

@Language("AGSL")
private fun overviewPillBatchGeometryShader(pillCount: Int) =
    """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    }
    float gradX = step(cornerCoord.y, cornerCoord.x);
    return sign(coord) * float2(gradX, 1.0 - gradX);
}

float4 findPillBounds(float2 coord, float expansion) {
    float4 selectedBounds = float4(0.0);
    for (int index = 0; index < $pillCount; index++) {
        float4 candidate = pillBounds[index];
        bool contains = coord.x >= candidate.x - expansion &&
            coord.x <= candidate.z + expansion &&
            coord.y >= candidate.y - expansion &&
            coord.y <= candidate.w + expansion;
        if (contains) {
            selectedBounds = candidate;
        }
    }
    return selectedBounds;
}
    """.trimIndent()

@Language("AGSL")
private fun overviewPillBatchLensShader(pillCount: Int): String {
    require(pillCount in 1..MaxBatchedOverviewPillCount)
    OverviewPillBatchLensShaderCache[pillCount]?.let { return it }
    val shader =
        """
uniform shader content;
uniform float4 pillBounds[$pillCount];
uniform float2 offset;
uniform float pillRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

${overviewPillBatchGeometryShader(pillCount)}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 componentCoord = coord + offset;
    float4 bounds = findPillBounds(componentCoord, 0.0);
    if (bounds.z <= bounds.x || bounds.w <= bounds.y) {
        return content.eval(coord);
    }
    float2 halfSize = (bounds.zw - bounds.xy) * 0.5;
    float2 centeredCoord = componentCoord - (bounds.xy + halfSize);
    float sd = sdRoundedRect(centeredCoord, halfSize, pillRadius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float normalizedDistance = clamp(1.0 - -sd / refractionHeight, 0.0, 1.0);
    float displacement = circleMap(normalizedDistance) * refractionAmount;
    float gradRadius = min(pillRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 gradient = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
        depthEffect * normalize(centeredCoord)
    );
    return content.eval(coord + displacement * gradient);
}
        """.trimIndent()
    OverviewPillBatchLensShaderCache[pillCount] = shader
    return shader
}

@Language("AGSL")
private fun overviewPillBatchHighlightShader(pillCount: Int): String {
    require(pillCount in 1..MaxBatchedOverviewPillCount)
    OverviewPillBatchHighlightShaderCache[pillCount]?.let { return it }
    val shader =
        """
uniform float4 pillBounds[$pillCount];
uniform float pillRadius;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;

${overviewPillBatchGeometryShader(pillCount)}

half4 main(float2 coord) {
    float4 bounds = findPillBounds(coord, 2.0);
    if (bounds.z <= bounds.x || bounds.w <= bounds.y) {
        return half4(0.0);
    }
    float2 halfSize = (bounds.zw - bounds.xy) * 0.5;
    float2 centeredCoord = coord - (bounds.xy + halfSize);
    float gradRadius = min(pillRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 gradient = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float intensity = pow(abs(dot(gradient, normal)), falloff);
    return color * intensity;
}
        """.trimIndent()
    OverviewPillBatchHighlightShaderCache[pillCount] = shader
    return shader
}
