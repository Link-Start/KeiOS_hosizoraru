package os.kei.ui.page.main.student.section.gallery

import android.app.Application
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GuideWebMemoryLobbyBgmTest {
    @Test
    fun `music loops and starts only once the scene is playing in the foreground`() {
        val recording = RecordingPlayer()
        val controller = recording.controller()
        assertEquals(Player.REPEAT_MODE_ONE, recording.repeatMode)
        assertEquals(1, recording.items)
        controller.update(false, false, true, 0)
        assertFalse(recording.playing)
        assertFalse(recording.handlesAudioFocus)
        controller.update(true, false, true, 0)
        assertTrue(recording.playing)
        assertTrue(recording.handlesAudioFocus)
        assertEquals(1, recording.prepares)
    }

    @Test
    fun `mute and restoring volume preserve the current music instead of restarting it`() {
        val recording = RecordingPlayer()
        val controller = recording.controller()
        recording.position = 12_000L
        controller.update(true, false, true, 0)
        controller.update(true, true, true, 0)
        assertEquals(0f, recording.volume)
        assertFalse(recording.playing)
        assertFalse(recording.handlesAudioFocus)
        controller.update(true, false, true, 0)
        assertEquals(1f, recording.volume)
        assertTrue(recording.playing)
        assertTrue(recording.handlesAudioFocus)
        assertEquals(12_000L, recording.position)
        assertEquals(1, recording.items)
        assertEquals(1, recording.prepares)
    }

    @Test
    fun `background and playback pause keep position and resume the existing music`() {
        val recording = RecordingPlayer()
        val controller = recording.controller()
        recording.position = 34_000L
        controller.update(true, false, true, 0)
        controller.update(true, false, false, 0)
        assertFalse(recording.playing)
        assertFalse(recording.handlesAudioFocus)
        controller.update(true, false, true, 0)
        controller.update(false, false, true, 0)
        assertFalse(recording.playing)
        assertFalse(recording.handlesAudioFocus)
        controller.update(true, false, true, 0)
        assertTrue(recording.playing)
        assertTrue(recording.handlesAudioFocus)
        assertEquals(34_000L, recording.position)
        assertEquals(1, recording.items)
    }

    @Test
    fun `reloading the Web scene preserves healthy music but retries failed audio`() {
        val recording = RecordingPlayer()
        val controller = recording.controller()
        recording.position = 56_000L
        controller.update(true, false, true, 1)
        assertEquals(1, recording.prepares)
        recording.state = Player.STATE_IDLE
        controller.update(true, false, true, 2)
        assertEquals(2, recording.prepares)
        controller.update(true, false, true, 2)
        assertEquals(2, recording.prepares)
        assertEquals(1, recording.items)
        assertEquals(56_000L, recording.position)
    }

    @Test
    fun `leaving the lobby releases its audio player`() {
        val recording = RecordingPlayer()
        val controller = recording.controller()
        controller.release()
        assertEquals(1, recording.releases)
    }

    private class RecordingPlayer {
        var repeatMode = Player.REPEAT_MODE_OFF
        var playing = false
        var handlesAudioFocus = true
        var volume = 1f
        var state = Player.STATE_READY
        var position = 0L
        var items = 0
        var prepares = 0
        var releases = 0
        private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getAudioAttributes" -> AudioAttributes.DEFAULT
                "setAudioAttributes" -> {
                    assertEquals(AudioAttributes.DEFAULT, args!![0])
                    handlesAudioFocus = args[1] as Boolean
                    if (!handlesAudioFocus) assertFalse(playing)
                    null
                }
                "setRepeatMode" -> { repeatMode = args!![0] as Int; null }
                "setPlayWhenReady" -> { playing = args!![0] as Boolean; null }
                "setVolume" -> { volume = args!![0] as Float; null }
                "setMediaItem" -> { items += 1; position = 0L; null }
                "getPlaybackState" -> state
                "prepare" -> { prepares += 1; null }
                "release" -> { releases += 1; null }
                else -> error("Unexpected playback operation ${method.name}")
            }
        } as Player
        fun controller() = GuideWebMemoryLobbyBgmController(player, "https://cdnimg-v2.gamekee.com/bgm.mp3")
    }
}
