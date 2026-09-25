package os.kei.core.prefs

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuperIslandAutoCloseTest {
    @Test
    fun `a missing or unknown stored value means never, and every stored id reads back`() {
        assertEquals(SuperIslandAutoClose.Never, SuperIslandAutoClose.fromStorageId(null))
        assertEquals(SuperIslandAutoClose.Never, SuperIslandAutoClose.fromStorageId("forever"))
        assertEquals(SuperIslandAutoClose.Never, UiPrefs.defaultSnapshot().superIslandAutoClose)
        // The ids are persisted; renaming one would silently reset that user's choice to never.
        assertEquals(
            listOf("never", "5m", "15m", "30m", "1h", "3h", "12h"),
            SuperIslandAutoClose.entries.map { it.storageId },
        )
        SuperIslandAutoClose.entries.forEach { choice ->
            assertEquals(choice, SuperIslandAutoClose.fromStorageId(choice.storageId))
        }
    }

    @Test
    fun `no choice overflows a 32-bit millisecond count on either field`() {
        // The protocol has no "never" value (0 means the default, -1 about five seconds), so Never is a
        // long finite time. Int.MAX_VALUE would look like never and overflow if the host converts it.
        SuperIslandAutoClose.entries.forEach { choice ->
            assertTrue(choice.focusTimeoutMinutes > 0, "$choice sends a non-positive timeout")
            assertTrue(choice.focusTimeoutMinutes.toLong() * 60_000L < Int.MAX_VALUE, "$choice timeout overflows")
            assertTrue(choice.islandTimeoutSeconds.toLong() * 1_000L < Int.MAX_VALUE, "$choice islandTimeout overflows")
        }
    }
}
