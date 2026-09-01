package os.kei.ui.page.main.ba

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class BaCalendarPoolThemeSourceTest {
    /**
     * One route, one gradient, and it reads the app's theme rather than the system's.
     *
     * The calendar and the banner list were two routes with a copy of this wash each; they are two tabs of
     * one page now, so there is one. The count still matters: a second `isAppInDarkTheme()` here would mean
     * the wash had been duplicated per tab, which is what merging them was meant to stop.
     */
    @Test
    fun calendarPoolRouteGradientsFollowTheKeiOSAppTheme() {
        val source = sourceFile(BA_CALENDAR_POOL_SOURCE)

        assertFalse("isSystemInDarkTheme" in source)
        assertEquals(1, source.occurrencesOf("isAppInDarkTheme()"))
    }

    /** And the two halves stayed halves: neither list content grew a scaffold or a wash of its own. */
    @Test
    fun neitherHalfCarriesItsOwnPageChrome() {
        listOf(
            sourceFile(BA_ACTIVITY_CALENDAR_SOURCE),
            sourceFile(BA_POOL_SOURCE),
        ).forEach { source ->
            assertFalse("AppPageScaffold(" in source)
            assertEquals(0, source.occurrencesOf("isAppInDarkTheme()"))
        }
    }
}

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from $workingDirectory"
    }.readText()
}

private fun String.occurrencesOf(needle: String): Int = windowed(needle.length).count { it == needle }

private const val BA_CALENDAR_POOL_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaCalendarPoolPage.kt"
private const val BA_ACTIVITY_CALENDAR_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaActivityCalendarPage.kt"
private const val BA_POOL_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaPoolPage.kt"
