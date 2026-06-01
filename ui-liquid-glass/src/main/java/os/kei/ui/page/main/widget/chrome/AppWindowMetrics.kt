package os.kei.ui.page.main.widget.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

@Composable
fun appWindowSizeDp(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) {
        DpSize(
            width = containerSize.width.toDp(),
            height = containerSize.height.toDp()
        )
    }
}

@Composable
fun appWindowWidthDp(): Dp = appWindowSizeDp().width

@Composable
fun appWindowHeightDp(): Dp = appWindowSizeDp().height

@Composable
fun appWindowHeightPx(): Int = LocalWindowInfo.current.containerSize.height.coerceAtLeast(1)
