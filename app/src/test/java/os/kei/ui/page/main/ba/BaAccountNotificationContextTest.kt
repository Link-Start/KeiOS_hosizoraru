package os.kei.ui.page.main.ba

import kotlin.test.assertEquals
import org.junit.Test
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaAccountProfile
import os.kei.ui.page.main.ba.support.BaAccountRecord
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings

class BaAccountNotificationContextTest {
    @Test
    fun `office account state resolves active account notification context`() {
        val accountId = BaAccountId("cn-main")
        val state =
            BaOfficeAccountUiState(
                accounts =
                    listOf(
                        BaOfficeAccountCardUiState(
                            id = accountId,
                            displayName = "国服主号",
                            nickname = "Sensei",
                            friendCode = "ABCDEFGH",
                            serverIndex = 0,
                            enabled = true,
                            notificationMode = BaAccountNotificationMode.FollowGlobal,
                            remindersEnabled = true,
                            customReminderSettings = BaGlobalReminderSettings(),
                        ),
                    ),
                activeAccountId = accountId,
            )

        val context = state.activeNotificationContext()

        assertEquals(accountId, context.accountId)
        assertEquals("国服主号", context.accountDisplayName)
        assertEquals(
            BaAccountNotificationKind.Ap.notificationId(accountId),
            context.notificationId(BaAccountNotificationKind.Ap),
        )
    }

    @Test
    fun `store snapshot resolves active account notification context`() {
        val accountId = BaAccountId("jp-main")
        val state =
            BaAccountStoreSnapshot(
                accounts =
                    listOf(
                        BaAccountRecord(
                            profile =
                                BaAccountProfile(
                                    id = accountId,
                                    serverIndex = 1,
                                    displayName = "日服主号",
                                    nickname = "Sensei",
                                    friendCode = "ABCDEFGH",
                                ),
                        ),
                    ),
                activeAccountId = accountId,
                allAccountsFollowGlobalNotificationSettings = true,
                globalReminderSettings = BaGlobalReminderSettings(),
            )

        val context = state.activeNotificationContext()

        assertEquals(accountId, context.accountId)
        assertEquals("日服主号", context.accountDisplayName)
        assertEquals(
            BaAccountNotificationKind.CafeAp.notificationId(accountId),
            context.notificationId(BaAccountNotificationKind.CafeAp),
        )
    }

    @Test
    fun `missing active account falls back to legacy notification id`() {
        val context = BaOfficeAccountUiState().activeNotificationContext()

        assertEquals(null, context.accountId)
        assertEquals("", context.accountDisplayName)
        assertEquals(
            BaAccountNotificationKind.Ap.legacyId,
            context.notificationId(BaAccountNotificationKind.Ap),
        )
    }
}
