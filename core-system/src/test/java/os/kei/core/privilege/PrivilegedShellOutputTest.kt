package os.kei.core.privilege

import org.junit.Test
import os.kei.core.system.AppCommandResult
import kotlin.test.assertEquals

class PrivilegedShellOutputTest {
    @Test
    fun `command output prefers stdout, falls back to stderr, and maps blank to null`() {
        data class Row(val label: String, val stdout: String, val stderr: String, val exitCode: Int?, val expected: String?)
        listOf(
            Row(
                "stderr kept when the command produced no exit code",
                stdout = "",
                stderr = "Root unavailable (no su binary found)",
                exitCode = null,
                expected = "Root unavailable (no su binary found)",
            ),
            Row("stdout preferred over stderr", stdout = "root", stderr = "ignored", exitCode = 0, expected = "root"),
            Row("blank output maps to null", stdout = "   ", stderr = "", exitCode = 0, expected = null),
        ).forEach { row ->
            val result =
                AppCommandResult(
                    stdout = row.stdout,
                    stderr = row.stderr,
                    exitCode = row.exitCode,
                    timedOut = false,
                    cancelled = false,
                )

            assertEquals(row.expected, privilegedCommandOutputOrNull(result), row.label)
        }
    }

    @Test
    fun `detailed probe rows keep a stable order and drop unknown keys`() {
        val output =
            """
            __keios_privilege_id=uid=0(root) gid=0(root)
            __keios_privilege_whoami=root
            __keios_privilege_uname=Linux localhost 6.6.0
            __keios_privilege_getenforce=Enforcing
            __keios_privilege_process_count=412
            __keios_privilege_unknown=ignored
            unrelated=line
            """.trimIndent()

        assertEquals(
            listOf(
                PRIVILEGE_ROW_ID to "uid=0(root) gid=0(root)",
                PRIVILEGE_ROW_WHOAMI to "root",
                PRIVILEGE_ROW_UNAME to "Linux localhost 6.6.0",
                PRIVILEGE_ROW_GETENFORCE to "Enforcing",
                PRIVILEGE_ROW_PROCESS_COUNT to "412",
            ),
            parsePrivilegeDetailedCommandRows(output),
        )
    }

    @Test
    fun `detailed probe rows skip entries with blank values`() {
        val output =
            """
            __keios_privilege_id=uid=2000(shell)
            __keios_privilege_whoami=
            """.trimIndent()

        assertEquals(
            listOf(PRIVILEGE_ROW_ID to "uid=2000(shell)"),
            parsePrivilegeDetailedCommandRows(output),
        )
    }
}
