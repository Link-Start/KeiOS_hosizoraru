package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import os.kei.ui.animation.DampedDragAnimation
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

@Composable
internal fun V2LiquidDock(
    items: List<V2GlassTabItem>,
    selectedIndex: Int,
    selectedPosition: Float? = null,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
    compact: Boolean = false,
    labelPolicy: V2GlassTabLabelPolicy = V2GlassTabLabelPolicy.Always,
    selectionStyle: V2GlassSelectionStyle = V2GlassSelectionStyle.Indicator,
    activeTint: Color = Color.Unspecified,
    scrollBehavior: V2LiquidDockScrollBehavior = V2LiquidDockScrollBehavior(),
    spec: V2LiquidDockSpec = V2LiquidDockSpec()
) {
    val palette = rememberV2LiquidGlassPalette()
    val density = LocalDensity.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsBackdrop = rememberLayerBackdrop()
    val safeTabsCount = items.size.coerceAtLeast(1)
    val safeSelected = selectedIndex.coerceIn(0, safeTabsCount - 1)
    val currentOnSelected by rememberUpdatedState(onSelectedIndexChange)
    val layout = spec.resolvedLayout(labelPolicy, scrollBehavior.mode)
    val material = spec.materialSpec
    val blob = spec.blobSpec
    val outerParameters = material.outerParameters.bounded()
    val blobParameters = material.blobParameters.bounded()
    val dockHeight = when (layout.mode) {
        V2LiquidDockMode.FloatingCompact -> spec.collapsedHeight
        V2LiquidDockMode.Expanded,
        V2LiquidDockMode.SplitDock -> layout.height
    }
    val effectiveLabelPolicy = when (layout.mode) {
        V2LiquidDockMode.Expanded -> layout.labelPolicy
        V2LiquidDockMode.FloatingCompact -> when (layout.labelPolicy) {
            V2GlassTabLabelPolicy.Always -> V2GlassTabLabelPolicy.Selected
            else -> layout.labelPolicy
        }

        V2LiquidDockMode.SplitDock -> layout.labelPolicy
    }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var currentIndex by remember(safeTabsCount) { mutableIntStateOf(safeSelected) }
    var totalWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var itemWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val externalSelectionPosition =
        selectedPosition?.fastCoerceIn(0f, (safeTabsCount - 1).toFloat())
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember {
        derivedStateOf {
            if (totalWidthPx <= 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() } * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    class V2DockDragHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { V2DockDragHolder() }
    val dampedDrag = remember(animationScope, safeTabsCount, density, isLtr, spec.dragEnabled) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = externalSelectionPosition ?: safeSelected.toFloat(),
            valueRange = 0f..(safeTabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            gestureKey = safeTabsCount to spec.dragEnabled,
            canDrag = { offset ->
                when {
                    !spec.dragEnabled -> false
                    holder.instance == null -> true
                    itemWidthPx <= 0f || totalWidthPx <= 0f -> false
                    else -> {
                        val anim = holder.instance!!
                        val paddingPx = with(density) { layout.outerPadding.toPx() }
                        val indicatorX = anim.value * itemWidthPx
                        val globalTouchX = if (isLtr) {
                            paddingPx + indicatorX + offset.x
                        } else {
                            totalWidthPx - paddingPx - itemWidthPx - indicatorX + offset.x
                        }
                        globalTouchX in 0f..totalWidthPx
                    }
                }
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, safeTabsCount - 1)
                currentIndex = targetIndex
                currentOnSelected(targetIndex)
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (itemWidthPx > 0f) {
                    val progressDelta = dragAmount.x / itemWidthPx * if (isLtr) 1f else -1f
                    updateValue(
                        (targetValue + progressDelta).fastCoerceIn(
                            0f,
                            (safeTabsCount - 1).toFloat()
                        )
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }
    LaunchedEffect(externalSelectionPosition, safeTabsCount) {
        val pagerPosition = externalSelectionPosition ?: return@LaunchedEffect
        dampedDrag.snapToValue(pagerPosition, updateVelocity = false)
    }
    LaunchedEffect(safeSelected, externalSelectionPosition, safeTabsCount) {
        currentIndex = safeSelected
        if (externalSelectionPosition == null) {
            dampedDrag.animateToValue(safeSelected.toFloat())
        }
    }
    LaunchedEffect(dampedDrag, safeTabsCount, externalSelectionPosition) {
        if (externalSelectionPosition != null) return@LaunchedEffect
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index ->
                dampedDrag.animateToValue(index.fastCoerceIn(0, safeTabsCount - 1).toFloat())
            }
    }
    val displaySelectedPosition = (externalSelectionPosition ?: dampedDrag.value)
        .fastCoerceIn(0f, (safeTabsCount - 1).toFloat())
    val currentDisplaySelectedPosition by rememberUpdatedState(displaySelectedPosition)
    val currentPanelOffset by rememberUpdatedState(panelOffset)
    val visualSelectedIndex = displaySelectedPosition.fastRoundToInt()
        .fastCoerceIn(0, safeTabsCount - 1)
    val pressProgress by appMotionFloatState(
        targetValue = if (pressedIndex >= 0 || dampedDrag.pressProgress > 0.01f) 1f else 0f,
        durationMillis = 120,
        label = "v2_liquid_dock_press"
    )
    val combinedPressProgress = max(pressProgress, dampedDrag.pressProgress)
    val interactiveHighlight = remember(animationScope, isLtr) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (currentDisplaySelectedPosition + 0.5f) * itemWidthPx +
                                currentPanelOffset
                    } else {
                        layerSize.width - (currentDisplaySelectedPosition + 0.5f) * itemWidthPx +
                                currentPanelOffset
                    },
                    y = layerSize.height / 2f
                )
            },
            highlightColor = Color.White,
            highlightStrength = if (isDark) 0.90f else 0.62f,
            highlightRadiusScale = if (isDark) 1.08f else 0.92f
        )
    }

    BoxWithConstraints(
        modifier = modifier.height(dockHeight),
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
        val outerPaddingPx = with(density) { layout.outerPadding.toPx() }
        val measuredItemWidthPx = ((widthPx - outerPaddingPx * 2f) / safeTabsCount).coerceAtLeast(
            with(density) { layout.itemMinWidth.toPx() }
        )
        val indicatorWidthPx = (measuredItemWidthPx * blob.minWidthFraction.coerceIn(0.72f, 1.08f))
            .coerceAtLeast(with(density) { 42.dp.toPx() })
        val indicatorCenterOffsetPx = (measuredItemWidthPx - indicatorWidthPx) / 2f
        val indicatorHeight = when {
            compact && effectiveLabelPolicy == V2GlassTabLabelPolicy.Never -> 50.dp
            compact -> dockHeight - 12.dp
            else -> dockHeight - 14.dp
        }
        val indicatorBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
        val captureTint = when {
            activeTint.isSpecified -> activeTint
            spec.activeTint.isSpecified -> spec.activeTint
            else -> palette.accent
        }.copy(alpha = material.captureContentAlpha.coerceIn(0.20f, 1f))

        V2GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    totalWidthPx = size.width.toFloat()
                    val nextItemWidth = ((size.width - outerPaddingPx * 2f) / safeTabsCount)
                        .coerceAtLeast(1f)
                    if (abs(itemWidthPx - nextItemWidth) > 0.5f) {
                        itemWidthPx = nextItemWidth
                    }
                }
                .then(interactiveHighlight.modifier),
            spec = V2GlassSurfaceSpec.capsule(
                surfaceColor = palette.clearTint,
                interactive = true
            ).copy(
                materialStyle = V2LiquidMaterialStyle.Dock,
                parameters = outerParameters,
                rimLightAlpha = material.rimLightAlpha,
                edgeChromaticAlpha = material.edgeChromaticAlpha,
                causticAlpha = material.causticAlpha,
                contentVibrancy = material.contentVibrancy,
                clearDimmingAlpha = material.readabilityFillAlpha,
                readabilityProfile = V2LiquidReadabilityProfile.Auto,
                shapeMorph = 0.7f,
                gestureTransform = { progress ->
                    translationX = currentPanelOffset
                    translationY = -blob.liftDp.toPx() * combinedPressProgress
                    scaleX = lerp(1f, 1.006f, progress)
                    scaleY = lerp(1f, 0.996f, progress)
                }
            ),
            contentPadding = PaddingValues(horizontal = layout.outerPadding, vertical = 5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            V2LiquidDockContentCaptureLayer(
                items = items,
                selectedPosition = displaySelectedPosition,
                visualSelectedIndex = visualSelectedIndex,
                showIcons = showIcons,
                compact = compact,
                labelPolicy = effectiveLabelPolicy,
                layout = layout,
                captureScale = lerp(1f, 1.18f, dampedDrag.pressProgress),
                captureTint = captureTint,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .graphicsLayer {
                        translationX = currentPanelOffset
                        colorFilter = ColorFilter.tint(captureTint)
                    }
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
                        highlight = {
                            Highlight.Default.copy(
                                alpha = material.rimLightAlpha.coerceIn(
                                    0.20f,
                                    1f
                                )
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 14.dp,
                                color = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f)
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * combinedPressProgress,
                                alpha = combinedPressProgress
                            )
                        },
                        layerBlock = {
                            val rawOffset = displaySelectedPosition * measuredItemWidthPx
                            translationX = if (isLtr) {
                                outerPaddingPx + indicatorCenterOffsetPx + rawOffset + panelOffset
                            } else {
                                totalWidthPx - outerPaddingPx - indicatorCenterOffsetPx -
                                        indicatorWidthPx - rawOffset + panelOffset
                            }
                            translationY = -blob.liftDp.toPx() * combinedPressProgress
                            val velocity = (dampedDrag.velocity / 10f)
                                .fastCoerceIn(-blob.velocityMorph, blob.velocityMorph)
                            val pressStretch =
                                combinedPressProgress * blob.stretchOnPress
                            val dragStretch =
                                dampedDrag.pressProgress * blob.stretchOnDrag
                            scaleX = 1f + pressStretch + dragStretch + velocity
                            scaleY = 1f - combinedPressProgress * 0.04f - abs(velocity) * 0.18f
                        },
                        onDrawSurface = {
                            val itemTint = items.getOrNull(safeSelected)?.selectedTint
                            val tint = when {
                                itemTint != null && itemTint.isSpecified -> itemTint
                                activeTint.isSpecified -> activeTint
                                spec.activeTint.isSpecified -> spec.activeTint
                                else -> palette.accent.copy(alpha = 0.18f)
                            }
                            val readability = if (isDark) {
                                Color.White.copy(
                                    alpha = material.blobReadabilityAlpha.coerceIn(
                                        0f,
                                        0.18f
                                    )
                                )
                            } else {
                                Color.Black.copy(
                                    alpha = material.blobReadabilityAlpha.coerceIn(
                                        0f,
                                        0.16f
                                    )
                                )
                            }
                            drawRect(readability, alpha = lerp(1f, 0.58f, combinedPressProgress))
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = tint.alpha * 0.38f))
                            drawRect(
                                Color.White.copy(alpha = lerp(0.10f, 0.18f, combinedPressProgress)),
                                blendMode = BlendMode.Screen
                            )
                            drawRect(
                                Color.White.copy(alpha = material.causticAlpha.coerceIn(0f, 0.18f)),
                                blendMode = BlendMode.Overlay
                            )
                        }
                    )
                    .then(interactiveHighlight.gestureModifier)
                    .then(if (spec.dragEnabled) dampedDrag.modifier else Modifier)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = currentPanelOffset }
                .padding(horizontal = layout.outerPadding, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selectionProgress = (1f - abs(displaySelectedPosition - index))
                    .fastCoerceIn(0f, 1f)
                V2LiquidDockItem(
                    item = item,
                    selected = index == visualSelectedIndex,
                    selectionProgress = selectionProgress,
                    showIcon = showIcons,
                    compact = compact,
                    labelPolicy = effectiveLabelPolicy,
                    spec = spec,
                    layout = layout,
                    onPressedChange = { pressed ->
                        pressedIndex =
                            if (pressed) index else if (pressedIndex == index) -1 else pressedIndex
                    },
                    onClick = {
                        currentIndex = index
                        currentOnSelected(index)
                    },
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
    selectionProgress: Float,
    showIcon: Boolean,
    compact: Boolean,
    labelPolicy: V2GlassTabLabelPolicy,
    spec: V2LiquidDockSpec,
    layout: V2LiquidDockLayoutSpec,
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
        targetValue = when {
            pressed -> lerp(0.94f, 0.98f, selectionProgress)
            selectionProgress > 0f -> lerp(1f, 1.08f, selectionProgress)
            selected -> 1.02f
            else -> 1f
        },
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
            layout = layout,
            badgeColor = if (spec.badgeStyle.isSpecified) spec.badgeStyle else palette.danger,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp)
                .alpha(if (item.enabled) 1f else 0.42f)
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
    layout: V2LiquidDockLayoutSpec,
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
                    modifier = Modifier.size(if (compact) layout.iconSize - 3.dp else layout.iconSize)
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
                        .offset(layout.badgeOffset.x, layout.badgeOffset.y)
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
    }
}

@Composable
private fun V2LiquidDockContentCaptureLayer(
    items: List<V2GlassTabItem>,
    selectedPosition: Float,
    visualSelectedIndex: Int,
    showIcons: Boolean,
    compact: Boolean,
    labelPolicy: V2GlassTabLabelPolicy,
    layout: V2LiquidDockLayoutSpec,
    captureScale: Float,
    captureTint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selectionProgress = (1f - abs(selectedPosition - index))
                .fastCoerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                V2LiquidDockItemContent(
                    item = item,
                    selected = index == visualSelectedIndex,
                    color = captureTint,
                    showIcon = showIcons,
                    compact = compact,
                    labelPolicy = labelPolicy,
                    layout = layout,
                    badgeColor = captureTint,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 3.dp)
                        .v2DockItemScale(lerp(1f, captureScale, selectionProgress))
                )
            }
        }
    }
}

private fun V2LiquidDockSpec.resolvedLayout(
    requestedLabelPolicy: V2GlassTabLabelPolicy,
    mode: V2LiquidDockMode
): V2LiquidDockLayoutSpec {
    val base = layoutSpec
    val policy = when {
        requestedLabelPolicy != V2GlassTabLabelPolicy.Always -> requestedLabelPolicy
        base.labelPolicy != V2GlassTabLabelPolicy.Always -> base.labelPolicy
        else -> labelPolicy
    }
    return base.copy(
        mode = mode,
        height = if (base.height == V2LiquidGlassTokens.dockHeight) height else base.height,
        itemMinWidth = if (base.itemMinWidth == V2LiquidGlassTokens.dockItemMinWidth) {
            itemMinWidth
        } else {
            base.itemMinWidth
        },
        outerPadding = if (base.outerPadding == 6.dp) outerPadding else base.outerPadding,
        indicatorInset = if (base.indicatorInset == V2LiquidGlassTokens.dockIndicatorInset) {
            indicatorInset
        } else {
            base.indicatorInset
        },
        labelPolicy = policy
    )
}

private fun Modifier.v2DockItemScale(scale: Float): Modifier {
    return this.then(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}
