package os.kei.ui.page.main.student

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmQueueMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmMediaButtonPreferencesTest {
    @Test
    fun `repeat and stop buttons use stable actions and slots`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val buttons = BaGuideBgmMediaButtonPreferences.mediaButtonPreferences(
            context = context,
            queueMode = BaGuideBgmQueueMode.Continuous
        )

        assertEquals(2, buttons.size)
        val repeat = buttons[0]
        assertEquals(CommandButton.ICON_REPEAT_ALL, repeat.icon)
        assertEquals(BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT, repeat.sessionCommand?.customAction)
        assertEquals("Continuous play", repeat.displayName.toString())
        assertEquals(
            listOf(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW),
            repeat.slots.toArray().toList()
        )

        val stop = buttons[1]
        assertEquals(CommandButton.ICON_STOP, stop.icon)
        assertEquals(
            BA_GUIDE_BGM_COMMAND_STOP_PLAYBACK,
            stop.sessionCommand?.customAction
        )
        assertEquals("Stop playback", stop.displayName.toString())
        assertEquals(
            listOf(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW),
            stop.slots.toArray().toList()
        )
    }

    @Test
    fun `single loop mode uses repeat one icon`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repeat = BaGuideBgmMediaButtonPreferences.mediaButtonPreferences(
            context = context,
            queueMode = BaGuideBgmQueueMode.SingleLoop
        ).first()

        assertEquals(CommandButton.ICON_REPEAT_ONE, repeat.icon)
        assertEquals("Single-track loop", repeat.displayName.toString())
    }

    @Test
    fun `custom commands are available to media controllers`() {
        val commands = BaGuideBgmMediaButtonPreferences.availableSessionCommands(
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
        )

        assertTrue(
            commands.contains(
                SessionCommand(
                    BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT,
                    android.os.Bundle.EMPTY
                )
            )
        )
        assertTrue(
            commands.contains(
                SessionCommand(
                    BA_GUIDE_BGM_COMMAND_STOP_PLAYBACK,
                    android.os.Bundle.EMPTY
                )
            )
        )
    }

    @Test
    fun `repeat mode maps between ui mode and native player mode`() {
        assertEquals(
            Player.REPEAT_MODE_ALL,
            BaGuideBgmMediaButtonPreferences.nativeRepeatMode(BaGuideBgmQueueMode.Continuous)
        )
        assertEquals(
            Player.REPEAT_MODE_ONE,
            BaGuideBgmMediaButtonPreferences.nativeRepeatMode(BaGuideBgmQueueMode.SingleLoop)
        )
        assertEquals(
            BaGuideBgmQueueMode.SingleLoop,
            BaGuideBgmMediaButtonPreferences.queueModeFromNativeRepeatMode(Player.REPEAT_MODE_ONE)
        )
        assertEquals(
            BaGuideBgmQueueMode.Continuous,
            BaGuideBgmMediaButtonPreferences.nextQueueMode(BaGuideBgmQueueMode.SingleLoop)
        )
    }

    @Test
    fun `provider small icon is the KeiOS notification icon`() {
        assertEquals(R.drawable.ic_launcher_monochrome, BA_GUIDE_BGM_MEDIA_AOSP_SMALL_ICON_RES)
        assertEquals(R.drawable.ic_launcher_foreground, BA_GUIDE_BGM_MEDIA_XIAOMI_SMALL_ICON_RES)
        assertNotNull(
            BaGuideBgmMediaNotificationProviderFactory.create(
                ApplicationProvider.getApplicationContext()
            )
        )
    }
}
