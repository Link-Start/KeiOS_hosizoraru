package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

/**
 * Skips drawing this element entirely while it is scrolled fully out of its clip.
 *
 * Compose clips an off-screen child but still draws it, and for a glass surface "drawn" means an
 * offscreen layer recorded, rasterized and uploaded — every frame, for something no pixel of which
 * reaches the screen. Measured on the API 37 AVD: a long sheet's content glass costs ~23ms of its
 * ~38ms RenderThread, and its `sync` stage — layer upload — is 9.3ms against 0.3ms with content
 * glass off. Most of that is cards the user cannot see.
 *
 * Skipping the draw of a fully clipped element is visually identical by construction: a clipped
 * element contributes nothing. This is the culling that `LazyColumn` gives by not composing, made
 * available to the eager columns that most sheets still use, without restructuring them.
 *
 * `boundsInWindow` returns the *clipped* rectangle, so a fully clipped element reports a zero-area
 * rect. That is the signal, and it is read from layout rather than guessed from a scroll offset, so
 * it stays correct under nesting, translation and IME insets alike.
 */
fun Modifier.cullWhenFullyClipped(): Modifier = this then CullWhenFullyClippedElement

private object CullWhenFullyClippedElement : ModifierNodeElement<CullWhenFullyClippedNode>() {
    override fun create(): CullWhenFullyClippedNode = CullWhenFullyClippedNode()

    override fun update(node: CullWhenFullyClippedNode) = Unit

    override fun hashCode(): Int = "cullWhenFullyClipped".hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private class CullWhenFullyClippedNode :
    Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {
    private var visible = true

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsInWindow()
        val nowVisible = bounds.width > 0f && bounds.height > 0f
        if (nowVisible != visible) {
            visible = nowVisible
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (visible) drawContent()
    }
}
