package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaGuideBgmDockMotionTest {
    @Test
    fun panelOffsetClampsToFourDpAtFullWidth() {
        val density = Density(density = 2f)
        val maxOffset = with(density) { 4.dp.toPx() }

        assertEquals(
            expected = maxOffset,
            actual =
                baGuideBgmDockPanelOffset(
                    rawOffsetPx = 200f,
                    totalWidthPx = 100f,
                    density = density,
                ),
            absoluteTolerance = 0.001f,
        )
        assertEquals(
            expected = -maxOffset,
            actual =
                baGuideBgmDockPanelOffset(
                    rawOffsetPx = -200f,
                    totalWidthPx = 100f,
                    density = density,
                ),
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun panelOffsetKeepsPartialDragWithinBounds() {
        val density = Density(density = 3f)
        val maxOffset = with(density) { 4.dp.toPx() }
        val offset =
            baGuideBgmDockPanelOffset(
                rawOffsetPx = 18f,
                totalWidthPx = 120f,
                density = density,
            )

        assertTrue(offset > 0f)
        assertTrue(offset < maxOffset)
    }

    @Test
    fun panelOffsetFallsBackToZeroBeforeMeasurement() {
        val density = Density(density = 2f)

        assertEquals(
            expected = 0f,
            actual =
                baGuideBgmDockPanelOffset(
                    rawOffsetPx = 32f,
                    totalWidthPx = 0f,
                    density = density,
                ),
        )
    }
}
