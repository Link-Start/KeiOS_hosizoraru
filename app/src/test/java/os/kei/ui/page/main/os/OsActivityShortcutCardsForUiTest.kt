package os.kei.ui.page.main.os

import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import org.junit.Test
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

    private fun sampleActivityCard(id: String): OsActivityShortcutCard {
        return OsActivityShortcutCard(
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
    }
}
