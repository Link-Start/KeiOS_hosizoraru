package os.kei.core.download.segmented

import org.junit.Test
import kotlin.test.assertEquals

class RangeLeaseTest {
    @Test
    fun `lease follows remaining bytes, minimum speed and retries`() {
        data class Case(val label: String, val remainingBytes: Long, val retryCount: Int, val expectedMs: Long)
        listOf(
            Case("fresh large part has no lease", 2L * 1024L * 1024L + 1L, retryCount = 0, expectedMs = 0L),
            Case("lease derives from remaining bytes / min speed", 512L * 1024L, retryCount = 0, expectedMs = 8_000L),
            Case("retried small part keeps a bounded minimum", 128L * 1024L, retryCount = 1, expectedMs = 4_000L),
        ).forEach { case ->
            assertEquals(
                case.expectedMs,
                rangeLeaseMs(
                    remainingBytes = case.remainingBytes,
                    retryCount = case.retryCount,
                    minExpectedBytesPerSecond = 64L * 1024L,
                ),
                case.label,
            )
        }
    }

    @Test
    fun `new progress resets continuous no progress expiry`() {
        var now = 0L
        val tracker = RangeLeaseTracker(
            remainingBytes = 128L * 1024L,
            retryCount = 0,
            minExpectedBytesPerSecond = 64L * 1024L,
            nowMs = { now },
        )

        now = 3_999L
        assertEquals(false, tracker.isExpired())

        tracker.recordProgress(remainingBytes = 64L * 1024L)
        now = 7_998L
        assertEquals(false, tracker.isExpired())
        now = 7_999L
        assertEquals(true, tracker.isExpired())
    }
}
