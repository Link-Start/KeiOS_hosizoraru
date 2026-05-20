package os.kei.feature.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal object MiFocusNotificationActions {
    fun markReadPendingIntent(
        context: Context,
        notificationId: Int,
        requestCode: Int = notificationId + MARK_READ_REQUEST_OFFSET,
    ): PendingIntent {
        val intent =
            Intent(context, MiFocusNotificationActionReceiver::class.java).apply {
                action = MiFocusNotificationActionReceiver.ACTION_MARK_READ
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(MiFocusNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val MARK_READ_REQUEST_OFFSET = 1
}
