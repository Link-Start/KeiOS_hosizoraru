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
import os.kei.mcp.framework.notification.builder.NotificationRenderStyle
import os.kei.mcp.notification.McpNotificationActiveStateCache
import os.kei.mcp.notification.McpNotificationSnapshot
import os.kei.mcp.notification.McpNotificationSnapshotStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun `mark read receiver clears cached notification runtime state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationId = 243_221
        McpNotificationSnapshotStore.put(
            notificationId = notificationId,
            snapshot =
                McpNotificationSnapshot(
                    serverName = "BlueArchive AP",
                    running = true,
                    port = 120,
                    path = "120",
                    clients = 240,
                    ongoing = true,
                    onlyAlertOnce = true,
                    style = NotificationRenderStyle.MI_ISLAND,
                    useXiaomiMagic = true,
                ),
        )
        McpNotificationActiveStateCache.markActive(notificationId, active = true)

        MiFocusNotificationActionReceiver().onReceive(
            context,
            Intent(context, MiFocusNotificationActionReceiver::class.java).apply {
                action = MiFocusNotificationActionReceiver.ACTION_MARK_READ
                putExtra(MiFocusNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
        )

        assertNull(McpNotificationSnapshotStore.get(notificationId))
        assertFalse(
            McpNotificationActiveStateCache.isActive(notificationId, nowMs = 20_000L) {
                false
            }
        )
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
