package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun MainLoadedPager(
    state: MainLoadedPagerState,
    userScrollEnabled: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
    pageContent: @Composable (pageIndex: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = rememberDraggableState { deltaPx ->
                        state.dragBy(-deltaPx / pageWidthPx)
                    },
                    orientation = Orientation.Horizontal,
                    enabled = userScrollEnabled && state.pageCount > 1,
                    startDragImmediately = state.isScrollInProgress,
                    onDragStarted = { state.startUserScroll() },
                    onDragStopped = { velocityPxPerSecond ->
                        coroutineScope.launch {
                            state.settleAfterDrag(
                                velocityPagesPerSecond = -velocityPxPerSecond / pageWidthPx,
                                animationsEnabled = animationsEnabled
                            )
                        }
                    }
                )
        ) {
            repeat(state.pageCount) { pageIndex ->
                val semanticsModifier = if (pageIndex == state.accessibilityPage) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics { }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .loadedPagerPageOffset(pageIndex, state, pageWidthPx)
                        .drawLoadedPagerPage(pageIndex, state)
                        .then(semanticsModifier)
                ) {
                    pageContent(pageIndex)
                }
            }
        }
    }
}

private fun Modifier.loadedPagerPageOffset(
    pageIndex: Int,
    state: MainLoadedPagerState,
    pageWidthPx: Float
): Modifier = offset {
    IntOffset(
        x = ((pageIndex - state.pagePosition) * pageWidthPx).roundToInt(),
        y = 0
    )
}

private fun Modifier.drawLoadedPagerPage(
    pageIndex: Int,
    state: MainLoadedPagerState
): Modifier = drawWithContent {
    val drawDistance = if (state.isScrollInProgress) {
        MainLoadedPagerActiveDrawDistance
    } else {
        MainLoadedPagerSettledDrawDistance
    }
    val relativePosition = pageIndex - state.pagePosition
    if (abs(relativePosition) <= drawDistance) {
        val pageOffsetPx = relativePosition * size.width
        val clipLeft = max(0f, -pageOffsetPx)
        val clipRight = min(size.width, size.width - pageOffsetPx)
        if (clipRight > clipLeft) {
            clipRect(
                left = clipLeft,
                top = 0f,
                right = clipRight,
                bottom = size.height
            ) {
                this@drawWithContent.drawContent()
            }
        }
    }
}

private const val MainLoadedPagerSettledDrawDistance = 0.05f
private const val MainLoadedPagerActiveDrawDistance = 1.05f
