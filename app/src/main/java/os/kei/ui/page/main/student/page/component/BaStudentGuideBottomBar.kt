@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import os.kei.ui.page.main.widget.chrome.appContentWidth
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.chrome.tabbedPageSizedTabMinWidth
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The bar's own margin from the page edges, which is also what its width budget excludes. */
private val GuideBottomBarOuterPadding: Dp = 24.dp

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
    val barOuterPadding = GuideBottomBarOuterPadding
    val availableBarWidth = (appContentWidth() - barOuterPadding * 2f).coerceAtLeast(0.dp)
    val metrics = guideBottomBarMetrics(availableWidth = availableBarWidth, tabCount = bottomTabs.size)
    val showTabLabels =
        guideBottomBarShowsLabels(
            perTabWidth = metrics.perTabWidth,
            fontScale = LocalDensity.current.fontScale,
        )
    AnimatedCompactBottomBar(
        expanded = visible,
        expandedContent = { motionModifier, interactionEnabled ->
            Box(modifier = motionModifier.align(Alignment.BottomCenter)) {
                val bottomBarModifier =
                    Modifier
                        .padding(
                            start = barOuterPadding,
                            end = barOuterPadding,
                            bottom = if (navigationBarBottom != 0.dp) 8.dp + navigationBarBottom else 36.dp,
                        ).then(
                            // A cap the bar may stay under rather than a width it must meet, so its own
                            // IntrinsicSize.Min can measure it against the tabs.
                            if (metrics.sizedToTabs) Modifier.widthIn(max = availableBarWidth) else Modifier,
                        )
                val bottomBarTabs: @Composable RowScope.() -> Unit = {
                    bottomTabs.forEachIndexed { index, tab ->
                        val selected = selectedPage == index
                        val tabColor = liquidGlassBottomBarItemContentColor(index)
                        val tabLabel = stringResource(tab.labelRes)
                        val tabContent: @Composable ColumnScope.() -> Unit = {
                            val tabIconModifier = Modifier.size(20.dp)
                            if (tab.localLogoRes != null) {
                                Icon(
                                    painter = painterResource(id = tab.localLogoRes),
                                    contentDescription = null,
                                    tint = if (tab.tintsLocalLogo()) tabColor else Color.Unspecified,
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
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = metrics.perTabWidth)
                                    .testTag(guideBottomTabTestTag(tab)),
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
                    expandToMaxWidth = !metrics.sizedToTabs,
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

/**
 * Narrowest a tab can be and still carry its label at 11sp.
 *
 * 68dp, which is what a filled bar gives five tabs on a phone -- the case that already showed labels
 * and has to keep them. Six tabs on that phone get 58dp and stay iconic; six on a tablet get the
 * derived rhythm below and gain labels, which is the point of measuring per tab rather than counting
 * them. The old rule was `tabCount <= 5`, and a count cannot tell a 360dp phone from a 1280dp panel.
 */
internal val GuideBottomBarLabelMinTabWidth: Dp = 68.dp

/** How wide the bar ends up and what each tab gets, which is the same decision twice over. */
internal data class GuideBottomBarMetrics(
    /** True while the bar sizes itself to its tabs rather than taking the width it is offered. */
    val sizedToTabs: Boolean,
    val perTabWidth: Dp,
)

/**
 * Resolves the bar's shape for the width it has.
 *
 * Filling is right when the tabs would not fit any other way, which is every phone: six tabs across
 * a 350dp bar is the only arrangement available. Given room, the bar takes the same per-tab width a
 * *filled* bar settles on -- see [tabbedPageSizedTabMinWidth], which derives it from the window so
 * one number does not read as cramped on a compact phone and lost on a panel. On the Pad that turns
 * a 1204dp strip of six icons into a ~728dp pill of six labelled tabs.
 */
internal fun guideBottomBarMetrics(
    availableWidth: Dp,
    tabCount: Int,
): GuideBottomBarMetrics {
    val tabs = tabCount.coerceAtLeast(1)
    val barPadding = AppChromeTokens.floatingBottomBarHorizontalPadding * 2f
    val sizedTab = tabbedPageSizedTabMinWidth(availableWidth = availableWidth)
    val sizedBarWidth = sizedTab * tabs.toFloat() + barPadding
    return if (sizedBarWidth <= availableWidth) {
        GuideBottomBarMetrics(sizedToTabs = true, perTabWidth = sizedTab)
    } else {
        GuideBottomBarMetrics(
            sizedToTabs = false,
            perTabWidth = ((availableWidth - barPadding) / tabs.toFloat()).coerceAtLeast(0.dp),
        )
    }
}

internal fun guideBottomBarShowsLabels(
    perTabWidth: Dp,
    fontScale: Float,
): Boolean = perTabWidth >= GuideBottomBarLabelMinTabWidth && fontScale <= 1.2f

/**
 * The tag a baseline-profile journey taps to reach one guide tab.
 *
 * Exhaustive over the enum on purpose: a tab added later fails to compile here rather than quietly
 * becoming the one tab no journey can reach.
 */
/**
 * Whether a tab's bundled logo takes the theme tint rather than its own colours.
 *
 * Three of the six are line icons drawn to be tinted; the rest are artwork that would be flattened to a
 * silhouette by a tint. Shared with the sidebar, which draws the same six.
 */
internal fun GuideBottomTab.tintsLocalLogo(): Boolean =
    this == GuideBottomTab.Skills ||
        this == GuideBottomTab.Profile ||
        this == GuideBottomTab.Simulate

/** The tag on one row of the guide's sidebar, mirroring [guideBottomTabTestTag]. */
internal fun guideSidebarRowTestTag(tab: GuideBottomTab): String = "${guideBottomTabTestTag(tab)}_sidebar_row"

internal fun guideBottomTabTestTag(tab: GuideBottomTab): String =
    when (tab) {
        GuideBottomTab.Archive -> KeiOsTestTags.BaStudentGuideTabArchive
        GuideBottomTab.Skills -> KeiOsTestTags.BaStudentGuideTabSkills
        GuideBottomTab.Profile -> KeiOsTestTags.BaStudentGuideTabProfile
        GuideBottomTab.Voice -> KeiOsTestTags.BaStudentGuideTabVoice
        GuideBottomTab.Gallery -> KeiOsTestTags.BaStudentGuideTabGallery
        GuideBottomTab.Simulate -> KeiOsTestTags.BaStudentGuideTabSimulate
    }
