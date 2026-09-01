package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

@Composable
fun BoxScope.AppTopEndActionBarOverlay(
    modifier: Modifier = Modifier,
    topSpacing: Dp = AppChromeTokens.topBarChromeTopPadding,
    endSpacing: Dp? = null,
    content: @Composable () -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    // Follows the content column in. Pinned to the true window edge, these actions would sit a gutter's width
    // away from the page they act on — on the Pad AVD in landscape that is 280dp of empty panel between a
    // list and its own toolbar. Zero on phones.
    val sideGutter = appTopBarChromeGutter()
    // Null means "the row's own margin", which grows on a large window. A caller that passes a value keeps it.
    val resolvedEndSpacing = endSpacing ?: appTopBarEdgePadding()
    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(
                top = safeDrawingPadding.calculateTopPadding() + topSpacing,
                end = safeDrawingPadding.calculateEndPadding(layoutDirection) + resolvedEndSpacing + sideGutter
            )
    ) {
        content()
    }
}
