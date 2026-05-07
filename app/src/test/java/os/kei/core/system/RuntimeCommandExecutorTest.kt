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

    @Test
    fun `app command executor preserves large stdout`() {
        val result = AppCommandExecutor.execute(
            command = "awk 'BEGIN { for (i = 0; i < 12000; i++) printf \"x\" }'",
            timeoutMs = 2_000L
        )

        assertEquals(12_000, result.stdout.length)
        assertEquals(0, result.exitCode)
        assertTrue(result.succeeded)
    }

    @Test
    fun `app command executor reports invalid command through stderr and exit code`() {
        val result = AppCommandExecutor.execute(
            command = "__keios_missing_command__",
            timeoutMs = 1_000L
        )

        assertEquals(127, result.exitCode)
        assertTrue(result.stderr.contains("__keios_missing_command__"))
        assertFalse(result.succeeded)
    }
}
