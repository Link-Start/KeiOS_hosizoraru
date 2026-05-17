@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.about.page

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.chrome.AppBottomSearchDock
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomTabItem
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomTabStrip
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.chrome.miuixFloatingBottomBarLayout
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutBottomChrome(
    navigationBarBottom: Dp,
    categories: List<AboutCategory>,
    selectedPage: Int,
    selectedPagePosition: Float?,
    selectedPageProvider: () -> Int,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    searchContentDescription: String,
    searchPlaceholder: String,
    backdrop: LayerBackdrop,
    isLiquidEffectEnabled: Boolean,
    miuixMainNavigationEnabled: Boolean,
    onSelectCategory: (Int) -> Unit,
) {
    val safeSelectedPage = selectedPage.coerceIn(0, categories.lastIndex)
    val size = AppChromeTokens.floatingBottomBarOuterHeight
    val gap = AboutBottomChromeSearchGap
    val outerPadding = AppChromeTokens.pageHorizontalPadding
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val density = LocalDensity.current
    val keyboardLift =
        rememberAppFloatingKeyboardLift(
            focusedLift = 18.dp,
            restingBottomGap = navigationBarBottom + 12.dp,
            label = "about_bottom_chrome_keyboard_lift",
        )
    val transition =
        updateTransition(
            targetState = searchExpanded,
            label = "about_bottom_chrome",
        )
    val sizeAnimationSpec =
        tween<Dp>(
            durationMillis = resolvedMotionDuration(AboutBottomChromeMotionMs, animationsEnabled),
            easing = FastOutSlowInEasing,
        )
    val fadeAnimationSpec =
        tween<Float>(
            durationMillis = resolvedMotionDuration(AboutBottomChromeFadeMotionMs, animationsEnabled),
            easing = FastOutSlowInEasing,
        )
    val fullDockAlpha by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "about_full_dock_alpha",
    ) { expanded ->
        if (expanded) 0f else 1f
    }
    val compactDockAlpha by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "about_compact_dock_alpha",
    ) { expanded ->
        if (expanded) 1f else 0f
    }
    val fullDockScale by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "about_full_dock_scale",
    ) { expanded ->
        if (expanded) 0.96f else 1f
    }
    val compactDockScale by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "about_compact_dock_scale",
    ) { expanded ->
        if (expanded) 1f else 0.92f
    }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset {
                    with(density) {
                        IntOffset(x = 0, y = -keyboardLift.roundToPx())
                    }
                }
                .padding(
                    start = outerPadding,
                    end = outerPadding,
                    top = 12.dp,
                    bottom = 12.dp + navigationBarBottom,
                ).height(size),
    ) {
        val expandedSearchWidth =
            aboutExpandedSearchWidth(
                availableWidth = maxWidth,
                compactDockWidth = size,
                gap = gap,
            )
        val collapsedDockWidth =
            aboutCollapsedDockWidth(
                availableWidth = maxWidth,
                searchDockWidth = size,
                gap = gap,
            )
        val miuixDockLayout =
            miuixFloatingBottomBarLayout(
                availableWidth = collapsedDockWidth,
                itemCount = categories.size,
                horizontalMargin = 0.dp,
                preferredItemWidth = 52.dp,
                maxItemWidth = 56.dp,
                maxBarWidth = collapsedDockWidth,
            )
        val visibleDockWidth =
            if (miuixMainNavigationEnabled) {
                miuixDockLayout.barWidth
            } else {
                collapsedDockWidth
            }
        val searchX by transition.animateDp(
            transitionSpec = { sizeAnimationSpec },
            label = "about_search_dock_x",
        ) { expanded ->
            if (expanded) size + gap else visibleDockWidth + gap
        }
        val searchWidth by transition.animateDp(
            transitionSpec = { sizeAnimationSpec },
            label = "about_search_dock_width",
        ) { expanded ->
            if (expanded) expandedSearchWidth else size
        }

        if (fullDockAlpha > AboutBottomChromeVisibleAlpha && miuixMainNavigationEnabled) {
            AboutMiuixCategoryBar(
                categories = categories,
                selectedPage = safeSelectedPage,
                backdrop = backdrop,
                onSelectCategory = onSelectCategory,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .requiredWidth(miuixDockLayout.barWidth)
                        .graphicsLayer {
                            alpha = fullDockAlpha
                            scaleX = fullDockScale
                            scaleY = fullDockScale
                        },
            )
        } else if (fullDockAlpha > AboutBottomChromeVisibleAlpha) {
            val bottomBarTabs: @Composable RowScope.() -> Unit = {
                categories.forEachIndexed { index, category ->
                    val tabColor = liquidGlassBottomBarItemContentColor(index)
                    val tabContent: @Composable ColumnScope.() -> Unit = {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = category.label(),
                            tint = tabColor,
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = 1f
                                        scaleY = 1f
                                    },
                        )
                        Text(
                            text = category.label(),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = tabColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                    LiquidGlassBottomBarItem(
                        selected = safeSelectedPage == index,
                        tabIndex = index,
                        onClick = { onSelectCategory(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 62.dp),
                        content = tabContent,
                    )
                }
            }
            LiquidGlassBottomBar(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .requiredWidth(collapsedDockWidth)
                        .height(size)
                        .graphicsLayer {
                            alpha = fullDockAlpha
                            scaleX = fullDockScale
                            scaleY = fullDockScale
                        },
                selectedIndex = safeSelectedPage,
                selectedPosition = selectedPagePosition,
                onSelected = { index ->
                    if (categories.getOrNull(index) != null && index != selectedPageProvider()) {
                        onSelectCategory(index)
                    }
                },
                backdrop = backdrop,
                tabsCount = categories.size,
                isLiquidEffectEnabled = isLiquidEffectEnabled,
                content = bottomBarTabs,
            )
        }

        if (compactDockAlpha > AboutBottomChromeVisibleAlpha) {
            AboutCompactCategoryDock(
                category = categories[safeSelectedPage],
                backdrop = backdrop,
                onClick = { onSearchExpandedChange(false) },
                modifier =
                    Modifier
                        .width(size)
                        .height(size)
                        .graphicsLayer {
                            alpha = compactDockAlpha
                            scaleX = compactDockScale
                            scaleY = compactDockScale
                        },
            )
        }

        AppBottomSearchDock(
            backdrop = backdrop,
            expanded = searchExpanded,
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onExpandedChange = onSearchExpandedChange,
            searchIcon = searchIcon,
            contentDescription = searchContentDescription,
            placeholder = searchPlaceholder,
            modifier =
                Modifier
                    .offset {
                        with(density) {
                            IntOffset(x = searchX.roundToPx(), y = 0)
                        }
                    },
            expandedWidth = searchWidth,
        )
    }
}

@Composable
private fun AboutMiuixCategoryBar(
    categories: List<AboutCategory>,
    selectedPage: Int,
    backdrop: LayerBackdrop,
    onSelectCategory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixFloatingBottomTabStrip(
        itemCount = categories.size,
        selectedIndex = selectedPage,
        onSelected = onSelectCategory,
        backdrop = backdrop,
        modifier = modifier,
    ) { index, selected, contentColor ->
        val category = categories[index]
        MiuixFloatingBottomTabItem(
            selected = selected,
            label = category.label(),
            color = contentColor,
            onClick = { onSelectCategory(index) },
        ) { contentColor, iconModifier ->
            Icon(
                imageVector = category.icon(),
                contentDescription = category.label(),
                tint = contentColor,
                modifier = iconModifier,
            )
        }
    }
}

@Composable
private fun AboutCompactCategoryDock(
    category: AboutCategory,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLiquidFloatingSurface(
        modifier = modifier,
        shape = CircleShape,
        backdrop = backdrop,
        onClick = onClick,
        pressDurationMillis = 120,
        pressLabel = "about_compact_category_dock_press",
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = category.label(),
            tint = MiuixTheme.colorScheme.primary,
            modifier =
                Modifier
                    .size(27.dp),
        )
    }
}

internal val AboutBottomChromeSearchGap: Dp = 8.dp
internal val AboutBottomChromeMinSearchWidth: Dp = 196.dp
private const val AboutBottomChromeMotionMs = 220
private const val AboutBottomChromeFadeMotionMs = 120
private const val AboutBottomChromeVisibleAlpha = 0.01f

internal fun aboutExpandedSearchWidth(
    availableWidth: Dp,
    compactDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = AboutBottomChromeSearchGap,
    minWidth: Dp = AboutBottomChromeMinSearchWidth,
): Dp = (availableWidth - compactDockWidth - gap).coerceAtLeast(minWidth)

internal fun aboutCollapsedDockWidth(
    availableWidth: Dp,
    searchDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = AboutBottomChromeSearchGap,
    minWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
): Dp = (availableWidth - searchDockWidth - gap).coerceAtLeast(minWidth)
