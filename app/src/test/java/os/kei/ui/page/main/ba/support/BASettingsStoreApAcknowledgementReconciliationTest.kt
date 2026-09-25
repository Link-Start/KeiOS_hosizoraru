package os.kei.ui.page.main.ba.support

import org.junit.Test
import os.kei.core.background.AppBackgroundSchedulePolicy
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BASettingsStoreApAcknowledgementReconciliationTest {
    @Test
    fun `disabled or below threshold account persists ordinary and cafe acknowledgement resets`() {
        data class Case(
            val label: String,
            val accountId: String,
            val enabled: Boolean,
            val ap: Int,
            val anchors: Pair<Long, Long>,
            val dismissals: Pair<Long, Long>,
        )
        val cases =
            listOf(
                Case("disabled", "disabled", enabled = false, ap = 130, anchors = 1_000L to 2_000L, dismissals = 3_000L to 4_000L),
                Case("below threshold", "below", enabled = true, ap = 119, anchors = 3_000L to 4_000L, dismissals = 5_000L to 6_000L),
            )
        for (case in cases) {
            val fixture =
                fixture(
                    account =
                        account(
                            accountId = BaAccountId(case.accountId),
                            enabled = case.enabled,
                            ap = case.ap.toDouble(),
                            cafeAp = case.ap.toDouble(),
                            apLastNotifiedLevel = case.ap,
                            cafeApLastNotifiedLevel = case.ap,
                        ),
                )
            fixture.seedAnchors(ap = case.anchors.first, cafeAp = case.anchors.second)
            fixture.seedDismissals(ap = case.dismissals.first, cafeAp = case.dismissals.second)

            assertTrue(fixture.reconcile(), case.label)
            fixture.assertResetState(case.label)
        }
    }

    @Test
    fun `disabled stale AP level at cap becomes schedulable after reenable`() {
        val fixture =
            fixture(
                account =
                    account(
                        accountId = BaAccountId("cap"),
                        enabled = false,
                        ap = 240.0,
                        cafeAp = 0.0,
                        apLastNotifiedLevel = 240,
                        cafeApLastNotifiedLevel = -1,
                    ),
            )
        fixture.seedAnchors(ap = 5_000L, cafeAp = 0L)

        assertTrue(fixture.reconcile())
        val disabled = fixture.accountStore.loadAccounts().single()
        assertTrue(
            fixture.accountStore.updateAccount(
                disabled.copy(profile = disabled.profile.copy(enabled = true)),
            ),
        )
        val accountState = fixture.accountStore.loadState()
        val enabled = accountState.accounts.single()
        val snapshot =
            BaPageSnapshot()
                .withBaAccount(accountState = accountState, account = enabled)
                .withLocalApAcknowledgementAnchors(
                    accountId = enabled.profile.id,
                    acknowledgementStore = fixture.acknowledgementStore,
                )

        val schedule =
            assertNotNull(
                AppBackgroundSchedulePolicy.nextBaReminderSchedule(
                    snapshot = snapshot,
                    nowMs = NOW_MS,
                ),
            )

        assertEquals(NOW_MS, schedule.triggerAtMillis)
    }

    private fun fixture(account: BaAccountRecord): ReconciliationFixture {
        val backingStore = InMemoryBaAccountKeyValueStore()
        val accountStore = BaAccountStore(backingStore)
        accountStore.replaceAll(
            accounts = listOf(account),
            activeAccountId = account.profile.id,
        )
        accountStore.saveGlobalReminderSettings(
            BaGlobalReminderSettings(
                apNotifyEnabled = true,
                apNotifyThreshold = 120,
                cafeApNotifyEnabled = true,
                cafeApNotifyThreshold = 120,
                keepApRemindersReadUntilBelowThreshold = true,
            ),
        )
        return ReconciliationFixture(
            accountStore = accountStore,
            acknowledgementStore = BaApAcknowledgementStore(backingStore),
            accountId = account.profile.id,
        )
    }

    private fun account(
        accountId: BaAccountId,
        enabled: Boolean,
        ap: Double,
        cafeAp: Double,
        apLastNotifiedLevel: Int,
        cafeApLastNotifiedLevel: Int,
    ): BaAccountRecord =
        BaAccountRecord(
            profile =
                BaAccountProfile(
                    id = accountId,
                    serverIndex = 2,
                    displayName = accountId.value,
                    nickname = accountId.value,
                    friendCode = "ABC12345",
                    enabled = enabled,
                ),
            runtime =
                BaAccountRuntime(
                    apLimit = 240,
                    apCurrent = ap,
                    apRegenBaseMs = NOW_MS,
                    cafeStoredAp = cafeAp,
                    cafeLastHourMs = NOW_MS,
                ),
            reminderRuntime =
                BaAccountReminderRuntime(
                    apLastNotifiedLevel = apLastNotifiedLevel,
                    cafeApLastNotifiedLevel = cafeApLastNotifiedLevel,
                ),
        )

    private data class ReconciliationFixture(
        val accountStore: BaAccountStore,
        val acknowledgementStore: BaApAcknowledgementStore,
        val accountId: BaAccountId,
    ) {
        fun seedAnchors(ap: Long, cafeAp: Long) {
            acknowledgementStore.setSuppressionAnchor(accountId, BaApReminderKind.Ap, ap)
            acknowledgementStore.setSuppressionAnchor(accountId, BaApReminderKind.CafeAp, cafeAp)
        }

        fun seedDismissals(ap: Long, cafeAp: Long) {
            acknowledgementStore.setDismissedUntil(accountId, BaApReminderKind.Ap, ap)
            acknowledgementStore.setDismissedUntil(accountId, BaApReminderKind.CafeAp, cafeAp)
        }

        fun reconcile(): Boolean =
            BASettingsStore.reconcileApAcknowledgements(
                accountState = accountStore.loadState(),
                baseSnapshot = BaPageSnapshot(),
                accountStore = accountStore,
                acknowledgementStore = acknowledgementStore,
                nowMs = NOW_MS,
            )

        fun assertResetState(label: String) {
            val persisted = accountStore.loadAccounts().single().reminderRuntime
            assertEquals(-1, persisted.apLastNotifiedLevel, label)
            assertEquals(-1, persisted.cafeApLastNotifiedLevel, label)
            assertEquals(
                0L,
                acknowledgementStore.loadSuppressionAnchor(accountId, BaApReminderKind.Ap),
                label,
            )
            assertEquals(
                0L,
                acknowledgementStore.loadSuppressionAnchor(accountId, BaApReminderKind.CafeAp),
                label,
            )
            assertEquals(
                0L,
                acknowledgementStore.loadDismissedUntil(accountId, BaApReminderKind.Ap),
                label,
            )
            assertEquals(
                0L,
                acknowledgementStore.loadDismissedUntil(accountId, BaApReminderKind.CafeAp),
                label,
            )
        }
    }

    private companion object {
        private const val NOW_MS = 20_000_000L
    }
}
