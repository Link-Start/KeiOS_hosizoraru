package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaAccountTransferTest {
    @Test
    fun `custom reminder settings retain hourly AP read suppression through editor input and export`() {
        val customSettings =
            BaGlobalReminderSettings(
                apNotifyEnabled = true,
                cafeApNotifyEnabled = true,
                keepApRemindersReadUntilBelowThreshold = false,
            )
        val accountId = BaAccountId("cn-main")
        val account =
            testBaAccountRecord(id = accountId.value, serverIndex = 0).copy(
                reminderOverride = customSettings.toAccountReminderOverride(accountId),
            )
        val snapshot =
            BaAccountStoreSnapshot(
                accounts = listOf(account),
                activeAccountId = accountId,
                allAccountsFollowGlobalNotificationSettings = false,
                globalReminderSettings = BaGlobalReminderSettings(),
            )

        val parsed = parseBaAccountsExportJson(buildBaAccountsExportJson(snapshot, nowMs = 42L))
        val restoredOverride = parsed.accounts.single().reminderOverride

        assertFalse(customSettings.keepApRemindersReadUntilBelowThreshold)
        assertFalse(restoredOverride?.keepApRemindersReadUntilBelowThreshold ?: true)
    }

    @Test
    fun `export json round trips account state and count`() {
        val activeAccountId = BaAccountId("cn-alt")
        val snapshot =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testBaAccountRecord(id = "cn-main", serverIndex = 0, sortOrder = 0),
                        testBaAccountRecord(id = activeAccountId.value, serverIndex = 0, sortOrder = 1),
                    ),
                activeAccountId = activeAccountId,
                allAccountsFollowGlobalNotificationSettings = false,
                globalReminderSettings =
                    BaGlobalReminderSettings(
                        apNotifyEnabled = true,
                        apNotifyThreshold = 120,
                        cafeApNotifyEnabled = true,
                        cafeApNotifyThreshold = 80,
                        keepApRemindersReadUntilBelowThreshold = false,
                    ),
            )

        val raw = buildBaAccountsExportJson(snapshot = snapshot, nowMs = 42L)
        val parsed = parseBaAccountsExportJson(raw)

        assertEquals(2, countBaAccountsExportJson(raw))
        assertFalse(raw.contains("suppression_anchor", ignoreCase = true))
        assertEquals(42L, parsed.exportedAtMs)
        assertEquals(activeAccountId, parsed.activeAccountId)
        assertFalse(parsed.allAccountsFollowGlobalNotificationSettings)
        assertTrue(parsed.globalReminderSettings.apNotifyEnabled)
        assertEquals(120, parsed.globalReminderSettings.apNotifyThreshold)
        assertFalse(parsed.globalReminderSettings.keepApRemindersReadUntilBelowThreshold)
        assertEquals(listOf("cn-main", "cn-alt"), parsed.accounts.map { it.profile.id.value })
    }

    @Test
    fun `merge keeps local account fields when remote copy has no newer field timestamps`() {
        val localActiveAccountId = BaAccountId("local-only")
        val local =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testBaAccountRecord(
                            id = "cn-main",
                            serverIndex = 0,
                            nickname = "Local",
                            sortOrder = 0,
                            runtime = BaAccountRuntime(apCurrent = 66.0),
                            profileUpdatedAtMs = 1_000L,
                            runtimeUpdatedAtMs = 1_000L,
                        ),
                        testBaAccountRecord(id = localActiveAccountId.value, serverIndex = 1, sortOrder = 1),
                    ),
                activeAccountId = localActiveAccountId,
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
                activeAccountUpdatedAtMs = 1_000L,
                allAccountsFollowGlobalNotificationSettingsUpdatedAtMs = 1_000L,
                globalReminderSettingsUpdatedAtMs = 1_000L,
            )
        val remoteActiveAccountId = BaAccountId("jp-alt")
        val remote =
            BaAccountsTransferPayload(
                exportedAtMs = 100L,
                accounts =
                    listOf(
                        testBaAccountRecord(id = remoteActiveAccountId.value, serverIndex = 2, sortOrder = 0),
                        testBaAccountRecord(
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
            listOf("cn-main", "local-only", "jp-alt"),
            merged.accounts.map { it.profile.id.value },
        )
        assertEquals(localActiveAccountId, merged.activeAccountId)
        assertTrue(merged.allAccountsFollowGlobalNotificationSettings)
        assertFalse(merged.globalReminderSettings.apNotifyEnabled)
        val preserved = merged.accounts.first { it.profile.id.value == "cn-main" }
        assertEquals("Local", preserved.profile.nickname)
        assertEquals(66.0, preserved.runtime.apCurrent)
        assertEquals(remoteActiveAccountId, merged.accounts.last().profile.id)
    }

    @Test
    fun `merge applies remote account fields when remote field timestamps are newer`() {
        val accountId = BaAccountId("cn-main")
        val local =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testBaAccountRecord(
                            id = accountId.value,
                            serverIndex = 0,
                            nickname = "Local",
                            runtime = BaAccountRuntime(apCurrent = 66.0),
                            profileUpdatedAtMs = 1_000L,
                            runtimeUpdatedAtMs = 1_000L,
                        ),
                    ),
                activeAccountId = accountId,
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
                activeAccountUpdatedAtMs = 1_000L,
                allAccountsFollowGlobalNotificationSettingsUpdatedAtMs = 1_000L,
                globalReminderSettingsUpdatedAtMs = 1_000L,
            )
        val remote =
            BaAccountsTransferPayload(
                exportedAtMs = 100L,
                accounts =
                    listOf(
                        testBaAccountRecord(
                            id = accountId.value,
                            serverIndex = 0,
                            nickname = "Remote",
                            runtime = BaAccountRuntime(apCurrent = 88.0),
                            profileUpdatedAtMs = 2_000L,
                            runtimeUpdatedAtMs = 2_000L,
                        ),
                    ),
                activeAccountId = accountId,
                allAccountsFollowGlobalNotificationSettings = false,
                globalReminderSettings = BaGlobalReminderSettings(apNotifyEnabled = true),
                activeAccountUpdatedAtMs = 2_000L,
                allAccountsFollowGlobalNotificationSettingsUpdatedAtMs = 2_000L,
                globalReminderSettingsUpdatedAtMs = 2_000L,
            )

        val merged = mergeBaAccountsForSync(local = local, remote = remote, nowMs = 3_000L)

        assertEquals(accountId, merged.activeAccountId)
        assertFalse(merged.allAccountsFollowGlobalNotificationSettings)
        assertTrue(merged.globalReminderSettings.apNotifyEnabled)
        val updated = merged.accounts.single()
        assertEquals("Remote", updated.profile.nickname)
        assertEquals(88.0, updated.runtime.apCurrent)
        assertEquals(2_000L, updated.profileUpdatedAtMs)
        assertEquals(2_000L, updated.runtimeUpdatedAtMs)
    }

    @Test
    fun `sync merge import keeps AP read suppression false after fresh load`() {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val importStore = BaAccountStore(backingStore)
        val remoteRaw =
            buildBaAccountsExportJson(
                snapshot =
                    BaAccountStoreSnapshot(
                        accounts = emptyList(),
                        activeAccountId = null,
                        allAccountsFollowGlobalNotificationSettings = true,
                        globalReminderSettings =
                            BaGlobalReminderSettings(
                                keepApRemindersReadUntilBelowThreshold = false,
                            ),
                        globalReminderSettingsUpdatedAtMs = 2_000L,
                    ),
                nowMs = 3_000L,
            )
        val merged =
            mergeBaAccountsForSync(
                local = importStore.loadState(),
                remote = parseBaAccountsExportJson(remoteRaw),
                nowMs = 4_000L,
            )

        importStore.replaceAll(
            accounts = merged.accounts,
            activeAccountId = merged.activeAccountId,
            activeAccountUpdatedAtMs = merged.activeAccountUpdatedAtMs,
        )
        importStore.saveAllAccountsFollowGlobalNotificationSettingsFromSync(
            merged.allAccountsFollowGlobalNotificationSettings,
            merged.allAccountsFollowGlobalNotificationSettingsUpdatedAtMs,
        )
        importStore.saveGlobalReminderSettingsFromSync(
            merged.globalReminderSettings,
            merged.globalReminderSettingsUpdatedAtMs,
        )

        val reloaded = BaAccountStore(backingStore).loadState()
        assertFalse(reloaded.globalReminderSettings.keepApRemindersReadUntilBelowThreshold)
        assertEquals(2_000L, reloaded.globalReminderSettingsUpdatedAtMs)
    }

    @Test
    fun `merge preserves different account edits from two devices for a third device`() {
        val base =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        testBaAccountRecord(
                            id = "cn-main",
                            serverIndex = 0,
                            nickname = "Base 1",
                            runtime = BaAccountRuntime(apCurrent = 10.0),
                            profileUpdatedAtMs = 100L,
                            runtimeUpdatedAtMs = 100L,
                        ),
                        testBaAccountRecord(
                            id = "jp-alt",
                            serverIndex = 2,
                            nickname = "Base 2",
                            runtime = BaAccountRuntime(apCurrent = 20.0),
                            profileUpdatedAtMs = 100L,
                            runtimeUpdatedAtMs = 100L,
                        ),
                    ),
                activeAccountId = BaAccountId("cn-main"),
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )
        val device1 =
            base.copy(
                accounts =
                    listOf(
                        testBaAccountRecord(
                            id = "cn-main",
                            serverIndex = 0,
                            nickname = "Device 1",
                            runtime = BaAccountRuntime(apCurrent = 66.0),
                            profileUpdatedAtMs = 1_000L,
                            runtimeUpdatedAtMs = 1_000L,
                        ),
                        base.accounts[1],
                    ),
            )
        val device2 =
            base.copy(
                accounts =
                    listOf(
                        base.accounts[0],
                        testBaAccountRecord(
                            id = "jp-alt",
                            serverIndex = 2,
                            nickname = "Device 2",
                            runtime = BaAccountRuntime(apCurrent = 88.0),
                            profileUpdatedAtMs = 2_000L,
                            runtimeUpdatedAtMs = 2_000L,
                        ),
                    ),
            )

        val remoteAfterDevice1 =
            parseBaAccountsExportJson(buildBaAccountsExportJson(snapshot = device1, nowMs = 1_100L))
        val remoteAfterDevice2 =
            mergeBaAccountsForSync(
                local = device2,
                remote = remoteAfterDevice1,
                nowMs = 2_100L,
            )
        val device3Merged =
            mergeBaAccountsForSync(
                local = base,
                remote = remoteAfterDevice2,
                nowMs = 3_100L,
            )

        val account1 = device3Merged.accounts.first { it.profile.id.value == "cn-main" }
        val account2 = device3Merged.accounts.first { it.profile.id.value == "jp-alt" }
        assertEquals("Device 1", account1.profile.nickname)
        assertEquals(66.0, account1.runtime.apCurrent)
        assertEquals("Device 2", account2.profile.nickname)
        assertEquals(88.0, account2.runtime.apCurrent)
    }

    @Test
    fun `stable sync fingerprint ignores export time metadata`() {
        val snapshot =
            BaAccountStoreSnapshot(
                accounts = listOf(testBaAccountRecord(id = "cn-main", serverIndex = 0)),
                activeAccountId = BaAccountId("cn-main"),
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )

        val first = buildBaAccountsSyncFingerprintJson(buildBaAccountsExportJson(snapshot, nowMs = 1_000L))
        val second = buildBaAccountsSyncFingerprintJson(buildBaAccountsExportJson(snapshot, nowMs = 2_000L))

        assertEquals(first, second)
    }

    @Test
    fun `merge falls back to valid local active account when remote active is missing`() {
        data class Case(
            val label: String,
            val localActiveUpdatedAtMs: Long,
            val remoteActiveUpdatedAtMs: Long,
            val nowMs: Long,
        )
        val cases =
            listOf(
                Case("remote active missing, same age", 0L, 0L, nowMs = 10L),
                Case("newer remote active is invalid", 1_000L, 2_000L, nowMs = 3_000L),
            )
        val localActiveAccountId = BaAccountId("cn-main")
        for (case in cases) {
            val local =
                BaAccountStoreSnapshot(
                    accounts = listOf(testBaAccountRecord(id = localActiveAccountId.value, serverIndex = 0)),
                    activeAccountId = localActiveAccountId,
                    allAccountsFollowGlobalNotificationSettings = true,
                    globalReminderSettings = BaGlobalReminderSettings(),
                    activeAccountUpdatedAtMs = case.localActiveUpdatedAtMs,
                )
            val remote =
                BaAccountsTransferPayload(
                    accounts = listOf(testBaAccountRecord(id = "jp-alt", serverIndex = 2)),
                    activeAccountId = BaAccountId("missing"),
                    activeAccountUpdatedAtMs = case.remoteActiveUpdatedAtMs,
                )

            val merged = mergeBaAccountsForSync(local = local, remote = remote, nowMs = case.nowMs)

            assertEquals(localActiveAccountId, merged.activeAccountId, case.label)
            assertEquals(listOf("cn-main", "jp-alt"), merged.accounts.map { it.profile.id.value }, case.label)
        }
    }
}
