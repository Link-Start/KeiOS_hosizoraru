package os.kei.ui.page.main.os.shell

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsShellOutputRendererTest {
    @Test
    fun `parser keeps command result and time marker`() {
        val entries =
            parseShellOutputDisplayEntries(
                raw =
                    """
                    ${'$'} settings list global

                    Result:
                    adb_enabled=1

                    [12:34:56]
                    """.trimIndent(),
                stoppedOutputText = "Stopped",
                outputResultLabel = "Result",
                outputTimeLabel = "Time",
            )

        assertEquals(1, entries.size)
        assertEquals("settings list global", entries.single().command)
        assertEquals("adb_enabled=1", entries.single().result)
        assertEquals("[12:34:56]", entries.single().timeLabel)
        assertFalse(entries.single().isStopped)
    }

    @Test
    fun `parser marks stopped output entries`() {
        val entries =
            parseShellOutputDisplayEntries(
                raw =
                    """
                    ${'$'} sleep 10
                    Stopped
                    [01:02:03]
                    """.trimIndent(),
                stoppedOutputText = "Stopped",
                outputResultLabel = "Result",
                outputTimeLabel = "Time",
            )

        assertEquals(1, entries.size)
        assertEquals("sleep 10", entries.single().command)
        assertTrue(entries.single().isStopped)
    }

    @Test
    fun `stable shell output row keys survive head trimming`() {
        val first = ShellOutputDisplayEntry(command = "cmd 1", result = "one", isStopped = false, timeLabel = "[01:00:00]")
        val second = ShellOutputDisplayEntry(command = "cmd 2", result = "two", isStopped = false, timeLabel = "[01:00:01]")
        val third = ShellOutputDisplayEntry(command = "cmd 3", result = "three", isStopped = false, timeLabel = "[01:00:02]")

        val originalRows = listOf(first, second, third).toStableShellOutputRows()
        val trimmedRows = listOf(second, third).toStableShellOutputRows()

        assertEquals(originalRows[1].stableKey, trimmedRows[0].stableKey)
        assertEquals(originalRows[2].stableKey, trimmedRows[1].stableKey)
    }
}
