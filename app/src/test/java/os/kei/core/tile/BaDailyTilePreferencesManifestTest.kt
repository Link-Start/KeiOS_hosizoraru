package os.kei.core.tile

import android.service.quicksettings.TileService
import org.junit.Test
import org.w3c.dom.Element
import os.kei.ui.page.main.ba.support.BA_DAILY_TILE_SLOTS
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The manifest half of the tile long-press, which nothing else can assert.
 *
 * SystemUI resolves [TileService.ACTION_QS_TILE_PREFERENCES] against the tile's own package and *silently*
 * falls back to the system app-info screen when the resolve fails. Every way of getting this wrong — a
 * missing filter, a typo in the action, `exported="false"` so the resolve finds nothing launchable — looks
 * exactly like the bug this activity was added to fix, with no crash and no log to follow.
 */
class BaDailyTilePreferencesManifestTest {
    @Test
    fun `the action in the manifest is the platform constant, not a lookalike`() {
        // Read from the SDK rather than restated: it is a compile-time String constant, so no Android
        // runtime is involved, and a renamed action would fail here instead of in the shade.
        assertEquals(
            "android.service.quicksettings.action.QS_TILE_PREFERENCES",
            TileService.ACTION_QS_TILE_PREFERENCES,
        )
        assertTrue(
            preferencesActivity().childElements("intent-filter").any { filter ->
                filter.childElements("action").any {
                    it.androidAttr("name") == TileService.ACTION_QS_TILE_PREFERENCES
                }
            },
            "the daily template activity must handle the tile preferences action",
        )
    }

    @Test
    fun `the activity is exported, because SystemUI is the caller`() {
        // Not a trust decision: an unexported activity cannot be resolved or launched by the shade, and
        // the long-press would quietly go back to app info.
        assertEquals("true", preferencesActivity().androidAttr("exported"))
    }

    @Test
    fun `it opens as a translucent sheet window and stays out of recents`() {
        val activity = preferencesActivity()
        assertEquals("@style/Theme.KeiOS.TranslucentSheetWindow", activity.androidAttr("theme"))
        // Launched from the shade, so it is not part of the app's task or its history.
        assertEquals("true", activity.androidAttr("excludeFromRecents"))
        assertEquals("", activity.androidAttr("taskAffinity"))
        // singleTop is what makes a long-press on a second tile re-target the open editor.
        assertEquals("singleTop", activity.androidAttr("launchMode"))
    }

    @Test
    fun `every declared daily tile still ships disabled, so the editor did not enable the pool`() {
        // The tiles are claimed from the settings sheet; a preferences activity must not change that.
        val services =
            application()
                .childElements("service")
                .filter { it.androidAttr("name").startsWith(".core.tile.BaDailyDone") }
        assertEquals(1 + BA_DAILY_TILE_SLOTS, services.size)
        services.forEach { service ->
            assertEquals("false", service.androidAttr("enabled"), service.androidAttr("name"))
        }
    }

    private fun preferencesActivity(): Element =
        application()
            .childElements("activity")
            .single { it.androidAttr("name") == ".ui.page.main.ba.BaDailyDoneTemplateActivity" }

    private fun application(): Element =
        androidManifest().documentElement.childElements("application").single()

    private fun Element.childElements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length)
            .mapNotNull { nodes.item(it) as? Element }
            .filter { it.parentNode === this }
    }

    private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private fun androidManifest() =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(projectFile("app/src/main/AndroidManifest.xml"))

    private fun projectFile(path: String): File = File(projectRoot(), path)

    private fun projectRoot(): File {
        val start = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Cannot locate project root from ${start.path}")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
