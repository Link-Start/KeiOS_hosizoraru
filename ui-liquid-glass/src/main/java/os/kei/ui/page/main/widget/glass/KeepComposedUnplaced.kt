package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.unit.Constraints

/**
 * Keeps content composed while it is not shown: measured and placed when [visible], otherwise laid out at
 * zero size, never placed and with its semantics cleared, so it is not drawn, not hit-tested and not read
 * by accessibility services.
 *
 * For glass chrome that a transition swaps while the user scrolls. The bottom bar composed its compact button
 * each time it hid and the whole glass bar again each time it came back, and the floating docks did the same
 * with their compact and full forms. That composition landed on the frame the scroll started: on the phone,
 * the worst UI-thread frame per pass fell from 13-23ms to 6-10ms (BA) and 12-24ms to 9-13ms (OS) once both
 * were kept composed (docs/planning/liquid-glass-clip-and-resolution.md, section 4).
 *
 * One node whether shown or not, so toggling keeps the modifier chain — and the glass modifier nodes after it —
 * intact. Unplaced content alone is not enough: it stays in the semantics tree with a stale position.
 */
fun Modifier.keepComposedUnplaced(visible: Boolean): Modifier = this.then(KeepComposedElement(visible))

private data class KeepComposedElement(
    val visible: Boolean,
) : ModifierNodeElement<KeepComposedNode>() {
    override fun create() = KeepComposedNode(visible)

    override fun update(node: KeepComposedNode) {
        if (node.visible == visible) return
        node.visible = visible
        node.invalidateMeasurement()
        node.invalidateSemantics()
    }
}

private class KeepComposedNode(
    var visible: Boolean,
) : Modifier.Node(),
    LayoutModifierNode,
    SemanticsModifierNode {
    override val shouldClearDescendantSemantics: Boolean
        get() = !visible

    override fun SemanticsPropertyReceiver.applySemantics() = Unit

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        if (!visible) return layout(0, 0) {}
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
