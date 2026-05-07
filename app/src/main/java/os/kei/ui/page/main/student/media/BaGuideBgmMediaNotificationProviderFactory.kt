package os.kei.ui.page.main.student

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import os.kei.R

@DrawableRes
internal val BA_GUIDE_BGM_MEDIA_SMALL_ICON_RES: Int = R.drawable.ic_launcher_monochrome

@OptIn(UnstableApi::class)
internal object BaGuideBgmMediaNotificationProviderFactory {
    fun create(context: Context): DefaultMediaNotificationProvider {
        return DefaultMediaNotificationProvider.Builder(context)
            .build()
            .apply { setSmallIcon(BA_GUIDE_BGM_MEDIA_SMALL_ICON_RES) }
    }
}
