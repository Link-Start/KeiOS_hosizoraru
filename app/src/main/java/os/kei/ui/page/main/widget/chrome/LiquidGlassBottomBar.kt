@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.appGlassRuntimeEffectsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

val LocalLiquidGlassBottomBarTabScale = staticCompositionLocalOf { { 1f } }
private val LocalLiquidGlassBottomBarSelectionProgress = staticCompositionLocalOf<(Int) -> Float> { { 0f } }
private val LocalLiquidGlassBottomBarContentColor = staticCompositionLocalOf<(Int) -> Color> { { Color.Unspecified } }
private val LocalLiquidGlassBottomBarItemInteractive = staticCompositionLocalOf { true }
private val LocalLiquidGlassBottomBarItemPressHandler =
    staticCompositionLocalOf<(Int, Boolean) -> Unit> {
        { _, _ -> }
    }

@Composable
fun liquidGlassBottomBarItemSelectionProgress(tabIndex: Int): Float = LocalLiquidGlassBottomBarSelectionProgress.current(tabIndex)

@Composable
fun liquidGlassBottomBarItemContentColor(tabIndex: Int): Color = LocalLiquidGlassBottomBarContentColor.current(tabIndex)

@Composable
fun RowScope.LiquidGlassBottomBarItem(
    selected: Boolean,
    tabIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val selectedScale = LocalLiquidGlassBottomBarTabScale.current
    val selectionProgress = liquidGlassBottomBarItemSelectionProgress(tabIndex)
    val interactive = LocalLiquidGlassBottomBarItemInteractive.current
    val onItemPressed = LocalLiquidGlassBottomBarItemPressHandler.current
    val currentOnClick by rememberUpdatedState(onClick)

    val targetScale =
        when {
            selected || selectionProgress > 0f -> lerp(1f, selectedScale(), selectionProgress)
            else -> 1f
        }
    val scaleState =
        appMotionFloatState(
            targetValue = targetScale,
            durationMillis = 160,
            label = "liquid_bottom_bar_item_scale",
        )
    val scaleProvider = remember(scaleState) { { scaleState.value } }
    DisposableEffect(interactive, tabIndex) {
        onDispose {
            if (interactive) {
                onItemPressed(tabIndex, false)
            }
        }
    }

    Column(
        modifier =
            modifier
                .appSquircleClip(999.dp)
                .then(
                    if (interactive) {
                        Modifier.pointerInput(tabIndex) {
                            detectTapGestures(
                                onPress = {
                                    onItemPressed(tabIndex, true)
                                    tryAwaitRelease()
                                    onItemPressed(tabIndex, false)
                                },
                                onTap = { currentOnClick() },
                            )
                        }
                    } else {
                        Modifier
                    },
                ).fillMaxHeight()
                .weight(1f)
                .graphicsLayer {
                    val scale = scaleProvider()
                    scaleX = scale
                    scaleY = scale
                },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun LiquidGlassBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    selectedPosition: Float? = null,
    selectedPositionProvider: (() -> Float?)? = null,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isLiquidEffectEnabled: Boolean = true,
    expandToMaxWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isInLightTheme = !isSystemInDarkTheme()
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val animationScope = rememberCoroutineScope()
    val effectiveLiquidEffectEnabled = isLiquidEffectEnabled && appGlassRuntimeEffectsEnabled()

    val safeTabsCount = tabsCount.coerceAtLeast(1)
    val horizontalPadding = AppChromeTokens.floatingBottomBarHorizontalPadding

    val palette =
        rememberLiquidBottomBarPalette(
            isLiquidEffectEnabled = effectiveLiquidEffectEnabled,
            isInLightTheme = isInLightTheme,
            primary = MiuixTheme.colorScheme.primary,
            onSurface = MiuixTheme.colorScheme.onSurface,
            surfaceContainer = MiuixTheme.colorScheme.surfaceContainer,
        )

    val tabsBackdrop = rememberLayerBackdrop()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val panelOffsetProvider =
        remember(density, offsetAnimation) {
            {
                liquidBottomBarPanelOffset(
                    rawOffsetPx = offsetAnimation.value,
                    totalWidthPx = totalWidthPx,
                    density = density,
                )
            }
        }

    var currentIndex by remember(safeTabsCount) {
        mutableIntStateOf(selectedIndex.fastCoerceIn(0, safeTabsCount - 1))
    }
    var pressedTabIndex by remember(safeTabsCount) { mutableIntStateOf(-1) }
    val currentOnSelected by rememberUpdatedState(onSelected)

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation =
        remember(animationScope, safeTabsCount, density, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentIndex.toFloat(),
                valueRange = 0f..(safeTabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                gestureKey = safeTabsCount to isLtr,
                canDrag = { offset ->
                    val animation = holder.instance ?: return@DampedDragAnimation true
                    if (tabWidthPx <= 0f || totalWidthPx <= 0f) return@DampedDragAnimation false
                    val paddingPx = with(density) { horizontalPadding.toPx() }
                    val indicatorX = animation.value * tabWidthPx
                    val globalTouchX =
                        if (isLtr) {
                            paddingPx + indicatorX + offset.x
                        } else {
                            totalWidthPx - paddingPx - tabWidthPx - indicatorX + offset.x
                        }
                    globalTouchX in 0f..totalWidthPx
                },
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, safeTabsCount - 1)
                    currentIndex = targetIndex
                    currentOnSelected(targetIndex)
                    animationScope.launch {
                        if (transitionAnimationsEnabled) {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        } else {
                            offsetAnimation.snapTo(0f)
                        }
                    }
                },
                onDrag = { _, dragAmount ->
                    if (tabWidthPx > 0f) {
                        val progressDelta = dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f
                        updateValue(
                            (targetValue + progressDelta).fastCoerceIn(0f, (safeTabsCount - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            ).also { holder.instance = it }
        }
    val externalSelectionPosition =
        selectedPosition?.fastCoerceIn(
            0f,
            (safeTabsCount - 1).toFloat(),
        )
    val currentExternalSelectionPosition = rememberUpdatedState(externalSelectionPosition)
    val currentSelectedPositionProvider = rememberUpdatedState(selectedPositionProvider)
    val displaySelectionValueProvider =
        remember(safeTabsCount, dampedDragAnimation) {
            {
                val providedPosition =
                    currentSelectedPositionProvider.value
                        ?.invoke()
                        ?.fastCoerceIn(0f, (safeTabsCount - 1).toFloat())
                val pagerDrivenPosition = providedPosition ?: currentExternalSelectionPosition.value
                if (
                    pagerDrivenPosition != null &&
                    dampedDragAnimation.pressProgress <= 0.001f
                ) {
                    pagerDrivenPosition
                } else {
                    dampedDragAnimation.value
                }
            }
        }

    LaunchedEffect(externalSelectionPosition, safeTabsCount) {
        val pagerDrivenPosition = externalSelectionPosition ?: return@LaunchedEffect
        dampedDragAnimation.snapToValue(
            value = pagerDrivenPosition,
            updateVelocity = false,
        )
    }
    LaunchedEffect(selectedIndex, safeTabsCount) {
        val index = selectedIndex.fastCoerceIn(0, safeTabsCount - 1)
        currentIndex = index
        if (selectedPositionProvider != null) {
            dampedDragAnimation.snapToValue(
                value = index.toFloat(),
                updateVelocity = false,
            )
        }
    }

    LaunchedEffect(dampedDragAnimation, transitionAnimationsEnabled, safeTabsCount) {
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index ->
                val target = index.fastCoerceIn(0, safeTabsCount - 1).toFloat()
                if (transitionAnimationsEnabled) {
                    dampedDragAnimation.animateToValue(target)
                } else {
                    dampedDragAnimation.snapToValue(target)
                }
            }
    }

    val pressProgressProvider =
        remember(dampedDragAnimation, effectiveLiquidEffectEnabled) {
            { if (effectiveLiquidEffectEnabled) dampedDragAnimation.pressProgress else 0f }
        }
    val itemPressProgressState =
        appMotionFloatState(
            targetValue = if (pressedTabIndex >= 0 && effectiveLiquidEffectEnabled) 1f else 0f,
            durationMillis = 120,
            label = "liquid_bottom_bar_item_press",
        )
    val itemPressProgressProvider = remember(itemPressProgressState) { { itemPressProgressState.value } }
    val combinedPressProgressProvider =
        remember(pressProgressProvider, itemPressProgressProvider) {
            { max(pressProgressProvider(), itemPressProgressProvider()) }
        }
    val interactionLensScale = 1f
    val effectBlurDp = UiPerformanceBudget.backdropBlur
    val effectLensDp = UiPerformanceBudget.backdropLens
    val useLightweightBackdrop = !effectiveLiquidEffectEnabled

    val selectionProgressValue =
        if (selectedPositionProvider != null || externalSelectionPosition != null) {
            selectedIndex.fastCoerceIn(0, safeTabsCount - 1).toFloat()
        } else {
            currentIndex.toFloat()
        }
    val selectionProgressProvider: (Int) -> Float =
        remember(selectionProgressValue) {
            { tabIndex ->
                (1f - abs(selectionProgressValue - tabIndex)).fastCoerceIn(0f, 1f)
            }
        }

    val interactiveHighlight =
        if (
            effectiveLiquidEffectEnabled
        ) {
            remember(animationScope, tabWidthPx, isLtr, displaySelectionValueProvider, panelOffsetProvider) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        val displayValue = displaySelectionValueProvider()
                        val x =
                            if (isLtr) {
                                (displayValue + 0.5f) * tabWidthPx + panelOffsetProvider()
                            } else {
                                size.width - (displayValue + 0.5f) * tabWidthPx + panelOffsetProvider()
                            }
                        Offset(
                            x,
                            size.height / 2f,
                        )
                    },
                    highlightColor = Color.White,
                    highlightStrength = if (isInLightTheme) 0.60f else 0.90f,
                    highlightRadiusScale = if (isInLightTheme) 0.90f else 1.08f,
                )
            }
        } else {
            null
        }
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    CompositionLocalProvider(
        LocalLiquidGlassBottomBarTabScale provides {
            if (effectiveLiquidEffectEnabled) lerp(1f, 1.2f, combinedPressProgressProvider()) else 1f
        },
        LocalLiquidGlassBottomBarSelectionProgress provides selectionProgressProvider,
        LocalLiquidGlassBottomBarContentColor provides { palette.inactiveContentColor },
        LocalLiquidGlassBottomBarItemInteractive provides true,
        LocalLiquidGlassBottomBarItemPressHandler provides { index, isPressed ->
            when {
                isPressed -> pressedTabIndex = index
                pressedTabIndex == index -> pressedTabIndex = -1
            }
        },
    ) {
        Box(
            modifier =
                modifier.then(
                    if (expandToMaxWidth) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Min),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier =
                    Modifier
                        .onGloballyPositioned { coords ->
                            val measuredTotalWidthPx = coords.size.width.toFloat()
                            if (abs(totalWidthPx - measuredTotalWidthPx) > 0.5f) {
                                totalWidthPx = measuredTotalWidthPx
                            }
                            val contentWidthPx =
                                measuredTotalWidthPx -
                                    with(density) {
                                        (horizontalPadding * 2).toPx()
                                    }
                            val measuredTabWidthPx = (contentWidthPx / safeTabsCount).coerceAtLeast(0f)
                            if (abs(tabWidthPx - measuredTabWidthPx) > 0.5f) {
                                tabWidthPx = measuredTabWidthPx
                            }
                        }.graphicsLayer {
                            translationX = panelOffsetProvider()
                            val combinedPressProgress = combinedPressProgressProvider()
                            translationY = -with(density) { 1.25.dp.toPx() } * combinedPressProgress
                        }.then(
                            if (useLightweightBackdrop) {
                                Modifier
                                    .appSquircleBackground(palette.baseFillColor, 999.dp)
                            } else {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        if (effectiveLiquidEffectEnabled) {
                                            vibrancy()
                                            blur(effectBlurDp.toPx())
                                            lens(effectLensDp.toPx(), effectLensDp.toPx())
                                        }
                                    },
                                    highlight = {
                                        Highlight.Default.copy(alpha = if (effectiveLiquidEffectEnabled) 1f else 0f)
                                    },
                                    shadow = {
                                        Shadow.Default.copy(
                                            color = Color.Black.copy(if (isInLightTheme) 0.10f else 0.20f),
                                        )
                                    },
                                    layerBlock = {
                                        val combinedPressProgress = combinedPressProgressProvider()
                                        scaleX = lerp(1f, 1.006f, combinedPressProgress)
                                        scaleY = lerp(1f, 0.996f, combinedPressProgress)
                                    },
                                    onDrawSurface = { drawRect(palette.baseFillColor) },
                                )
                            },
                        ).then(if (interactiveHighlight != null) interactiveHighlight.modifier else Modifier)
                        .height(AppChromeTokens.floatingBottomBarOuterHeight)
                        .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )

            CompositionLocalProvider(
                LocalLiquidGlassBottomBarContentColor provides { palette.activeContentColor },
                LocalLiquidGlassBottomBarItemInteractive provides false,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .then(
                            if (useLightweightBackdrop) {
                                Modifier
                            } else {
                                Modifier.layerBackdrop(
                                    tabsBackdrop,
                                )
                            },
                        ).graphicsLayer {
                            val combinedPressProgress = combinedPressProgressProvider()
                            translationX = panelOffsetProvider()
                            translationY = -with(density) { 1.25.dp.toPx() } * combinedPressProgress
                            scaleX = lerp(1f, 1.006f, combinedPressProgress)
                            scaleY = lerp(1f, 0.996f, combinedPressProgress)
                        }.then(
                            if (useLightweightBackdrop) {
                                Modifier
                            } else {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        if (effectiveLiquidEffectEnabled) {
                                            val progress = combinedPressProgressProvider()
                                            vibrancy()
                                            blur(effectBlurDp.toPx())
                                            lens(
                                                effectLensDp.toPx() * progress,
                                                effectLensDp.toPx() * progress,
                                            )
                                        }
                                    },
                                    highlight = {
                                        Highlight.Default.copy(alpha = if (effectiveLiquidEffectEnabled) combinedPressProgressProvider() else 0f)
                                    },
                                    onDrawSurface = { drawRect(palette.baseFillColor) },
                                )
                            },
                        ).height(AppChromeTokens.floatingBottomBarInnerHeight)
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }

            if (tabWidthPx > 0f) {
                Box(
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .graphicsLayer {
                            val combinedPressProgress = combinedPressProgressProvider()
                            val contentWidth =
                                totalWidthPx -
                                    with(density) {
                                        (horizontalPadding * 2).toPx()
                                    }
                            val singleTabWidth = contentWidth / safeTabsCount
                            val progressOffset = displaySelectionValueProvider() * singleTabWidth
                            val panelOffset = panelOffsetProvider()
                            translationX =
                                if (isLtr) {
                                    progressOffset + panelOffset
                                } else {
                                    -progressOffset + panelOffset
                                }
                            translationY = -with(density) { 1.25.dp.toPx() } * combinedPressProgress
                            scaleX = lerp(1f, 1.006f, combinedPressProgress)
                            scaleY = lerp(1f, 0.996f, combinedPressProgress)
                        }.then(if (interactiveHighlight != null) interactiveHighlight.gestureModifier else Modifier)
                        .then(dampedDragAnimation.modifier)
                        .then(
                            if (useLightweightBackdrop) {
                                Modifier.appSquircleBackground(
                                    color =
                                        if (isInLightTheme) {
                                            Color.Black.copy(0.10f)
                                        } else {
                                            Color.White.copy(0.10f)
                                        },
                                    cornerRadius = 999.dp,
                                )
                            } else {
                                Modifier.drawBackdrop(
                                    backdrop = combinedBackdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        val progress =
                                            if (effectiveLiquidEffectEnabled) {
                                                combinedPressProgressProvider()
                                            } else {
                                                0f
                                            }
                                        if (progress > 0f) {
                                            lens(
                                                10f.dp.toPx() * progress * interactionLensScale,
                                                14f.dp.toPx() * progress * interactionLensScale,
                                                true,
                                            )
                                        }
                                    },
                                    highlight = {
                                        Highlight.Default.copy(alpha = if (effectiveLiquidEffectEnabled) combinedPressProgressProvider() else 0f)
                                    },
                                    shadow = {
                                        Shadow(alpha = if (effectiveLiquidEffectEnabled) combinedPressProgressProvider() else 0f)
                                    },
                                    innerShadow = {
                                        val progress = if (effectiveLiquidEffectEnabled) combinedPressProgressProvider() else 0f
                                        InnerShadow(
                                            radius = 8f.dp * progress,
                                            alpha = progress,
                                        )
                                    },
                                    layerBlock = {
                                        if (effectiveLiquidEffectEnabled) {
                                            val clickScale = lerp(1f, 1.045f, itemPressProgressProvider())
                                            scaleX = dampedDragAnimation.scaleX * clickScale
                                            scaleY = dampedDragAnimation.scaleY * clickScale
                                            val velocity = dampedDragAnimation.velocity / 10f
                                            scaleX /= 1f -
                                                (velocity * 0.75f).fastCoerceIn(
                                                    -0.2f,
                                                    0.2f,
                                                )
                                            scaleY *= 1f -
                                                (velocity * 0.25f).fastCoerceIn(
                                                    -0.2f,
                                                    0.2f,
                                                )
                                        }
                                    },
                                    onDrawSurface = {
                                        val progress =
                                            if (effectiveLiquidEffectEnabled) combinedPressProgressProvider() else 0f
                                        drawRect(
                                            color =
                                                if (isInLightTheme) {
                                                    Color.Black.copy(0.10f)
                                                } else {
                                                    Color.White.copy(
                                                        0.10f,
                                                    )
                                                },
                                            alpha = 1f - progress,
                                        )
                                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                    },
                                )
                            },
                        ).clearAndSetSemantics {}
                        .height(AppChromeTokens.floatingBottomBarInnerHeight)
                        .width(
                            with(density) {
                                ((totalWidthPx - (horizontalPadding * 2).toPx()) / safeTabsCount).toDp()
                            },
                        ),
                )
            }
        }
    }
}

private fun liquidBottomBarPanelOffset(
    rawOffsetPx: Float,
    totalWidthPx: Float,
    density: Density,
): Float {
    if (totalWidthPx == 0f) return 0f
    val fraction = (rawOffsetPx / totalWidthPx).fastCoerceIn(-1f, 1f)
    return with(density) {
        4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
    }
}

@Composable
private fun rememberLiquidBottomBarPalette(
    isLiquidEffectEnabled: Boolean,
    isInLightTheme: Boolean,
    primary: Color,
    onSurface: Color,
    surfaceContainer: Color,
): LiquidBottomBarPalette =
    remember(
        isLiquidEffectEnabled,
        isInLightTheme,
        primary,
        onSurface,
        surfaceContainer,
    ) {
        if (!isLiquidEffectEnabled) {
            return@remember LiquidBottomBarPalette(
                baseFillColor = surfaceContainer,
                inactiveContentColor = onSurface,
                activeContentColor = primary,
            )
        }

        if (isInLightTheme) {
            return@remember LiquidBottomBarPalette(
                baseFillColor = surfaceContainer.copy(alpha = 0.40f),
                inactiveContentColor = onSurface.copy(alpha = 0.88f),
                activeContentColor = primary,
            )
        }

        return@remember LiquidBottomBarPalette(
            baseFillColor = surfaceContainer.copy(alpha = 0.20f),
            inactiveContentColor = onSurface.copy(alpha = 0.84f),
            activeContentColor = primary.copy(alpha = 0.98f),
        )
    }

@Stable
private class LiquidBottomBarPalette(
    val baseFillColor: Color,
    val inactiveContentColor: Color,
    val activeContentColor: Color,
)
