@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.animation.DampedDragAnimation
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.shape.appSquircleClip
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.ui.util.lerp as lerpFloat

@Composable
internal fun BaGuideBgmDockGroupContent(
    tabs: List<BaGuideBgmDockTab>,
    selectedDockKey: String,
    accent: Color,
    expandedProgress: Float,
    compactProgress: Float,
    backdrop: Backdrop,
    compactInteractionSource: MutableInteractionSource? = null,
    onSelectedDockKeyChange: (String) -> Unit,
    onCompactDockClick: () -> Unit,
) {
    val expanded = expandedProgress.coerceIn(0f, 1f)
    val compact = compactProgress.coerceIn(0f, 1f)
    val expandedEnabled = expanded > 0.54f
    val compactEnabled = compact > 0.54f
    val safeTabCount = tabs.size.coerceAtLeast(1)
    val selectedIndex = tabs.indexOfFirst { it.key == selectedDockKey }.coerceAtLeast(0)
    val resolvedCompactInteractionSource = compactInteractionSource ?: remember { MutableInteractionSource() }
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val currentExpandedEnabled by rememberUpdatedState(expandedEnabled)
    val currentAnimationsEnabled by rememberUpdatedState(animationsEnabled)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isDark = isSystemInDarkTheme()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val offsetAnimation = remember { Animatable(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val panelOffsetProvider =
        remember(density, offsetAnimation) {
            {
                baGuideBgmDockPanelOffset(
                    rawOffsetPx = offsetAnimation.value,
                    totalWidthPx = totalWidthPx,
                    density = density,
                )
            }
        }
    var currentIndex by remember(safeTabCount) {
        mutableIntStateOf(selectedIndex.fastCoerceIn(0, safeTabCount - 1))
    }
    var pressedTabIndex by remember { mutableIntStateOf(-1) }
    val itemPressProgressState =
        animateFloatAsState(
            targetValue = if (pressedTabIndex >= 0) 1f else 0f,
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
            label = "ba_catalog_bgm_dock_item_press",
        )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentHorizontalPadding = AppChromeTokens.floatingBottomBarHorizontalPadding
        val contentVerticalPadding =
            (
                AppChromeTokens.floatingBottomBarOuterHeight -
                    AppChromeTokens.floatingBottomBarInnerHeight
            ) / 2f
        val tabSlotWidth =
            ((maxWidth - contentHorizontalPadding * 2f) / safeTabCount.toFloat())
                .coerceAtLeast(42.dp)
        val selectedPillWidth =
            (tabSlotWidth * BaGuideBgmDockSelectionWidthFraction)
                .coerceAtLeast(AppChromeTokens.floatingBottomBarInnerHeight + 12.dp)
                .coerceAtMost(tabSlotWidth)
        val selectedPillInset = (tabSlotWidth - selectedPillWidth) / 2f
        val fallbackTabWidthPx = with(density) { tabSlotWidth.toPx() }.coerceAtLeast(1f)
        val selectedPillHeight = AppChromeTokens.floatingBottomBarInnerHeight.coerceAtMost(maxHeight)

        class DampedDragAnimationHolder {
            var instance: DampedDragAnimation? = null
        }

        val holder = remember { DampedDragAnimationHolder() }
        val dampedDragAnimation =
            remember(animationScope, safeTabCount, density, isLtr) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = currentIndex.toFloat(),
                    valueRange = 0f..(safeTabCount - 1).toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = BaGuideBgmDockPressedScale,
                    gestureKey = safeTabCount to isLtr,
                    canDrag = { offset ->
                        if (!currentExpandedEnabled) return@DampedDragAnimation false
                        val animation = holder.instance ?: return@DampedDragAnimation true
                        val measuredTabWidthPx = tabWidthPx.takeIf { it > 0f } ?: fallbackTabWidthPx
                        if (measuredTabWidthPx <= 0f || totalWidthPx <= 0f) return@DampedDragAnimation true
                        val paddingPx = with(density) { contentHorizontalPadding.toPx() }
                        val indicatorX = animation.value * measuredTabWidthPx
                        val globalTouchX =
                            if (isLtr) {
                                paddingPx + indicatorX + offset.x
                            } else {
                                totalWidthPx - paddingPx - measuredTabWidthPx - indicatorX + offset.x
                            }
                        globalTouchX in 0f..totalWidthPx
                    },
                    onDragStarted = {
                        pressedTabIndex = currentIndex
                    },
                    onDragStopped = {
                        val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, safeTabCount - 1)
                        currentIndex = targetIndex
                        pressedTabIndex = -1
                        if (currentAnimationsEnabled) {
                            animateToValue(targetIndex.toFloat())
                        } else {
                            snapToValue(targetIndex.toFloat())
                        }
                        animationScope.launch {
                            if (currentAnimationsEnabled) {
                                offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                            } else {
                                offsetAnimation.snapTo(0f)
                            }
                        }
                    },
                    onDrag = { _, dragAmount ->
                        val measuredTabWidthPx = tabWidthPx.takeIf { it > 0f } ?: fallbackTabWidthPx
                        val dragDirection = if (isLtr) 1f else -1f
                        snapToValue(
                            (value + dragAmount.x / measuredTabWidthPx * dragDirection)
                                .fastCoerceIn(0f, (safeTabCount - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    },
                )
            }
        holder.instance = dampedDragAnimation
        val itemPressProgressProvider =
            remember(itemPressProgressState) {
                { itemPressProgressState.value }
            }
        val combinedInteractionProgressProvider =
            remember(dampedDragAnimation, itemPressProgressProvider) {
                {
                    max(dampedDragAnimation.pressProgress, itemPressProgressProvider())
                }
            }
        val selectedContentScaleProvider =
            remember(combinedInteractionProgressProvider) {
                {
                    lerpFloat(
                        1f,
                        BaGuideBgmDockSelectedContentPressedScale,
                        combinedInteractionProgressProvider(),
                    )
                }
            }
        val selectionValueProvider =
            remember(dampedDragAnimation) {
                { dampedDragAnimation.value }
            }
        val selectionProgressProvider =
            remember(selectionValueProvider) {
                { tabIndex: Int ->
                    (1f - abs(selectionValueProvider() - tabIndex)).coerceIn(0f, 1f)
                }
            }
        val interactiveHighlight =
            remember(
                animationScope,
                selectionValueProvider,
                panelOffsetProvider,
                tabWidthPx,
                isLtr,
            ) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        val selectionValue = selectionValueProvider()
                        val panelOffset = panelOffsetProvider()
                        Offset(
                            if (isLtr) {
                                (selectionValue + 0.5f) *
                                    (tabWidthPx.takeIf { it > 0f } ?: fallbackTabWidthPx) + panelOffset
                            } else {
                                size.width - (selectionValue + 0.5f) *
                                    (tabWidthPx.takeIf { it > 0f } ?: fallbackTabWidthPx) + panelOffset
                            },
                            size.height / 2f,
                        )
                    },
                    highlightColor = Color.White,
                    highlightStrength = if (isDark) 0.90f else 0.60f,
                    highlightRadiusScale = if (isDark) 1.08f else 0.90f,
                )
            }

        LaunchedEffect(selectedIndex, safeTabCount) {
            currentIndex = selectedIndex.fastCoerceIn(0, safeTabCount - 1)
        }
        val snapshotFlowManager = rememberAppSnapshotFlowManager()
        LaunchedEffect(dampedDragAnimation, animationsEnabled, snapshotFlowManager) {
            snapshotFlowManager
                .snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (animationsEnabled) {
                        dampedDragAnimation.animateToValue(index.toFloat())
                    } else {
                        dampedDragAnimation.snapToValue(index.toFloat())
                    }
                    tabs.getOrNull(index)?.key?.let(onSelectedDockKeyChange)
                }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(if (expanded >= compact) 1f else 0f)
                    .onGloballyPositioned { coords ->
                        val measuredTotalWidthPx = coords.size.width.toFloat()
                        if (abs(totalWidthPx - measuredTotalWidthPx) > 0.5f) {
                            totalWidthPx = measuredTotalWidthPx
                        }
                        val contentWidthPx =
                            measuredTotalWidthPx -
                                with(density) {
                                    (contentHorizontalPadding * 2f).toPx()
                                }
                        val measuredTabWidthPx = (contentWidthPx / safeTabCount).coerceAtLeast(0f)
                        if (abs(tabWidthPx - measuredTabWidthPx) > 0.5f) {
                            tabWidthPx = measuredTabWidthPx
                        }
                    }.graphicsLayer {
                        val interactionProgress = combinedInteractionProgressProvider()
                        alpha = expanded
                        translationX = panelOffsetProvider()
                        translationY = -with(density) { 1.25.dp.toPx() } * interactionProgress
                        scaleX =
                            (0.96f + 0.04f * expanded) *
                            lerpFloat(1f, 1.006f, interactionProgress)
                        scaleY =
                            (0.96f + 0.04f * expanded) *
                            lerpFloat(1f, 0.996f, interactionProgress)
                    }.then(interactiveHighlight.modifier)
                    .padding(horizontal = contentHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { tabIndex, tab ->
                BaGuideBgmExpandedDockTab(
                    tab = tab,
                    selected = currentIndex == tabIndex,
                    selectionProgress = { selectionProgressProvider(tabIndex) },
                    selectedContentScale = selectedContentScaleProvider,
                    itemPressProgress = itemPressProgressProvider,
                    accent = accent,
                    activeTint = false,
                    onClick = {
                        currentIndex = tabIndex
                    },
                    onPressedChange = { pressed ->
                        pressedTabIndex = if (pressed) tabIndex else -1
                    },
                    enabled = expandedEnabled,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .offset(y = contentVerticalPadding)
                    .width(maxWidth)
                    .height(selectedPillHeight)
                    .alpha(0f)
                    .zIndex(if (expanded >= compact) 1.5f else 0f)
                    .appSquircleClip(999.dp)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        val interactionProgress = combinedInteractionProgressProvider()
                        alpha = expanded
                        translationX = panelOffsetProvider()
                        translationY = -with(density) { 1.25.dp.toPx() } * interactionProgress
                        scaleX =
                            (0.96f + 0.04f * expanded) *
                            lerpFloat(1f, 1.006f, interactionProgress)
                        scaleY =
                            (0.96f + 0.04f * expanded) *
                            lerpFloat(1f, 0.996f, interactionProgress)
                    }.padding(horizontal = contentHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { tabIndex, tab ->
                BaGuideBgmExpandedDockTab(
                    tab = tab,
                    selected = currentIndex == tabIndex,
                    selectionProgress = { selectionProgressProvider(tabIndex) },
                    selectedContentScale = selectedContentScaleProvider,
                    itemPressProgress = itemPressProgressProvider,
                    accent = accent,
                    activeTint = true,
                    onClick = {},
                    onPressedChange = {},
                    enabled = false,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
            }
        }

        BaGuideBgmDockSelectionPill(
            modifier =
                Modifier
                    .offset {
                        with(density) {
                            val selectedPillOffset =
                                contentHorizontalPadding +
                                    tabSlotWidth * dampedDragAnimation.value +
                                    selectedPillInset
                            IntOffset(
                                x = selectedPillOffset.roundToPx(),
                                y = contentVerticalPadding.roundToPx(),
                            )
                        }
                    }.width(selectedPillWidth)
                    .height(selectedPillHeight)
                    .zIndex(if (expanded >= compact) 2f else 0f)
                    .graphicsLayer {
                        val interactionProgress = combinedInteractionProgressProvider()
                        alpha = expanded
                        translationX = panelOffsetProvider()
                        translationY = -with(density) { 1.25.dp.toPx() } * interactionProgress
                        scaleX =
                            (0.96f + 0.04f * expanded) *
                            lerpFloat(1f, 1.006f, interactionProgress)
                        scaleY =
                            (0.96f + 0.04f * expanded) *
                            lerpFloat(1f, 0.996f, interactionProgress)
                    }.then(interactiveHighlight.gestureModifier)
                    .then(if (expandedEnabled) dampedDragAnimation.modifier else Modifier),
            backdrop = combinedBackdrop,
            isDark = isDark,
            pressProgress = combinedInteractionProgressProvider,
            itemPressProgress = itemPressProgressProvider,
            dragScaleX = { dampedDragAnimation.scaleX },
            dragScaleY = { dampedDragAnimation.scaleY },
            velocity = { dampedDragAnimation.velocity },
        )

        val compactTab = tabs.firstOrNull { it.key == selectedDockKey } ?: tabs.last()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(if (compact > expanded) 1f else 0f)
                    .graphicsLayer {
                        alpha = compact
                        scaleX = 0.88f + 0.12f * compact
                        scaleY = 0.88f + 0.12f * compact
                    }.then(
                        if (compactEnabled) {
                            Modifier.clickable(
                                interactionSource = resolvedCompactInteractionSource,
                                indication = null,
                            ) { onCompactDockClick() }
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            BaGuideBgmDockTabIcon(
                icon = compactTab.icon,
                label = compactTab.label,
                selected = true,
                accent = accent,
                iconSize = 27.dp,
            )
        }
    }
}
