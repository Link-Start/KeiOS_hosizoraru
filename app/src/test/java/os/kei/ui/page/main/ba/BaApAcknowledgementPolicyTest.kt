package os.kei.ui.page.main.ba

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaApAcknowledgementPolicyTest {
    @Test
    fun `persistent read suppresses while AP remains above threshold`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = NOW_MS - 10_000L,
                nowMs = NOW_MS,
            )

        assertTrue(decision.suppressed)
        assertNull(decision.nextEligibleAtMs)
        assertFalse(decision.resetSuppressionAnchor)
    }

    @Test
    fun `hourly read schedules exact one hour boundary`() {
        val anchor = NOW_MS - 30L * 60L * 1000L
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = anchor,
                nowMs = NOW_MS,
            )

        assertTrue(decision.suppressed)
        assertEquals(anchor + BA_AP_READ_REPEAT_INTERVAL_MS, decision.nextEligibleAtMs)
    }

    @Test
    fun `expired hourly read bypasses level dedupe and advances after delivery`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS,
                nowMs = NOW_MS,
            )

        assertFalse(decision.suppressed)
        assertTrue(decision.bypassLastLevelDeduplication)
        assertTrue(decision.advanceSuppressionAnchorAfterDelivery)
    }

    @Test
    fun `notification disabled resets existing read state`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = false,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = NOW_MS,
                nowMs = NOW_MS,
            )

        assertTrue(decision.resetSuppressionAnchor)
        assertFalse(decision.eligible)
    }

    @Test
    fun `no suppression anchor remains eligible above threshold`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = 0L,
                nowMs = NOW_MS,
            )

        assertTrue(decision.eligible)
        assertFalse(decision.suppressed)
        assertNull(decision.nextEligibleAtMs)
    }

    @Test
    fun `hourly read overflow clamps next eligibility to max value`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = Long.MAX_VALUE,
                nowMs = NOW_MS,
            )

        assertTrue(decision.suppressed)
        assertEquals(Long.MAX_VALUE, decision.nextEligibleAtMs)
    }

    @Test
    fun `dismissed reminder suppresses AP growth until exact snooze boundary`() {
        val dismissedUntilAtMs = NOW_MS + 30L * 60L * 1000L
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 131,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = 0L,
                dismissedUntilAtMs = dismissedUntilAtMs,
                nowMs = NOW_MS,
            )

        assertTrue(decision.suppressed)
        assertEquals(dismissedUntilAtMs, decision.nextEligibleAtMs)
        assertFalse(decision.advanceSuppressionAnchorAfterDelivery)
        assertFalse(decision.clearDismissedUntilAfterDelivery)
    }

    @Test
    fun `expired dismissal bypasses level dedupe and clears after delivery`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = 0L,
                dismissedUntilAtMs = NOW_MS,
                nowMs = NOW_MS,
            )

        assertFalse(decision.suppressed)
        assertTrue(decision.bypassLastLevelDeduplication)
        assertFalse(decision.advanceSuppressionAnchorAfterDelivery)
        assertTrue(decision.clearDismissedUntilAfterDelivery)
    }

    @Test
    fun `below threshold resets read and dismissal state`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 119,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = NOW_MS,
                dismissedUntilAtMs = NOW_MS + BA_AP_DISMISS_SNOOZE_INTERVAL_MS,
                nowMs = NOW_MS,
            )

        assertTrue(decision.resetSuppressionAnchor)
        assertTrue(decision.resetDismissedUntil)
        assertFalse(decision.eligible)
    }

    @Test
    fun `permanent read remains stronger than a dismissal deadline`() {
        val decision =
            BaApAcknowledgementPolicy.evaluate(
                notificationEnabled = true,
                currentDisplay = 130,
                thresholdDisplay = 120,
                keepReadUntilBelowThreshold = true,
                suppressionAnchorAtMs = NOW_MS - 1_000L,
                dismissedUntilAtMs = NOW_MS - 1L,
                nowMs = NOW_MS,
            )

        assertTrue(decision.suppressed)
        assertNull(decision.nextEligibleAtMs)
        assertFalse(decision.clearDismissedUntilAfterDelivery)
    }

    private companion object {
        private const val NOW_MS = 20_000_000L
    }
}
