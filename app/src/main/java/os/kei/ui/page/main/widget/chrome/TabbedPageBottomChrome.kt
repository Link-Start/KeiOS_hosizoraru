// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLiftState
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ── Category contract ────────────────────────────────────────────────────────

/**
 * Minimal contract for categories rendered by [TabbedPageBottomChrome].
 * Implement on an enum or sealed class to get a shared bottom chrome.
 */
@Immutable
internal interface TabbedPageCategory {
    /** Drawable resource ID for the category tab icon. */
    val iconRes: Int
    /** String resource ID for the category tab label. */
    val labelRes: Int
}

// ── Shared constants ─────────────────────────────────────────────────────────

internal val TabbedPageBottomChromeSearchGap: Dp = 8.dp
internal val TabbedPageBottomChromeMinSearchWidth: Dp = 196.dp
internal const val TabbedPageBottomChromeMotionMs = 220

// ── Shared utility functions ─────────────────────────────────────────────────

internal fun tabbedPageExpandedSearchWidth(
    availableWidth: Dp,
    compactDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = TabbedPageBottomChromeSearchGap,
    minWidth: Dp = TabbedPageBottomChromeMinSearchWidth,
): Dp = (availableWidth - compactDockWidth - gap).coerceAtLeast(minWidth)

internal fun tabbedPageCollapsedDockWidth(
    availableWidth: Dp,
    searchDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = TabbedPageBottomChromeSearchGap,
    minWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
): Dp = (availableWidth - searchDockWidth - gap).coerceAtLeast(minWidth)

internal fun tabbedPageSearchDockTargetX(
    searchExpanded: Boolean,
    collapsedDockWidth: Dp,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = TabbedPageBottomChromeSearchGap,
): Dp =
    if (searchExpanded) {
        size + gap
    } else {
        collapsedDockWidth + gap
    }

internal fun tabbedPageSearchDockTargetWidth(
    searchExpanded: Boolean,
    expandedSearchWidth: Dp,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
): Dp =
    if (searchExpanded) {
        expandedSearchWidth
    } else {
        size
    }

internal fun tabbedPageCategoryDockExpanded(
    visible: Boolean,
    searchExpanded: Boolean,
): Boolean = visible && !searchExpanded

// ── Generic bottom chrome ────────────────────────────────────────────────────

/**
 * Shared bottom chrome for tabbed pages with a floating category dock, search
 * expansion, and compact category dock. Parameterized on [C] so Settings,
 * About, and future tabbed pages share one implementation.
 *
 * @param labelPrefix unique prefix for Compose transition/debug labels
 *  (e.g. "settings", "about").
 */
@Composable
internal fun <C : TabbedPageCategory> TabbedPageBottomChrome(
    visible: Boolean,
    navigationBarBottom: Dp,
    categories: List<C>,
    selectedPage: Int,
    selectedPagePosition: Float?,
    selectedPagePositionProvider: (() -> Float?)? = null,
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
    onSelectCategory: (Int) -> Unit,
    onExpandDock: () -> Unit,
    labelPrefix: String = "tabbed_page",
) {
    val safeSelectedPage = selectedPage.coerceIn(0, categories.lastIndex)
    val size = AppChromeTokens.floatingBottomBarOuterHeight
    val gap = TabbedPageBottomChromeSearchGap
    val outerPadding = AppChromeTokens.pageHorizontalPadding
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val categoryDockExpanded =
        tabbedPageCategoryDockExpanded(
            visible = visible,
            searchExpanded = searchExpanded,
        )
    val keyboardLiftState =
        rememberAppFloatingKeyboardLiftState(
            focusedLift = 18.dp,
            restingBottomGap = navigationBarBottom + 12.dp,
            label = "${labelPrefix}_bottom_chrome_keyboard_lift",
        )
    val keyboardLiftProvider = remember(keyboardLiftState) { { keyboardLiftState.value } }
    val sizeAnimationSpec =
        tween<Dp>(
            durationMillis = resolvedMotionDuration(TabbedPageBottomChromeMotionMs, animationsEnabled),
            easing = FastOutSlowInEasing,
        )
    val searchDockAlphaProvider = remember { { TabbedPageBottomChromeSearchDockVisibleAlpha } }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(x = 0, y = -keyboardLiftProvider().roundToPx())
                }
                .padding(
                    start = outerPadding,
                    end = outerPadding,
                    top = 12.dp,
                    bottom = 12.dp + navigationBarBottom,
                )
                .height(size),
    ) {
        val expandedSearchWidth =
            tabbedPageExpandedSearchWidth(
                availableWidth = maxWidth,
                compactDockWidth = size,
                gap = gap,
            )
        val collapsedDockWidth =
            tabbedPageCollapsedDockWidth(
                availableWidth = maxWidth,
                searchDockWidth = size,
                gap = gap,
            )
        val visibleDockWidth = collapsedDockWidth
        val searchTransition =
            updateTransition(
                targetState = searchExpanded,
                label = "${labelPrefix}_search_dock",
            )
        val searchXState =
            searchTransition.animateDp(
                transitionSpec = { sizeAnimationSpec },
                label = "${labelPrefix}_search_dock_x",
            ) { expanded ->
                tabbedPageSearchDockTargetX(
                    searchExpanded = expanded,
                    collapsedDockWidth = visibleDockWidth,
                    size = size,
                    gap = gap,
                )
            }
        val searchWidthState =
            searchTransition.animateDp(
                transitionSpec = { sizeAnimationSpec },
                label = "${labelPrefix}_search_dock_width",
            ) { expanded ->
                tabbedPageSearchDockTargetWidth(
                    searchExpanded = expanded,
                    expandedSearchWidth = expandedSearchWidth,
                    size = size,
                )
            }
        val searchXProvider = remember(searchXState) { { searchXState.value } }
        val searchWidthProvider = remember(searchWidthState) { { searchWidthState.value } }

        AnimatedCompactBottomBar(
            expanded = categoryDockExpanded,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(size),
            expandedContent = { motionModifier ->
                Box(modifier = motionModifier.align(Alignment.BottomStart)) {
                    TabbedPageCategoryBar(
                        categories = categories,
                        safeSelectedPage = safeSelectedPage,
                        selectedPagePosition = selectedPagePosition,
                        selectedPagePositionProvider = selectedPagePositionProvider,
                        selectedPageProvider = selectedPageProvider,
                        collapsedDockWidth = collapsedDockWidth,
                        backdrop = backdrop,
                        isLiquidEffectEnabled = isLiquidEffectEnabled,
                        onSelectCategory = onSelectCategory,
                    )
                }
            },
            compactContent = { motionModifier ->
                Box(modifier = motionModifier.align(Alignment.BottomStart)) {
                    TabbedPageCompactCategoryDock(
                        category = categories[safeSelectedPage],
                        backdrop = backdrop,
                        onClick = {
                            if (visible) {
                                onSearchExpandedChange(false)
                            } else {
                                onExpandDock()
                            }
                        },
                        modifier =
                            Modifier
                                .width(size)
                                .height(size),
                    )
                }
            },
        )

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
                    .zIndex(3f)
                    .graphicsLayer {
                        alpha = searchDockAlphaProvider()
                    }
                    .offset {
                        IntOffset(x = searchXProvider().roundToPx(), y = 0)
                    },
            expandedWidth = expandedSearchWidth,
            expandedWidthProvider = searchWidthProvider,
        )
    }
}

internal const val TabbedPageBottomChromeSearchDockVisibleAlpha = 1f

@Composable
private fun <C : TabbedPageCategory> TabbedPageCategoryBar(
    categories: List<C>,
    safeSelectedPage: Int,
    selectedPagePosition: Float?,
    selectedPagePositionProvider: (() -> Float?)?,
    selectedPageProvider: () -> Int,
    collapsedDockWidth: Dp,
    backdrop: LayerBackdrop,
    isLiquidEffectEnabled: Boolean,
    onSelectCategory: (Int) -> Unit,
) {
    val bottomBarTabs: @Composable RowScope.() -> Unit = {
        categories.forEachIndexed { index, category ->
            val tabColor = liquidGlassBottomBarItemContentColor(index)
            val tabContent: @Composable ColumnScope.() -> Unit = {
                Icon(
                    imageVector = ImageVector.vectorResource(category.iconRes),
                    contentDescription = null,
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
                    text = stringResource(category.labelRes),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = tabColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
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
                .requiredWidth(collapsedDockWidth)
                .height(AppChromeTokens.floatingBottomBarOuterHeight),
        selectedIndex = safeSelectedPage,
        selectedPosition = selectedPagePosition,
        selectedPositionProvider = selectedPagePositionProvider,
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

@Composable
private fun <C : TabbedPageCategory> TabbedPageCompactCategoryDock(
    category: C,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactBottomBarDock(
        modifier = modifier,
        backdrop = backdrop,
        onClick = onClick,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(category.iconRes),
            contentDescription = stringResource(category.labelRes),
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(27.dp),
        )
    }
}
