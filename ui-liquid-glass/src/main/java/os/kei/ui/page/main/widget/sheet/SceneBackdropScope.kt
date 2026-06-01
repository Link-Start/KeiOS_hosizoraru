@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The scene-level backdrop that on-top glass surfaces (notably [LiquidGlassBottomSheet]) sample.
 *
 * Per the kyant.backdrop tutorial: a glass overlay needs a real `LayerBackdrop` in its draw path
 * with the entire app content rendered behind it. We wire that backdrop once at the activity
 * root and expose it through this [staticCompositionLocalOf] so any composable lower in the tree
 * can reach it without prop-drilling. The default value is the library's [emptyBackdrop] no-op
 * so consumers that want graceful degradation outside of the activity tree (previews, tests,
 * isolated harnesses) don't crash — they just render without backdrop sampling.
 */
val LocalSceneBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/**
 * Hosts the scene backdrop. Wraps [content] with a `Modifier.layerBackdrop(...)` so the entire
 * subtree (the actual MainScreen) becomes the source any `Modifier.drawBackdrop(...)` consumer
 * samples. The backdrop pre-paints [backgroundColor] underneath the content so glass surfaces
 * see a stable opaque background instead of a transparent compose surface.
 *
 * Usage at the activity root:
 *
 * ```kotlin
 * SceneBackdropHost(backgroundColor = MiuixTheme.colorScheme.background) {
 *     MainScreen(...)
 * }
 * ```
 */
@Composable
fun SceneBackdropHost(
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    // Pre-multiply onto black so even a translucent theme background still produces an opaque
    // source for the blur. The compose Color stays in the graph; the blend happens inside the
    // backdrop drawer. compositeOver keeps theme transparency math correct.
    val solidBackground = backgroundColor.compositeOver(Color.Black)
    val sceneBackdrop = rememberLayerBackdrop {
        drawRect(solidBackground)
        drawContent()
    }
    CompositionLocalProvider(LocalSceneBackdrop provides sceneBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(sceneBackdrop),
        ) {
            content()
        }
    }
}
