package os.kei.ui.pip

import org.junit.Test
import kotlin.test.assertEquals

class AppPictureInPictureSeekTest {
    private data class SeekCase(
        val label: String,
        val currentPositionMs: Long,
        val durationMs: Long,
        val deltaMs: Long,
        val expectedMs: Long,
    )

    @Test
    fun `seek position moves by delta and clamps to the known duration`() {
        listOf(
            SeekCase("moves by delta inside known duration", 50_000L, 120_000L, APP_PIP_SEEK_INTERVAL_10_SECONDS_MS, 60_000L),
            SeekCase("clamps to start", 5_000L, 120_000L, -APP_PIP_SEEK_INTERVAL_10_SECONDS_MS, 0L),
            SeekCase("clamps to duration", 118_000L, 120_000L, APP_PIP_SEEK_INTERVAL_10_SECONDS_MS, 120_000L),
            SeekCase(
                "keeps forward target when duration is unknown",
                118_000L,
                Long.MIN_VALUE + 1L,
                APP_PIP_SEEK_INTERVAL_10_SECONDS_MS,
                128_000L,
            ),
        ).forEach { case ->
            assertEquals(
                case.expectedMs,
                resolveAppPictureInPictureSeekPositionMs(
                    currentPositionMs = case.currentPositionMs,
                    durationMs = case.durationMs,
                    deltaMs = case.deltaMs,
                ),
                case.label,
            )
        }
    }
}
