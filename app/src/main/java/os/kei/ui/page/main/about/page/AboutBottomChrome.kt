package os.kei.ui.page.main.about.page

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutBottomChrome(
    navigationBarBottom: Dp,
    categories: List<AboutCategory>,
    selectedPage: Int,
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
) {
    val safeSelectedPage = selectedPage.coerceIn(0, categories.lastIndex)
    val size = AppChromeTokens.floatingBottomBarOuterHeight
    val gap = AboutBottomChromeSearchGap
    val outerPadding = AppChromeTokens.pageHorizontalPadding
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = outerPadding,
                end = outerPadding,
                top = 12.dp,
                bottom = 12.dp + navigationBarBottom,
            )
            .height(size),
    ) {
        if (searchExpanded) {
            val searchWidth = aboutExpandedSearchWidth(
                availableWidth = maxWidth,
                compactDockWidth = size,
                gap = gap,
            )
            AboutCompactCategoryDock(
                category = categories[safeSelectedPage],
                backdrop = backdrop,
                onClick = { onSearchExpandedChange(false) },
                modifier = Modifier
                    .width(size)
                    .height(size),
            )
            AboutSearchDock(
                backdrop = backdrop,
                expanded = true,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onExpandedChange = onSearchExpandedChange,
                searchIcon = searchIcon,
                contentDescription = searchContentDescription,
                placeholder = searchPlaceholder,
                modifier = Modifier
                    .offset(x = size + gap, y = 0.dp),
                expandedWidth = searchWidth,
            )
        } else {
            val bottomBarTabs: @Composable RowScope.() -> Unit = {
                categories.forEachIndexed { index, category ->
                    val tabColor = liquidGlassBottomBarItemContentColor(index)
                    val tabContent: @Composable ColumnScope.() -> Unit = {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = category.label(),
                            tint = tabColor,
                            modifier = Modifier
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
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(size),
                selectedIndex = safeSelectedPage,
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
            AboutSearchDock(
                backdrop = backdrop,
                expanded = false,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onExpandedChange = onSearchExpandedChange,
                searchIcon = searchIcon,
                contentDescription = searchContentDescription,
                placeholder = searchPlaceholder,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(size)
                    .height(size),
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
            modifier = Modifier
                .size(27.dp),
        )
    }
}

internal val AboutBottomChromeSearchGap: Dp = 8.dp
internal val AboutBottomChromeMinSearchWidth: Dp = 196.dp

internal fun aboutExpandedSearchWidth(
    availableWidth: Dp,
    compactDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = AboutBottomChromeSearchGap,
    minWidth: Dp = AboutBottomChromeMinSearchWidth,
): Dp {
    return (availableWidth - compactDockWidth - gap).coerceAtLeast(minWidth)
}
