@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import os.kei.ui.animation.DampedDragAnimation
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

@Immutable
data class MiuixFloatingBottomBarLayout(
    val barWidth: Dp,
)

fun miuixFloatingBottomBarLayout(
    availableWidth: Dp,
    itemCount: Int,
    horizontalMargin: Dp = MiuixFloatingBottomBarDefaults.HorizontalMargin,
    preferredItemWidth: Dp = MiuixFloatingBottomBarDefaults.PreferredItemWidth,
    minItemWidth: Dp = MiuixFloatingBottomBarDefaults.MinItemWidth,
    maxItemWidth: Dp = MiuixFloatingBottomBarDefaults.MaxItemWidth,
    maxBarWidth: Dp = MiuixFloatingBottomBarDefaults.MaxBarWidth,
): MiuixFloatingBottomBarLayout {
    val safeCount = itemCount.coerceAtLeast(1)
    val availableBarWidth = (availableWidth - horizontalMargin * 2f).coerceAtLeast(1.dp)
    val targetItemWidth = preferredItemWidth.coerceIn(minItemWidth, maxItemWidth)
    val preferredBarWidth =
        (targetItemWidth * safeCount + MiuixFloatingBottomBarDefaults.HorizontalPadding * 2f)
            .coerceAtLeast(minItemWidth * safeCount + MiuixFloatingBottomBarDefaults.HorizontalPadding * 2f)
    return MiuixFloatingBottomBarLayout(
        barWidth = preferredBarWidth.coerceAtMost(maxBarWidth).coerceAtMost(availableBarWidth),
    )
}

fun miuixFloatingBottomBarBottomPadding(navigationBarBottom: Dp): Dp =
    if (navigationBarBottom != 0.dp) {
        8.dp + navigationBarBottom
    } else {
        36.dp
    }

@Composable
fun MiuixFloatingBottomBarHost(
    navigationBarBottom: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = MiuixFloatingBottomBarDefaults.HorizontalMargin,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = miuixFloatingBottomBarBottomPadding(navigationBarBottom),
                    ).fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
            content = content,
        )
    }
}

@Composable
fun MiuixFloatingBottomTabStrip(
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    itemContent: @Composable RowScope.(index: Int, selected: Boolean, color: Color) -> Unit,
) {
    if (itemCount <= 0) return
    val safeCount = itemCount
    val safeSelectedIndex = selectedIndex.coerceIn(0, safeCount - 1)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val isDark = isSystemInDarkTheme()
    val activeColor = MiuixTheme.colorScheme.primary
    val inactiveColor =
        if (isDark) {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.74f)
        } else {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.70f)
        }
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.42f else 0.56f)
    val indicatorColor = activeColor.copy(alpha = if (isDark) 0.18f else 0.15f)
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }
    var currentIndex by remember(safeCount) {
        mutableIntStateOf(safeSelectedIndex)
    }
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    class DampedDragHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragHolder() }
    val dampedDrag =
        remember(animationScope, safeCount, density, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = safeSelectedIndex.toFloat(),
                valueRange = 0f..(safeCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                gestureKey = safeCount to isLtr,
                canDrag = { offset ->
                    val animation = holder.instance ?: return@DampedDragAnimation true
                    if (tabWidthPx <= 0f || totalWidthPx <= 0f) return@DampedDragAnimation false
                    val paddingPx = with(density) { MiuixFloatingBottomBarDefaults.HorizontalPadding.toPx() }
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
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, safeCount - 1)
                    currentIndex = targetIndex
                    onSelectedUpdated(targetIndex)
                    animationScope.launch {
                        if (animationsEnabled) {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        } else {
                            offsetAnimation.snapTo(0f)
                        }
                    }
                },
                onDrag = { _, dragAmount ->
                    if (tabWidthPx > 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, (safeCount - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            ).also { holder.instance = it }
        }
    val currentSelectionValue by rememberUpdatedState(dampedDrag.value)
    val currentPanelOffset by rememberUpdatedState(panelOffset)
    val interactiveHighlight =
        remember(animationScope, tabWidthPx, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        x =
                            if (isLtr) {
                                (currentSelectionValue + 0.5f) * tabWidthPx + currentPanelOffset
                            } else {
                                size.width - (currentSelectionValue + 0.5f) * tabWidthPx + currentPanelOffset
                            },
                        y = size.height / 2f,
                    )
                },
                highlightColor = Color.White,
                highlightStrength = if (isDark) 0.88f else 0.60f,
                highlightRadiusScale = if (isDark) 1.08f else 0.90f,
            )
        }

    LaunchedEffect(safeSelectedIndex) {
        currentIndex = safeSelectedIndex
    }
    LaunchedEffect(dampedDrag, animationsEnabled, safeCount) {
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index ->
                val target = index.fastCoerceIn(0, safeCount - 1).toFloat()
                if (animationsEnabled) {
                    dampedDrag.animateToValue(target)
                } else {
                    dampedDrag.snapToValue(target)
                }
            }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .onSizeChanged { coords ->
                        totalWidthPx = coords.width.toFloat()
                        val contentWidthPx =
                            totalWidthPx - with(density) { MiuixFloatingBottomBarDefaults.HorizontalPadding.toPx() * 2f }
                        tabWidthPx = (contentWidthPx / safeCount).coerceAtLeast(0f)
                    }.graphicsLayer { translationX = panelOffset }
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { ContinuousCapsule },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx())
                                    lens(
                                        24.dp.toPx(),
                                        24.dp.toPx(),
                                    )
                                },
                                highlight = {
                                    Highlight.Default.copy(alpha = if (isDark) 0.50f else 0.64f)
                                },
                                shadow = {
                                    Shadow.Default.copy(
                                        color = Color.Black.copy(alpha = if (isDark) 0.24f else 0.18f),
                                    )
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                        } else {
                            Modifier
                                .clip(ContinuousCapsule)
                                .background(containerColor, ContinuousCapsule)
                        },
                    ).then(interactiveHighlight.modifier)
                    .height(MiuixFloatingBottomBarDefaults.Height)
                    .padding(MiuixFloatingBottomBarDefaults.HorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(safeCount) { index ->
                val selected = safeSelectedIndex == index
                itemContent(index, selected, inactiveColor)
            }
        }
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = MiuixFloatingBottomBarDefaults.HorizontalPadding)
                        .graphicsLayer {
                            val progressOffset = dampedDrag.value * tabWidthPx
                            translationX =
                                if (isLtr) {
                                    progressOffset + panelOffset
                                } else {
                                    -progressOffset + panelOffset
                                }
                        }.then(interactiveHighlight.gestureModifier)
                        .then(dampedDrag.modifier)
                        .clip(ContinuousCapsule)
                        .background(indicatorColor, ContinuousCapsule)
                        .height(MiuixFloatingBottomBarDefaults.IndicatorHeight)
                        .width(tabWidthDp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier =
                        Modifier
                            .clearAndSetSemantics {}
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .requiredWidth(
                                with(density) { (totalWidthPx - MiuixFloatingBottomBarDefaults.HorizontalPadding.toPx() * 2f).toDp() },
                            ).height(MiuixFloatingBottomBarDefaults.IndicatorHeight)
                            .graphicsLayer {
                                val progressOffset = dampedDrag.value * tabWidthPx
                                translationX = if (isLtr) -progressOffset else progressOffset
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(safeCount) { index ->
                        val selected = safeSelectedIndex == index
                        itemContent(index, selected, activeColor)
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.MiuixFloatingBottomTabItem(
    selected: Boolean,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (color: Color, iconModifier: Modifier) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .weight(1f)
                .fillMaxHeight()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    indication = null,
                    interactionSource = interactionSource,
                ),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(
            color,
            Modifier.size(MiuixFloatingBottomBarDefaults.IconSize),
        )
        Text(
            text = label,
            fontSize = MiuixFloatingBottomBarDefaults.LabelFontSize,
            fontWeight = FontWeight.Normal,
            lineHeight = MiuixFloatingBottomBarDefaults.LabelLineHeight,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

object MiuixFloatingBottomBarDefaults {
    val Height: Dp = 64.dp
    val ItemHeight: Dp = 56.dp
    val IndicatorHeight: Dp = 56.dp
    val IconSize: Dp = 22.dp
    val HorizontalPadding: Dp = 4.dp
    val HorizontalMargin: Dp = 24.dp
    val PreferredItemWidth: Dp = 72.dp
    val MinItemWidth: Dp = 56.dp
    val MaxBarWidth: Dp = 520.dp
    val MaxItemWidth: Dp = 92.dp
    val LabelFontSize = 11.sp
    val LabelLineHeight = 13.sp
}
