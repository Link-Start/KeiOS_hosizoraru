package os.kei.ui.page.main.student.section.gallery

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideWebMemoryLobbyCameraTest {
    @Test fun zoomKeepsTheFocalPointAnchoredAndItsInverseRestoresTheView() {
        val original = GuideLobbyCameraTransform()
        val zoomed = original.transform(0.75f, 0.25f, 0f, 0f, 2f)
        assertEquals(2f, zoomed.scale)
        assertEquals(-0.25f, zoomed.panX)
        assertEquals(0.25f, zoomed.panY)
        assertEquals(original, zoomed.transform(0.75f, 0.25f, 0f, 0f, 0.5f))
    }

    @Test fun clampingScaleDoesNotApplyAnUnachievableZoomToThePan() {
        val max = GuideLobbyCameraTransform(4f, 0.2f, -0.3f)
        assertEquals(max, max.transform(0.9f, 0.1f, 0f, 0f, 100f))
        val min = GuideLobbyCameraTransform(0.5f)
        assertEquals(min, min.transform(0.9f, 0.1f, 0f, 0f, 0.001f))
    }

    @Test fun largeDragsRemainBoundedAndResetAlwaysReturnsToTheInitialView() {
        val camera = GuideWebMemoryLobbyCamera()
        camera.move(0.5f, 0.5f, 100f, -100f, 1f)
        assertEquals(0.85f, camera.transform.value.panX)
        assertEquals(-0.85f, camera.transform.value.panY)
        repeat(20) { camera.zoomIn() }
        assertEquals(4f, camera.transform.value.scale)
        camera.reset()
        assertEquals(GuideLobbyCameraTransform(), camera.transform.value)
    }

    @Test fun invalidInputDoesNotPoisonSubsequentCameraCommands() {
        val initial = GuideLobbyCameraTransform()
        for (zoom in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertEquals(initial, initial.transform(0.5f, 0.5f, 0f, 0f, zoom))
        }
        assertEquals(initial, initial.transform(Float.NaN, 0.5f, 0f, 0f, 2f))
        val camera = GuideWebMemoryLobbyCamera()
        camera.zoomOut()
        assertTrue(camera.transform.value.scale < 1f)
        camera.zoomIn()
        assertEquals(initial, camera.transform.value)
    }
}
