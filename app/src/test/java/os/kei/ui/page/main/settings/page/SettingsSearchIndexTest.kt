package os.kei.ui.page.main.settings.page

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchIndexTest {
    @Test
    fun `blank query returns empty matching targets`() {
        val targets =
            listOf(
                SettingsSearchTarget(
                    card = SettingsSearchCard.Visual,
                    category = SettingsCategory.Appearance,
                    tokens = listOf("appearance", "theme"),
                ),
            )

        assertEquals(emptyList<SettingsSearchTarget>(), deriveSettingsSearchTargets(targets, "  "))
    }

    @Test
    fun `query matches target tokens ignoring case`() {
        val visual =
            SettingsSearchTarget(
                card = SettingsSearchCard.Visual,
                category = SettingsCategory.Appearance,
                tokens = listOf("Appearance", "Theme Mode"),
            )
        val cache =
            SettingsSearchTarget(
                card = SettingsSearchCard.Cache,
                category = SettingsCategory.Data,
                tokens = listOf("Cache", "Diagnostics"),
            )

        assertEquals(listOf(visual), deriveSettingsSearchTargets(listOf(visual, cache), "theme"))
    }
}
