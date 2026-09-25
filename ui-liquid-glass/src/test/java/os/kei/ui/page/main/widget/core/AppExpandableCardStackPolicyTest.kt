package os.kei.ui.page.main.widget.core

import kotlin.test.assertEquals
import org.junit.Test

class AppExpandableCardStackPolicyTest {
    @Test
    fun `only a card resting collapsed participates in the edge stack`() {
        data class Case(val label: String, val currentState: Boolean, val targetState: Boolean, val expected: Boolean)
        listOf(
            Case("collapsed and resting", currentState = false, targetState = false, expected = true),
            Case("expanding", currentState = false, targetState = true, expected = false),
            Case("expanded", currentState = true, targetState = true, expected = false),
            // Rejoins only after the body has finished leaving.
            Case("collapsing", currentState = true, targetState = false, expected = false),
        ).forEach { case ->
            assertEquals(
                case.expected,
                shouldApplyEdgeStackToExpandableCard(
                    currentState = case.currentState,
                    targetState = case.targetState,
                ),
                case.label,
            )
        }
    }
}
