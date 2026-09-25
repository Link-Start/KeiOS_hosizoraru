package os.kei.core.download.segmented

import org.junit.Test
import kotlin.test.assertEquals

class RateProbeIdleTrackerTest {
    @Test
    fun `progress resets continuous idle timeout`() {
        var now = 0L
        val tracker = RateProbeIdleTracker(
            timeoutMs = 1_000L,
            nowMs = { now },
        )

        now = 999L
        assertEquals(1L, tracker.millisUntilExpiration())

        tracker.recordProgress()
        now = 1_998L
        assertEquals(1L, tracker.millisUntilExpiration())
        now = 1_999L
        assertEquals(0L, tracker.millisUntilExpiration())
    }
}
