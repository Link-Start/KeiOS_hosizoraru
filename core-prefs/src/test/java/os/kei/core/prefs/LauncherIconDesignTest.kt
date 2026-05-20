package os.kei.core.prefs

import org.junit.Test
import kotlin.test.assertEquals

class LauncherIconDesignTest {
    @Test
    fun `storage id defaults to android designs`() {
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId(null))
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId(""))
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId("unknown"))
        assertEquals(LauncherIconDesign.Apple, LauncherIconDesign.fromStorageId("apple"))
        assertEquals(LauncherIconDesign.Android, LauncherIconDesign.fromStorageId("android"))
    }
}
