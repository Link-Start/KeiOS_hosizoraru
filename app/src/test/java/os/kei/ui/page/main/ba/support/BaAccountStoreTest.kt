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
    fun `runtime updates only active account`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())
        store.saveAccounts(
            listOf(
                testAccount(id = "cn-main", serverIndex = 0, sortOrder = 0),
                testAccount(id = "cn-alt", serverIndex = 0, sortOrder = 1),
            ),
        )
        assertTrue(store.selectActiveAccount(BaAccountId("cn-alt")))

        assertTrue(
            store.updateActiveAccountRuntime { runtime ->
                runtime.copy(
                    apLimit = 180,
                    apCurrent = 77.5,
                    cafeLevel = 8,
                    cafeStoredAp = 90.25,
                    coffeeHeadpatMs = 10_000L,
                )
            },
        )

        val accounts = store.loadAccounts()
        assertEquals(DEFAULT_AP_LIMIT, accounts[0].runtime.apLimit)
        assertEquals(DEFAULT_CAFE_LEVEL, accounts[0].runtime.cafeLevel)
        assertEquals(180, accounts[1].runtime.apLimit)
        assertEquals(77.5, accounts[1].runtime.apCurrent)
        assertEquals(8, accounts[1].runtime.cafeLevel)
        assertEquals(90.25, accounts[1].runtime.cafeStoredAp)
        assertEquals(10_000L, accounts[1].runtime.coffeeHeadpatMs)
    }

    @Test
    fun `runtime updates target account by id`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())
        store.saveAccounts(
            listOf(
                testAccount(id = "cn-main", serverIndex = 0, sortOrder = 0),
                testAccount(id = "cn-alt", serverIndex = 0, sortOrder = 1),
            ),
        )

        assertTrue(
            store.updateAccountRuntime(BaAccountId("cn-main")) { runtime ->
                runtime.copy(
                    apCurrent = 90.0,
                    cafeStoredAp = 45.0,
                )
            },
        )
        assertFalse(
            store.updateAccountRuntime(BaAccountId("missing")) { runtime ->
                runtime.copy(apCurrent = 1.0)
            },
        )

        val accounts = store.loadAccounts()
        assertEquals(90.0, accounts[0].runtime.apCurrent)
        assertEquals(45.0, accounts[0].runtime.cafeStoredAp)
        assertEquals(DEFAULT_AP_CURRENT, accounts[1].runtime.apCurrent)
        assertEquals(DEFAULT_CAFE_STORED_AP, accounts[1].runtime.cafeStoredAp)
    }

    @Test
    fun `profile updates only active account and keeps account id stable`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())
        store.saveAccounts(
            listOf(
                testAccount(id = "jp-main", serverIndex = 2, nickname = "Main", sortOrder = 0),
                testAccount(id = "jp-alt", serverIndex = 2, nickname = "Alt", sortOrder = 1),
            ),
        )
        assertTrue(store.selectActiveAccount(BaAccountId("jp-alt")))

        assertTrue(
            store.updateActiveAccountProfile { profile ->
                profile.copy(
                    id = BaAccountId("should-not-win"),
                    serverIndex = 1,
                    displayName = "Renamed",
                    nickname = "Global",
                    friendCode = "GLFriend",
                )
            },
        )

        val accounts = store.loadAccounts()
        assertEquals(BaAccountId("jp-main"), accounts[0].profile.id)
        assertEquals(2, accounts[0].profile.serverIndex)
        assertEquals("Main", accounts[0].profile.nickname)
        assertEquals(BaAccountId("jp-alt"), accounts[1].profile.id)
        assertEquals(1, accounts[1].profile.serverIndex)
        assertEquals("Renamed", accounts[1].profile.displayName)
        assertEquals("Global", accounts[1].profile.nickname)
        assertEquals("GLFRIEND", accounts[1].profile.friendCode)
    }

    @Test
    fun `reminder runtime updates only active account`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())
        store.saveAccounts(
            listOf(
                testAccount(id = "global-main", serverIndex = 1, sortOrder = 0),
                testAccount(id = "global-alt", serverIndex = 1, sortOrder = 1),
            ),
        )
        assertTrue(store.selectActiveAccount(BaAccountId("global-alt")))

        assertTrue(
            store.updateActiveAccountReminderRuntime { runtime ->
                runtime.copy(
                    apLastNotifiedLevel = 120,
                    cafeApLastNotifiedLevel = 130,
                    arenaRefreshLastNotifiedSlotMs = 4000L,
                    cafeVisitLastNotifiedSlotMs = 5000L,
                )
            },
        )

        val accounts = store.loadAccounts()
        assertEquals(-1, accounts[0].reminderRuntime.apLastNotifiedLevel)
        assertEquals(-1, accounts[0].reminderRuntime.cafeApLastNotifiedLevel)
        assertEquals(0L, accounts[0].reminderRuntime.arenaRefreshLastNotifiedSlotMs)
        assertEquals(120, accounts[1].reminderRuntime.apLastNotifiedLevel)
        assertEquals(130, accounts[1].reminderRuntime.cafeApLastNotifiedLevel)
        assertEquals(4000L, accounts[1].reminderRuntime.arenaRefreshLastNotifiedSlotMs)
        assertEquals(5000L, accounts[1].reminderRuntime.cafeVisitLastNotifiedSlotMs)
    }

    @Test
    fun `reminder runtime updates target account by id`() {
        val store = BaAccountStore(InMemoryBaAccountKeyValueStore())
        store.saveAccounts(
            listOf(
                testAccount(id = "global-main", serverIndex = 1, sortOrder = 0),
                testAccount(id = "global-alt", serverIndex = 1, sortOrder = 1),
            ),
        )

        assertTrue(
            store.updateAccountReminderRuntime(BaAccountId("global-main")) { runtime ->
                runtime.copy(
                    apLastNotifiedLevel = 111,
                    cafeVisitLastNotifiedSlotMs = 6_000L,
                )
            },
        )
        assertFalse(
            store.updateAccountReminderRuntime(BaAccountId("missing")) { runtime ->
                runtime.copy(apLastNotifiedLevel = 1)
            },
        )

        val accounts = store.loadAccounts()
        assertEquals(111, accounts[0].reminderRuntime.apLastNotifiedLevel)
        assertEquals(6_000L, accounts[0].reminderRuntime.cafeVisitLastNotifiedSlotMs)
        assertEquals(-1, accounts[1].reminderRuntime.apLastNotifiedLevel)
        assertEquals(0L, accounts[1].reminderRuntime.cafeVisitLastNotifiedSlotMs)
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
