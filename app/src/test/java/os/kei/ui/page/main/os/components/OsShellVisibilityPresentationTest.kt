package os.kei.ui.page.main.os.components

import org.junit.Test
import os.kei.ui.page.main.os.shell.BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsShellVisibilityPresentationTest {
    @Test
    fun `blank query keeps shell runner and splits cards`() {
        val builtIn = sampleCard(id = BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID, title = "Hide gesture line")
        val custom = sampleCard(id = "custom", title = "Custom command")

        val state =
            deriveOsShellVisibilityPresentationState(
                cards = listOf(builtIn, custom),
                shellRunnerTitle = "Shell runner",
                query = "",
            )

        assertTrue(state.showShellRunner)
        assertEquals(listOf(BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID), state.builtInCards.map { it.id })
        assertEquals(listOf("custom"), state.customCards.map { it.id })
        assertFalse(state.emptySearchActive)
    }

    @Test
    fun `query matches shell runner title and card text`() {
        val cards =
            listOf(
                sampleCard(id = "title", title = "Notification count"),
                sampleCard(id = "subtitle", subtitle = "Gesture line"),
                sampleCard(id = "command", command = "settings put global hide_gesture_line 1"),
            )

        val shellRunnerMatch =
            deriveOsShellVisibilityPresentationState(
                cards = cards,
                shellRunnerTitle = "Shell runner",
                query = "runner",
            )
        val cardMatch =
            deriveOsShellVisibilityPresentationState(
                cards = cards,
                shellRunnerTitle = "Shell runner",
                query = "gesture",
            )

        assertTrue(shellRunnerMatch.showShellRunner)
        assertTrue(shellRunnerMatch.customCards.isEmpty())
        assertFalse(cardMatch.showShellRunner)
        assertEquals(listOf("subtitle", "command"), cardMatch.customCards.map { it.id })
    }

    @Test
    fun `empty search state is active when query matches nothing`() {
        val state =
            deriveOsShellVisibilityPresentationState(
                cards = listOf(sampleCard(id = "notification", title = "Notification count")),
                shellRunnerTitle = "Shell runner",
                query = "missing",
            )

        assertFalse(state.showShellRunner)
        assertTrue(state.builtInCards.isEmpty())
        assertTrue(state.customCards.isEmpty())
        assertTrue(state.emptySearchActive)
    }

    private fun sampleCard(
        id: String,
        title: String = "",
        subtitle: String = "",
        command: String = "",
    ): OsShellCommandCard =
        OsShellCommandCard(
            id = id,
            visible = true,
            title = title,
            subtitle = subtitle,
            command = command,
        )
}
