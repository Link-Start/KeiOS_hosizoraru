package os.kei.core.download.segmented

import java.io.IOException
import org.junit.Test
import kotlin.test.assertEquals

class RangeRetryPolicyTest {
    @Test
    fun `failures map to their retry budget`() {
        val cancelled = IOException("cancelled")
        data class Case(val label: String, val actual: RangeFailureKind?, val expected: RangeFailureKind?)
        listOf(
            Case(
                "file writer failure is fatal",
                BoundedAsyncFileWriterException(IOException("disk full")).rangeFailureKindOrNull(),
                null,
            ),
            Case(
                "429 is rate limited",
                SegmentedDownloadHttpException(code = 429, retryable = true).rangeFailureKindOrNull(),
                RangeFailureKind.RateLimited,
            ),
            Case(
                "503 is rate limited",
                SegmentedDownloadHttpException(code = 503, retryable = true).rangeFailureKindOrNull(),
                RangeFailureKind.RateLimited,
            ),
            Case(
                "509 is rate limited on the single stream too",
                SegmentedDownloadHttpException(code = 509, retryable = true).singleStreamFailureKindOrNull(),
                RangeFailureKind.RateLimited,
            ),
            Case(
                "rate probe idle timeout is rate limited",
                RateProbeIdleTimeoutException(cancelled).rangeFailureKindOrNull(),
                RangeFailureKind.RateLimited,
            ),
            Case(
                "tail idle timeout retries as a timeout",
                TailIdleTimeoutException(cancelled).rangeFailureKindOrNull(),
                RangeFailureKind.Timeout,
            ),
        ).forEach { case -> assertEquals(case.expected, case.actual, case.label) }
    }

    @Test
    fun `retry after parses delta seconds`() {
        assertEquals(3_000L, parseRetryAfterMs("3", nowEpochMs = 1_000L))
    }

    @Test
    fun `retry after parses http date`() {
        assertEquals(
            5_000L,
            parseRetryAfterMs(
                value = "Thu, 01 Jan 1970 00:00:10 GMT",
                nowEpochMs = 5_000L,
            ),
        )
    }
}
