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
import os.kei.core.notification.live.builder.NotificationRenderStyle
import os.kei.mcp.notification.McpNotificationActiveStateCache
import os.kei.mcp.notification.McpNotificationSnapshot
import os.kei.mcp.notification.McpNotificationSnapshotStore
import os.kei.ui.page.main.ba.BA_AP_DISMISS_SNOOZE_INTERVAL_MS
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
    fun `mark read action targets the unexported focus receiver with foreground delivery`() {
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
        // Must stay unexported. The action can only arrive as this PendingIntent, which carries our
        // own identity, so exporting the receiver adds no delivery path and only lets any installed
        // app fire mark-read by component name. Verified on HyperOS: the Super Island still renders
        // and still carries both actions with the receiver unexported.
        assertFalse(isReceiverExported(context))
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

    @Test
    fun `dismiss receiver clears cached notification runtime state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationId = 243_222
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
                action = MiFocusNotificationActionReceiver.ACTION_DISMISS
                putExtra(MiFocusNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
        )

        assertNull(McpNotificationSnapshotStore.get(notificationId))
        assertFalse(
            McpNotificationActiveStateCache.isActive(notificationId, nowMs = 20_000L) {
                false
            },
        )
    }

    @Test
    fun `dismiss interaction writes one hour snooze without read acknowledgement`() {
        val write =
            resolveBaApNotificationInteractionWrite(
                action = MiFocusNotificationActionReceiver.ACTION_DISMISS,
                nowMs = 20_000L,
            )

        assertNotNull(write)
        assertNull(write.suppressionAnchorAtMs)
        assertEquals(20_000L + BA_AP_DISMISS_SNOOZE_INTERVAL_MS, write.dismissedUntilAtMs)
    }

    @Test
    fun `mark read interaction clears snooze and writes acknowledgement`() {
        val write =
            resolveBaApNotificationInteractionWrite(
                action = MiFocusNotificationActionReceiver.ACTION_MARK_READ,
                nowMs = 20_000L,
            )

        assertNotNull(write)
        assertEquals(20_000L, write.suppressionAnchorAtMs)
        assertEquals(0L, write.dismissedUntilAtMs)
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
