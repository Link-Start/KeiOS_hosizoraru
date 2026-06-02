package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaAccountStoreTest {
    @Test
    fun `same server can hold multiple accounts`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())
        val first = testAccount(id = "cn-main", serverIndex = 0, nickname = "Main", sortOrder = 0)
        val second = testAccount(id = "cn-alt", serverIndex = 0, nickname = "Alt", sortOrder = 1)

        store.saveAccounts(listOf(first, second))

        val accounts = store.loadAccounts()
        assertEquals(listOf("cn-main", "cn-alt"), accounts.map { it.profile.id.value })
        assertEquals(listOf(0, 0), accounts.map { it.profile.serverIndex })
        assertEquals(BaAccountId("cn-main"), store.loadState().activeAccountId)
    }

    @Test
    fun `active account is repaired when saved id disappears`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val store = BaAccountStore(backingStore)
        store.saveAccounts(
            listOf(
                testAccount(id = "jp-main", serverIndex = 2, sortOrder = 0),
                testAccount(id = "jp-alt", serverIndex = 2, sortOrder = 1),
            ),
        )
        assertTrue(store.selectActiveAccount(BaAccountId("jp-alt")))

        assertTrue(store.deleteAccount(BaAccountId("jp-alt")))

        assertEquals(BaAccountId("jp-main"), store.loadState().activeAccountId)
        assertEquals("jp-main", backingStore.decodeString(KEY_BA_ACTIVE_ACCOUNT_ID, ""))
    }

    @Test
    fun `active account key is cleared when every account is deleted`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val store = BaAccountStore(backingStore)
        store.saveAccounts(listOf(testAccount(id = "only", serverIndex = 1)))
        assertEquals(BaAccountId("only"), store.loadState().activeAccountId)

        assertTrue(store.deleteAccount(BaAccountId("only")))

        assertNull(store.loadState().activeAccountId)
        assertFalse(backingStore.containsKey(KEY_BA_ACTIVE_ACCOUNT_ID))
    }

    @Test
    fun `all accounts follow global notification setting defaults on and persists`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())

        assertTrue(store.loadAllAccountsFollowGlobalNotificationSettings())

        store.saveAllAccountsFollowGlobalNotificationSettings(false)

        assertFalse(store.loadAllAccountsFollowGlobalNotificationSettings())
    }

    @Test
    fun `custom reminder settings are ignored while all accounts follow global`() {
        val account =
            testAccount(
                id = "custom",
                serverIndex = 2,
            ).copy(
                profile =
                    testAccount(id = "custom", serverIndex = 2)
                        .profile
                        .copy(notificationMode = BaAccountNotificationMode.Custom),
                reminderOverride =
                    BaAccountReminderOverride(
                        accountId = BaAccountId("custom"),
                        apNotifyEnabled = true,
                        apNotifyThreshold = 50,
                        cafeApNotifyEnabled = true,
                        cafeApNotifyThreshold = 60,
                    ),
            )
        val global =
            BaGlobalReminderSettings(
                apNotifyEnabled = true,
                apNotifyThreshold = 120,
                cafeApNotifyEnabled = false,
                cafeApNotifyThreshold = 130,
            )

        val effective =
            account.effectiveReminderSettings(
                globalSettings = global,
                allAccountsFollowGlobalNotificationSettings = true,
            )

        assertEquals(global, effective)
    }

    @Test
    fun `custom reminder settings apply when global follow is disabled`() {
        val accountId = BaAccountId("custom")
        val account =
            testAccount(
                id = accountId.value,
                serverIndex = 2,
            ).copy(
                profile =
                    testAccount(id = accountId.value, serverIndex = 2)
                        .profile
                        .copy(notificationMode = BaAccountNotificationMode.Custom),
                reminderOverride =
                    BaAccountReminderOverride(
                        accountId = accountId,
                        apNotifyEnabled = true,
                        apNotifyThreshold = 50,
                        cafeApNotifyEnabled = true,
                        cafeApNotifyThreshold = 60,
                        arenaRefreshNotifyEnabled = true,
                        cafeVisitNotifyEnabled = true,
                    ),
            )

        val effective =
            account.effectiveReminderSettings(
                globalSettings = BaGlobalReminderSettings(),
                allAccountsFollowGlobalNotificationSettings = false,
            )

        assertTrue(effective.apNotifyEnabled)
        assertEquals(50, effective.apNotifyThreshold)
        assertTrue(effective.cafeApNotifyEnabled)
        assertEquals(60, effective.cafeApNotifyThreshold)
        assertTrue(effective.arenaRefreshNotifyEnabled)
        assertTrue(effective.cafeVisitNotifyEnabled)
    }

    private fun testAccount(
        id: String,
        serverIndex: Int,
        nickname: String = "Kei",
        sortOrder: Int = 0,
    ): BaAccountRecord =
        BaAccountRecord(
            profile =
                BaAccountProfile(
                    id = BaAccountId(id),
                    serverIndex = serverIndex,
                    displayName = nickname,
                    nickname = nickname,
                    friendCode = "ABCDEFGH",
                    sortOrder = sortOrder,
                ),
        )
}
