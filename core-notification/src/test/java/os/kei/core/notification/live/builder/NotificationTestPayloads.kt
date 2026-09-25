package os.kei.core.notification.live.builder

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import org.json.JSONObject
import os.kei.core.notification.live.LiveNotificationPayload
import kotlin.test.assertNotNull

internal fun testPendingIntent(
    context: Context,
    requestCode: Int,
    action: String,
    broadcast: Boolean = false,
): PendingIntent {
    val intent = Intent(action).setPackage(context.packageName)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return if (broadcast) {
        PendingIntent.getBroadcast(context, requestCode, intent, flags)
    } else {
        PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}

internal fun testNotificationPayload(
    state: LiveNotificationPayload,
    settings: UserSettings = UserSettings(miIslandOuterGlow = true),
    semanticIconBitmap: Bitmap? = null,
    miIslandProgressColorOverride: String? = null,
    channelId: String = "test_mi_island_channel",
    isHyperOS: Boolean = true,
): NotificationPayload =
    NotificationPayload(
        state = state,
        settings = settings,
        environment = EnvironmentContext(channelId = channelId, isHyperOS = isHyperOS),
        semanticIconBitmap = semanticIconBitmap,
        miIslandProgressColorOverride = miIslandProgressColorOverride,
    )

internal fun Notification.focusParam(): String = extras.getString("miui.focus.param").orEmpty()

internal fun Notification.focusJson(): JSONObject = JSONObject(focusParam()).getJSONObject("param_v2")

@Suppress("DEPRECATION")
internal fun Notification.focusAction(key: String): Notification.Action {
    val actions = extras.getBundle("miui.focus.actions")
    assertNotNull(actions, "Focus actions bundle should be present")
    return actions.getParcelable<Notification.Action>(key) ?: error("Missing focus action: $key")
}

@Suppress("DEPRECATION")
internal fun Notification.focusPicture(key: String): Icon {
    val pics = extras.getBundle("miui.focus.pics")
    assertNotNull(pics, "Focus pictures bundle should be present")
    return pics.getParcelable<Icon>(key) ?: error("Missing focus picture: $key")
}
