package os.kei.feature.notification

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = MiFocusNotificationActionsTestApp::class,
    sdk = [35],
)
class MiFocusNotificationActionsTest {
    @Test
    fun `mark read action targets exported focus receiver with foreground delivery`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pendingIntent =
            MiFocusNotificationActions.markReadPendingIntent(
                context = context,
                notificationId = 38990,
                requestCode = 2002,
            )

        val savedIntent = assertNotNull(shadowOf(pendingIntent).savedIntent)

        assertEquals(
            ComponentName(context, MiFocusNotificationActionReceiver::class.java),
            savedIntent.component,
        )
        assertEquals(MiFocusNotificationActionReceiver.ACTION_MARK_READ, savedIntent.action)
        assertEquals(
            38990,
            savedIntent.getIntExtra(MiFocusNotificationActionReceiver.EXTRA_NOTIFICATION_ID, -1),
        )
        assertTrue(savedIntent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0)
        assertTrue(isReceiverExported(context))
    }

    @Suppress("DEPRECATION")
    private fun isReceiverExported(context: Application): Boolean {
        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(context, MiFocusNotificationActionReceiver::class.java),
                PackageManager.GET_META_DATA,
            )
        return info.exported
    }
}

class MiFocusNotificationActionsTestApp : Application()
