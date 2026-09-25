package os.kei.ui.page.main.widget.dialog

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the parts of Apple's alert and action-sheet guidance that are encoded rather than left to
 * callers, so they cannot drift back out.
 */
class LiquidPresentationActionTest {
    private fun action(
        label: String,
        role: LiquidActionRole,
    ) = LiquidPresentationAction(label = label, onClick = {}, role = role)

    /**
     * "Make destructive choices visually prominent ... place these buttons at the top of the action
     * sheet where they tend to be most noticeable" and "place the Cancel button at the bottom".
     */
    @Test
    fun actionSheetPutsDestructiveFirstAndCancelLast() {
        val ordered = liquidActionSheetOrder(
            listOf(
                action("Cancel", LiquidActionRole.Cancel),
                action("Save Draft", LiquidActionRole.Default),
                action("Delete Draft", LiquidActionRole.Destructive),
                action("Duplicate", LiquidActionRole.Default),
            ),
        )

        assertEquals(
            listOf("Delete Draft", "Save Draft", "Duplicate", "Cancel"),
            ordered.map { it.label },
        )
    }

    @Test
    fun actionSheetOrderingIsStableWithinEachRole() {
        val ordered = liquidActionSheetOrder(
            listOf(
                action("First", LiquidActionRole.Default),
                action("Second", LiquidActionRole.Default),
                action("Third", LiquidActionRole.Default),
            ),
        )
        assertEquals(listOf("First", "Second", "Third"), ordered.map { it.label })
    }

    /**
     * "Place the button people are most likely to choose on the trailing side in a row of buttons."
     */
    @Test
    fun alertRowPutsTheExpectedChoiceOnTheTrailingSide() {
        val ordered = liquidAlertRowOrder(
            listOf(
                action("Erase", LiquidActionRole.Primary),
                action("Cancel", LiquidActionRole.Cancel),
            ),
        )
        assertEquals(listOf("Cancel", "Erase"), ordered.map { it.label })
    }

    @Test
    fun alertRowKeepsDestructiveAheadOfTheEmphasisedChoice() {
        val ordered = liquidAlertRowOrder(
            listOf(
                action("Keep", LiquidActionRole.Primary),
                action("Delete", LiquidActionRole.Destructive),
                action("Cancel", LiquidActionRole.Cancel),
            ),
        )
        assertEquals(listOf("Cancel", "Delete", "Keep"), ordered.map { it.label })
    }

    /** Two buttons fit a row; three cannot hold readable titles, so alerts stack them. */
    @Test
    fun alertsUseARowUpToTwoButtonsAndStackBeyond() {
        assertTrue(liquidAlertUsesButtonRow(1))
        assertTrue(liquidAlertUsesButtonRow(2))
        assertFalse(liquidAlertUsesButtonRow(3))
    }

    /** The card must be fully faded and fully sized by the time the presentation completes. */
    @Test
    fun cardMotionResolvesToRestAtFullProgress() {
        assertEquals(1f, liquidModalCardScale(1f), 0.0001f)
        assertEquals(1f, liquidModalCardAlpha(1f), 0.0001f)
        assertEquals(0f, liquidModalBottomOffsetPx(progress = 1f, cardHeightPx = 800f), 0.0001f)
    }

    /**
     * And fully gone at zero — the same invariant the sheet's scrim has. Anything left visible at
     * progress 0 is a frame that gets cut when the presentation unmounts.
     */
    @Test
    fun cardMotionIsFullyHiddenAtZeroProgress() {
        assertEquals(0f, liquidModalCardAlpha(0f), 0.0001f)
        assertEquals(800f, liquidModalBottomOffsetPx(progress = 0f, cardHeightPx = 800f), 0.0001f)
        assertTrue(
            liquidModalCardScale(0f) < 1f,
            "A centred card scales in, so it must start smaller than rest",
        )
    }

    @Test
    fun cardMotionClampsOutOfRangeProgress() {
        assertEquals(1f, liquidModalCardAlpha(1.4f), 0.0001f)
        assertEquals(0f, liquidModalCardAlpha(-0.2f), 0.0001f)
        assertEquals(1f, liquidModalCardScale(1.4f), 0.0001f)
        assertEquals(0f, liquidModalBottomOffsetPx(progress = 1.4f, cardHeightPx = 800f), 0.0001f)
    }
}
