package os.kei.ui.page.main.os.shell

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsShellOutputRendererTest {
    @Test
    fun `parser keeps command result and time marker`() {
        val entries = parseShellOutputDisplayEntries(
            raw = """
            ${'$'} settings list global

            Result:
            adb_enabled=1

            [12:34:56]
            """.trimIndent(),
            stoppedOutputText = "Stopped",
            outputResultLabel = "Result",
            outputTimeLabel = "Time"
        )

        assertEquals(1, entries.size)
        assertEquals("settings list global", entries.single().command)
        assertEquals("adb_enabled=1", entries.single().result)
        assertEquals("[12:34:56]", entries.single().timeLabel)
        assertFalse(entries.single().isStopped)
    }

    @Test
    fun `parser marks stopped output entries`() {
        val entries = parseShellOutputDisplayEntries(
            raw = """
            ${'$'} sleep 10
            Stopped
            [01:02:03]
            """.trimIndent(),
            stoppedOutputText = "Stopped",
            outputResultLabel = "Result",
            outputTimeLabel = "Time"
        )

        assertEquals(1, entries.size)
        assertEquals("sleep 10", entries.single().command)
        assertTrue(entries.single().isStopped)
    }
}
