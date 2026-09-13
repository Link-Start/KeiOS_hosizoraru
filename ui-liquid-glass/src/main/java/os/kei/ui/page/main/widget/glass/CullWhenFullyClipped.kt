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
 *
 * ## A card in the edge-stack pile is the one element whose layout rect does not say where it is
 *
 * The pile pins a card by translating it inside its glass layer, which sits *below* this node — so
 * `boundsInWindow` here reports the card's layout rect, which keeps scrolling up while the card is
 * drawn holding still at the stack line. Read as a visibility signal, that says "gone" for a plate the
 * reader is still looking at: a card is clipped away at `overshoot > stackLine + height`, whereas the
 * pile's own retirement only reaches `fade == 0` at `overshoot == extent`, and for every card under
 * about 400dp the first comes first. Measured against the real constants, 7-18dp of plate edge was
 * being cut at full opacity — the top edge of the receding plate, which is the one part of it the pile
 * exists to show.
 *
 * So a stacking card is culled on the pile's own verdict instead: it contributes nothing once it has
 * retired, and until then it is on screen whatever its layout rect says. Passing the slot is what
 * makes the two cases distinguishable — without it this node cannot tell a clipped card from a pinned
 * one, and there is no signal it could read that would.
 */
fun Modifier.cullWhenFullyClipped(edgeStack: AppEdgeStackSlot = AppEdgeStackSlot.Inert): Modifier =
    this then CullWhenFullyClippedElement(edgeStack.card)

private data class CullWhenFullyClippedElement(
    private val card: AppEdgeStackCard?,
) : ModifierNodeElement<CullWhenFullyClippedNode>() {
    override fun create(): CullWhenFullyClippedNode = CullWhenFullyClippedNode(card)

    override fun update(node: CullWhenFullyClippedNode) {
        node.card = card
    }
}

private class CullWhenFullyClippedNode(
    var card: AppEdgeStackCard?,
) : Modifier.Node(),
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
        val stacking = card
        // Snapshot reads, so a card that retires or rejoins the pile invalidates this draw on its own.
        if (stacking != null && stacking.stacked) {
            if (stacking.fade > 0f) drawContent()
            return
        }
        if (visible) drawContent()
    }
}
