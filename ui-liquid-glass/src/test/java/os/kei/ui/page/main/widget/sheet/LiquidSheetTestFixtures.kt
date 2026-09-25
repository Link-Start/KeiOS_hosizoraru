@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun LiquidSheetTestTheme(content: @Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
        content()
    }
}

/** [count] full-width gray rows of [height], the filler content every sheet test scrolls or measures. */
@Composable
internal fun GrayRows(count: Int, height: Dp) {
    repeat(count) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(Color.Gray),
        )
    }
}

internal fun ComposeContentTestRule.rootHeight(): Dp {
    val heightPx =
        onAllNodes(isRoot())
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.height }
    return with(density) { heightPx.toDp() }
}

internal fun ComposeContentTestRule.nodeBounds(tag: String): DpRect {
    val bounds: Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    return with(density) {
        DpRect(
            left = bounds.left.toDp(),
            top = bounds.top.toDp(),
            right = bounds.right.toDp(),
            bottom = bounds.bottom.toDp(),
        )
    }
}
