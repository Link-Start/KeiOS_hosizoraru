package os.kei.ui.page.main.os.components

import org.junit.Test
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsActivityVisibilityPresentationTest {
    @Test
    fun `blank query splits built in and custom activity cards`() {
        val builtIn = sampleCard(id = "builtin", builtIn = true, title = "Full screen")
        val custom = sampleCard(id = "custom", builtIn = false, title = "Language")

        val state =
            deriveOsActivityVisibilityPresentationState(
                cards = listOf(builtIn, custom),
                defaultCardTitle = "Activity",
                query = "",
            )

        assertEquals(listOf("builtin"), state.builtInItems.map { it.id })
        assertEquals(listOf("custom"), state.customItems.map { it.id })
        assertFalse(state.emptySearchActive)
    }

    @Test
    fun `query matches title package and class name`() {
        val cards =
            listOf(
                sampleCard(id = "title", title = "Full screen"),
                sampleCard(id = "package", packageName = "com.android.settings"),
                sampleCard(id = "class", className = "com.android.settings.LanguageSettings"),
                sampleCard(id = "hidden", title = "Battery"),
            )

        val packageMatch =
            deriveOsActivityVisibilityPresentationState(
                cards = cards,
                defaultCardTitle = "Activity",
                query = "settings",
            )
        val classMatch =
            deriveOsActivityVisibilityPresentationState(
                cards = cards,
                defaultCardTitle = "Activity",
                query = "language",
            )
        val titleMatch =
            deriveOsActivityVisibilityPresentationState(
                cards = cards,
                defaultCardTitle = "Activity",
                query = "full",
            )

        assertEquals(listOf("package", "class"), packageMatch.builtInItems.map { it.id })
        assertEquals(listOf("class"), classMatch.builtInItems.map { it.id })
        assertEquals(listOf("title"), titleMatch.builtInItems.map { it.id })
    }

    @Test
    fun `empty search state is active only when non blank query has no results`() {
        val state =
            deriveOsActivityVisibilityPresentationState(
                cards = listOf(sampleCard(id = "battery", title = "Battery")),
                defaultCardTitle = "Activity",
                query = "missing",
            )

        assertTrue(state.emptySearchActive)
        assertTrue(state.builtInItems.isEmpty())
        assertTrue(state.customItems.isEmpty())
    }

    private fun sampleCard(
        id: String,
        builtIn: Boolean = true,
        title: String = "",
        packageName: String = "",
        className: String = "",
    ): OsActivityShortcutCard =
        OsActivityShortcutCard(
            id = id,
            visible = true,
            isBuiltInSample = builtIn,
            config =
                OsGoogleSystemServiceConfig(
                    title = title,
                    subtitle = "",
                    appName = "Settings",
                    packageName = packageName,
                    className = className,
                    intentAction = "android.intent.action.VIEW",
                ),
        )
}
