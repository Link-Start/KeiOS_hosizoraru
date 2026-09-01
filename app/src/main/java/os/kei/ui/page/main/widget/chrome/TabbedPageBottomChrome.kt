// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.chrome

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.glass.AppFloatingSearchDock
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLiftState
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
internal val TabbedPageBottomChromeCompactHeightMax: Dp = 480.dp
internal const val TabbedPageBottomChromeMotionMs = 220

// ── Shared utility functions ─────────────────────────────────────────────────

internal fun tabbedPageExpandedSearchWidth(
    availableWidth: Dp,
    compactDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = TabbedPageBottomChromeSearchGap,
): Dp = (availableWidth - compactDockWidth - gap).coerceAtLeast(0.dp)

internal fun tabbedPageCollapsedDockWidth(
    availableWidth: Dp,
    searchDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = TabbedPageBottomChromeSearchGap,
    minWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
): Dp = (availableWidth - searchDockWidth - gap).coerceAtLeast(minWidth)

internal fun tabbedPageSearchDockRegionOffset(
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = TabbedPageBottomChromeSearchGap,
): Dp = size + gap

internal fun tabbedPageCategoryDockExpanded(
    visible: Boolean,
    searchExpanded: Boolean,
): Boolean = visible && !searchExpanded

internal fun tabbedPageUsesCompactHeightDock(
    availableWidth: Dp,
    availableHeight: Dp,
): Boolean =
    availableWidth > availableHeight &&
        availableHeight <= TabbedPageBottomChromeCompactHeightMax

internal fun tabbedPageChromeVisible(
    visible: Boolean,
    compactHeightPresentation: Boolean,
    compactHeightDockExpanded: Boolean,
): Boolean = visible && (!compactHeightPresentation || compactHeightDockExpanded)

internal enum class TabbedPageCompactDockAction {
    CloseSearch,
    ExpandCompactHeightDock,
    ShowDock,
}

internal fun tabbedPageCompactDockAction(
    searchExpanded: Boolean,
    compactHeightPresentation: Boolean,
): TabbedPageCompactDockAction =
    when {
        searchExpanded -> TabbedPageCompactDockAction.CloseSearch
        compactHeightPresentation -> TabbedPageCompactDockAction.ExpandCompactHeightDock
        else -> TabbedPageCompactDockAction.ShowDock
    }

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
    modifier: Modifier = Modifier,
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
    searchEnabled: Boolean = true,
    backdrop: Backdrop,
    isLiquidEffectEnabled: Boolean,
    onSelectCategory: (Int) -> Unit,
    onExpandDock: () -> Unit,
    labelPrefix: String = "tabbed_page",
) {
    val safeSelectedPage = selectedPage.coerceIn(0, categories.lastIndex)
    val configuration = LocalConfiguration.current
    val compactHeightPresentation =
        tabbedPageUsesCompactHeightDock(
            availableWidth = configuration.screenWidthDp.dp,
            availableHeight = configuration.screenHeightDp.dp,
        )
    var compactHeightDockExpanded by remember { mutableStateOf(!compactHeightPresentation) }
    LaunchedEffect(compactHeightPresentation) {
        compactHeightDockExpanded = !compactHeightPresentation
    }
    LaunchedEffect(visible, compactHeightPresentation) {
        if (compactHeightPresentation && !visible) {
            compactHeightDockExpanded = false
        }
    }
    val size = AppChromeTokens.floatingBottomBarOuterHeight
    val gap = TabbedPageBottomChromeSearchGap
    // The category bar and the search dock size themselves from the width they are given, so folding the
    // large-screen gutter into the outer padding narrows the whole bottom chrome onto the content column
    // instead of stretching a five-tab strip across 1280dp. Zero on phones. Pinned to the single-column cap
    // on purpose: a page that widens its column for two columns of cards must not drag this bar out with it.
    val outerStartPadding = AppChromeTokens.pageHorizontalPadding + appBottomChromeSideGutterStart()
    val outerEndPadding = AppChromeTokens.pageHorizontalPadding + appBottomChromeSideGutterEnd()
    val effectiveSearchExpanded = searchEnabled && searchExpanded
    val categoryDockExpanded =
        tabbedPageCategoryDockExpanded(
            visible =
                tabbedPageChromeVisible(
                    visible = visible,
                    compactHeightPresentation = compactHeightPresentation,
                    compactHeightDockExpanded = compactHeightDockExpanded,
                ),
            searchExpanded = effectiveSearchExpanded,
        )
    val keyboardLiftState =
        rememberAppFloatingKeyboardLiftState(
            focusedLift = 18.dp,
            restingBottomGap = navigationBarBottom + 12.dp,
            label = "${labelPrefix}_bottom_chrome_keyboard_lift",
        )
    val keyboardLiftProvider = remember(keyboardLiftState) { { keyboardLiftState.value } }
    val searchDockAlphaProvider = remember { { TabbedPageBottomChromeSearchDockVisibleAlpha } }
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(x = 0, y = -keyboardLiftProvider().roundToPx())
                }.padding(
                    start = outerStartPadding,
                    end = outerEndPadding,
                    top = 12.dp,
                    bottom = 12.dp + navigationBarBottom,
                ).height(size),
    ) {
        val expandedSearchWidth =
            tabbedPageExpandedSearchWidth(
                availableWidth = maxWidth,
                compactDockWidth = size,
                gap = gap,
            )
        val collapsedDockWidth =
            if (searchEnabled) {
                tabbedPageCollapsedDockWidth(
                    availableWidth = maxWidth,
                    searchDockWidth = size,
                    gap = gap,
                )
            } else {
                maxWidth
            }
        AnimatedCompactBottomBar(
            expanded = categoryDockExpanded,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(size),
            expandedContent = { motionModifier, interactionEnabled ->
                Box(modifier = motionModifier.align(Alignment.BottomStart)) {
                    TabbedPageCategoryBar(
                        labelPrefix = labelPrefix,
                        categories = categories,
                        safeSelectedPage = safeSelectedPage,
                        selectedPagePosition = selectedPagePosition,
                        selectedPagePositionProvider = selectedPagePositionProvider,
                        selectedPageProvider = selectedPageProvider,
                        collapsedDockWidth = collapsedDockWidth,
                        backdrop = backdrop,
                        isLiquidEffectEnabled = isLiquidEffectEnabled,
                        interactionEnabled = interactionEnabled,
                        onSelectCategory = onSelectCategory,
                    )
                }
            },
            compactContent = { motionModifier, interactionEnabled ->
                Box(modifier = motionModifier.align(Alignment.BottomStart)) {
                    TabbedPageCompactCategoryDock(
                        category = categories[safeSelectedPage],
                        backdrop = backdrop,
                        enabled = interactionEnabled,
                        onClick = {
                            when (
                                tabbedPageCompactDockAction(
                                    searchExpanded = effectiveSearchExpanded,
                                    compactHeightPresentation = compactHeightPresentation,
                                )
                            ) {
                                TabbedPageCompactDockAction.CloseSearch -> {
                                    onSearchExpandedChange(false)
                                }

                                TabbedPageCompactDockAction.ExpandCompactHeightDock -> {
                                    compactHeightDockExpanded = true
                                    onExpandDock()
                                }

                                TabbedPageCompactDockAction.ShowDock -> {
                                    onExpandDock()
                                }
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

        if (searchEnabled) {
            AppFloatingSearchDock(
                backdrop = backdrop,
                expanded = effectiveSearchExpanded,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onExpandedChange = onSearchExpandedChange,
                searchIcon = searchIcon,
                contentDescription = searchContentDescription,
                placeholder = searchPlaceholder,
                modifier =
                    Modifier
                        .width(expandedSearchWidth)
                        .testTag(tabbedPageSearchDockTestTag(labelPrefix))
                        .zIndex(3f)
                        .graphicsLayer {
                            alpha = searchDockAlphaProvider()
                        }.offset(
                            x = tabbedPageSearchDockRegionOffset(size = size, gap = gap),
                            y = 0.dp,
                        ),
                keyboardLift = 0.dp,
                compactIconTint = MiuixTheme.colorScheme.primary,
                showActionWhenExpanded = false,
            )
        }
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
    backdrop: Backdrop,
    isLiquidEffectEnabled: Boolean,
    interactionEnabled: Boolean,
    labelPrefix: String,
    onSelectCategory: (Int) -> Unit,
) {
    val bottomBarTabs: @Composable RowScope.() -> Unit = {
        categories.forEachIndexed { index, category ->
            val tabColor = liquidGlassBottomBarItemContentColor(index)
            val tabLabel = stringResource(category.labelRes)
            val tabContent: @Composable ColumnScope.() -> Unit = {
                Icon(
                    imageVector = ImageVector.vectorResource(category.iconRes),
                    contentDescription = null,
                    tint = tabColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = tabLabel,
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
                label = tabLabel,
                onClick = { onSelectCategory(index) },
                // Derived from the page's own prefix rather than declared per page: this chrome is
                // generic over its categories, so a tag list would have to be threaded through every
                // caller to name tabs it cannot see. The values are spelled out in KeiOsTestTags for the
                // pages a baseline-profile journey walks.
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = 62.dp)
                        .testTag(tabbedPageCategoryTabTestTag(labelPrefix, index)),
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
            if ((categories.getOrNull(index) != null) && (index != selectedPageProvider())) {
                onSelectCategory(index)
            }
        },
        backdrop = backdrop,
        tabsCount = categories.size,
        isLiquidEffectEnabled = isLiquidEffectEnabled,
        interactionEnabled = interactionEnabled,
        content = bottomBarTabs,
    )
}

@Composable
private fun <C : TabbedPageCategory> TabbedPageCompactCategoryDock(
    category: C,
    backdrop: Backdrop,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactBottomBarDock(
        modifier = modifier,
        backdrop = backdrop,
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(category.iconRes),
            contentDescription = stringResource(category.labelRes),
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(27.dp),
        )
    }
}

/** The tag on one category tab of a [TabbedPageBottomChrome], e.g. `github_history_tab_2`. */
internal fun tabbedPageCategoryTabTestTag(
    labelPrefix: String,
    index: Int,
): String = "${labelPrefix}_tab_$index"

/** The tag on a [TabbedPageBottomChrome]'s search dock, e.g. `about_search`. */
internal fun tabbedPageSearchDockTestTag(labelPrefix: String): String = "${labelPrefix}_search"
