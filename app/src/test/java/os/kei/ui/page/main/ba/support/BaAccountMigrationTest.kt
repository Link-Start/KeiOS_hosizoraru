package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BaAccountMigrationTest {
    @Test
    fun `shared legacy identity migrates to one active account`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val idSettings = BaIdSettingsAccessor(backingStore)
        idSettings.saveIndependentByServerEnabled(false)
        idSettings.saveNickname("Shared")
        idSettings.saveFriendCode("ABCDEFGH")
        backingStore.encode(KEY_SERVER_INDEX, 1)
        backingStore.encode(KEY_AP_LIMIT, 180)
        backingStore.encode(KEY_AP_CURRENT_EXACT, "77.25")
        backingStore.encode(KEY_AP_REGEN_BASE_MS, 1000L)
        backingStore.encode(KEY_AP_SYNC_MS, 2000L)
        backingStore.encode(KEY_CAFE_LEVEL, 7)
        backingStore.encode(KEY_CAFE_STORED_AP, "80.5")
        backingStore.encode(KEY_CAFE_LAST_HOUR_MS, 3000L)
        backingStore.encode(KEY_AP_LAST_NOTIFIED_LEVEL, 120)
        backingStore.encode(KEY_CAFE_AP_LAST_NOTIFIED_LEVEL, 130)
        backingStore.encode(KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS, 4000L)
        backingStore.encode(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, 5000L)

        val accountStore = BaAccountStore(backingStore)
        val migration = BaAccountMigration(accountStore, backingStore)

        val result = migration.migrateLegacyIfNeeded()

        assertEquals(BaAccountMigrationStatus.Migrated, result.status)
        assertEquals(1, result.accountCount)
        assertEquals(BaAccountId("legacy-main"), result.activeAccountId)
        val state = accountStore.loadState()
        val account = state.accounts.single()
        assertEquals(BaAccountId("legacy-main"), account.profile.id)
        assertEquals(1, account.profile.serverIndex)
        assertEquals("Shared", account.profile.nickname)
        assertEquals("ABCDEFGH", account.profile.friendCode)
        assertEquals(180, account.runtime.apLimit)
        assertEquals(77.25, account.runtime.apCurrent)
        assertEquals(7, account.runtime.cafeLevel)
        assertEquals(80.5, account.runtime.cafeStoredAp)
        assertEquals(120, account.reminderRuntime.apLastNotifiedLevel)
        assertEquals(130, account.reminderRuntime.cafeApLastNotifiedLevel)
        assertEquals(4000L, account.reminderRuntime.arenaRefreshLastNotifiedSlotMs)
        assertEquals(5000L, account.reminderRuntime.cafeVisitLastNotifiedSlotMs)
    }

    @Test
    fun `server specific legacy identity migrates three server accounts`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val idSettings = BaIdSettingsAccessor(backingStore)
        idSettings.saveNickname("Shared")
        idSettings.saveFriendCode("ABCDEFGH")
        idSettings.saveIndependentByServerEnabled(true)
        idSettings.saveNickname("Global", serverIndex = 1)
        idSettings.saveFriendCode("GLFriend", serverIndex = 1)
        idSettings.saveNickname("JP", serverIndex = 2)
        idSettings.saveFriendCode("JPFriend", serverIndex = 2)
        backingStore.encode(KEY_SERVER_INDEX, 1)
        backingStore.encode(KEY_AP_LIMIT, 200)
        backingStore.encode(KEY_AP_CURRENT_EXACT, "88.5")
        backingStore.encode(KEY_CAFE_LEVEL, 8)
        backingStore.encode(KEY_CAFE_STORED_AP, "90.25")
        backingStore.encode(KEY_AP_LAST_NOTIFIED_LEVEL, 150)

        val accountStore = BaAccountStore(backingStore)
        val migration = BaAccountMigration(accountStore, backingStore)

        val result = migration.migrateLegacyIfNeeded()

        assertEquals(BaAccountMigrationStatus.Migrated, result.status)
        assertEquals(3, result.accountCount)
        assertEquals(BaAccountId("legacy-server-1"), result.activeAccountId)
        val accounts = accountStore.loadAccounts()
        assertEquals(listOf("legacy-server-0", "legacy-server-1", "legacy-server-2"), accounts.map { it.profile.id.value })
        assertEquals("Shared", accounts[0].profile.nickname)
        assertEquals("ABCDEFGH", accounts[0].profile.friendCode)
        assertEquals("Global", accounts[1].profile.nickname)
        assertEquals("GLFRIEND", accounts[1].profile.friendCode)
        assertEquals("JP", accounts[2].profile.nickname)
        assertEquals("JPFRIEND", accounts[2].profile.friendCode)
        assertEquals(DEFAULT_AP_LIMIT, accounts[0].runtime.apLimit)
        assertEquals(200, accounts[1].runtime.apLimit)
        assertEquals(88.5, accounts[1].runtime.apCurrent)
        assertEquals(8, accounts[1].runtime.cafeLevel)
        assertEquals(90.25, accounts[1].runtime.cafeStoredAp)
        assertEquals(150, accounts[1].reminderRuntime.apLastNotifiedLevel)
        assertEquals(DEFAULT_AP_LIMIT, accounts[2].runtime.apLimit)
        assertFalse(idSettings.loadIndependentByServerEnabled())
    }

    @Test
    fun `legacy migration is idempotent`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val idSettings = BaIdSettingsAccessor(backingStore).apply {
            saveIndependentByServerEnabled(true)
            saveNickname("Shared")
            saveFriendCode("ABCDEFGH")
        }
        val accountStore = BaAccountStore(backingStore)
        val migration = BaAccountMigration(accountStore, backingStore)

        val first = migration.migrateLegacyIfNeeded()
        val second = migration.migrateLegacyIfNeeded()

        assertEquals(BaAccountMigrationStatus.Migrated, first.status)
        assertEquals(BaAccountMigrationStatus.AlreadyInitialized, second.status)
        assertEquals(first.accountCount, second.accountCount)
        assertEquals(accountStore.loadAccounts().map { it.profile.id.value }.distinct().size, accountStore.loadAccounts().size)
        assertFalse(idSettings.loadIndependentByServerEnabled())
    }

    @Test
    fun `already initialized accounts clear lingering legacy server id mode`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val idSettings = BaIdSettingsAccessor(backingStore).apply {
            saveIndependentByServerEnabled(true)
            saveNickname("Shared")
            saveFriendCode("ABCDEFGH")
        }
        val accountStore = BaAccountStore(backingStore)
        accountStore.addAccount(
            BaAccountRecord(
                profile =
                    BaAccountProfile(
                        id = BaAccountId("manual-1"),
                        serverIndex = 2,
                        displayName = "JP",
                        nickname = "JP",
                        friendCode = "JPFriend",
                    ),
            ),
        )

        val result = BaAccountMigration(accountStore, backingStore).migrateLegacyIfNeeded()

        assertEquals(BaAccountMigrationStatus.AlreadyInitialized, result.status)
        assertEquals(1, result.accountCount)
        assertFalse(idSettings.loadIndependentByServerEnabled())
    }
}
