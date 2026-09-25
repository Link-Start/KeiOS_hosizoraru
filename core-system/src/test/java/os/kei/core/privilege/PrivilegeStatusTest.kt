package os.kei.core.privilege

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivilegeStatusTest {
    @Test
    fun `only ready reports command readiness`() {
        PrivilegeStatusCode.entries.forEach { code ->
            val status = PrivilegeStatus(mode = PrivilegeMode.Shizuku, code = code)
            if (code == PrivilegeStatusCode.Ready) {
                assertTrue(status.isCommandReady, "expected $code to be ready")
            } else {
                assertFalse(status.isCommandReady, "expected $code to not be ready")
            }
        }
    }

    @Test
    fun `shizuku ready text keeps the identity detail`() {
        val status =
            PrivilegeStatus(
                mode = PrivilegeMode.Shizuku,
                code = PrivilegeStatusCode.Ready,
                detail = "shell",
            )

        assertEquals("Shizuku permission: granted (shell)", status.text)
    }

    @Test
    fun `root text names the root backend for the same code`() {
        val status =
            PrivilegeStatus(
                mode = PrivilegeMode.Root,
                code = PrivilegeStatusCode.ServiceUnavailable,
            )

        assertEquals("Root unavailable (no su binary found)", status.text)
    }

    @Test
    fun `notice text passes the detail through unchanged`() {
        val status =
            PrivilegeStatus(
                mode = PrivilegeMode.Root,
                code = PrivilegeStatusCode.Notice,
                detail = "UI dump redirected: /sdcard/dump.xml",
            )

        assertEquals("UI dump redirected: /sdcard/dump.xml", status.text)
    }

    @Test
    fun `every mode and code pair renders non blank text`() {
        PrivilegeMode.entries.forEach { mode ->
            PrivilegeStatusCode.entries
                .filter { it != PrivilegeStatusCode.Notice }
                .forEach { code ->
                    val text = PrivilegeStatus(mode = mode, code = code, detail = "x").text
                    assertTrue(text.isNotBlank(), "blank text for $mode/$code")
                }
        }
    }
}
