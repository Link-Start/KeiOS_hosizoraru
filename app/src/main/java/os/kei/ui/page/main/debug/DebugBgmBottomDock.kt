package os.kei.ui.page.main.debug

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.os.appLucideGridIcon
import os.kei.ui.page.main.os.appLucideHomeIcon
import os.kei.ui.page.main.os.appLucideLibraryIcon
import os.kei.ui.page.main.os.appLucideRadioIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.floor

@Composable
internal fun DebugBgmDockGroupContent(
    tabs: List<DebugBgmDockTab>,
    selectedDockKey: String,
    selectedDockPosition: Float,
    accent: Color,
    expandedProgress: Float,
    compactProgress: Float,
    backdrop: Backdrop? = null,
    compactInteractionSource: MutableInteractionSource? = null,
    onSelectedDockKeyChange: (String) -> Unit,
    onCompactDockClick: () -> Unit
) {
    val expanded = expandedProgress.coerceIn(0f, 1f)
    val compact = compactProgress.coerceIn(0f, 1f)
    val expandedEnabled = expanded > 0.54f
    val compactEnabled = compact > 0.54f
    val safeTabCount = tabs.size.coerceAtLeast(1)
    val resolvedCompactInteractionSource = compactInteractionSource ?: remember { MutableInteractionSource() }
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isDark = isSystemInDarkTheme()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = if (backdrop != null) rememberCombinedBackdrop(backdrop, tabsBackdrop) else null
    val tabListState = rememberLazyListState()
    var pressedTabIndex by remember { mutableIntStateOf(-1) }
    val itemPressProgress by animateFloatAsState(
        targetValue = if (pressedTabIndex >= 0) 1f else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "debug_bgm_dock_item_press"
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentHorizontalPadding = AppChromeTokens.floatingBottomBarHorizontalPadding
        val contentVerticalPadding = (AppChromeTokens.floatingBottomBarOuterHeight -
            AppChromeTokens.floatingBottomBarInnerHeight) / 2f
        val tabSlotWidth = ((maxWidth - contentHorizontalPadding * 2f) / safeTabCount.toFloat())
            .coerceAtLeast(42.dp)
        val selectedPillWidth = (tabSlotWidth * DebugBgmDockSelectionWidthFraction)
            .coerceAtLeast(AppChromeTokens.floatingBottomBarInnerHeight + 12.dp)
            .coerceAtMost(tabSlotWidth)
        val selectedPillInset = (tabSlotWidth - selectedPillWidth) / 2f
        val fallbackTabWidthPx = with(density) { tabSlotWidth.toPx() }.coerceAtLeast(1f)
        val selectedPillHeight = AppChromeTokens.floatingBottomBarInnerHeight.coerceAtMost(maxHeight)
        val indicatorPosition = selectedDockPosition.fastCoerceIn(0f, (safeTabCount - 1).toFloat())
        val combinedInteractionProgress = itemPressProgress
        val dockLiftPx = with(density) { 1.25.dp.toPx() } * combinedInteractionProgress
        val dockScaleX = lerpFloat(1f, 1.006f, combinedInteractionProgress)
        val dockScaleY = lerpFloat(1f, 0.996f, combinedInteractionProgress)
        val selectedContentScale = lerpFloat(
            1f,
            DebugBgmDockSelectedContentPressedScale,
            combinedInteractionProgress
        )
        val currentIndicatorPosition by rememberUpdatedState(indicatorPosition)
        val interactiveHighlight = if (
            backdrop != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            remember(
                animationScope,
                tabListState,
                safeTabCount,
                fallbackTabWidthPx,
                contentHorizontalPadding,
                density,
                isLtr
            ) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        val highlightCenterPx = lazyTabCenterOffsetPx(
                            listState = tabListState,
                            position = currentIndicatorPosition,
                            tabsCount = safeTabCount,
                            fallbackTabWidthPx = fallbackTabWidthPx,
                            fallbackStartPx = with(density) { contentHorizontalPadding.toPx() }
                        )
                        Offset(
                            if (isLtr) {
                                highlightCenterPx
                            } else {
                                size.width - highlightCenterPx
                            },
                            size.height / 2f
                        )
                    },
                    highlightColor = Color.White,
                    highlightStrength = if (isDark) 0.90f else 0.60f,
                    highlightRadiusScale = if (isDark) 1.08f else 0.90f
                )
            }
        } else {
            null
        }

        val selectedPillOffset = lazyTabPillOffset(
            listState = tabListState,
            position = indicatorPosition,
            tabsCount = safeTabCount,
            fallbackTabWidthPx = fallbackTabWidthPx,
            fallbackStartPx = with(density) { contentHorizontalPadding.toPx() },
            selectedPillInsetPx = with(density) { selectedPillInset.toPx() },
            density = density
        )

        LazyRow(
            state = tabListState,
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = contentHorizontalPadding),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (expanded >= compact) 1f else 0f)
                .graphicsLayer {
                    alpha = expanded
                    translationY = -dockLiftPx
                    scaleX = (0.96f + 0.04f * expanded) * dockScaleX
                    scaleY = (0.96f + 0.04f * expanded) * dockScaleY
                }
                .clip(ContinuousCapsule)
                .then(interactiveHighlight?.modifier ?: Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(tabs) { tabIndex, tab ->
                val selectionProgress = (1f - abs(indicatorPosition - tabIndex))
                    .coerceIn(0f, 1f)
                DebugBgmExpandedDockTab(
                    tab = tab,
                    selected = selectionProgress > 0.52f,
                    selectionProgress = selectionProgress,
                    selectedContentScale = selectedContentScale,
                    accent = accent,
                    activeTint = false,
                    onClick = {
                        onSelectedDockKeyChange(tab.key)
                    },
                    onPressedChange = { pressed ->
                        pressedTabIndex = if (pressed) tabIndex else -1
                    },
                    enabled = expandedEnabled,
                    modifier = Modifier
                        .width(tabSlotWidth)
                        .fillMaxHeight()
                )
            }
        }

        Row(
            modifier = Modifier
                .clearAndSetSemantics {}
                .offset(y = contentVerticalPadding)
                .width(maxWidth)
                .height(selectedPillHeight)
                .alpha(0f)
                .zIndex(if (expanded >= compact) 1.5f else 0f)
                .clip(ContinuousCapsule)
                .then(if (backdrop != null) Modifier.layerBackdrop(tabsBackdrop) else Modifier)
                .graphicsLayer {
                    alpha = expanded
                    translationY = -dockLiftPx
                    scaleX = (0.96f + 0.04f * expanded) * dockScaleX
                    scaleY = (0.96f + 0.04f * expanded) * dockScaleY
                }
                .padding(horizontal = contentHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { tabIndex, tab ->
                val selectionProgress = (1f - abs(indicatorPosition - tabIndex))
                    .coerceIn(0f, 1f)
                DebugBgmExpandedDockTab(
                    tab = tab,
                    selected = selectionProgress > 0.52f,
                    selectionProgress = selectionProgress,
                    selectedContentScale = selectedContentScale,
                    accent = accent,
                    activeTint = true,
                    onClick = {},
                    onPressedChange = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        DebugBgmDockSelectionPill(
            modifier = Modifier
                .offset(x = selectedPillOffset, y = contentVerticalPadding)
                .width(selectedPillWidth)
                .height(selectedPillHeight)
                .zIndex(if (expanded >= compact) 2f else 0f)
                .graphicsLayer {
                    alpha = expanded
                    translationY = -dockLiftPx
                    scaleX = (0.96f + 0.04f * expanded) * dockScaleX
                    scaleY = (0.96f + 0.04f * expanded) * dockScaleY
                }
                .then(interactiveHighlight?.gestureModifier ?: Modifier)
                .then(if (expandedEnabled) Modifier else Modifier),
            backdrop = combinedBackdrop ?: backdrop,
            isDark = isDark,
            pressProgress = combinedInteractionProgress,
            itemPressProgress = itemPressProgress,
            dragScaleX = 1f,
            dragScaleY = 1f,
            velocity = 0f
        )

        val compactTab = tabs.firstOrNull { it.key == selectedDockKey } ?: tabs.last()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (compact > expanded) 1f else 0f)
                .graphicsLayer {
                    alpha = compact
                    scaleX = 0.88f + 0.12f * compact
                    scaleY = 0.88f + 0.12f * compact
                }
                .clip(CircleShape)
                .then(
                    if (compactEnabled) {
                        Modifier.clickable(
                            interactionSource = resolvedCompactInteractionSource,
                            indication = null
                        ) { onCompactDockClick() }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            DebugBgmDockTabIcon(
                icon = compactTab.icon,
                label = compactTab.label,
                selected = true,
                accent = accent,
                iconSize = 27.dp
            )
        }
    }
}

private fun lazyTabPillOffset(
    listState: LazyListState,
    position: Float,
    tabsCount: Int,
    fallbackTabWidthPx: Float,
    fallbackStartPx: Float,
    selectedPillInsetPx: Float,
    density: androidx.compose.ui.unit.Density
): Dp {
    val tabStartPx = lazyTabStartOffsetPx(
        listState = listState,
        position = position,
        tabsCount = tabsCount,
        fallbackTabWidthPx = fallbackTabWidthPx,
        fallbackStartPx = fallbackStartPx
    )
    return with(density) { (tabStartPx + selectedPillInsetPx).toDp() }
}

private fun lazyTabCenterOffsetPx(
    listState: LazyListState,
    position: Float,
    tabsCount: Int,
    fallbackTabWidthPx: Float,
    fallbackStartPx: Float
): Float {
    val tabStartPx = lazyTabStartOffsetPx(
        listState = listState,
        position = position,
        tabsCount = tabsCount,
        fallbackTabWidthPx = fallbackTabWidthPx,
        fallbackStartPx = fallbackStartPx
    )
    return tabStartPx + fallbackTabWidthPx / 2f
}

private fun lazyTabStartOffsetPx(
    listState: LazyListState,
    position: Float,
    tabsCount: Int,
    fallbackTabWidthPx: Float,
    fallbackStartPx: Float
): Float {
    if (tabsCount <= 1) return fallbackStartPx

    val safePosition = position.fastCoerceIn(0f, (tabsCount - 1).toFloat())
    val lowerIndex = floor(safePosition).toInt()
    val upperIndex = (lowerIndex + 1).coerceAtMost(tabsCount - 1)
    val fraction = safePosition - lowerIndex
    val visibleItems = listState.layoutInfo.visibleItemsInfo

    fun itemOffset(index: Int): Float {
        return visibleItems.firstOrNull { it.index == index }?.offset?.toFloat()
            ?: (fallbackStartPx + fallbackTabWidthPx * index)
    }

    return lerpFloat(
        itemOffset(lowerIndex),
        itemOffset(upperIndex),
        fraction
    )
}

@Composable
private fun DebugBgmDockSelectionPill(
    backdrop: Backdrop?,
    isDark: Boolean,
    pressProgress: Float,
    itemPressProgress: Float,
    dragScaleX: Float,
    dragScaleY: Float,
    velocity: Float,
    modifier: Modifier = Modifier
) {
    val clampedPress = pressProgress.coerceIn(0f, 1f)
    val clickScale = lerpFloat(1f, DebugBgmDockClickScale, itemPressProgress.coerceIn(0f, 1f))
    val velocityScale = velocity / 10f
    val deformationScaleX = dragScaleX * clickScale /
        (1f - (velocityScale * DebugBgmDockVelocityScaleXFactor)
            .coerceIn(-DebugBgmDockVelocityScaleClamp, DebugBgmDockVelocityScaleClamp))
    val deformationScaleY = dragScaleY * clickScale *
        (1f - (velocityScale * DebugBgmDockVelocityScaleYFactor)
            .coerceIn(-DebugBgmDockVelocityScaleClamp, DebugBgmDockVelocityScaleClamp))
    val neutralFill = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            if (clampedPress > 0f) {
                                lens(
                                    5.dp.toPx() * clampedPress,
                                    7.dp.toPx() * clampedPress,
                                    true
                                )
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = clampedPress)
                        },
                        shadow = {
                            Shadow(alpha = clampedPress)
                        },
                        innerShadow = {
                            InnerShadow(radius = 8.dp * clampedPress, alpha = clampedPress)
                        },
                        layerBlock = {
                            scaleX = deformationScaleX
                            scaleY = deformationScaleY
                        },
                        onDrawSurface = {
                            drawRect(neutralFill, alpha = 1f - clampedPress)
                            drawRect(Color.Black.copy(alpha = 0.03f * clampedPress))
                        }
                    )
                } else {
                    Modifier
                        .clip(ContinuousCapsule)
                        .background(neutralFill, ContinuousCapsule)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(ContinuousCapsule)
                .background(
                    if (isDark) {
                        Color.White.copy(alpha = 0.03f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    },
                    ContinuousCapsule
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(ContinuousCapsule)
                .border(1.dp, borderColor, ContinuousCapsule)
        )
    }
}

@Composable
private fun DebugBgmExpandedDockTab(
    tab: DebugBgmDockTab,
    selected: Boolean,
    selectionProgress: Float,
    selectedContentScale: Float,
    accent: Color,
    activeTint: Boolean,
    onClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val normalizedSelectionProgress = selectionProgress.coerceIn(0f, 1f)
    val itemScale by animateFloatAsState(
        targetValue = when {
            pressed && enabled -> lerpFloat(0.92f, 0.96f, normalizedSelectionProgress)
            normalizedSelectionProgress > 0f -> {
                lerpFloat(1f, selectedContentScale, normalizedSelectionProgress)
            }
            else -> 1f
        },
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "debug_bgm_dock_tab_press_scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (enabled) onPressedChange(pressed)
    }
    Box(
        modifier = modifier
            .clip(ContinuousCapsule)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            },
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tintProgress = if (activeTint) 1f else 0f
            DebugBgmDockTabIcon(
                icon = tab.icon,
                label = tab.label,
                selected = selected,
                accent = accent,
                selectionProgress = tintProgress
            )
            Text(
                text = tab.label,
                color = DebugBgmDockTint(
                    selected = selected,
                    accent = accent,
                    selectionProgress = tintProgress
                ),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DebugBgmDockTabIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    iconSize: Dp = 24.dp,
    selectionProgress: Float = if (selected) 1f else 0f
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = DebugBgmDockTint(
            selected = selected,
            accent = accent,
            selectionProgress = selectionProgress
        ),
        modifier = Modifier.size(iconSize)
    )
}

@Composable
internal fun rememberDebugBgmDockTabs(): List<DebugBgmDockTab> {
    val homeLabel = stringResource(R.string.debug_component_lab_nav_home)
    val discoverLabel = stringResource(R.string.debug_component_lab_nav_discover)
    val radioLabel = stringResource(R.string.debug_component_lab_nav_radio)
    val libraryLabel = stringResource(R.string.debug_component_lab_nav_library)
    val homeIcon = appLucideHomeIcon()
    val discoverIcon = appLucideGridIcon()
    val radioIcon = appLucideRadioIcon()
    val libraryIcon = appLucideLibraryIcon()
    return remember(
        homeLabel,
        discoverLabel,
        radioLabel,
        libraryLabel,
        homeIcon,
        discoverIcon,
        radioIcon,
        libraryIcon
    ) {
        listOf(
            DebugBgmDockTab(DebugBgmDockKeys.Home, homeIcon, homeLabel),
            DebugBgmDockTab(DebugBgmDockKeys.Discover, discoverIcon, discoverLabel),
            DebugBgmDockTab(DebugBgmDockKeys.Radio, radioIcon, radioLabel),
            DebugBgmDockTab(DebugBgmDockKeys.Library, libraryIcon, libraryLabel)
        )
    }
}

@Composable
private fun DebugBgmDockTint(
    selected: Boolean,
    accent: Color,
    selectionProgress: Float = if (selected) 1f else 0f
): Color = lerpColor(
    MiuixTheme.colorScheme.onBackground.copy(alpha = 0.90f),
    accent,
    selectionProgress.coerceIn(0f, 1f)
)

internal object DebugBgmDockKeys {
    const val Home = "home"
    const val Discover = "discover"
    const val Radio = "radio"
    const val Library = "library"
}

internal data class DebugBgmDockTab(
    val key: String,
    val icon: ImageVector,
    val label: String
)

private const val DebugBgmDockSelectionWidthFraction = 1f
private const val DebugBgmDockPressedScale = 68f / 54f
private const val DebugBgmDockClickScale = 1.032f
private const val DebugBgmDockVelocityScaleXFactor = 0.48f
private const val DebugBgmDockVelocityScaleYFactor = 0.16f
private const val DebugBgmDockVelocityScaleClamp = 0.12f
private const val DebugBgmDockSelectedContentPressedScale = 1.12f
