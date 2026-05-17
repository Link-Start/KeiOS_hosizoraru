@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.motion.appFloatingEnter
import os.kei.ui.page.main.widget.motion.appFloatingExit
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainMiuixBottomBar(
    visible: Boolean,
    tabs: List<BottomPage>,
    selectedPageIndex: Int,
    onPageSelected: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible,
            enter = appFloatingEnter(),
            exit = appFloatingExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingNavigationBar(
                color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                cornerRadius = 28.dp,
                horizontalOutSidePadding = 24.dp,
                shadowElevation = 1.dp,
                showDivider = true,
            ) {
                tabs.forEachIndexed { index, page ->
                    MainMiuixBottomBarItem(
                        page = page,
                        selected = selectedPageIndex == index,
                        onClick = { onPageSelected(index) },
                        modifier =
                            if (page == BottomPage.GitHub) {
                                Modifier.testTag(KeiOsTestTags.MainBottomTabGitHub)
                            } else {
                                Modifier
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMiuixBottomBarItem(
    page: BottomPage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MiuixTheme.colorScheme.onSurfaceContainer
    val unselectedColor = selectedColor.copy(alpha = 0.42f)
    val contentColor = if (selected) selectedColor else unselectedColor
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .width(48.dp)
                .height(56.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    indication = null,
                    interactionSource = interactionSource,
                ),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tabIconModifier =
            Modifier
                .size(22.dp)
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
        Text(
            text = page.label,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
