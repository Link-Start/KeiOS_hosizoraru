package os.kei.core.platform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransientExternalLaunchGuardTest {
    @Test
    fun `permission launch defers stop work inside transient window`() {
        val guard = TransientExternalLaunchGuard(transientWindowMs = 1_000L)

        guard.markLaunching(
            reason = TransientExternalLaunchGuard.Reason.NotificationPermission,
            nowMillis = 10_000L
        )

        assertTrue(guard.shouldDeferStopWork(nowMillis = 10_500L))
        assertEquals(
            TransientExternalLaunchGuard.Reason.NotificationPermission,
            guard.snapshot.reason
        )
    }

    @Test
    fun `stale launch reason does not defer stop work`() {
        val guard = TransientExternalLaunchGuard(transientWindowMs = 1_000L)

        guard.markLaunching(
            reason = TransientExternalLaunchGuard.Reason.SystemSettings,
            nowMillis = 10_000L
        )

        assertFalse(guard.shouldDeferStopWork(nowMillis = 12_000L))
    }

    @Test
    fun `notification route does not freeze foreground content`() {
        val guard = TransientExternalLaunchGuard(transientWindowMs = 1_000L)

        guard.markLaunching(
            reason = TransientExternalLaunchGuard.Reason.NotificationRoute,
            nowMillis = 10_000L
        )

        assertFalse(guard.shouldDeferStopWork(nowMillis = 10_100L))
    }

    @Test
    fun `resume clears transient launch state`() {
        val guard = TransientExternalLaunchGuard(transientWindowMs = 1_000L)

        guard.markLaunching(
            reason = TransientExternalLaunchGuard.Reason.ExternalActivity,
            nowMillis = 10_000L
        )
        guard.onResume()

        assertFalse(guard.snapshot.active)
        assertFalse(guard.shouldDeferStopWork(nowMillis = 10_100L))
    }
}

