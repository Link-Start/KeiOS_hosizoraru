package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController

@Stable
internal class BaGuideBgmBottomChromeScrollState(
    private val scrollThresholdPx: Float
) : NestedScrollConnection {
    var isCompact by mutableStateOf(false)
        private set

    private val visibilityController = ScrollChromeVisibilityController(scrollThresholdPx)
    private var canScrollBackward = false
    private var canScrollForward = false

    fun expand() {
        visibilityController.showNow(visible = !isCompact, onVisibleChange = ::updateVisible)
    }

    fun compact() {
        isCompact = true
        visibilityController.reset()
    }

    fun expandForStaticContent(
        canScrollBackward: Boolean,
        canScrollForward: Boolean
    ) {
        this.canScrollBackward = canScrollBackward
        this.canScrollForward = canScrollForward
        visibilityController.showForStaticContent(
            visible = !isCompact,
            canScrollBackward = canScrollBackward,
            canScrollForward = canScrollForward,
            onVisibleChange = ::updateVisible,
        )
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        visibilityController.updateWithinScrollBounds(
            deltaY = consumed.y,
            visible = !isCompact,
            canScrollBackward = canScrollBackward,
            canScrollForward = canScrollForward,
            onVisibleChange = ::updateVisible,
        )
        return Offset.Zero
    }

    private fun updateVisible(visible: Boolean) {
        isCompact = !visible
    }
}

@Composable
internal fun rememberBaGuideBgmBottomChromeScrollState(
    scrollThreshold: Dp = 22.dp
): BaGuideBgmBottomChromeScrollState = with(LocalDensity.current) {
    val thresholdPx = scrollThreshold.toPx()
    remember(thresholdPx) { BaGuideBgmBottomChromeScrollState(thresholdPx) }
}
