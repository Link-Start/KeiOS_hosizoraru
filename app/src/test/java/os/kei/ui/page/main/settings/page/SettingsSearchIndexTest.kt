package os.kei.ui.page.main.settings.page

import org.junit.Assert.assertEquals
import org.junit.Test
import os.kei.R

class SettingsSearchIndexTest {
    @Test
    fun `builder creates all searchable settings cards`() {
        val targets = buildSettingsSearchTargets { resId -> "label-$resId" }

        assertEquals(SettingsSearchCard.entries.toSet(), targets.map { it.card }.toSet())
    }

    @Test
    fun `builder uses string resolver tokens`() {
        val targets =
            buildSettingsSearchTargets { resId ->
                when (resId) {
                    R.string.settings_theme_mode_title -> "Theme Mode"
                    else -> "label-$resId"
                }
            }

        assertEquals(
            listOf(SettingsSearchCard.Visual),
            deriveSettingsSearchTargets(targets, "theme").map { it.card },
        )
    }

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
