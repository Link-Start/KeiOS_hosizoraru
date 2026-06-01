package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaGuideBgmPlaybackRuntimeStateTest {
    @Test
    fun `seek runtime keeps playing intent while backend reports transient paused state`() {
        val nextState =
            resolveBaGuideBgmSeekRuntimeState(
                currentState =
                    BaGuideBgmPlaybackRuntimeState(
                        positionMs = 12_000L,
                        durationMs = 200_000L,
                        isPlaying = true,
                    ),
                backendState =
                    BaGuideBgmPlaybackRuntimeState(
                        positionMs = 12_100L,
                        durationMs = 200_000L,
                        isPlaying = false,
                        isBuffering = true,
                    ),
                progress = 0.5f,
            )

        assertTrue(nextState.isPlaying)
        assertTrue(nextState.isBuffering)
        assertEquals(100_000L, nextState.positionMs)
        assertEquals(200_000L, nextState.durationMs)
    }

    @Test
    fun `seek runtime keeps paused state for paused playback`() {
        val nextState =
            resolveBaGuideBgmSeekRuntimeState(
                currentState =
                    BaGuideBgmPlaybackRuntimeState(
                        positionMs = 12_000L,
                        durationMs = 200_000L,
                        isPlaying = false,
                    ),
                backendState =
                    BaGuideBgmPlaybackRuntimeState(
                        positionMs = 12_100L,
                        durationMs = 200_000L,
                        isPlaying = false,
                    ),
                progress = 0.25f,
            )

        assertFalse(nextState.isPlaying)
        assertEquals(50_000L, nextState.positionMs)
        assertEquals(200_000L, nextState.durationMs)
    }

    @Test
    fun `seek runtime prefers backend duration when it becomes available`() {
        val nextState =
            resolveBaGuideBgmSeekRuntimeState(
                currentState =
                    BaGuideBgmPlaybackRuntimeState(
                        positionMs = 0L,
                        durationMs = 0L,
                        isPlaying = true,
                    ),
                backendState =
                    BaGuideBgmPlaybackRuntimeState(
                        positionMs = 0L,
                        durationMs = 90_000L,
                        isPlaying = true,
                    ),
                progress = 0.75f,
            )

        assertTrue(nextState.isPlaying)
        assertEquals(67_500L, nextState.positionMs)
        assertEquals(90_000L, nextState.durationMs)
    }
}
