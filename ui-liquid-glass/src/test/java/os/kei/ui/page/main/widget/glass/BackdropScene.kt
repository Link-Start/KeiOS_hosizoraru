package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/** A [size] box whose white fill is recorded into [backdrop], with [content] drawn over it. */
@Composable
internal fun BackdropScene(
    backdrop: LayerBackdrop,
    size: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.White)
                    .layerBackdrop(backdrop),
        )
        content()
    }
}
