package os.kei.core.icon

import android.content.pm.PackageManager
import os.kei.core.prefs.LauncherIconDesign
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherIconContractTest {
    @Test
    fun `storage id defaults to android designs`() {
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId(null))
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId(""))
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId("unknown"))
        assertEquals(LauncherIconDesign.Apple, LauncherIconDesign.fromStorageId("apple"))
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId("android"))
    }

    @Test
    fun `component class names stay on the manifest namespace when app id has suffixes`() {
        assertEquals("os.kei.LauncherAppleDesigns", LauncherIconDesign.Apple.qualifiedAliasClassName())
        assertEquals("os.kei.LauncherAndroidDesigns", LauncherIconDesign.Android.qualifiedAliasClassName())
    }

    @Test
    fun `component package follows installed application id while class stays manifest namespace`() {
        val appleDebugComponent = LauncherIconDesign.Apple.componentSpec("os.kei.debug")
        val androidBenchmarkComponent = LauncherIconDesign.Android.componentSpec("os.kei.benchmark")

        assertEquals("os.kei.debug", appleDebugComponent.packageName)
        assertEquals("os.kei.LauncherAppleDesigns", appleDebugComponent.className)
        assertEquals("os.kei.benchmark", androidBenchmarkComponent.packageName)
        assertEquals("os.kei.LauncherAndroidDesigns", androidBenchmarkComponent.className)
    }

    @Test
    fun `component state specs reapply selected design for benchmark package`() {
        val specs = LauncherIconDesign.Apple.componentStateSpecs("os.kei.benchmark")

        assertEquals(2, specs.size)
        assertEquals("os.kei.benchmark", specs[0].component.packageName)
        assertEquals("os.kei.LauncherAndroidDesigns", specs[0].component.className)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, specs[0].enabledState)
        assertEquals("os.kei.benchmark", specs[1].component.packageName)
        assertEquals("os.kei.LauncherAppleDesigns", specs[1].component.className)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, specs[1].enabledState)
    }

    @Test
    fun `launcher manifest defaults to android designs and keeps apple designs disabled`() {
        val manifest = androidManifest()
        val application =
            manifest.documentElement
                .childElements("application")
                .single()

        assertEquals("@mipmap/ic_launcher_android", application.androidAttr("icon"))
        assertEquals("@mipmap/ic_launcher_round_android", application.androidAttr("roundIcon"))

        val mainActivity =
            application
                .childElements("activity")
                .single { it.androidAttr("name") == ".MainActivity" }
        assertFalse(mainActivity.hasLauncherFilter())

        val appleAlias = application.launcherAlias(".LauncherAppleDesigns")
        assertEquals("false", appleAlias.androidAttr("enabled"))
        assertEquals("@mipmap/ic_launcher_apple", appleAlias.androidAttr("icon"))
        assertEquals("@mipmap/ic_launcher_round_apple", appleAlias.androidAttr("roundIcon"))
        assertEquals(".MainActivity", appleAlias.androidAttr("targetActivity"))
        assertTrue(appleAlias.hasLauncherFilter())

        val androidAlias = application.launcherAlias(".LauncherAndroidDesigns")
        assertEquals("true", androidAlias.androidAttr("enabled"))
        assertEquals("@mipmap/ic_launcher_android", androidAlias.androidAttr("icon"))
        assertEquals("@mipmap/ic_launcher_round_android", androidAlias.androidAttr("roundIcon"))
        assertEquals(".MainActivity", androidAlias.androidAttr("targetActivity"))
        assertTrue(androidAlias.hasLauncherFilter())
    }

    @Test
    fun `launcher icon resources preserve apple and android design assets`() {
        assertTrue(resFile("drawable-nodpi/ic_launcher_apple_background.png").isFile)
        assertTrue(resFile("drawable-nodpi/ic_launcher_apple_foreground.png").isFile)

        val appleIcon = resFile("mipmap-anydpi-v26/ic_launcher_apple.xml").readText()
        assertContains(appleIcon, "@drawable/ic_launcher_apple_background")
        assertContains(appleIcon, "@drawable/ic_launcher_apple_foreground")

        val androidIcon = resFile("mipmap-anydpi-v26/ic_launcher_android.xml").readText()
        assertContains(androidIcon, "@drawable/ic_launcher_background")
        assertContains(androidIcon, "@drawable/ic_launcher_foreground_inset")
    }

    private fun Element.launcherAlias(name: String): Element =
        childElements("activity-alias")
            .single { it.androidAttr("name") == name }

    private fun Element.hasLauncherFilter(): Boolean =
        childElements("intent-filter").any { filter ->
            val hasMainAction =
                filter.childElements("action").any {
                    it.androidAttr("name") == "android.intent.action.MAIN"
                }
            val hasLauncherCategory =
                filter.childElements("category").any {
                    it.androidAttr("name") == "android.intent.category.LAUNCHER"
                }
            hasMainAction && hasLauncherCategory
        }

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

    private fun resFile(path: String): File = projectFile("app/src/main/res/$path")

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
