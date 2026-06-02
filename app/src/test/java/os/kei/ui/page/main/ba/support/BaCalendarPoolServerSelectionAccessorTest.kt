package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals

class BaCalendarPoolServerSelectionAccessorTest {
    @Test
    fun `calendar pool server selection falls back to legacy server before save`() {
        val accessor = BaCalendarPoolServerSelectionAccessor(InMemoryBaAccountKeyValueStore())

        assertEquals(1, accessor.load(legacyServerIndex = 1))
    }

    @Test
    fun `calendar pool server selection persists independently from legacy server`() {
        val store = InMemoryBaAccountKeyValueStore()
        val accessor = BaCalendarPoolServerSelectionAccessor(store)
        store.encode(KEY_SERVER_INDEX, 1)

        assertEquals(2, accessor.save(5))

        assertEquals(2, accessor.load(legacyServerIndex = store.decodeInt(KEY_SERVER_INDEX, 0)))
        assertEquals(1, store.decodeInt(KEY_SERVER_INDEX, 0))
    }

    @Test
    fun `enabled account servers are distinct and preserve account order`() {
        val snapshot =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testAccount(id = "cn-main", serverIndex = 0),
                        testAccount(id = "cn-alt", serverIndex = 0),
                        testAccount(id = "jp-main", serverIndex = 2),
                        testAccount(id = "global-disabled", serverIndex = 1, enabled = false),
                    ),
                activeAccountId = BaAccountId("cn-main"),
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )

        assertEquals(listOf(0, 2), snapshot.enabledServerIndices())
    }

    private fun testAccount(
        id: String,
        serverIndex: Int,
        enabled: Boolean = true,
    ): BaAccountRecord =
        BaAccountRecord(
            profile =
                BaAccountProfile(
                    id = BaAccountId(id),
                    serverIndex = serverIndex,
                    displayName = id,
                    nickname = id,
                    friendCode = "ABCDEFGH",
                    enabled = enabled,
                ),
        )
}
