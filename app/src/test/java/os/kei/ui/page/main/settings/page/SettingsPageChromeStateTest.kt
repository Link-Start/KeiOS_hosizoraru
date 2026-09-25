package os.kei.ui.page.main.settings.page

import org.junit.Test
import os.kei.ui.page.main.settings.state.SettingsCardExpansionId
import os.kei.ui.page.main.settings.state.isSettingsCardExpanded
import kotlin.test.assertEquals

class SettingsPageChromeStateTest {
    @Test
    fun `active category follows target page while scrolling`() {
        assertEquals(
            2,
            settingsActiveCategoryIndex(
                scrolling = true,
                targetPage = 2,
                settledPage = 0,
                lastIndex = 3,
            ),
        )
    }

    @Test
    fun `active category follows settled page when idle`() {
        assertEquals(
            1,
            settingsActiveCategoryIndex(
                scrolling = false,
                targetPage = 3,
                settledPage = 1,
                lastIndex = 3,
            ),
        )
    }

    @Test
    fun `active category clamps page bounds`() {
        assertEquals(
            3,
            settingsActiveCategoryIndex(
                scrolling = true,
                targetPage = 8,
                settledPage = 1,
                lastIndex = 3,
            ),
        )
        assertEquals(
            0,
            settingsActiveCategoryIndex(
                scrolling = false,
                targetPage = 2,
                settledPage = -5,
                lastIndex = 3,
            ),
        )
    }

    @Test
    fun `card expansion reads the snapshot and falls back to each card's default`() {
        // One default of each kind, and overrides against both: the old cases used only open-by-default
        // cards and overrode one to the value it already had, so they passed with the map ignored.
        val empty = emptyMap<SettingsCardExpansionId, Boolean>()
        assertEquals(true, empty.isSettingsCardExpanded(SettingsCardExpansionId.KeepAlive))
        assertEquals(false, empty.isSettingsCardExpanded(SettingsCardExpansionId.Performance))

        val snapshot =
            mapOf(
                SettingsCardExpansionId.KeepAlive to false,
                SettingsCardExpansionId.Performance to true,
            )
        assertEquals(false, snapshot.isSettingsCardExpanded(SettingsCardExpansionId.KeepAlive))
        assertEquals(true, snapshot.isSettingsCardExpanded(SettingsCardExpansionId.Performance))
    }

    @Test
    fun `search result expansion does not persist card state`() {
        assertEquals(true, shouldPersistSettingsCardExpansion(""))
        assertEquals(true, shouldPersistSettingsCardExpansion("   "))
        assertEquals(false, shouldPersistSettingsCardExpansion("cache"))
    }
}
