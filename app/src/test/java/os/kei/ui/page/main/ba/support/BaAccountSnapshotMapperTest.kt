package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaAccountSnapshotMapperTest {
    @Test
    fun `active account overrides identity runtime and reminder fields`() {
        val activeAccount =
            BaAccountRecord(
                profile =
                    BaAccountProfile(
                        id = BaAccountId("account-2"),
                        serverIndex = 1,
                        displayName = "Global",
                        nickname = "Global",
                        friendCode = "GLFRIEND",
                    ),
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
            BaAccountStoreSnapshot(
                accounts = listOf(activeAccount),
                activeAccountId = activeAccount.profile.id,
                allAccountsFollowGlobalNotificationSettings = true,
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
    fun `base snapshot is preserved when active account is missing`() {
        val base = BaPageSnapshot(serverIndex = 0, idNickname = "Base")
        val state =
            BaAccountStoreSnapshot(
                accounts = emptyList(),
                activeAccountId = null,
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )

        assertEquals(base, base.withActiveBaAccount(state))
    }
}
