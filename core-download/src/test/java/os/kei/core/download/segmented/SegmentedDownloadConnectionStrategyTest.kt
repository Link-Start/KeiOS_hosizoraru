package os.kei.core.download.segmented

import kotlin.test.assertEquals
import okhttp3.Protocol
import org.junit.Test

class SegmentedDownloadConnectionStrategyTest {
    @Test
    fun `adaptive shares an HTTP 1 pool and isolates HTTP 2 workers`() {
        val adaptive = SegmentedDownloadConnectionStrategy.Adaptive

        assertEquals(SegmentedDownloadConnectionStrategy.Shared, resolveConnectionStrategy(adaptive, Protocol.HTTP_1_1))
        assertEquals(
            SegmentedDownloadConnectionStrategy.IsolatedPerWorker,
            resolveConnectionStrategy(adaptive, Protocol.HTTP_2),
        )
    }
}
