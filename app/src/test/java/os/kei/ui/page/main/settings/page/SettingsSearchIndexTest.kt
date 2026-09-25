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
    fun `resolved string labels route a query to its cards`() {
        data class Case(
            val name: String,
            val labels: Map<Int, String>,
            val query: String,
            val expected: List<SettingsSearchCard>,
        )
        listOf(
            Case(
                name = "builder uses string resolver tokens",
                labels = mapOf(R.string.settings_theme_mode_title to "Theme Mode"),
                query = "theme",
                expected = listOf(SettingsSearchCard.ThemeLanguage),
            ),
            Case(
                name = "battery query targets keepalive card",
                labels = mapOf(R.string.settings_battery_optimization_title to "Battery Optimization"),
                query = "battery",
                expected = listOf(SettingsSearchCard.KeepAlive),
            ),
            Case(
                name = "accessibility query targets guard cards",
                labels = mapOf(
                    R.string.settings_accessibility_guard_policy_title to "Self Guard Policy",
                    R.string.settings_accessibility_guard_history_title to "Guard History",
                ),
                query = "guard",
                expected = listOf(
                    SettingsSearchCard.AccessibilityGuardPolicy,
                    SettingsSearchCard.AccessibilityGuardHistory,
                ),
            ),
        ).forEach { case ->
            val targets = buildSettingsSearchTargets { resId -> case.labels[resId] ?: "label-$resId" }

            assertEquals(
                case.name,
                case.expected,
                deriveSettingsSearchTargets(targets, case.query).map { it.card },
            )
        }
    }

    @Test
    fun `blank query returns empty matching targets`() {
        val targets =
            listOf(
                SettingsSearchTarget(
                    card = SettingsSearchCard.ThemeLanguage,
                    category = SettingsCategory.Interface,
                    tokens = listOf("appearance", "theme"),
                ),
            )

        assertEquals(emptyList<SettingsSearchTarget>(), deriveSettingsSearchTargets(targets, "  "))
    }

    @Test
    fun `query matches target tokens ignoring case`() {
        val visual =
            SettingsSearchTarget(
                card = SettingsSearchCard.ThemeLanguage,
                category = SettingsCategory.Interface,
                tokens = listOf("Appearance", "Theme Mode"),
            )
        val cache =
            SettingsSearchTarget(
                card = SettingsSearchCard.CacheDiagnostics,
                category = SettingsCategory.Data,
                tokens = listOf("Cache", "Diagnostics"),
            )

        assertEquals(listOf(visual), deriveSettingsSearchTargets(listOf(visual, cache), "theme"))
    }
}
