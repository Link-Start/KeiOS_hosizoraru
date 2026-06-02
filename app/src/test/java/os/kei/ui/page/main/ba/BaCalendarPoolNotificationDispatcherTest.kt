package os.kei.ui.page.main.ba

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BaCalendarPoolNotificationDispatcherTest {
    @Test
    fun `calendar pool grouped notification id separates servers for same deadline`() {
        val notifyAtMs = 1_777_392_000_000L

        assertNotEquals(
            baCalendarPoolGroupedNotificationId(
                baseId = BASE_ID,
                serverIndex = 0,
                notifyAtMs = notifyAtMs,
            ),
            baCalendarPoolGroupedNotificationId(
                baseId = BASE_ID,
                serverIndex = 1,
                notifyAtMs = notifyAtMs,
            ),
        )
    }

    @Test
    fun `calendar pool grouped notification id keeps each server inside base bucket`() {
        val notifyAtMs = 1_777_392_000_000L
        val ids =
            (0..2).map { serverIndex ->
                baCalendarPoolGroupedNotificationId(
                    baseId = BASE_ID,
                    serverIndex = serverIndex,
                    notifyAtMs = notifyAtMs,
                )
            }

        assertEquals(ids.distinct(), ids)
        ids.forEach { id ->
            assertTrue(id in BASE_ID until BASE_ID + 900_000)
        }
    }

    private companion object {
        private const val BASE_ID = 389_100_000
    }
}
