package os.kei.ui.page.main.os.components

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/**
 * The second column of the OS page is *reserved*, not packed.
 *
 * A staggered grid places by height, so it could not promise that the activity shortcuts stay in one
 * column — the assignment would move as cards were expanded. These are the rules that replace it.
 */
class OsCardLanesTest {
    @Test
    fun `the second column is the activity cards`() {
        val cards =
            listOf(
                entry("os-top-info-card", "os_top_info_card"),
                entry(SHELL_KEY, "os_key_value_card"),
                entry("os-shell-command-a", "os_shell_command_card"),
                entry("os-activity-1", OS_ACTIVITY_CARD_CONTENT_TYPE),
                entry("os-activity-2", OS_ACTIVITY_CARD_CONTENT_TYPE),
            )

        val lanes = requireNotNull(osCardLanes(cards, SHELL_KEY))

        assertEquals(listOf("os-activity-1", "os-activity-2"), lanes.secondary.map { it.key })
        // Everything else keeps its order in the first column, the Shell card included.
        assertEquals(
            listOf("os-top-info-card", SHELL_KEY, "os-shell-command-a"),
            lanes.primary.map { it.key },
        )
    }

    /**
     * With every activity card hidden or deleted the reserved column would be empty, so the Shell card
     * moves across — the one card on this page that is used rather than read.
     */
    @Test
    fun `the shell card takes the column when no activity card shows`() {
        val cards =
            listOf(
                entry("os-top-info-card", "os_top_info_card"),
                entry(SHELL_KEY, "os_key_value_card"),
                entry("os-shell-command-a", "os_shell_command_card"),
            )

        val lanes = requireNotNull(osCardLanes(cards, SHELL_KEY))

        assertEquals(listOf(SHELL_KEY), lanes.secondary.map { it.key })
        assertEquals(listOf("os-top-info-card", "os-shell-command-a"), lanes.primary.map { it.key })
    }

    /** A shell *command* card is not the Shell card, and must not be mistaken for it. */
    @Test
    fun `a shell command card does not stand in for the shell card`() {
        val cards =
            listOf(
                entry("os-top-info-card", "os_top_info_card"),
                entry("os-shell-command-a", "os_shell_command_card"),
            )

        assertNull(osCardLanes(cards, SHELL_KEY))
    }

    /**
     * Nothing left to reserve a column for, so the page goes back to one. A wide page with a dead half is
     * worse than a centred column.
     */
    @Test
    fun `no activity and no shell card falls back to one column`() {
        assertNull(osCardLanes(listOf(entry("os-top-info-card", "os_top_info_card")), SHELL_KEY))
        assertNull(osCardLanes(emptyList(), SHELL_KEY))
    }
}

private const val SHELL_KEY = "os-section-SHELL_RUNNER"

private fun entry(
    key: String,
    contentType: String,
) = OsCardEntry(key = key, contentType = contentType, content = {})
