@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomBarHost
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomTabItem
import os.kei.ui.page.main.widget.chrome.MiuixFloatingBottomTabStrip
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun MainMiuixBottomBar(
    visible: Boolean,
    navigationBarBottom: Dp,
    tabs: List<BottomPage>,
    selectedPageIndex: Int,
    selectedPagePositionProvider: (() -> Float?)? = null,
    backdrop: LayerBackdrop,
    onPageSelected: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MiuixFloatingBottomBarHost(navigationBarBottom = navigationBarBottom) {
                MiuixFloatingBottomTabStrip(
                    itemCount = tabs.size,
                    selectedIndex = selectedPageIndex,
                    selectedPositionProvider = selectedPagePositionProvider,
                    onSelected = onPageSelected,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                ) { index, selected, contentColor ->
                    tabs.getOrNull(index)?.let { page ->
                        MainMiuixBottomBarItem(
                            page = page,
                            selected = selected,
                            contentColor = contentColor,
                            onClick = { onPageSelected(index) },
                            modifier = Modifier.testTag(page.bottomTabTestTag()),
                        )
                    }
                }
            }
        }
    }
}

private fun BottomPage.bottomTabTestTag(): String =
    when (this) {
        BottomPage.Home -> KeiOsTestTags.MainBottomTabHome
        BottomPage.Os -> KeiOsTestTags.MainBottomTabOs
        BottomPage.Mcp -> KeiOsTestTags.MainBottomTabMcp
        BottomPage.GitHub -> KeiOsTestTags.MainBottomTabGitHub
        BottomPage.Ba -> KeiOsTestTags.MainBottomTabBa
    }

@Composable
private fun RowScope.MainMiuixBottomBarItem(
    page: BottomPage,
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixFloatingBottomTabItem(
        selected = selected,
        label = page.label,
        color = contentColor,
        onClick = onClick,
        modifier = modifier,
    ) { contentColor, iconModifier ->
        val tabIconModifier =
            iconModifier
                .graphicsLayer {
                    scaleX = page.iconScale
                    scaleY = page.iconScale
                }
        if (page.iconRes != null) {
            Icon(
                painter = painterResource(id = page.iconRes),
                contentDescription = page.label,
                tint = if (page.keepOriginalColors && selected) Color.Unspecified else contentColor,
                modifier = tabIconModifier,
            )
        } else {
            page.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = page.label,
                    tint = contentColor,
                    modifier = tabIconModifier,
                )
            }
        }
    }
}
