package os.kei.ui.page.main.student.section.gallery

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.GameKeeMediaImageLoader

internal class GuideFullscreenMediaRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
) {
    suspend fun loadMediaAdaptiveRotationEnabled(): Boolean =
        withContext(ioDispatcher) {
            BASettingsStore.loadMediaAdaptiveRotationEnabled()
        }

    suspend fun loadSampledBitmap(
        context: Context,
        source: String,
        maxDecodeDimension: Int,
    ): Bitmap? =
        withContext(ioDispatcher) {
            GameKeeMediaImageLoader.loadGuideBitmap(
                context = context,
                source = source,
                maxDecodeDimension = maxDecodeDimension,
            )
        }
}
