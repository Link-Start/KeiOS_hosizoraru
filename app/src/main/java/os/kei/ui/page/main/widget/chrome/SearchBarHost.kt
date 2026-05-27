@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.appMotionDpState
import os.kei.ui.page.main.widget.motion.appMotionFloatState

@Composable
fun SearchBarHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    animationLabelPrefix: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val searchBarHeightState =
        appMotionDpState(
            targetValue = if (visible) AppChromeTokens.searchBarHostHeight else 0.dp,
            durationMillis = AppMotionTokens.searchBarSlideMs,
            label = "${animationLabelPrefix}Height",
        )
    val searchBarAlphaState =
        appMotionFloatState(
            targetValue = if (visible) 1f else 0f,
            durationMillis = AppMotionTokens.searchBarFadeMs,
            label = "${animationLabelPrefix}Alpha",
        )
    val searchBarOffsetYState =
        appMotionDpState(
            targetValue = if (visible) 0.dp else (-12).dp,
            durationMillis = AppMotionTokens.searchBarSlideMs,
            label = "${animationLabelPrefix}Offset",
        )
    val searchBarHeightProvider = remember(searchBarHeightState) { { searchBarHeightState.value } }
    val searchBarAlphaProvider = remember(searchBarAlphaState) { { searchBarAlphaState.value } }
    val searchBarOffsetYProvider = remember(searchBarOffsetYState) { { searchBarOffsetYState.value } }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .searchBarHostAnimatedHeight(searchBarHeightProvider)
                .clipToBounds()
                .graphicsLayer {
                    alpha = searchBarAlphaProvider()
                    translationY = with(density) { searchBarOffsetYProvider().toPx() }
                },
        content = content,
    )
}

private fun Modifier.searchBarHostAnimatedHeight(height: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val heightPx = height().roundToPx().coerceAtLeast(0)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = heightPx,
                    maxHeight = heightPx,
                ),
            )
        layout(placeable.width, heightPx) {
            placeable.place(0, 0)
        }
    }
