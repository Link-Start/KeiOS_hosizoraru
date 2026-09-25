package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaApAcknowledgementStoreTest {
    private val backing = InMemoryBaAccountKeyValueStore()
    private val store = BaApAcknowledgementStore(backing)

    @Test
    fun `anchors are isolated by account and AP kind`() {
        val first = BaAccountId("cn-main")
        val second = BaAccountId("jp-main")

        store.setSuppressionAnchor(first, BaApReminderKind.Ap, 1_000L)
        store.setSuppressionAnchor(first, BaApReminderKind.CafeAp, 2_000L)
        store.setSuppressionAnchor(second, BaApReminderKind.Ap, 3_000L)

        assertEquals(1_000L, store.loadSuppressionAnchor(first, BaApReminderKind.Ap))
        assertEquals(2_000L, store.loadSuppressionAnchor(first, BaApReminderKind.CafeAp))
        assertEquals(3_000L, store.loadSuppressionAnchor(second, BaApReminderKind.Ap))
    }

    @Test
    fun `dismissal deadlines are isolated by account and AP kind`() {
        val first = BaAccountId("cn-main")
        val second = BaAccountId("jp-main")

        store.setDismissedUntil(first, BaApReminderKind.Ap, 4_000L)
        store.setDismissedUntil(first, BaApReminderKind.CafeAp, 5_000L)
        store.setDismissedUntil(second, BaApReminderKind.Ap, 6_000L)

        assertEquals(4_000L, store.loadDismissedUntil(first, BaApReminderKind.Ap))
        assertEquals(5_000L, store.loadDismissedUntil(first, BaApReminderKind.CafeAp))
        assertEquals(6_000L, store.loadDismissedUntil(second, BaApReminderKind.Ap))
        assertEquals(0L, store.loadSuppressionAnchor(first, BaApReminderKind.Ap))
    }

    @Test
    fun `clear account removes both AP anchors`() {
        val accountId = BaAccountId("cn-main")
        store.setSuppressionAnchor(accountId, BaApReminderKind.Ap, 1_000L)
        store.setSuppressionAnchor(accountId, BaApReminderKind.CafeAp, 2_000L)
        store.setDismissedUntil(accountId, BaApReminderKind.Ap, 3_000L)
        store.setDismissedUntil(accountId, BaApReminderKind.CafeAp, 4_000L)

        assertTrue(store.clearAccount(accountId))
        assertEquals(0L, store.loadSuppressionAnchor(accountId, BaApReminderKind.Ap))
        assertEquals(0L, store.loadSuppressionAnchor(accountId, BaApReminderKind.CafeAp))
        assertEquals(0L, store.loadDismissedUntil(accountId, BaApReminderKind.Ap))
        assertEquals(0L, store.loadDismissedUntil(accountId, BaApReminderKind.CafeAp))
    }

    @Test
    fun `negative anchors normalize to zero`() {
        val accountId = BaAccountId("cn-main")

        store.setSuppressionAnchor(accountId, BaApReminderKind.Ap, -10L)

        assertEquals(0L, store.loadSuppressionAnchor(accountId, BaApReminderKind.Ap))
    }

    @Test
    fun `clear physically removes stale raw zero and negative keys`() {
        val accountId = BaAccountId("cn-main")
        val apKey = "ba_ap_read_anchor:ap:${accountId.value}"
        val cafeApKey = "ba_ap_read_anchor:cafe_ap:${accountId.value}"
        backing.encode(apKey, 0L)
        backing.encode(cafeApKey, -10L)

        assertTrue(store.clear(accountId, BaApReminderKind.Ap))
        assertTrue(store.clearAccount(accountId))
        assertFalse(backing.containsKey(apKey))
        assertFalse(backing.containsKey(cafeApKey))
    }

    @Test
    fun `deleting an account clears both local anchors with the account`() {
        val accountId = BaAccountId("cn-main")
        val accountStore = BaAccountStore(backing)
        accountStore.addAccount(testBaAccountRecord(id = accountId.value, serverIndex = 0))
        store.setSuppressionAnchor(accountId, BaApReminderKind.Ap, 1_000L)
        store.setSuppressionAnchor(accountId, BaApReminderKind.CafeAp, 2_000L)
        store.setDismissedUntil(accountId, BaApReminderKind.Ap, 3_000L)
        store.setDismissedUntil(accountId, BaApReminderKind.CafeAp, 4_000L)

        assertTrue(deleteBaAccountAndClearAcknowledgements(accountStore, store, accountId))
        assertTrue(accountStore.loadAccounts().none { it.profile.id == accountId })
        assertEquals(0L, store.loadSuppressionAnchor(accountId, BaApReminderKind.Ap))
        assertEquals(0L, store.loadSuppressionAnchor(accountId, BaApReminderKind.CafeAp))
        assertEquals(0L, store.loadDismissedUntil(accountId, BaApReminderKind.Ap))
        assertEquals(0L, store.loadDismissedUntil(accountId, BaApReminderKind.CafeAp))
        assertFalse(backing.containsKey("ba_ap_read_anchor:ap:${accountId.value}"))
        assertFalse(backing.containsKey("ba_ap_read_anchor:cafe_ap:${accountId.value}"))
        assertFalse(backing.containsKey("ba_ap_dismissed_until:ap:${accountId.value}"))
        assertFalse(backing.containsKey("ba_ap_dismissed_until:cafe_ap:${accountId.value}"))
    }
}
