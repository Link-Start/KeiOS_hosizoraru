package os.kei.ui.page.main.student

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuideVideoPictureInPictureLaunchBoundsTest {
    @Test
    fun `previews smaller than the video expand to a 16 by 9 rect inside the window`() {
        data class Case(
            val label: String,
            val window: GuidePictureInPictureLaunchBounds,
            val source: GuidePictureInPictureLaunchBounds,
            val alsoTaller: Boolean,
        )
        listOf(
            Case(
                "small preview",
                GuidePictureInPictureLaunchBounds(0, 0, 1080, 2400),
                GuidePictureInPictureLaunchBounds(850, 1200, 1010, 1290),
                alsoTaller = true,
            ),
            Case(
                "large mismatched preview",
                GuidePictureInPictureLaunchBounds(0, 0, 1280, 2856),
                GuidePictureInPictureLaunchBounds(84, 1222, 1196, 2038),
                alsoTaller = false,
            ),
            Case(
                "full width memory preview",
                GuidePictureInPictureLaunchBounds(0, 0, 1280, 2856),
                GuidePictureInPictureLaunchBounds(128, 1342, 1152, 1918),
                alsoTaller = true,
            ),
        ).forEach { case ->
            val result = resolveGuidePictureInPictureLaunchBounds(
                windowBounds = case.window,
                sourceRectHint = case.source,
            )
            assertNotNull(result, case.label)

            assertTrue(result.width() > case.source.width(), "${case.label}: width did not grow")
            if (case.alsoTaller) {
                assertTrue(result.height() > case.source.height(), "${case.label}: height did not grow")
            }
            assertEquals(16f / 9f, result.width().toFloat() / result.height().toFloat(), 0.02f, case.label)
            assertTrue(case.window.contains(result), "${case.label}: $result escapes ${case.window}")
        }
    }

    @Test
    fun `large preview keeps existing source rect when it already matches video size`() {
        val source = GuidePictureInPictureLaunchBounds(96, 420, 984, 920)
        val result = resolveGuidePictureInPictureLaunchBounds(
            windowBounds = GuidePictureInPictureLaunchBounds(0, 0, 1080, 2400),
            sourceRectHint = source,
        )

        assertEquals(source, result)
    }

    @Test
    fun `edge source rect clamps adaptive bounds inside window`() {
        val result = resolveGuidePictureInPictureLaunchBounds(
            windowBounds = GuidePictureInPictureLaunchBounds(0, 0, 1080, 2400),
            sourceRectHint = GuidePictureInPictureLaunchBounds(1010, 2200, 1070, 2270),
        )
        assertNotNull(result)

        assertTrue(result.left >= 0)
        assertTrue(result.top >= 0)
        assertTrue(result.right <= 1080)
        assertTrue(result.bottom <= 2400)
        assertEquals(16f / 9f, result.width().toFloat() / result.height().toFloat(), 0.02f)
    }

    @Test
    fun `landscape window uses available height for adaptive video bounds`() {
        val result = resolveGuidePictureInPictureLaunchBounds(
            windowBounds = GuidePictureInPictureLaunchBounds(0, 0, 2400, 1080),
            sourceRectHint = GuidePictureInPictureLaunchBounds(1000, 500, 1120, 568),
        )
        assertNotNull(result)

        assertTrue(result.width() <= 2400)
        assertTrue(result.height() <= 1080)
        assertTrue(result.width() > 120)
        assertEquals(16f / 9f, result.width().toFloat() / result.height().toFloat(), 0.02f)
    }
}
