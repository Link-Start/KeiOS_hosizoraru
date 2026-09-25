package os.kei.core.download.segmented

import org.junit.Test
import kotlin.test.assertEquals

class TailIdleTrackerTest {
    @Test
    fun `tail timeout starts only after activation and resets on progress`() {
        var now = 0L
        val tracker = TailIdleTracker(timeoutMs = 1_000L, nowMs = { now })

        now = 5_000L
        assertEquals(Long.MAX_VALUE, tracker.millisUntilExpiration())

        tracker.activate()
        now = 5_999L
        assertEquals(1L, tracker.millisUntilExpiration())

        tracker.recordProgress()
        now = 6_998L
        assertEquals(1L, tracker.millisUntilExpiration())
        now = 6_999L
        assertEquals(0L, tracker.millisUntilExpiration())
    }
}
