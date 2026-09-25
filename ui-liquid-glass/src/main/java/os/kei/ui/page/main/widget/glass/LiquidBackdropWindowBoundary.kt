@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.backdrops.emptyBackdrop
import os.kei.ui.page.main.widget.sheet.LocalSceneBackdrop

/**
 * Marks a secondary Compose window whose coordinates are independent from its parent window.
 *
 * A `LayerBackdrop` is coordinates-dependent, so Dialog and Popup content must not inherit a
 * producer from the activity window. Descendants can still create a producer inside this window
 * and provide it through [LocalLiquidParentBackdrop].
 */
internal val LocalLiquidBackdropWindowBoundary = staticCompositionLocalOf { false }

@Composable
internal fun LiquidBackdropWindowBoundary(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLiquidBackdropWindowBoundary provides true,
        LocalSceneBackdrop provides emptyBackdrop(),
        LocalLiquidParentBackdrop provides null,
        LocalLiquidParentBackdropOverridesFallback provides false,
        LocalLiquidDialogBackdrop provides null,
        content = content,
    )
}

/**
 * Prevents backdrop producers from a parent window from leaking into an independently positioned
 * Dialog or Popup window.
 */
@Composable
fun AppLiquidWindowBoundary(content: @Composable () -> Unit) {
    LiquidBackdropWindowBoundary(content = content)
}
