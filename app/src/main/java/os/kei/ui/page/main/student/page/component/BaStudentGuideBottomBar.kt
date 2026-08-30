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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.widget.chrome.AnimatedCompactBottomBar
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.CompactBottomBarDock
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.testing.KeiOsTestTags
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
    backdrop: Backdrop,
    isLiquidEffectEnabled: Boolean,
    onSelectTab: (Int) -> Unit,
    onExpand: () -> Unit,
) {
    val showTabLabels = guideBottomBarShowsLabels(bottomTabs.size, LocalDensity.current.fontScale)
    AnimatedCompactBottomBar(
        expanded = visible,
        expandedContent = { motionModifier, interactionEnabled ->
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
                            val tabIconModifier = Modifier.size(20.dp)
                            if (tab.localLogoRes != null) {
                                val useThemeTintForLocalLogo =
                                    tab == GuideBottomTab.Skills ||
                                        tab == GuideBottomTab.Profile ||
                                        tab == GuideBottomTab.Simulate
                                Icon(
                                    painter = painterResource(id = tab.localLogoRes),
                                    contentDescription = null,
                                    tint = if (useThemeTintForLocalLogo) tabColor else Color.Unspecified,
                                    modifier = tabIconModifier,
                                )
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = tabColor,
                                    modifier = tabIconModifier,
                                )
                            }
                            if (showTabLabels) {
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
                        }
                        LiquidGlassBottomBarItem(
                            selected = selected,
                            tabIndex = index,
                            label = tabLabel,
                            onClick = { onSelectTab(index) },
                            modifier = Modifier.testTag(guideBottomTabTestTag(tab)),
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
                    interactionEnabled = interactionEnabled,
                    content = bottomBarTabs,
                )
            }
        },
        compactContent = { motionModifier, interactionEnabled ->
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
                    enabled = interactionEnabled,
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

internal fun guideBottomBarShowsLabels(
    tabCount: Int,
    fontScale: Float,
): Boolean = tabCount <= 5 && fontScale <= 1.2f

/**
 * The tag a baseline-profile journey taps to reach one guide tab.
 *
 * Exhaustive over the enum on purpose: a tab added later fails to compile here rather than quietly
 * becoming the one tab no journey can reach.
 */
private fun guideBottomTabTestTag(tab: GuideBottomTab): String =
    when (tab) {
        GuideBottomTab.Archive -> KeiOsTestTags.BaStudentGuideTabArchive
        GuideBottomTab.Skills -> KeiOsTestTags.BaStudentGuideTabSkills
        GuideBottomTab.Profile -> KeiOsTestTags.BaStudentGuideTabProfile
        GuideBottomTab.Voice -> KeiOsTestTags.BaStudentGuideTabVoice
        GuideBottomTab.Gallery -> KeiOsTestTags.BaStudentGuideTabGallery
        GuideBottomTab.Simulate -> KeiOsTestTags.BaStudentGuideTabSimulate
    }
