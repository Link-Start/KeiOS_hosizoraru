package os.kei.ui.page.main.os

import org.junit.Test
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsActivityShortcutCardsForUiTest {
    @Test
    fun `unloaded persistent state does not expose default activity cards`() {
        val defaultCard = sampleActivityCard(id = "builtin-language")
        val state =
            OsPagePersistentState(
                activityShortcutCards = listOf(defaultCard),
                loaded = false,
            )

        assertTrue(osActivityShortcutCardsForUi(state).isEmpty())
    }

    @Test
    fun `loaded persistent state exposes stored activity cards`() {
        val storedCard = sampleActivityCard(id = "stored-extra-dim")
        val state =
            OsPagePersistentState(
                activityShortcutCards = listOf(storedCard),
                loaded = true,
            )

        assertEquals(listOf(storedCard), osActivityShortcutCardsForUi(state))
    }

    @Test
    fun `card list derived state filters visible cards`() {
        val visibleActivity = sampleActivityCard(id = "visible-activity")
        val hiddenActivity = sampleActivityCard(id = "hidden-activity", visible = false)
        val visibleShell = sampleShellCard(id = "visible-shell")
        val hiddenShell = sampleShellCard(id = "hidden-shell", visible = false)
        val state =
            OsPagePersistentState(
                activityShortcutCards = listOf(visibleActivity, hiddenActivity),
                shellCommandCards = listOf(visibleShell, hiddenShell),
                loaded = true,
            )

        val derived = deriveOsPageCardListState(state)

        assertEquals(listOf(visibleActivity, hiddenActivity), derived.activityShortcutCards)
        assertEquals(listOf(visibleActivity), derived.visibleActivityShortcutCards)
        assertEquals(listOf(visibleShell, hiddenShell), derived.shellCommandCards)
        assertEquals(listOf(visibleShell), derived.visibleShellCommandCards)
    }

    private fun sampleActivityCard(id: String): OsActivityShortcutCard =
        OsActivityShortcutCard(
            id = id,
            visible = true,
            isBuiltInSample = true,
            config =
                OsGoogleSystemServiceConfig(
                    title = "Activity",
                    subtitle = "Shortcut",
                    appName = "Settings",
                    packageName = "com.android.settings",
                    className = "com.android.settings.Settings",
                    intentAction = "android.intent.action.VIEW",
                ),
        )

    private fun sampleActivityCard(
        id: String,
        visible: Boolean,
    ): OsActivityShortcutCard = sampleActivityCard(id).copy(visible = visible)

    private fun sampleShellCard(
        id: String,
        visible: Boolean = true,
    ): OsShellCommandCard =
        OsShellCommandCard(
            id = id,
            visible = visible,
            title = "Shell",
            command = "echo ok",
        )
}
