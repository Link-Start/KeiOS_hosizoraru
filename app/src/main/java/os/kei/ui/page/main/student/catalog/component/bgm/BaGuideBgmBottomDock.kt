@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBar
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarItem
import os.kei.ui.page.main.widget.chrome.LiquidGlassBottomBarSelectionOptics
import os.kei.ui.page.main.widget.chrome.liquidGlassBottomBarItemContentColor
import os.kei.ui.page.main.widget.isAppInDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BaGuideBgmExpandedDock(
    tabs: List<BaGuideBgmDockTab>,
    selectedDockKey: String,
    selectedPositionProvider: (() -> Float?)?,
    interactionEnabled: Boolean,
    backdrop: Backdrop,
    onSelectedDockKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    val selectedIndex =
        tabs
            .indexOfFirst { it.key == selectedDockKey }
            .coerceAtLeast(0)
    val isDark = isAppInDarkTheme()
    val selectionOptics = remember(isDark) { baGuideBgmDockSelectionOptics(isDark) }
    val tabContent: @Composable RowScope.() -> Unit = {
        tabs.forEachIndexed { index, tab ->
            val contentColor = liquidGlassBottomBarItemContentColor(index)
            LiquidGlassBottomBarItem(
                selected = selectedIndex == index,
                tabIndex = index,
                label = tab.label,
                onClick = { onSelectedDockKeyChange(tab.key) },
                modifier = tab.testTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = tab.label,
                    color = contentColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    LiquidGlassBottomBar(
        modifier = modifier,
        selectedIndex = selectedIndex,
        selectedPositionProvider = selectedPositionProvider,
        onSelected = { index ->
            tabs
                .getOrNull(index)
                ?.key
                ?.let(onSelectedDockKeyChange)
        },
        backdrop = backdrop,
        tabsCount = tabs.size,
        interactionEnabled = interactionEnabled,
        expandToMaxWidth = true,
        selectionOptics = selectionOptics,
        content = tabContent,
    )
}

internal fun baGuideBgmDockSelectionOptics(isDark: Boolean): LiquidGlassBottomBarSelectionOptics =
    LiquidGlassBottomBarSelectionOptics(
        overlayColor = Color.White.copy(alpha = if (isDark) 0.03f else 0.08f),
        rimColor = Color.White.copy(alpha = if (isDark) 0.16f else 0.38f),
    )
