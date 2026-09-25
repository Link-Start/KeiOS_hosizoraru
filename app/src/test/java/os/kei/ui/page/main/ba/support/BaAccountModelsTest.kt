package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaAccountModelsTest {
    @Test
    fun `display name fallback keeps full sanitized nickname`() {
        assertEquals("ABCDEFGHIJKL", sanitizeBaAccountDisplayName("", "ABCDEFGHIJKL"))
    }

    @Test
    fun `account normalization uses profile server identity rules`() {
        val record =
            BaAccountRecord(
                profile =
                    BaAccountProfile(
                        id = BaAccountId("cn-main"),
                        serverIndex = 0,
                        displayName = "",
                        nickname = "ABCDEFGHIJKL",
                        friendCode = "AB12cd3",
                    ),
            )

        val normalized = record.normalized(defaultSortOrder = 0)

        assertEquals("ABCDEFGHIJ", normalized?.profile?.nickname)
        assertEquals("ab12cd3", normalized?.profile?.friendCode)
    }

    @Test
    fun `runtime normalization clamps cafe stored ap to cafe capacity`() {
        val runtime =
            BaAccountRuntime(
                cafeLevel = 1,
                cafeStoredAp = 999.0,
            )

        val normalized = runtime.normalized()

        assertEquals(cafeStorageCap(1), normalized.cafeStoredAp)
    }

    @Test
    fun `global reminder settings default persistent AP read suppression on`() {
        assertTrue(BaGlobalReminderSettings().keepApRemindersReadUntilBelowThreshold)
    }

    @Test
    fun `account reminder override preserves AP read suppression mode`() {
        val accountId = BaAccountId("cn-main")
        val override =
            BaGlobalReminderSettings(
                keepApRemindersReadUntilBelowThreshold = false,
            ).toAccountReminderOverride(accountId)

        assertFalse(override.keepApRemindersReadUntilBelowThreshold)
    }
}
