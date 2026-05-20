package os.kei.core.prefs

import org.junit.Test
import java.io.File
import kotlin.test.assertContains

class UiPrefsDefaultSnapshotTest {
    @Test
    fun `home hdr highlight starts disabled`() {
        val source = projectFile("app/src/main/java/os/kei/core/prefs/UiPrefs.kt").readText()

        assertContains(source, "fun isHomeIconHdrEnabled(defaultValue: Boolean = false)")
        assertContains(source, "homeIconHdrEnabled = false,")
        assertContains(source, "homeIconHdrEnabled = store.decodeBool(KEY_HOME_ICON_HDR, false)")
    }

    private fun projectFile(path: String): File = File(projectRoot(), path)

    private fun projectRoot(): File {
        val start = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Cannot locate project root from ${start.path}")
    }
}
