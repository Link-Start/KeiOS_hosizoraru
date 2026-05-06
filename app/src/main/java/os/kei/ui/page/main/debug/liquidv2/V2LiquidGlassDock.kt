package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Immutable
internal data class V2LiquidDockSpec(
    val height: Dp = V2LiquidGlassTokens.dockHeight,
    val itemMinWidth: Dp = V2LiquidGlassTokens.dockItemMinWidth,
    val outerPadding: Dp = 5.dp,
    val indicatorInset: Dp = V2LiquidGlassTokens.dockIndicatorInset,
    val selectedBlobStyle: V2LiquidMaterialStyle = V2LiquidMaterialStyle.ControlThumb,
    val labelPolicy: V2GlassTabLabelPolicy = V2GlassTabLabelPolicy.Always,
    val dragEnabled: Boolean = true,
    val contentTint: Color = Color.Unspecified,
    val activeTint: Color = Color.Unspecified,
    val inactiveTint: Color = Color.Unspecified,
    val badgeStyle: Color = Color.Unspecified
)

@Stable
internal class V2LiquidDockGestureState internal constructor(
    val selectedPosition: Float,
    val pressedIndex: Int,
    val pressProgress: Float,
    val dragProgress: Float
)

@Composable
internal fun V2LiquidDock(
    items: List<V2GlassTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
    compact: Boolean = false,
    labelPolicy: V2GlassTabLabelPolicy = V2GlassTabLabelPolicy.Always,
    selectionStyle: V2GlassSelectionStyle = V2GlassSelectionStyle.Indicator,
    activeTint: Color = Color.Unspecified,
    spec: V2LiquidDockSpec = V2LiquidDockSpec()
) {
    val palette = rememberV2LiquidGlassPalette()
    val density = LocalDensity.current
    val tabsBackdrop = rememberLayerBackdrop()
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    val currentOnSelected by rememberUpdatedState(onSelectedIndexChange)
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    val selectedPosition by appMotionFloatState(
        targetValue = safeSelected.toFloat(),
        durationMillis = V2LiquidGlassTokens.stateMotionMs,
        label = "v2_liquid_dock_position"
    )
    val pressProgress by appMotionFloatState(
        targetValue = if (pressedIndex >= 0 || dragIndex >= 0) 1f else 0f,
        durationMillis = 120,
        label = "v2_liquid_dock_press"
    )

    BoxWithConstraints(
        modifier = modifier.height(spec.height),
        contentAlignment = Alignment.CenterStart
    ) {
        if (items.isEmpty()) {
            V2GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                spec = V2GlassSurfaceSpec.capsule(
                    surfaceColor = palette.clearTint,
                    interactive = false
                ).copy(materialStyle = V2LiquidMaterialStyle.Dock)
            )
            return@BoxWithConstraints
        }

        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val outerPaddingPx = with(density) { spec.outerPadding.toPx() }
        val indicatorInsetPx = with(density) { spec.indicatorInset.toPx() }
        val itemWidthPx = ((widthPx - outerPaddingPx * 2f) / items.size).coerceAtLeast(
            with(density) { spec.itemMinWidth.toPx() }
        )
        val indicatorWidthPx = (itemWidthPx - indicatorInsetPx * 2f).coerceAtLeast(36f)
        val effectiveLabelPolicy = if (labelPolicy == V2GlassTabLabelPolicy.Always) {
            spec.labelPolicy
        } else {
            labelPolicy
        }
        val indicatorHeight = when {
            compact && effectiveLabelPolicy == V2GlassTabLabelPolicy.Never -> 40.dp
            compact -> spec.height - 10.dp
            else -> spec.height - 12.dp
        }
        val blobParameters = when (spec.selectedBlobStyle) {
            V2LiquidMaterialStyle.Clear -> V2LiquidParameterSet.sampleClear
            V2LiquidMaterialStyle.Dock,
            V2LiquidMaterialStyle.Prominent -> V2LiquidParameterSet.dockProminent

            V2LiquidMaterialStyle.ControlThumb -> V2LiquidParameterSet.thumbLens
            V2LiquidMaterialStyle.Regular,
            V2LiquidMaterialStyle.Tinted,
            V2LiquidMaterialStyle.Widget -> V2LiquidParameterSet.controlRegular
        }.bounded()
        val indicatorBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

        val dragModifier = if (spec.dragEnabled) {
            Modifier.pointerInput(items.size, itemWidthPx, outerPaddingPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragIndex =
                            offset.x.toDockIndex(outerPaddingPx, itemWidthPx, items.lastIndex)
                    },
                    onDragEnd = {
                        if (dragIndex >= 0) currentOnSelected(dragIndex)
                        dragIndex = -1
                    },
                    onDragCancel = { dragIndex = -1 },
                    onDrag = { change, _ ->
                        val nextIndex = change.position.x.toDockIndex(
                            outerPaddingPx,
                            itemWidthPx,
                            items.lastIndex
                        )
                        if (nextIndex != dragIndex) {
                            dragIndex = nextIndex
                            currentOnSelected(nextIndex)
                        }
                    }
                )
            }
        } else {
            Modifier
        }

        V2GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxSize()
                .then(dragModifier),
            spec = V2GlassSurfaceSpec.capsule(
                surfaceColor = palette.clearTint,
                interactive = true
            ).copy(
                materialStyle = V2LiquidMaterialStyle.Dock,
                parameters = V2LiquidParameterSet.dockProminent,
                rimLightAlpha = 0.36f,
                edgeChromaticAlpha = 0.14f,
                causticAlpha = 0.10f,
                shapeMorph = 0.7f,
                gestureTransform = { progress ->
                    translationY = -1.25.dp.toPx() * pressProgress
                    scaleX = lerp(1f, 1.006f, progress)
                    scaleY = lerp(1f, 0.996f, progress)
                }
            ),
            contentPadding = PaddingValues(horizontal = spec.outerPadding, vertical = 5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            V2LiquidDockCaptureLayer(
                itemCount = items.size,
                compact = compact,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
            )
        }

        if (selectionStyle == V2GlassSelectionStyle.Indicator) {
            Box(
                Modifier
                    .width(with(density) { indicatorWidthPx.toDp() })
                    .height(indicatorHeight)
                    .align(Alignment.CenterStart)
                    .drawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            vibrancy()
                            blur(blobParameters.blur.toPx())
                            lens(
                                blobParameters.refractionHeight.toPx(),
                                blobParameters.refractionAmount.toPx(),
                                depthEffect = blobParameters.depthEffect,
                                chromaticAberration = blobParameters.chromaticAberration
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.96f) },
                        shadow = {
                            Shadow(
                                radius = 14.dp,
                                color = Color.Black.copy(alpha = 0.14f)
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * pressProgress,
                                alpha = pressProgress
                            )
                        },
                        layerBlock = {
                            translationX =
                                outerPaddingPx + indicatorInsetPx + selectedPosition * itemWidthPx
                            val velocityStretch =
                                if (pressedIndex >= 0 || dragIndex >= 0) 1f else 0f
                            scaleX = lerp(1f, 1.10f, pressProgress * velocityStretch)
                            scaleY = lerp(1f, 0.94f, pressProgress * velocityStretch)
                        },
                        onDrawSurface = {
                            val itemTint = items.getOrNull(safeSelected)?.selectedTint
                            val tint = when {
                                itemTint != null && itemTint.isSpecified -> itemTint
                                activeTint.isSpecified -> activeTint
                                spec.activeTint.isSpecified -> spec.activeTint
                                else -> palette.accent.copy(alpha = 0.16f)
                            }
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(
                                Color.White.copy(
                                    alpha = when (spec.selectedBlobStyle) {
                                        V2LiquidMaterialStyle.Prominent -> 0.30f
                                        V2LiquidMaterialStyle.Dock -> 0.24f
                                        V2LiquidMaterialStyle.ControlThumb -> 0.22f
                                        else -> 0.18f
                                    }
                                )
                            )
                            drawRect(tint.copy(alpha = tint.alpha * 0.72f))
                        }
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spec.outerPadding, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                V2LiquidDockItem(
                    item = item,
                    selected = index == safeSelected,
                    showIcon = showIcons,
                    compact = compact,
                    labelPolicy = effectiveLabelPolicy,
                    spec = spec,
                    onPressedChange = { pressed ->
                        pressedIndex =
                            if (pressed) index else if (pressedIndex == index) -1 else pressedIndex
                    },
                    onClick = { currentOnSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RowScope.V2LiquidDockItem(
    item: V2GlassTabItem,
    selected: Boolean,
    showIcon: Boolean,
    compact: Boolean,
    labelPolicy: V2GlassTabLabelPolicy,
    spec: V2LiquidDockSpec,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    androidx.compose.runtime.LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }
    val scale by appMotionFloatState(
        targetValue = if (pressed) 0.96f else if (selected) 1.02f else 1f,
        durationMillis = 130,
        label = "v2_liquid_dock_item_scale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled,
                role = Role.Tab,
                onClick = onClick
            )
            .alpha(if (item.enabled) 1f else 0.42f),
        contentAlignment = Alignment.Center
    ) {
        V2LiquidDockItemContent(
            item = item,
            selected = selected,
            color = when {
                selected && spec.activeTint.isSpecified -> spec.activeTint
                selected -> palette.content
                spec.inactiveTint.isSpecified -> spec.inactiveTint
                spec.contentTint.isSpecified -> spec.contentTint
                else -> palette.secondary
            },
            showIcon = showIcon,
            compact = compact,
            labelPolicy = labelPolicy,
            badgeColor = if (spec.badgeStyle.isSpecified) spec.badgeStyle else palette.danger,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp)
                .alpha(1f)
                .v2DockItemScale(scale)
        )
    }
}

@Composable
private fun V2LiquidDockItemContent(
    item: V2GlassTabItem,
    selected: Boolean,
    color: Color,
    showIcon: Boolean,
    compact: Boolean,
    labelPolicy: V2GlassTabLabelPolicy,
    badgeColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    val showLabel = when (labelPolicy) {
        V2GlassTabLabelPolicy.Always -> true
        V2GlassTabLabelPolicy.Selected -> selected
        V2GlassTabLabelPolicy.Never -> false
    }
    Column(
        modifier = modifier.padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact || !showLabel) 0.dp else 2.dp,
            Alignment.CenterVertically
        )
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (showIcon && item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.contentDescription,
                    tint = color,
                    modifier = Modifier.size(if (compact) 17.dp else 19.dp)
                )
            }
            if (!item.badge.isNullOrBlank()) {
                Text(
                    text = item.badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .align(Alignment.TopEnd)
                        .background(
                            color = if (badgeColor.isSpecified) badgeColor else palette.danger,
                            shape = ContinuousCapsule
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        if (showLabel) {
            Text(
                text = item.label,
                color = color,
                fontSize = if (compact) {
                    11.sp
                } else {
                    AppTypographyTokens.Caption.fontSize
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!selected && !showIcon && !showLabel) {
            Box(Modifier.size(4.dp))
        }
        if (selected && color == Color.Unspecified) {
            Text(
                text = item.label,
                color = palette.content,
                fontSize = AppTypographyTokens.Eyebrow.fontSize,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun V2LiquidDockCaptureLayer(
    itemCount: Int,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = itemCount.coerceAtLeast(1)
        val itemWidth = size.width / count
        val horizontalInset = if (compact) 8.dp.toPx() else 10.dp.toPx()
        val trackHeight = if (compact) {
            (size.height - 16.dp.toPx()).coerceAtLeast(28.dp.toPx())
        } else {
            (size.height - 14.dp.toPx()).coerceAtLeast(36.dp.toPx())
        }
        val top = (size.height - trackHeight) / 2f
        repeat(count) { index ->
            val left = index * itemWidth + horizontalInset
            val width = (itemWidth - horizontalInset * 2f).coerceAtLeast(24.dp.toPx())
            drawRoundRect(
                color = Color.White.copy(alpha = 0.32f),
                topLeft = Offset(left, top),
                size = Size(width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )
        }
    }
}

private fun Float.toDockIndex(
    outerPaddingPx: Float,
    itemWidthPx: Float,
    lastIndex: Int
): Int {
    if (lastIndex <= 0 || itemWidthPx <= 0f) return 0
    return ((this - outerPaddingPx) / itemWidthPx)
        .fastRoundToInt()
        .fastCoerceIn(0, lastIndex)
}

private fun Modifier.v2DockItemScale(scale: Float): Modifier {
    return this.then(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}
