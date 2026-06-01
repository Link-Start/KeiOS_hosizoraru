@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.widget.chrome.AnimatedCompactBottomBar
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.CompactBottomBarDock
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaStudentGuideBottomBar(
    visible: Boolean,
    navigationBarBottom: Dp,
    bottomTabs: List<GuideBottomTab>,
    selectedPage: Int,
    selectedPagePosition: Float,
    selectedPagePositionProvider: (() -> Float?)? = null,
    selectedPageProvider: () -> Int,
    backdrop: LayerBackdrop,
    isLiquidEffectEnabled: Boolean,
    onSelectTab: (Int) -> Unit,
    onExpand: () -> Unit,
) {
    AnimatedCompactBottomBar(
        expanded = visible,
        expandedContent = { motionModifier ->
            Box(modifier = motionModifier.align(Alignment.BottomCenter)) {
                val bottomBarModifier =
                    Modifier
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = if (navigationBarBottom != 0.dp) 8.dp + navigationBarBottom else 36.dp,
                        )
                val bottomBarTabs: @Composable RowScope.() -> Unit = {
                    bottomTabs.forEachIndexed { index, tab ->
                        val selected = selectedPage == index
                        val tabColor = liquidGlassBottomBarItemContentColor(index)
                        val tabLabel = stringResource(tab.labelRes)
                        val tabContent: @Composable ColumnScope.() -> Unit = {
                            val tabIconModifier =
                                Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = 1f
                                        scaleY = 1f
                                    }
                            if (tab.localLogoRes != null) {
                                val useThemeTintForLocalLogo =
                                    tab == GuideBottomTab.Skills ||
                                        tab == GuideBottomTab.Profile ||
                                        tab == GuideBottomTab.Simulate
                                Icon(
                                    painter = painterResource(id = tab.localLogoRes),
                                    contentDescription = tabLabel,
                                    tint = if (useThemeTintForLocalLogo) tabColor else Color.Unspecified,
                                    modifier = tabIconModifier,
                                )
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tabLabel,
                                    tint = tabColor,
                                    modifier = tabIconModifier,
                                )
                            }
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = tabColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                        LiquidGlassBottomBarItem(
                            selected = selected,
                            tabIndex = index,
                            onClick = { onSelectTab(index) },
                            modifier = Modifier,
                            content = tabContent,
                        )
                    }
                }

                LiquidGlassBottomBar(
                    modifier = bottomBarModifier,
                    selectedIndex = selectedPage,
                    selectedPosition = selectedPagePosition,
                    selectedPositionProvider = selectedPagePositionProvider,
                    onSelected = { index ->
                        if (index != selectedPageProvider()) {
                            onSelectTab(index)
                        }
                    },
                    backdrop = backdrop,
                    tabsCount = bottomTabs.size,
                    isLiquidEffectEnabled = isLiquidEffectEnabled,
                    expandToMaxWidth = true,
                    content = bottomBarTabs,
                )
            }
        },
        compactContent = { motionModifier ->
            Box(
                modifier =
                    motionModifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = AppChromeTokens.pageHorizontalPadding,
                            bottom = if (navigationBarBottom != 0.dp) 8.dp + navigationBarBottom else 36.dp,
                        ),
            ) {
                val tab = bottomTabs.getOrElse(selectedPage) { bottomTabs.first() }
                val tabLabel = stringResource(tab.labelRes)
                CompactBottomBarDock(
                    backdrop = backdrop,
                    onClick = onExpand,
                ) {
                    if (tab.localLogoRes != null) {
                        Icon(
                            painter = painterResource(id = tab.localLogoRes),
                            contentDescription = tabLabel,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(27.dp),
                        )
                    } else {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tabLabel,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
            }
        },
    )
}
