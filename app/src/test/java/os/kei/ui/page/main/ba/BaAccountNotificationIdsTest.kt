package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.ui.page.main.ba.support.BaAccountId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class BaAccountNotificationIdsTest {
    @Test
    fun `account notification ids are stable and separated by reminder kind`() {
        val accountId = BaAccountId("cn-main")
        val ids = BaAccountNotificationKind.entries.map { it.notificationId(accountId) }

        assertEquals(ids, BaAccountNotificationKind.entries.map { it.notificationId(accountId) })
        assertEquals(ids.distinct(), ids)
        assertEquals(ids[0] + 1, ids[1])
        assertEquals(ids[0] + 2, ids[2])
        assertEquals(ids[0] + 3, ids[3])
    }

    @Test
    fun `account notification ids stay out of legacy fixed id range`() {
        val accountId = BaAccountId("jp-main")
        val legacyIds =
            setOf(
                McpNotificationHelper.BA_AP_NOTIFICATION_ID,
                McpNotificationHelper.BA_CAFE_AP_NOTIFICATION_ID,
                McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID,
                McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID,
            )

        BaAccountNotificationKind.entries.forEach { kind ->
            assertNotEquals(kind.legacyId, kind.notificationId(accountId))
            assertFalse(kind.notificationId(accountId) in legacyIds)
        }
    }

    @Test
    fun `different accounts get different ids for the same reminder kind`() {
        assertNotEquals(
            BaAccountNotificationKind.Ap.notificationId(BaAccountId("cn-main")),
            BaAccountNotificationKind.Ap.notificationId(BaAccountId("cn-alt")),
        )
    }
}
