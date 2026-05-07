package os.kei.ui.page.main.student

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification

@OptIn(UnstableApi::class)
internal object BaGuideBgmMediaNotificationProviderFactory {
    fun create(context: Context): MediaNotification.Provider {
        val delegate = DefaultMediaNotificationProvider.Builder(context)
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
}
