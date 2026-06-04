package os.kei.core.shizuku

import os.kei.core.system.AppCommandResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShizukuApiUtilsTest {
    @Test
    fun `command output keeps stderr status when command result has no exit code`() {
        val result =
            AppCommandResult(
                stdout = "",
                stderr = "Shizuku command unavailable: unsupported service uid 1000",
                exitCode = null,
                timedOut = false,
                cancelled = false,
            )

        assertEquals(
            "Shizuku command unavailable: unsupported service uid 1000",
            shizukuCommandOutputOrNull(result),
        )
    }

    @Test
    fun `command output prefers stdout over stderr`() {
        val result =
            AppCommandResult(
                stdout = "shell",
                stderr = "ignored",
                exitCode = 0,
                timedOut = false,
                cancelled = false,
            )

        assertEquals("shell", shizukuCommandOutputOrNull(result))
    }

    @Test
    fun `command output stays null for blank command result`() {
        val result =
            AppCommandResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                timedOut = false,
                cancelled = false,
            )

        assertNull(shizukuCommandOutputOrNull(result))
    }

    @Test
    fun `command ready status text accepts shell or root granted states`() {
        assertEquals(
            true,
            ShizukuApiUtils.isCommandReadyStatusText("Shizuku permission: granted (shell)"),
        )
        assertEquals(
            true,
            ShizukuApiUtils.isCommandReadyStatusText("Shizuku permission: granted (root)"),
        )
    }

    @Test
    fun `command ready status text rejects not granted and denied states`() {
        assertEquals(
            false,
            ShizukuApiUtils.isCommandReadyStatusText("Shizuku permission: not granted"),
        )
        assertEquals(
            false,
            ShizukuApiUtils.isCommandReadyStatusText("Shizuku permission: denied"),
        )
    }
}
