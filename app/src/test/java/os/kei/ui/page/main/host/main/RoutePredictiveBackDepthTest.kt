package os.kei.ui.page.main.host.main

import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEvent.Companion.EDGE_RIGHT
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePredictiveBackDepthTest {
    @Test
    fun `top route depth follows left edge touch pivot`() {
        val values =
            resolveRoutePredictiveBackDepthValues(
                role = RoutePredictiveBackDepthRole.Top,
                progress = 1f,
                swipeEdge = EDGE_LEFT,
                touchY = 120f,
                containerHeightPx = 2000,
            )

        assertEquals(0.982f, values.scale, absoluteTolerance = 0.001f)
        assertEquals(0.96f, values.alpha, absoluteTolerance = 0.001f)
        assertEquals(0.82f, values.pivotX, absoluteTolerance = 0.001f)
        assertEquals(0.12f, values.pivotY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `top route depth reverses pivot for right edge`() {
        val values =
            resolveRoutePredictiveBackDepthValues(
                role = RoutePredictiveBackDepthRole.Top,
                progress = 0.5f,
                swipeEdge = EDGE_RIGHT,
                touchY = 1000f,
                containerHeightPx = 2000,
            )

        assertTrue(values.scale in 0.982f..0.991f)
        assertTrue(values.alpha in 0.96f..0.98f)
        assertEquals(0.18f, values.pivotX, absoluteTolerance = 0.001f)
        assertEquals(0.5f, values.pivotY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `idle route depth is identity`() {
        val values =
            resolveRoutePredictiveBackDepthValues(
                role = RoutePredictiveBackDepthRole.Idle,
                progress = 1f,
                swipeEdge = EDGE_LEFT,
                touchY = 0f,
                containerHeightPx = 0,
            )

        assertEquals(1f, values.scale, absoluteTolerance = 0.001f)
        assertEquals(1f, values.alpha, absoluteTolerance = 0.001f)
        assertEquals(0.5f, values.pivotX, absoluteTolerance = 0.001f)
        assertEquals(0.5f, values.pivotY, absoluteTolerance = 0.001f)
    }
}
