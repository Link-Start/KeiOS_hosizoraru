package os.kei.ui.page.main.os.shell

import os.kei.ui.page.main.os.shell.state.startStreamingShellRunnerOutput
import os.kei.ui.page.main.os.shell.state.updateStreamingShellRunnerOutput
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

    @Test
    fun `streaming output updates latest entry and keeps stable row key`() {
        val started =
            startStreamingShellRunnerOutput(
                currentOutputEntries = emptyList(),
                command = "logcat -d",
                outputSaveMode = OsShellRunnerOutputSaveMode.FullHistory,
                maxChars = 2_000,
                timeLabel = "[08:00:00]",
            )
        val initialKey = started.outputEntries.toStableShellOutputRows().single().stableKey

        val updated =
            updateStreamingShellRunnerOutput(
                currentOutputEntries = started.outputEntries,
                command = "logcat -d",
                result = "line 1\nline 2",
                commandStoppedText = "Stopped",
                outputSaveMode = OsShellRunnerOutputSaveMode.FullHistory,
                maxChars = 2_000,
            )

        assertEquals(1, updated.outputEntries.size)
        assertEquals("logcat -d", updated.outputEntries.single().command)
        assertEquals("line 1\nline 2", updated.outputEntries.single().result)
        assertEquals("line 1\nline 2", updated.latestRunResultOutput)
        assertEquals(initialKey, updated.outputEntries.toStableShellOutputRows().single().stableKey)
        assertTrue(updated.outputText.contains("${'$'} logcat -d"))
    }

    @Test
    fun `streaming latest-only mode replaces previous output entry`() {
        val previous = ShellOutputDisplayEntry(command = "old", result = "old result", isStopped = false, timeLabel = "[07:59:59]")

        val started =
            startStreamingShellRunnerOutput(
                currentOutputEntries = listOf(previous),
                command = "settings list global",
                outputSaveMode = OsShellRunnerOutputSaveMode.LatestOnly,
                maxChars = 2_000,
                timeLabel = "[08:00:01]",
            )

        assertEquals(1, started.outputEntries.size)
        assertEquals("settings list global", started.outputEntries.single().command)
        assertFalse(started.outputText.contains("old result"))
    }

    @Test
    fun `streaming update without started entry keeps first result`() {
        val updated =
            updateStreamingShellRunnerOutput(
                currentOutputEntries = emptyList(),
                command = "id",
                result = "uid=2000(shell)",
                commandStoppedText = "Stopped",
                outputSaveMode = OsShellRunnerOutputSaveMode.FullHistory,
                maxChars = 2_000,
            )

        assertEquals(1, updated.outputEntries.size)
        assertEquals("id", updated.outputEntries.single().command)
        assertEquals("uid=2000(shell)", updated.outputEntries.single().result)
        assertEquals("uid=2000(shell)", updated.latestRunResultOutput)
    }
}
