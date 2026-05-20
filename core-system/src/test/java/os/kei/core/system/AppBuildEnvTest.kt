package os.kei.core.system

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppBuildEnvTest {
    @Test
    fun `configure keeps debug ui dump identity`() {
        AppBuildEnv.configure(
            buildType = "debug",
            isDebugBuild = true,
            applicationId = "os.kei.debug",
        )

        assertEquals("debug", AppBuildEnv.buildType)
        assertTrue(AppBuildEnv.isDebugBuild)
        assertEquals("debug", AppBuildEnv.flavorFolderName)
        assertEquals("Debug", AppBuildEnv.displayName)
        assertContains(AppBuildEnv.uiDumpShellDirectory(), "Android/data/os.kei.debug/files/debug/ui")

        AppBuildEnv.configure(
            buildType = "release",
            isDebugBuild = false,
            applicationId = "os.kei",
        )
    }

    @Test
    fun `blank build type falls back from debug flag`() {
        AppBuildEnv.configure(
            buildType = " ",
            isDebugBuild = false,
            applicationId = " ",
        )

        assertEquals("release", AppBuildEnv.buildType)
        assertFalse(AppBuildEnv.isDebugBuild)
        assertEquals("release", AppBuildEnv.flavorFolderName)
        assertContains(AppBuildEnv.uiDumpShellDirectory(), "Android/data/os.kei/files/release/ui")
    }
}
