package os.kei.ui.page.main.settings.page

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.chrome.AppBottomSearchDock
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsBottomChrome(
    visible: Boolean,
    navigationBarBottom: Dp,
    categories: List<SettingsCategory>,
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
    onSelectCategory: (Int) -> Unit,
) {
    if (!visible && !searchExpanded) return
    val safeSelectedPage = selectedPage.coerceIn(0, categories.lastIndex)
    val size = AppChromeTokens.floatingBottomBarOuterHeight
    val gap = SettingsBottomChromeSearchGap
    val outerPadding = AppChromeTokens.pageHorizontalPadding
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val keyboardLift = rememberAppFloatingKeyboardLift(
        focusedLift = 18.dp,
        label = "settings_bottom_chrome_keyboard_lift",
    )
    val transition = updateTransition(
        targetState = searchExpanded,
        label = "settings_bottom_chrome",
    )
    val sizeAnimationSpec = tween<Dp>(
        durationMillis = resolvedMotionDuration(SettingsBottomChromeMotionMs, animationsEnabled),
        easing = FastOutSlowInEasing,
    )
    val fadeAnimationSpec = tween<Float>(
        durationMillis = resolvedMotionDuration(
            SettingsBottomChromeFadeMotionMs,
            animationsEnabled
        ),
        easing = FastOutSlowInEasing,
    )
    val fullDockAlpha by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "settings_full_dock_alpha",
    ) { expanded -> if (expanded) 0f else 1f }
    val compactDockAlpha by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "settings_compact_dock_alpha",
    ) { expanded -> if (expanded) 1f else 0f }
    val fullDockScale by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "settings_full_dock_scale",
    ) { expanded -> if (expanded) 0.96f else 1f }
    val compactDockScale by transition.animateFloat(
        transitionSpec = { fadeAnimationSpec },
        label = "settings_compact_dock_scale",
    ) { expanded -> if (expanded) 1f else 0.92f }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = -keyboardLift)
            .padding(
                start = outerPadding,
                end = outerPadding,
                top = 12.dp,
                bottom = 12.dp + navigationBarBottom,
            )
            .height(size),
    ) {
        val expandedSearchWidth = settingsExpandedSearchWidth(
            availableWidth = maxWidth,
            compactDockWidth = size,
            gap = gap,
        )
        val collapsedDockWidth = settingsCollapsedDockWidth(
            availableWidth = maxWidth,
            searchDockWidth = size,
            gap = gap,
        )
        val searchX by transition.animateDp(
            transitionSpec = { sizeAnimationSpec },
            label = "settings_search_dock_x",
        ) { expanded ->
            if (expanded) size + gap else collapsedDockWidth + gap
        }
        val searchWidth by transition.animateDp(
            transitionSpec = { sizeAnimationSpec },
            label = "settings_search_dock_width",
        ) { expanded ->
            if (expanded) expandedSearchWidth else size
        }

        if (fullDockAlpha > SettingsBottomChromeVisibleAlpha) {
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

        if (compactDockAlpha > SettingsBottomChromeVisibleAlpha) {
            SettingsCompactCategoryDock(
                category = categories[safeSelectedPage],
                backdrop = backdrop,
                onClick = { onSearchExpandedChange(false) },
                modifier = Modifier
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
            modifier = Modifier.offset(x = searchX, y = 0.dp),
            expandedWidth = searchWidth,
        )
    }
}

@Composable
private fun SettingsCompactCategoryDock(
    category: SettingsCategory,
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
        pressLabel = "settings_compact_category_dock_press",
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = category.label(),
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(27.dp),
        )
    }
}

private val SettingsBottomChromeSearchGap: Dp = 8.dp
private val SettingsBottomChromeMinSearchWidth: Dp = 196.dp
private const val SettingsBottomChromeMotionMs = 280
private const val SettingsBottomChromeFadeMotionMs = 180
private const val SettingsBottomChromeVisibleAlpha = 0.01f

private fun settingsExpandedSearchWidth(
    availableWidth: Dp,
    compactDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = SettingsBottomChromeSearchGap,
    minWidth: Dp = SettingsBottomChromeMinSearchWidth,
): Dp {
    return (availableWidth - compactDockWidth - gap).coerceAtLeast(minWidth)
}

private fun settingsCollapsedDockWidth(
    availableWidth: Dp,
    searchDockWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    gap: Dp = SettingsBottomChromeSearchGap,
    minWidth: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
): Dp {
    return (availableWidth - searchDockWidth - gap).coerceAtLeast(minWidth)
}
