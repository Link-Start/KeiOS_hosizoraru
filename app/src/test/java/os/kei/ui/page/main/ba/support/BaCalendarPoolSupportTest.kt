package os.kei.ui.page.main.ba.support

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaCalendarPoolSupportTest {
    @Test
    fun `hard timeout returns successful block result`() = runBlocking {
        val result =
            runWithHardTimeout(timeoutMs = 1_000L) {
                "ok"
            }

        assertEquals("ok", result)
    }

    @Test
    fun `hard timeout interrupts slow block`() = runBlocking {
        val elapsedMs =
            measureTimeMillis {
                assertFailsWith<IllegalStateException> {
                    runWithHardTimeout(timeoutMs = 100L) {
                        Thread.sleep(5_000L)
                    }
                }
            }

        assertTrue(elapsedMs < 1_000L)
    }
}
