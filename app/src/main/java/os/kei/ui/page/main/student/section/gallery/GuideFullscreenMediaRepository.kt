package os.kei.ui.page.main.student.section.gallery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore

internal class GuideFullscreenMediaRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
) {
    suspend fun loadMediaAdaptiveRotationEnabled(): Boolean =
        withContext(ioDispatcher) {
            BASettingsStore.loadMediaAdaptiveRotationEnabled()
        }
}
