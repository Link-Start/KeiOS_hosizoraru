package os.kei.core.background

import android.content.Intent
import kotlin.test.assertTrue
import org.junit.Test

class AppBackgroundSystemEventReceiverTest {
    @Test
    fun `receiver reschedules after native system recovery events`() {
        assertTrue(AppBackgroundSystemEventReceiver.shouldRescheduleForAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(AppBackgroundSystemEventReceiver.shouldRescheduleForAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(AppBackgroundSystemEventReceiver.shouldRescheduleForAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(AppBackgroundSystemEventReceiver.shouldRescheduleForAction(Intent.ACTION_TIMEZONE_CHANGED))
    }
}
