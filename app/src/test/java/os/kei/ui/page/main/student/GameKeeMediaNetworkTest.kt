package os.kei.ui.page.main.student

import org.junit.Test
import kotlin.test.assertEquals

class GameKeeMediaNetworkTest {
    @Test
    fun `cleanup waits until configured interval passes`() {
        assertEquals(
            false,
            shouldRunGameKeeMediaCacheCleanup(
                nowMs = 2_000L,
                lastCleanupMs = 1_500L,
                checkIntervalMs = 1_000L,
            ),
        )
        assertEquals(
            true,
            shouldRunGameKeeMediaCacheCleanup(
                nowMs = 2_500L,
                lastCleanupMs = 1_500L,
                checkIntervalMs = 1_000L,
            ),
        )
    }
}
