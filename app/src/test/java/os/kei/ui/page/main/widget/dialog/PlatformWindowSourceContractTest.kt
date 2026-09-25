package os.kei.ui.page.main.widget.dialog

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import os.kei.ui.testing.repoRoot
import os.kei.ui.testing.withoutComments

/**
 * Presentations open in this window, not a new platform one.
 *
 * A Liquid Glass surface samples a `LayerBackdrop` of the content behind it, and that backdrop cannot be
 * read from a separate `Dialog` or `Popup` window: the blur silently draws nothing. So every dialog, sheet
 * and menu goes through the shared host, which keeps it in-window, and the one place that does open a
 * platform window is named here.
 *
 * This replaces five per-screen tests that each asserted one dialog used the shared host. They could only
 * notice the screen they read; this notices any screen.
 */
class PlatformWindowSourceContractTest {
    @Test
    fun onlyTheSharedHostsOpenAPlatformWindow() {
        val offenders =
            platformWindowCalls()
                .filterNot { (path, _) -> path in ALLOWED }

        assertTrue(
            offenders.isEmpty(),
            "These open a platform window, where Liquid Glass cannot sample the page: $offenders. " +
                "Host the presentation through AppWindowDialogHost or the in-window Liquid overlay portal.",
        )
    }

    @Test
    fun theAllowedHostsStillOpenOneSoThisTestCannotPassVacuously() {
        assertEquals(ALLOWED, platformWindowCalls().map { (path, _) -> path }.toSet())
    }

    private fun platformWindowCalls(): List<Pair<String, String>> =
        MODULES
            .map { module -> File(repoRoot(), module) }
            .filter(File::isDirectory)
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { file ->
                val path = file.relativeTo(repoRoot()).path
                PLATFORM_WINDOW.findAll(file.readText().withoutComments()).map { match -> path to match.value }
            }

    private companion object {
        val MODULES = listOf("app/src/main", "ui-liquid-glass/src/main", "ui-pip/src/main")

        /** A bare call: not `GitHubDeleteTrackDialog(`, not `x.Dialog(`, not a definition. */
        val PLATFORM_WINDOW = Regex("""(?<![A-Za-z0-9_.])(Dialog|Popup|AlertDialog|BasicAlertDialog)\s*\(""")

        val ALLOWED =
            setOf(
                "app/src/main/java/os/kei/ui/page/main/widget/dialog/AppWindowDialogHosts.kt",
            )
    }
}
