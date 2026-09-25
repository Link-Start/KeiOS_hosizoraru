package os.kei.ui.page.main.widget.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thumb capsule must be whole at every value.
 *
 * It used to be centred on the value, so at the minimum a quarter of it sat at negative x and something
 * up the tree clipped there — a flat-left, round-right half capsule on every slider resting at 0. The
 * geometry now never asks to draw outside the row, which is a property worth stating as arithmetic:
 * a screenshot only ever covers the one value it was taken at.
 */
class LiquidSliderThumbGeometryTest {
    private val trackWidth = 1000f
    private val thumbWidth = 80f

    @Test
    fun theCapsuleStaysInsideTheTrackAtEveryValue() {
        val half = thumbWidth / 2f
        (0..100).forEach { step ->
            val progress = step / 100f
            val center = liquidSliderCenterAt(trackWidth, thumbWidth, progress)

            assertTrue(
                "At progress $progress the capsule's left edge is ${center - half}, outside the track",
                center - half >= -0.001f,
            )
            assertTrue(
                "At progress $progress the capsule's right edge is ${center + half}, past $trackWidth",
                center + half <= trackWidth + 0.001f,
            )
        }
    }

    @Test
    fun theEndsRestFlushWithTheTrackEnds() {
        assertEquals(thumbWidth / 2f, liquidSliderCenterAt(trackWidth, thumbWidth, 0f), 0.001f)
        assertEquals(trackWidth - thumbWidth / 2f, liquidSliderCenterAt(trackWidth, thumbWidth, 1f), 0.001f)
    }

    @Test
    fun theFilledTrackEndsUnderTheThumbsCentre() {
        // The fill's width and the thumb's translation both come from these two helpers, and this is the
        // relationship that has to hold or the fill runs ahead of the thumb below the midpoint and behind
        // it above — which is exactly what `trackWidth * progress` did once the thumb stopped being
        // centred on the value.
        (0..100).forEach { step ->
            val progress = step / 100f
            val thumbLeftEdge = liquidSliderThumbTravel(trackWidth, thumbWidth) * progress
            val fillEnd = liquidSliderCenterAt(trackWidth, thumbWidth, progress)

            assertEquals(
                "Fill and thumb centre disagree at progress $progress",
                thumbLeftEdge + thumbWidth / 2f,
                fillEnd,
                0.001f,
            )
        }
    }

    @Test
    fun aThumbWiderThanItsTrackParksRatherThanInverting() {
        // Reachable transiently during layout, when a row is measured at near-zero width.
        assertEquals(0f, liquidSliderThumbTravel(trackWidth = 10f, thumbWidth = 80f), 0f)
        assertEquals(40f, liquidSliderCenterAt(trackWidth = 10f, thumbWidth = 80f, progress = 1f), 0f)
    }
}
