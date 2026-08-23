@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.model.bottomPageIconScale
import os.kei.ui.page.main.widget.chrome.AnimatedCompactBottomBar
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppNavigationPlacement
import os.kei.ui.page.main.widget.chrome.appNavigationVisible
import os.kei.ui.page.main.widget.chrome.appTopBarNavigationMaxWidth
import os.kei.ui.page.main.widget.chrome.CompactBottomBarDock
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val MainPagerMiuixFloatingToolbarSpacing: Dp = 4.dp

/**
 * Keeps the visible phone dock on its established baseline after MIUIX Scaffold takes over placement.
 *
 * MIUIX already consumes the navigation inset and leaves [MainPagerMiuixFloatingToolbarSpacing] above it.
 * KeiOS historically left 8dp above a reported navigation inset, or 36dp when the inset was zero. This is the
 * remaining content-side distance, so the Liquid Glass bar lands on the same pixels while gaining Scaffold's
 * snackbar avoidance and safe-inset ownership.
 */
internal fun mainPagerFloatingToolbarContentBottomPadding(navigationBarBottom: Dp): Dp =
    if (navigationBarBottom > 0.dp) {
        8.dp - MainPagerMiuixFloatingToolbarSpacing
    } else {
        36.dp - MainPagerMiuixFloatingToolbarSpacing
    }

@Composable
internal fun MainPagerBottomBar(
    visible: Boolean,
    placement: AppNavigationPlacement,
    navigationBarBottom: Dp,
    topInset: Dp,
    tabs: List<BottomPage>,
    selectedPageIndex: Int,
    selectedPagePosition: Float?,
    selectedPagePositionProvider: (() -> Float?)? = null,
    backdrop: Backdrop,
    onPageSelected: (Int) -> Unit,
    onExpand: () -> Unit,
) {
    AnimatedCompactBottomBar(
        expanded = visible,
        expandedContent = { motionModifier, interactionEnabled ->
            val atTop = placement == AppNavigationPlacement.Top
            val bottomContentPadding =
                if (atTop) 0.dp else mainPagerFloatingToolbarContentBottomPadding(navigationBarBottom)
            Box(
                modifier =
                    motionModifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter),
            ) {
                val bottomBarTabs: @Composable RowScope.() -> Unit = {
                    tabs.forEachIndexed { index, page ->
                        val selected = selectedPageIndex == index
                        val tabColor = liquidGlassBottomBarItemContentColor(index)
                        LiquidGlassBottomBarItem(
                            selected = selected,
                            tabIndex = index,
                            label = page.label,
                            onClick = { onPageSelected(index) },
                            modifier = Modifier.testTag(page.bottomTabTestTag()),
                        ) {
                            val tabIconModifier =
                                Modifier
                                    .size(20.dp)
                                    .bottomPageIconScale(page)
                            if (page.iconRes != null) {
                                Icon(
                                    painter = painterResource(id = page.iconRes),
                                    contentDescription = null,
                                    tint = if (page.keepOriginalColors) Color.Unspecified else tabColor,
                                    modifier = tabIconModifier,
                                )
                            } else {
                                page.icon?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tabColor,
                                        modifier = tabIconModifier,
                                    )
                                }
                            }
                            Text(
                                text = page.label,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = tabColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val horizontalMargin =
                        when {
                            maxWidth < 360.dp -> 8.dp
                            maxWidth < 600.dp -> 12.dp
                            else -> 24.dp
                        }
                    val availableWidth = maxWidth - horizontalMargin * 2
                    val minBarWidth =
                        when {
                            tabs.size <= 2 -> 220.dp
                            tabs.size == 3 -> 280.dp
                            else -> 320.dp
                        }
                    val preferredWidth = (76.dp * tabs.size + 8.dp).coerceAtLeast(minBarWidth)
                    val maxBarWidth =
                        when {
                            maxWidth < 600.dp -> availableWidth
                            // Centred between a title and a toolbar, so it has to stay clear of both.
                            atTop -> minOf(460.dp, appTopBarNavigationMaxWidth(maxWidth))
                            else -> 460.dp
                        }
                    val bottomBarWidth = preferredWidth.coerceAtMost(maxBarWidth)
                    // Top placement measures from the status bar, bottom from the navigation bar. Same bar,
                    // same width maths; only which edge it is held against changes.
                    val bottomBarModifier =
                        Modifier
                            .width(bottomBarWidth)
                            .widthIn(max = availableWidth)
                            .padding(
                                top = if (atTop) topInset + 6.dp else 0.dp,
                                bottom = bottomContentPadding,
                            )

                    LiquidGlassBottomBar(
                        modifier = bottomBarModifier,
                        selectedIndex = selectedPageIndex,
                        selectedPosition = selectedPagePosition,
                        selectedPositionProvider = selectedPagePositionProvider,
                        onSelected = onPageSelected,
                        backdrop = backdrop,
                        tabsCount = tabs.size,
                        expandToMaxWidth = true,
                        interactionEnabled = interactionEnabled,
                        content = bottomBarTabs,
                    )
                }
            }
        },
        compactContent = { motionModifier, interactionEnabled ->
            // Collapses toward whichever edge the bar itself lives on, so the tuck stays a short move rather
            // than a flight across the window.
            val atTop = placement == AppNavigationPlacement.Top
            val bottomContentPadding =
                if (atTop) 0.dp else mainPagerFloatingToolbarContentBottomPadding(navigationBarBottom)
            Box(
                modifier =
                    motionModifier
                        .align(if (atTop) Alignment.TopStart else Alignment.BottomStart)
                        .padding(
                            start = AppChromeTokens.pageHorizontalPadding,
                            top = if (atTop) topInset + 6.dp else 0.dp,
                            bottom = bottomContentPadding,
                        ),
            ) {
                val page = tabs.getOrElse(selectedPageIndex) { tabs.first() }
                CompactBottomBarDock(
                    backdrop = backdrop,
                    onClick = onExpand,
                    enabled = interactionEnabled,
                    modifier = Modifier.testTag(page.bottomTabTestTag()),
                ) {
                    val iconModifier =
                        Modifier
                            .size(27.dp)
                            .bottomPageIconScale(page)
                    if (page.iconRes != null) {
                        Icon(
                            painter = painterResource(id = page.iconRes),
                            contentDescription = page.label,
                            tint = if (page.keepOriginalColors) Color.Unspecified else MiuixTheme.colorScheme.primary,
                            modifier = iconModifier,
                        )
                    } else {
                        page.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = page.label,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = iconModifier,
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun BottomPage.bottomTabTestTag(): String =
    when (this) {
        BottomPage.Home -> KeiOsTestTags.MainBottomTabHome
        BottomPage.Os -> KeiOsTestTags.MainBottomTabOs
        BottomPage.Mcp -> KeiOsTestTags.MainBottomTabMcp
        BottomPage.GitHub -> KeiOsTestTags.MainBottomTabGitHub
        BottomPage.Ba -> KeiOsTestTags.MainBottomTabBa
    }

/**
 * The same bar, composed inside the content instead of in the scaffold's bottom slot.
 *
 * Extracted so the two placements share one call site's worth of arguments rather than being written twice.
 * The scaffold's `bottomBar` slot is measured against the bottom edge of the window, so a top-aligned bar
 * cannot live there; at regular width it is an overlay over the content, which is also what the Liquid Glass
 * guidance describes — navigation floats above the content layer and content peeks through beneath it.
 */
@Composable
internal fun BoxScope.MainPagerTopNavigationBar(
    coordinator: MainPagerCoordinatorState,
    placement: AppNavigationPlacement,
    insets: MainPagerInsets,
    backGestureState: MainPagerHomeBackGestureState,
    backdrop: Backdrop,
) {
    val safeSelectedPageIndex =
        coordinator.pagerState.targetPage.coerceIn(0, (coordinator.tabs.size - 1).coerceAtLeast(0))
    val lastPagePosition = (coordinator.tabs.size - 1).coerceAtLeast(0).toFloat()
    val selectedPagePositionProvider =
        remember(coordinator.pagerState, backGestureState, lastPagePosition) {
            {
                if (backGestureState.inProgress) {
                    backGestureState.selectedPagePosition()
                } else {
                    coordinator.pagerState.pagePosition
                }.coerceIn(0f, lastPagePosition)
            }
        }
    MainPagerBottomBar(
        // Never collapses at this placement. See appNavigationCollapsesOnScroll.
        visible = appNavigationVisible(placement = placement, scrolledAway = !coordinator.showBottomBar),
        placement = placement,
        navigationBarBottom = insets.navigationBarBottom,
        topInset = insets.homeTopInset,
        tabs = coordinator.tabs,
        selectedPageIndex = safeSelectedPageIndex,
        selectedPagePosition = null,
        selectedPagePositionProvider = selectedPagePositionProvider,
        backdrop = backdrop,
        onPageSelected = coordinator.onPageSelected,
        onExpand = coordinator.onShowBottomBar,
    )
}
