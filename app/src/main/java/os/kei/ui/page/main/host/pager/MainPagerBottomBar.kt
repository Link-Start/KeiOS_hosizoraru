package os.kei.ui.page.main.host.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun MainPagerBottomBar(
    visible: Boolean,
    navigationBarBottom: Dp,
    tabs: List<BottomPage>,
    selectedPageIndex: Int,
    selectedPagePosition: Float?,
    backdrop: LayerBackdrop,
    liquidBottomBarEnabled: Boolean,
    onPageSelected: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val bottomBarTabs: @Composable RowScope.() -> Unit = {
                tabs.forEachIndexed { index, page ->
                    val selected = selectedPageIndex == index
                    val tabColor = liquidGlassBottomBarItemContentColor(index)
                    LiquidGlassBottomBarItem(
                        selected = selected,
                        tabIndex = index,
                        onClick = { onPageSelected(index) },
                        modifier = if (page == BottomPage.GitHub) {
                            Modifier.testTag(KeiOsTestTags.MainBottomTabGitHub)
                        } else {
                            Modifier
                        }
                    ) {
                        val tabIconModifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = page.iconScale
                                scaleY = page.iconScale
                            }
                        if (page.iconRes != null) {
                            Icon(
                                painter = painterResource(id = page.iconRes),
                                contentDescription = page.label,
                                tint = if (page.keepOriginalColors) Color.Unspecified else tabColor,
                                modifier = tabIconModifier
                            )
                        } else {
                            page.icon?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = page.label,
                                    tint = tabColor,
                                    modifier = tabIconModifier
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val horizontalMargin = when {
                    maxWidth < 360.dp -> 8.dp
                    maxWidth < 600.dp -> 12.dp
                    else -> 24.dp
                }
                val availableWidth = maxWidth - horizontalMargin * 2
                val minBarWidth = when {
                    tabs.size <= 2 -> 220.dp
                    tabs.size == 3 -> 280.dp
                    else -> 320.dp
                }
                val preferredWidth = (76.dp * tabs.size + 8.dp).coerceAtLeast(minBarWidth)
                val maxBarWidth = if (maxWidth < 600.dp) availableWidth else 460.dp
                val bottomBarWidth = preferredWidth.coerceAtMost(maxBarWidth)
                val bottomBarModifier = Modifier
                    .width(bottomBarWidth)
                    .widthIn(max = availableWidth)
                    .padding(
                        bottom = if (navigationBarBottom != 0.dp) {
                            8.dp + navigationBarBottom
                        } else {
                            36.dp
                        }
                    )

                LiquidGlassBottomBar(
                    modifier = bottomBarModifier,
                    selectedIndex = selectedPageIndex,
                    selectedPosition = selectedPagePosition,
                    onSelected = onPageSelected,
                    backdrop = backdrop,
                    tabsCount = tabs.size,
                    isLiquidEffectEnabled = liquidBottomBarEnabled,
                    expandToMaxWidth = true,
                    content = bottomBarTabs
                )
            }
        }
    }
}
