package os.kei.ui.page.main.os

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsPageSectionLoadGenerationTest {
    @Test
    fun `generation advances and invalidates older section load sessions`() {
        val generation = OsPageSectionLoadGeneration()
        val first = generation.current()

        val second = generation.advance()
        val third = generation.advance()

        assertEquals(0, first)
        assertEquals(1, second)
        assertEquals(2, third)
        assertFalse(generation.isCurrent(first))
        assertFalse(generation.isCurrent(second))
        assertTrue(generation.isCurrent(third))
    }
}
