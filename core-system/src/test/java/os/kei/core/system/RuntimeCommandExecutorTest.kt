package os.kei.core.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeCommandExecutorTest {
    @After
    fun tearDown() {
        RuntimeCommandExecutor.closePersistentShell()
    }

    @Test
    fun `executeAsync captures stdout for successful command`() = runTest {
        val result = RuntimeCommandExecutor.executeAsync("printf async", timeoutMs = 1_000L)

        assertEquals("async", result.stdout)
        assertEquals(0, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(result.succeeded)
    }

    @Test
    fun `executeAsync force stops commands after timeout`() = runTest {
        val result = RuntimeCommandExecutor.executeAsync("sleep 2; printf late", timeoutMs = 100L)

        assertTrue(result.timedOut)
        assertNull(result.exitCode)
        assertFalse(result.succeeded)
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
        assertFalse(result.stdoutTruncated)
        assertTrue(result.succeeded)
    }

    @Test
    fun `app command executor bounds oversized stdout`() {
        val result = AppCommandExecutor.execute(
            command = "awk 'BEGIN { for (i = 0; i < 12000; i++) printf \"x\" }'",
            timeoutMs = 2_000L,
            maxOutputBytes = 2_000
        )

        assertEquals(2_000, result.stdout.length)
        assertTrue(result.stdoutTruncated)
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

    @Test
    fun `app command result prefers stdout for combined output`() {
        val stdoutResult = AppCommandResult(
            stdout = "out",
            stderr = "err",
            exitCode = 0,
            timedOut = false
        )
        val stderrResult = AppCommandResult(
            stdout = "",
            stderr = "err",
            exitCode = 7,
            timedOut = false
        )

        assertEquals("out", stdoutResult.combinedOutput())
        assertEquals("err", stderrResult.combinedOutput())
    }

    @Test
    fun `app command executor returns empty result for blank commands`() {
        val result = AppCommandExecutor.execute("   ", timeoutMs = 1_000L)

        assertEquals("", result.stdout)
        assertEquals("", result.stderr)
        assertNull(result.exitCode)
        assertFalse(result.timedOut)
        assertFalse(result.succeeded)
    }

    @Test
    fun `persistent shell preserves exit code without leaking shell state`() = runTest {
        val executor = PersistentShellCommandExecutor()

        val cwdBefore = executor.executeAsync(
            AppCommandRequest("pwd", timeoutMs = 1_000L)
        )
        val changedDirectory = executor.executeAsync(
            AppCommandRequest("cd /; printf changed; exit 7", timeoutMs = 1_000L)
        )
        val cwdAfter = executor.executeAsync(
            AppCommandRequest("pwd", timeoutMs = 1_000L)
        )

        executor.close()

        assertEquals("changed", changedDirectory.stdout)
        assertEquals(7, changedDirectory.exitCode)
        assertEquals(cwdBefore.stdout, cwdAfter.stdout)
        assertTrue(cwdAfter.succeeded)
    }

    @Test
    fun `persistent shell bounds oversized stdout`() = runTest {
        val executor = PersistentShellCommandExecutor()

        val result = executor.executeAsync(
            AppCommandRequest(
                command = "awk 'BEGIN { for (i = 0; i < 12000; i++) printf \"x\" }'",
                timeoutMs = 2_000L,
                maxOutputBytes = 2_000
            )
        )

        executor.close()

        assertEquals(2_000, result.stdout.length)
        assertTrue(result.stdoutTruncated)
        assertEquals(0, result.exitCode)
        assertTrue(result.succeeded)
    }

    @Test
    fun `persistent shell rebuilds after timeout`() = runTest {
        val executor = PersistentShellCommandExecutor()

        val startedNs = System.nanoTime()
        val timedOut = executor.executeAsync(
            AppCommandRequest("sleep 2; printf late", timeoutMs = 100L)
        )
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
        val recovered = executor.executeAsync(
            AppCommandRequest("printf recovered", timeoutMs = 1_000L)
        )

        executor.close()

        assertTrue(timedOut.timedOut)
        assertNull(timedOut.exitCode)
        // The timeout has to fire while the command is quiet, not when it next prints: a blocking pipe
        // read ignored it, and this call took the full 2s of `sleep 2`.
        assertTrue(elapsedMs < 1_000, "a 100ms timeout returned after ${elapsedMs}ms")
        assertEquals("recovered", recovered.stdout)
        assertTrue(recovered.succeeded)
    }

    @Test
    fun `persistent shell stops waiting as soon as the caller is cancelled`() = runTest {
        val executor = PersistentShellCommandExecutor()
        val call = launch(Dispatchers.IO) {
            executor.executeAsync(AppCommandRequest("sleep 2; printf late", timeoutMs = 10_000L))
        }
        Thread.sleep(200)

        val startedNs = System.nanoTime()
        call.cancelAndJoin()
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
        val recovered = executor.executeAsync(
            AppCommandRequest("printf recovered", timeoutMs = 1_000L)
        )

        executor.close()

        assertTrue(elapsedMs < 1_000, "cancelling a quiet command took ${elapsedMs}ms")
        assertEquals("recovered", recovered.stdout)
    }

    @Test
    fun `persistent shell policy only accepts OS readonly commands`() {
        assertTrue(PersistentShellCommandPolicy.isEligible("getprop"))
        assertTrue(PersistentShellCommandPolicy.isEligible("settings list system"))
        assertTrue(
            PersistentShellCommandPolicy.isEligible(
                "printf '__keios_settings_section=system\\n'; settings list system 2>/dev/null; " +
                    "printf '__keios_settings_section=secure\\n'; settings list secure 2>/dev/null; " +
                    "printf '__keios_settings_section=global\\n'; settings list global 2>/dev/null"
            )
        )
        assertFalse(PersistentShellCommandPolicy.isEligible("cd /; pwd"))
        assertFalse(PersistentShellCommandPolicy.isEligible("echo warning >&2; exit 7"))
    }
}
