package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaAccountSnapshotMapperTest {
    @Test
    fun `active account overrides identity runtime and reminder fields`() {
        val activeAccount =
            testBaAccountRecord(
                id = "account-2",
                serverIndex = 1,
                nickname = "Global",
                friendCode = "GLFRIEND",
                runtime =
                    BaAccountRuntime(
                        apLimit = 180,
                        apCurrent = 87.5,
                        apRegenBaseMs = 1000L,
                        apSyncMs = 2000L,
                        cafeLevel = 8,
                        cafeStoredAp = 90.25,
                        cafeLastHourMs = 3000L,
                        coffeeHeadpatMs = 4000L,
                        coffeeInvite1UsedMs = 5000L,
                        coffeeInvite2UsedMs = 6000L,
                    ),
                reminderRuntime =
                    BaAccountReminderRuntime(
                        apLastNotifiedLevel = 120,
                        cafeApLastNotifiedLevel = 130,
                        arenaRefreshLastNotifiedSlotMs = 7000L,
                        cafeVisitLastNotifiedSlotMs = 8000L,
                    ),
            )
        val base =
            BaPageSnapshot(
                serverIndex = 2,
                idNickname = "Base",
                idFriendCode = "ABCDEFGH",
                showEndedActivities = true,
                showCalendarPoolImages = false,
                calendarUpcomingNotifyEnabled = true,
            )
        val state =
            testBaAccountState(
                accounts = listOf(activeAccount),
                globalReminderSettings =
                    BaGlobalReminderSettings(
                        apNotifyEnabled = true,
                        apNotifyThreshold = 160,
                        cafeApNotifyEnabled = true,
                        cafeApNotifyThreshold = 170,
                        arenaRefreshNotifyEnabled = true,
                        cafeVisitNotifyEnabled = true,
                    ),
            )

        val snapshot = base.withActiveBaAccount(state)

        assertEquals(1, snapshot.serverIndex)
        assertEquals("Global", snapshot.idNickname)
        assertEquals("GLFRIEND", snapshot.idFriendCode)
        assertEquals(180, snapshot.apLimit)
        assertEquals(87.5, snapshot.apCurrent)
        assertEquals(8, snapshot.cafeLevel)
        assertEquals(90.25, snapshot.cafeStoredAp)
        assertEquals(120, snapshot.apLastNotifiedLevel)
        assertEquals(130, snapshot.cafeApLastNotifiedLevel)
        assertEquals(7000L, snapshot.arenaRefreshLastNotifiedSlotMs)
        assertEquals(8000L, snapshot.cafeVisitLastNotifiedSlotMs)
        assertTrue(snapshot.apNotifyEnabled)
        assertEquals(160, snapshot.apNotifyThreshold)
        assertTrue(snapshot.cafeApNotifyEnabled)
        assertEquals(170, snapshot.cafeApNotifyThreshold)
        assertTrue(snapshot.arenaRefreshNotifyEnabled)
        assertTrue(snapshot.cafeVisitNotifyEnabled)
        assertTrue(snapshot.showEndedActivities)
        assertFalse(snapshot.showCalendarPoolImages)
        assertTrue(snapshot.calendarUpcomingNotifyEnabled)
    }

    @Test
    fun `reminder snapshots load independent local anchors for each account`() {
        val accounts =
            listOf(
                testBaAccountRecord(id = "account-first", serverIndex = 0),
                testBaAccountRecord(id = "account-second", serverIndex = 1),
            )
        val firstId = accounts[0].profile.id
        val secondId = accounts[1].profile.id
        val state = testBaAccountState(accounts)
        val acknowledgementStore = BaApAcknowledgementStore(InMemoryBaAccountKeyValueStore())
        acknowledgementStore.setSuppressionAnchor(firstId, BaApReminderKind.Ap, 1_000L)
        acknowledgementStore.setSuppressionAnchor(firstId, BaApReminderKind.CafeAp, 2_000L)
        acknowledgementStore.setSuppressionAnchor(secondId, BaApReminderKind.Ap, 3_000L)
        acknowledgementStore.setSuppressionAnchor(secondId, BaApReminderKind.CafeAp, 4_000L)
        acknowledgementStore.setDismissedUntil(firstId, BaApReminderKind.Ap, 5_000L)
        acknowledgementStore.setDismissedUntil(firstId, BaApReminderKind.CafeAp, 6_000L)
        acknowledgementStore.setDismissedUntil(secondId, BaApReminderKind.Ap, 7_000L)
        acknowledgementStore.setDismissedUntil(secondId, BaApReminderKind.CafeAp, 8_000L)

        val snapshots =
            accounts.associate { account ->
                account.profile.id to
                    BaPageSnapshot()
                        .withBaAccount(state, account)
                        .withLocalApAcknowledgementAnchors(account.profile.id, acknowledgementStore)
            }

        assertEquals(1_000L, snapshots.getValue(firstId).apSuppressionAnchorAtMs)
        assertEquals(2_000L, snapshots.getValue(firstId).cafeApSuppressionAnchorAtMs)
        assertEquals(3_000L, snapshots.getValue(secondId).apSuppressionAnchorAtMs)
        assertEquals(4_000L, snapshots.getValue(secondId).cafeApSuppressionAnchorAtMs)
        assertEquals(5_000L, snapshots.getValue(firstId).apDismissedUntilAtMs)
        assertEquals(6_000L, snapshots.getValue(firstId).cafeApDismissedUntilAtMs)
        assertEquals(7_000L, snapshots.getValue(secondId).apDismissedUntilAtMs)
        assertEquals(8_000L, snapshots.getValue(secondId).cafeApDismissedUntilAtMs)
    }

    @Test
    fun `base snapshot is preserved when active account is missing`() {
        val base = BaPageSnapshot(serverIndex = 0, idNickname = "Base")
        val state = testBaAccountState(accounts = emptyList())

        assertEquals(base, base.withActiveBaAccount(state))
    }

    @Test
    fun `custom account override maps AP read suppression mode to snapshot`() {
        val accountId = BaAccountId("account-custom")
        val account =
            testBaAccountRecord(
                id = accountId.value,
                serverIndex = 1,
                notificationMode = BaAccountNotificationMode.Custom,
                reminderOverride =
                    BaAccountReminderOverride(
                        accountId = accountId,
                        keepApRemindersReadUntilBelowThreshold = false,
                    ),
            )
        val state = testBaAccountState(listOf(account), allAccountsFollowGlobalNotificationSettings = false)

        val snapshot = BaPageSnapshot().withActiveBaAccount(state)

        assertFalse(snapshot.keepApRemindersReadUntilBelowThreshold)
    }

    @Test
    fun `switching account swaps craft state but not the craft card's expansion`() {
        // The two live side by side in BaPageSnapshot and must not be confused: `craft` is game state
        // each account owns, `craftCardExpanded` is one card's layout on one page.
        val account =
            testBaAccountRecord(
                id = "account-craft",
                serverIndex = 1,
                runtime =
                    BaAccountRuntime(
                        craft = BaCraftState(generate = listOf(BaCraftSlot(startedAtMs = 9_000L))),
                    ),
            )
        val state = testBaAccountState(listOf(account))

        val snapshot = BaPageSnapshot(craftCardExpanded = false).withActiveBaAccount(state)

        assertFalse(snapshot.craftCardExpanded)
        // The mapper normalizes, which pads to BA_CRAFT_SLOT_COUNT — index by slot, not by size.
        assertEquals(BA_CRAFT_SLOT_COUNT, snapshot.craft.generate.size)
        assertEquals(9_000L, snapshot.craft.slotAt(BaCraftFunction.Generate, 0).startedAtMs)
    }

    @Test
    fun `friend code sanitizer keeps uppercase letters for default server policy`() {
        assertEquals("ABCDARIS", normalizeBaAccountFriendCodeInput("a1-b2 c3_d4 arisu"))
        assertEquals("ABCDARIS", sanitizeBaAccountFriendCode("a1-b2 c3_d4 arisu"))
        assertEquals(BA_DEFAULT_FRIEND_CODE, sanitizeBaAccountFriendCode("A1B2"))
    }
}
