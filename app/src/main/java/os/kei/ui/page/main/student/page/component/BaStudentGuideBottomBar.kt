@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomBarHost
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomTabItem
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomTabStrip
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BaStudentGuideBottomBar(
    visible: Boolean,
    navigationBarBottom: Dp,
    bottomTabs: List<GuideBottomTab>,
    selectedPage: Int,
    selectedPagePosition: Float,
    selectedPageProvider: () -> Int,
    backdrop: LayerBackdrop,
    isLiquidEffectEnabled: Boolean,
    miuixMainNavigationEnabled: Boolean,
    onSelectTab: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (miuixMainNavigationEnabled) {
                MiuixFloatingBottomBarHost(navigationBarBottom = navigationBarBottom) {
                    MiuixFloatingBottomTabStrip(
                        itemCount = bottomTabs.size,
                        selectedIndex = selectedPage,
                        onSelected = { index ->
                            if (index != selectedPageProvider()) {
                                onSelectTab(index)
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    ) { index, selected, contentColor ->
                        bottomTabs.getOrNull(index)?.let { tab ->
                            BaStudentGuideMiuixBottomBarItem(
                                tab = tab,
                                selected = selected,
                                contentColor = contentColor,
                                onClick = {
                                    if (index != selectedPageProvider()) {
                                        onSelectTab(index)
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
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
        }
    }
}

@Composable
private fun RowScope.BaStudentGuideMiuixBottomBarItem(
    tab: GuideBottomTab,
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val tabLabel = stringResource(tab.labelRes)
    MiuixFloatingBottomTabItem(
        selected = selected,
        label = tabLabel,
        color = contentColor,
        onClick = onClick,
    ) { contentColor, iconModifier ->
        if (tab.localLogoRes != null) {
            val useThemeTintForLocalLogo =
                tab == GuideBottomTab.Skills ||
                    tab == GuideBottomTab.Profile ||
                    tab == GuideBottomTab.Simulate
            Icon(
                painter = painterResource(id = tab.localLogoRes),
                contentDescription = tabLabel,
                tint = if (useThemeTintForLocalLogo) contentColor else Color.Unspecified,
                modifier = iconModifier,
            )
        } else {
            Icon(
                imageVector = tab.icon,
                contentDescription = tabLabel,
                tint = contentColor,
                modifier = iconModifier,
            )
        }
    }
}
