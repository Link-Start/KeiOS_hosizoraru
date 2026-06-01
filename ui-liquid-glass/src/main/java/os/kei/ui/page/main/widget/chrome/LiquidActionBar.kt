@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import os.kei.ui.animation.DampedDragAnimation
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.appGlassRuntimeEffectsEnabled
import os.kei.ui.page.main.widget.glass.glassEffectRuntime
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class LiquidActionItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String? = null,
)

@Composable
fun LiquidActionBarPopupAnchors(
    itemCount: Int,
    modifier: Modifier = Modifier,
    compactSingleItem: Boolean = false,
    content: @Composable (Int, IntRect?) -> Unit,
) {
    if (itemCount <= 0) return
    val anchorBounds =
        remember(itemCount) {
            mutableStateListOf<IntRect?>().apply {
                repeat(itemCount) { add(null) }
            }
        }
    val minimumWidth =
        if (compactSingleItem && itemCount == 1) {
            AppChromeTokens.liquidActionBarSingleWidth
        } else {
            AppChromeTokens.liquidActionBarMinWidth
        }
    val barWidth = maxOf(minimumWidth, (itemCount * AppChromeTokens.liquidActionBarItemStep.value).dp)
    Row(
        modifier =
            modifier
                .width(barWidth)
                .height(AppChromeTokens.liquidActionBarOuterHeight)
                .padding(horizontal = AppChromeTokens.liquidActionBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(AppChromeTokens.liquidActionBarItemStep - 2.dp)
                            .height(AppChromeTokens.liquidActionBarInnerHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInWindow()
                                    val measuredBounds =
                                        IntRect(
                                            left = position.x.roundToInt(),
                                            top = position.y.roundToInt(),
                                            right = (position.x + coordinates.size.width).roundToInt(),
                                            bottom = (position.y + coordinates.size.height).roundToInt(),
                                        )
                                    if (anchorBounds[index] != measuredBounds) {
                                        anchorBounds[index] = measuredBounds
                                    }
                                },
                    )
                    content(index, anchorBounds.getOrNull(index))
                }
            }
        }
    }
}

@Composable
internal fun RowScope.LiquidActionItemSlot(
    item: LiquidActionItem,
    tint: () -> Color,
    iconScale: () -> Float = { 1f },
    onClick: (() -> Unit)? = null,
) {
    val clickModifier =
        if (onClick != null && item.enabled) {
            Modifier.clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }
    Box(
        modifier =
            Modifier
                .then(item.testTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier)
                .then(clickModifier)
                .fillMaxHeight()
                .weight(1f)
                .graphicsLayer {
                    val scale = iconScale()
                    scaleX = scale
                    scaleY = scale
                    colorFilter = ColorFilter.tint(tint())
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            tint = Color.White,
        )
    }
}

@Composable
fun LiquidActionBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    items: List<LiquidActionItem>,
    isBlurEnabled: Boolean = true,
    layeredStyleEnabled: Boolean = true,
    compactSingleItem: Boolean = false,
    selectedIndex: Int = 0,
    onInteractionChanged: (Boolean) -> Unit = {},
) {
    if (items.isEmpty()) return
    val clampedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)

    val isInLightTheme = !isSystemInDarkTheme()
    val effectiveBlurEnabled = isBlurEnabled && appGlassRuntimeEffectsEnabled()
    val accentColor = MiuixTheme.colorScheme.primary
    val palette =
        rememberLiquidActionBarPalette(
            layeredStyleEnabled = layeredStyleEnabled,
            isBlurEnabled = effectiveBlurEnabled,
            isInLightTheme = isInLightTheme,
            primary = accentColor,
            onSurface = MiuixTheme.colorScheme.onSurface,
            surfaceContainer = MiuixTheme.colorScheme.surfaceContainer,
        )

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val horizontalPaddingPx =
        with(density) {
            AppChromeTokens.liquidActionBarHorizontalPadding.toPx()
        }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val onInteractionChangedState = rememberUpdatedState(onInteractionChanged)
    val itemsState = rememberUpdatedState(items)
    val pressedScale = rememberLiquidActionBarPressedScale()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var settledIndex by remember(items.size) {
        mutableFloatStateOf(clampedSelectedIndex.toFloat())
    }

    val offsetAnimation = remember { Animatable(0f) }
    val effectivePanelOffsetProvider =
        remember(density, offsetAnimation, layeredStyleEnabled) {
            {
                if (layeredStyleEnabled) {
                    liquidActionBarPanelOffset(
                        rawOffsetPx = offsetAnimation.value,
                        totalWidthPx = totalWidthPx,
                        density = density,
                    )
                } else {
                    0f
                }
            }
        }

    var gestureActive by remember { mutableStateOf(false) }
    var dragMoved by remember { mutableStateOf(false) }
    var dragTravelPx by remember { mutableFloatStateOf(0f) }
    val dragActivationThresholdPx = rememberLiquidActionBarDragActivationThresholdPx()

    val dampedDragAnimation =
        remember(animationScope, items.size, density, isLtr, layeredStyleEnabled) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = clampedSelectedIndex.toFloat(),
                valueRange = 0f..(items.lastIndex).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = pressedScale,
                gestureKey = items.size to isLtr,
                canDrag = { true },
                onDragStarted = {
                    gestureActive = true
                    dragMoved = false
                    dragTravelPx = 0f
                    onInteractionChangedState.value(true)
                },
                onDragStopped = {
                    if (!gestureActive) return@DampedDragAnimation
                    gestureActive = false
                    onInteractionChangedState.value(false)
                    val currentItems = itemsState.value
                    val fallbackIndex =
                        settledIndex
                            .fastRoundToInt()
                            .fastCoerceIn(0, currentItems.lastIndex)
                    if (!dragMoved) {
                        settledIndex = fallbackIndex.toFloat()
                        animateToValue(settledIndex)
                        if (layeredStyleEnabled) {
                            currentItems
                                .getOrNull(fallbackIndex)
                                ?.takeIf { it.enabled }
                                ?.onClick
                                ?.invoke()
                        }
                        animationScope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                        return@DampedDragAnimation
                    }
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, currentItems.lastIndex)
                    val resolvedIndex =
                        if (currentItems.getOrNull(targetIndex)?.enabled == true) {
                            targetIndex
                        } else {
                            fallbackIndex
                        }
                    settledIndex = resolvedIndex.toFloat()
                    animateToValue(settledIndex)
                    currentItems
                        .getOrNull(resolvedIndex)
                        ?.takeIf { it.enabled }
                        ?.onClick
                        ?.invoke()
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (tabWidthPx > 0) {
                        dragTravelPx += abs(dragAmount.x)
                        if (!dragMoved && dragTravelPx >= dragActivationThresholdPx) {
                            dragMoved = true
                        }
                        val raw =
                            (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, items.lastIndex.toFloat())
                        snapToValue(raw)
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            )
        }
    LaunchedEffect(clampedSelectedIndex, items.size) {
        val target = clampedSelectedIndex.toFloat()
        settledIndex = target
        if (
            abs(dampedDragAnimation.value - target) > 0.001f ||
            abs(dampedDragAnimation.targetValue - target) > 0.001f
        ) {
            dampedDragAnimation.updateValue(target)
        }
    }

    val selectionProgressProvider =
        rememberLiquidActionBarSelectionProgressProvider(dampedDragAnimation, items.size)
    val interactionHighlightColor =
        if (layeredStyleEnabled || isInLightTheme) {
            Color.White
        } else {
            palette.selectionGlowColor
        }
    val interactionHighlightStrength =
        liquidActionBarInteractionHighlightStrength(
            layeredStyleEnabled = layeredStyleEnabled,
            isInLightTheme = isInLightTheme,
        )
    val interactionHighlightRadiusScale =
        liquidActionBarInteractionHighlightRadiusScale(
            layeredStyleEnabled = layeredStyleEnabled,
            isInLightTheme = isInLightTheme,
        )
    val interactionProgressProvider =
        remember(dampedDragAnimation) {
            { dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f) }
        }
    val actionItemTintProviderFactory =
        remember(layeredStyleEnabled, palette, selectionProgressProvider) {
            { index: Int, enabled: Boolean ->
                {
                    val baseColor =
                        if (layeredStyleEnabled) {
                            palette.inactiveContentColor
                        } else {
                            lerp(
                                palette.inactiveContentColor,
                                palette.activeContentColor,
                                selectionProgressProvider(index),
                            )
                        }
                    if (enabled) baseColor else baseColor.copy(alpha = 0.38f)
                }
            }
        }
    val actionItemScaleProviderFactory =
        remember(layeredStyleEnabled, selectionProgressProvider, interactionProgressProvider) {
            { index: Int ->
                {
                    if (layeredStyleEnabled) {
                        1f
                    } else {
                        val selectionProgress = selectionProgressProvider(index)
                        1f + (selectionProgress * 0.05f) + (interactionProgressProvider() * selectionProgress * 0.03f)
                    }
                }
            }
        }
    val interactionLensScale = glassEffectRuntime().interactionLensScale
    val effectBlurDp = UiPerformanceBudget.backdropBlur
    val effectLensDp = UiPerformanceBudget.backdropLens
    val interactiveHighlightEnabled = effectiveBlurEnabled && (layeredStyleEnabled || isInLightTheme)
    val interactiveHighlight =
        if (interactiveHighlightEnabled) {
            remember(
                animationScope,
                dampedDragAnimation,
                tabWidthPx,
                isInLightTheme,
                layeredStyleEnabled,
                isLtr,
                interactionHighlightColor,
                interactionHighlightStrength,
                interactionHighlightRadiusScale,
            ) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                horizontalPaddingPx +
                                    (dampedDragAnimation.value + 0.5f) * tabWidthPx +
                                    effectivePanelOffsetProvider()
                            } else {
                                size.width - horizontalPaddingPx -
                                    (dampedDragAnimation.value + 0.5f) * tabWidthPx +
                                    effectivePanelOffsetProvider()
                            },
                            size.height / 2f,
                        )
                    },
                    highlightColor = interactionHighlightColor,
                    highlightStrength = interactionHighlightStrength,
                    highlightRadiusScale = interactionHighlightRadiusScale,
                )
            }
        } else {
            null
        }
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    val minimumWidth =
        if (compactSingleItem && items.size == 1) {
            AppChromeTokens.liquidActionBarSingleWidth
        } else {
            AppChromeTokens.liquidActionBarMinWidth
        }
    val barWidth =
        remember(items.size, compactSingleItem) {
            maxOf(minimumWidth, (items.size * AppChromeTokens.liquidActionBarItemStep.value).dp)
        }
    val singleBreakoutPadding =
        if (compactSingleItem && items.size == 1) {
            AppChromeTokens.liquidActionBarSingleBreakoutPadding
        } else {
            0.dp
        }
    val canvasWidth = barWidth + singleBreakoutPadding * 2
    val interactionLockModifier =
        rememberLiquidActionBarInteractionLockModifier(
            onInteractionChanged = onInteractionChangedState.value,
        )

    Box(
        modifier =
            modifier
                .graphicsLayer { clip = false }
                .width(canvasWidth)
                .height(AppChromeTokens.liquidActionBarOuterHeight)
                .then(interactionLockModifier),
        contentAlignment = Alignment.Center,
    ) {
        val primaryRowModifier =
            Modifier
                .width(barWidth)
                .onGloballyPositioned { coords ->
                    val measuredTotalWidthPx = coords.size.width.toFloat()
                    if (abs(totalWidthPx - measuredTotalWidthPx) > 0.5f) {
                        totalWidthPx = measuredTotalWidthPx
                    }
                    val contentWidthPx = measuredTotalWidthPx - horizontalPaddingPx * 2f
                    val measuredTabWidthPx = contentWidthPx / items.size
                    if (abs(tabWidthPx - measuredTabWidthPx) > 0.5f) {
                        tabWidthPx = measuredTabWidthPx
                    }
                }.graphicsLayer {
                    translationX = effectivePanelOffsetProvider()
                    clip = false
                }.drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        if (effectiveBlurEnabled) {
                            vibrancy()
                            blur(effectBlurDp.toPx())
                            lens(effectLensDp.toPx(), effectLensDp.toPx())
                        }
                    },
                    highlight = {
                        liquidActionBarBaseHighlight(
                            layeredStyleEnabled = layeredStyleEnabled,
                            isBlurEnabled = effectiveBlurEnabled,
                            isInLightTheme = isInLightTheme,
                        )
                    },
                    shadow = {
                        liquidActionBarBaseShadow(
                            layeredStyleEnabled = layeredStyleEnabled,
                            isInLightTheme = isInLightTheme,
                        )
                    },
                    onDrawSurface = { drawRect(palette.baseFillColor) },
                ).border(
                    width = 1.dp,
                    color = palette.outlineColor,
                    shape = ContinuousCapsule,
                ).then(
                    if (!layeredStyleEnabled) {
                        Modifier.liquidActionBarSelectionAura(
                            enabled = true,
                            animation = dampedDragAnimation,
                            tabWidthPx = tabWidthPx,
                            panelOffsetPx = effectivePanelOffsetProvider,
                            isLtr = isLtr,
                            glowColor = palette.selectionGlowColor,
                            coreColor = palette.selectionCoreColor,
                            interactionProgress = interactionProgressProvider,
                        )
                    } else {
                        Modifier
                    },
                ).then(
                    if (effectiveBlurEnabled && interactiveHighlight != null) {
                        interactiveHighlight.modifier
                    } else {
                        Modifier
                    },
                ).then(
                    if (!layeredStyleEnabled && effectiveBlurEnabled && interactiveHighlight != null) {
                        interactiveHighlight.gestureModifier
                    } else {
                        Modifier
                    },
                ).then(if (!layeredStyleEnabled) dampedDragAnimation.modifier else Modifier)
                .height(AppChromeTokens.liquidActionBarOuterHeight)
                .padding(AppChromeTokens.liquidActionBarHorizontalPadding)

        Row(
            primaryRowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                LiquidActionItemSlot(
                    item = item,
                    tint = actionItemTintProviderFactory(index, item.enabled),
                    iconScale = actionItemScaleProviderFactory(index),
                    onClick = {
                        settledIndex = index.toFloat()
                        dampedDragAnimation.animateToValue(settledIndex)
                        item.onClick()
                        animationScope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                    },
                )
            }
        }

        LiquidActionBarLayeredVisualOverlay(
            layeredStyleEnabled = layeredStyleEnabled,
            isBlurEnabled = effectiveBlurEnabled,
            items = items,
            backdrop = backdrop,
            tabsBackdrop = tabsBackdrop,
            combinedBackdrop = combinedBackdrop,
            palette = palette,
            accentColor = accentColor,
            barWidth = barWidth,
            dampedDragAnimation = dampedDragAnimation,
            effectBlurDp = effectBlurDp,
            effectLensDp = effectLensDp,
            tabWidthPx = tabWidthPx,
            totalWidthPx = totalWidthPx,
            singleBreakoutPadding = singleBreakoutPadding,
            isInLightTheme = isInLightTheme,
            isLtr = isLtr,
            effectivePanelOffset = effectivePanelOffsetProvider,
            interactionLensScale = interactionLensScale,
            interactiveHighlight = interactiveHighlight,
        )
    }
}

private fun liquidActionBarPanelOffset(
    rawOffsetPx: Float,
    totalWidthPx: Float,
    density: Density,
): Float {
    if (totalWidthPx == 0f) return 0f
    val fraction = (rawOffsetPx / totalWidthPx).fastCoerceIn(-1f, 1f)
    return with(density) {
        3f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
    }
}
