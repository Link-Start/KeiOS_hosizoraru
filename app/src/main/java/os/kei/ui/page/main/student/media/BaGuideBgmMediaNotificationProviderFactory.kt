@file:OptIn(UnstableApi::class)

package os.kei.ui.page.main.student.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import os.kei.ui.page.main.student.BaGuideBgmMediaIslandShareNotificationProvider
import os.kei.ui.page.main.student.BaGuideBgmMediaOemCompat

@OptIn(UnstableApi::class)
internal val BA_GUIDE_BGM_MEDIA_NOTIFICATION_ID: Int =
    DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID

@OptIn(UnstableApi::class)
internal object BaGuideBgmMediaNotificationProviderFactory {
    fun create(context: Context): MediaNotification.Provider {
        val delegate = DefaultMediaNotificationProvider.Builder(context)
            .setNotificationId(BA_GUIDE_BGM_MEDIA_NOTIFICATION_ID)
            .build()
            .apply {
                setSmallIcon(BaGuideBgmMediaOemCompat.mediaSmallIconRes())
            }
        return BaGuideBgmMediaIslandShareNotificationProvider(
            context = context.applicationContext,
            delegate = delegate,
            enabled = BaGuideBgmMediaOemCompat.mediaIslandDragShareSupported()
        )
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context)
            .cancel(BA_GUIDE_BGM_MEDIA_NOTIFICATION_ID)
    }
}
