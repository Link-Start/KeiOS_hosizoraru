package os.kei.ui.testing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Root bounds of the unmerged node showing [text]. */
internal fun ComposeTestRule.boundsOf(text: String): Rect =
    onNodeWithText(text, useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot

/** How many rows [labels] wrap onto, counting centres within 2dp of each other as one row. */
internal fun ComposeTestRule.distinctRowCount(labels: List<String>): Int {
    val tolerancePx = with(density) { 2.dp.toPx() }
    return labels
        .map { label -> boundsOf(label).center.y }
        .fold(mutableListOf<Float>()) { rows, centerY ->
            if (rows.none { rowY -> abs(rowY - centerY) <= tolerancePx }) rows += centerY
            rows
        }.size
}
