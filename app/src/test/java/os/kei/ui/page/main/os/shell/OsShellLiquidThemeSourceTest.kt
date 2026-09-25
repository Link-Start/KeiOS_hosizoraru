package os.kei.ui.page.main.os.shell

import org.junit.Test
import kotlin.test.assertEquals
import os.kei.ui.testing.repoSource
class OsShellLiquidThemeSourceTest {

    /**
     * PrivilegedShell holds exactly one status callback, so a second attach would displace
     * MainActivity's and stop the rest of the app hearing about privilege changes.
     */
    @Test
    fun theShellRouteDoesNotAttachASecondPrivilegeCallback() {
        val hostSource = repoSource(MAIN_SCREEN_NAV_HOST_SOURCE)

        assertEquals(
            0,
            hostSource.occurrencesOf("privilegedShell.attach"),
            "The shell route must read the shared privilege status, not attach its own callback",
        )
        assertEquals(
            0,
            hostSource.occurrencesOf("PrivilegedShell()"),
            "The shell route must use the shared PrivilegedShell, not construct another",
        )
    }
}

private fun String.occurrencesOf(needle: String): Int =
    windowed(needle.length).count { it == needle }

private const val MAIN_SCREEN_NAV_HOST_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/host/main/MainScreenNavHost.kt"
