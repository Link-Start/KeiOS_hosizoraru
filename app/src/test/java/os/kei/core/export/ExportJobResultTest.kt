package os.kei.core.export

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportJobResultTest {
    @Test
    fun `success maps attempted items to saved count`() {
        val result = ExportJobResult.success(fileName = "logs.zip", attempted = 2)

        assertTrue(result.isSuccess)
        assertEquals(2, result.attempted)
        assertEquals(2, result.saved)
        assertEquals(0, result.failed)
    }

    @Test
    fun `failure keeps compact error preview`() {
        val result = ExportJobResult.failure(
            fileName = "logs.zip",
            error = IllegalStateException("line1\nline2"),
            attempted = 1
        )

        assertFalse(result.isSuccess)
        assertEquals(1, result.failed)
        assertEquals("IllegalStateException:line1 line2", result.errorPreview)
    }
}
