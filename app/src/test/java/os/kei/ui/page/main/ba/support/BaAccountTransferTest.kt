package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaAccountTransferTest {
    @Test
    fun `export json round trips account state and count`() {
        val activeAccountId = BaAccountId("cn-alt")
        val snapshot =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testAccount(id = "cn-main", serverIndex = 0, sortOrder = 0),
                        testAccount(id = activeAccountId.value, serverIndex = 0, sortOrder = 1),
                    ),
                activeAccountId = activeAccountId,
                allAccountsFollowGlobalNotificationSettings = false,
                globalReminderSettings =
                    BaGlobalReminderSettings(
                        apNotifyEnabled = true,
                        apNotifyThreshold = 120,
                        cafeApNotifyEnabled = true,
                        cafeApNotifyThreshold = 80,
                    ),
            )

        val raw = buildBaAccountsExportJson(snapshot = snapshot, nowMs = 42L)
        val parsed = parseBaAccountsExportJson(raw)

        assertEquals(2, countBaAccountsExportJson(raw))
        assertEquals(42L, parsed.exportedAtMs)
        assertEquals(activeAccountId, parsed.activeAccountId)
        assertFalse(parsed.allAccountsFollowGlobalNotificationSettings)
        assertTrue(parsed.globalReminderSettings.apNotifyEnabled)
        assertEquals(120, parsed.globalReminderSettings.apNotifyThreshold)
        assertEquals(listOf("cn-main", "cn-alt"), parsed.accounts.map { it.profile.id.value })
    }

    @Test
    fun `merge keeps remote account order and preserves local only accounts`() {
        val localActiveAccountId = BaAccountId("local-only")
        val local =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testAccount(id = "cn-main", serverIndex = 0, nickname = "Local", sortOrder = 0),
                        testAccount(id = localActiveAccountId.value, serverIndex = 1, sortOrder = 1),
                    ),
                activeAccountId = localActiveAccountId,
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )
        val remoteActiveAccountId = BaAccountId("jp-alt")
        val remote =
            BaAccountsTransferPayload(
                exportedAtMs = 100L,
                accounts =
                    listOf(
                        testAccount(id = remoteActiveAccountId.value, serverIndex = 2, sortOrder = 0),
                        testAccount(
                            id = "cn-main",
                            serverIndex = 0,
                            nickname = "Remote",
                            sortOrder = 1,
                        ).copy(runtime = BaAccountRuntime(apCurrent = 88.0)),
                    ),
                activeAccountId = remoteActiveAccountId,
                allAccountsFollowGlobalNotificationSettings = false,
                globalReminderSettings =
                    BaGlobalReminderSettings(
                        apNotifyEnabled = true,
                        apNotifyThreshold = 90,
                    ),
            )

        val merged = mergeBaAccountsForSync(local = local, remote = remote, nowMs = 200L)

        assertEquals(
            listOf("jp-alt", "cn-main", "local-only"),
            merged.accounts.map { it.profile.id.value },
        )
        assertEquals(remoteActiveAccountId, merged.activeAccountId)
        assertFalse(merged.allAccountsFollowGlobalNotificationSettings)
        assertTrue(merged.globalReminderSettings.apNotifyEnabled)
        assertEquals(90, merged.globalReminderSettings.apNotifyThreshold)
        val replaced = merged.accounts.first { it.profile.id.value == "cn-main" }
        assertEquals("Remote", replaced.profile.nickname)
        assertEquals(88.0, replaced.runtime.apCurrent)
    }

    @Test
    fun `merge falls back to local active account when remote active is missing`() {
        val localActiveAccountId = BaAccountId("cn-main")
        val local =
            BaAccountStoreSnapshot(
                accounts = listOf(testAccount(id = localActiveAccountId.value, serverIndex = 0)),
                activeAccountId = localActiveAccountId,
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )
        val remote =
            BaAccountsTransferPayload(
                accounts = listOf(testAccount(id = "jp-alt", serverIndex = 2)),
                activeAccountId = BaAccountId("missing"),
            )

        val merged = mergeBaAccountsForSync(local = local, remote = remote, nowMs = 10L)

        assertEquals(localActiveAccountId, merged.activeAccountId)
        assertEquals(listOf("jp-alt", "cn-main"), merged.accounts.map { it.profile.id.value })
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
