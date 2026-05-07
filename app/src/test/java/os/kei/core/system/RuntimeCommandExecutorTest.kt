package os.kei.core.system

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeCommandExecutorTest {
    @Test
    fun `execute captures stdout for successful command`() {
        val result = RuntimeCommandExecutor.execute("printf hello", timeoutMs = 1_000L)

        assertEquals("hello", result.stdout)
        assertEquals(0, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(result.succeeded)
    }

    @Test
    fun `execute captures stderr and exit code`() {
        val result = RuntimeCommandExecutor.execute("echo warning >&2; exit 7", timeoutMs = 1_000L)

        assertEquals("warning", result.stderr)
        assertEquals(7, result.exitCode)
        assertFalse(result.timedOut)
        assertFalse(result.succeeded)
    }

    @Test
    fun `execute force stops commands after timeout`() {
        val result = RuntimeCommandExecutor.execute("sleep 2; printf late", timeoutMs = 100L)

        assertTrue(result.timedOut)
        assertNull(result.exitCode)
        assertFalse(result.succeeded)
    }
}
