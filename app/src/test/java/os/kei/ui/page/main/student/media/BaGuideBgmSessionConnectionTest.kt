package os.kei.ui.page.main.student.media

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.student.BA_GUIDE_BGM_COMMAND_STOP_PLAYBACK
import os.kei.ui.page.main.student.BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT
import os.kei.ui.page.main.student.BaGuideBgmMediaButtonPreferences
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the BGM session hands a controller when it connects.
 *
 * Media3 1.11 deprecated `AcceptedResultBuilder(session)` because it granted the trusted defaults to
 * every caller. The replacement branches on `ControllerInfo.isTrusted()`, and adopting it is only
 * worth anything if this session's own commands are layered *on top of* whichever set Media3 picked
 * rather than on a hardcoded trusted one — otherwise the migration compiles, the warning goes away,
 * and an untrusted controller keeps the full session command set through our own helper.
 *
 * Building a real `MediaSession` here would need a real `ExoPlayer`, so the two halves are pinned
 * where they actually live: the helper is held to being base-preserving, and the call site is held to
 * passing the controller through.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmSessionConnectionTest {
    @Test
    fun `the session command helper adds two commands and invents none`() {
        listOf(
            "trusted" to MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
            "untrusted" to MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_COMMANDS,
        ).forEach { (label, base) ->
            val granted = BaGuideBgmMediaButtonPreferences.availableSessionCommands(base)

            assertTrue(
                base.commands.all { command -> granted.contains(command) },
                "$label: the helper dropped one of Media3's own commands",
            )
            assertEquals(
                base.commands.size + 2,
                granted.commands.size,
                "$label: the helper added something other than the repeat and stop commands",
            )
            listOf(BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT, BA_GUIDE_BGM_COMMAND_STOP_PLAYBACK).forEach { action ->
                assertTrue(
                    granted.commands.any { command -> command.customAction == action },
                    "$label: $action is missing, so its notification button would render disabled",
                )
            }
        }
    }

    /**
     * The trust-aware base is only reachable through the controller, so losing the argument would
     * silently restore the old behaviour while still compiling. `DEFAULT_SESSION_COMMANDS` appearing
     * at the call site is the same regression wearing the new constructor.
     */
    @Test
    fun `onConnect asks Media3 what this controller should get`() {
        val source = File(repositoryRoot(), SERVICE_SOURCE)
        assertTrue(source.isFile, "Missing $SERVICE_SOURCE")
        val body = source.readText().substringAfter("override fun onConnect(").substringBefore("\n        override fun")

        assertTrue(
            "AcceptedResultBuilder(session, controller)" in body,
            "onConnect must build on the controller-aware overload, not the deprecated session-only one",
        )
        assertFalse(
            "DEFAULT_SESSION_COMMANDS" in body,
            "onConnect hardcodes the trusted command set, which is what the deprecated overload did",
        )
    }
}

private const val SERVICE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/media/BaGuideBgmMediaSessionService.kt"

private fun repositoryRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, SERVICE_SOURCE).isFile },
    ) {
        "Unable to locate the repository root from $workingDirectory"
    }
}
