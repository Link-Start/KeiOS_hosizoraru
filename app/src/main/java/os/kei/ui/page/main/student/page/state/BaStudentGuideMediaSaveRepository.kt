package os.kei.ui.page.main.student.page.state

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore

internal data class BaStudentGuideMediaSaveLocation(
    val useFixedLocation: Boolean,
    val fixedTreeUriRaw: String,
)

internal object BaStudentGuideMediaSaveRepository {
    suspend fun loadSaveLocation(): BaStudentGuideMediaSaveLocation =
        withContext(AppDispatchers.baFetch) {
            BaStudentGuideMediaSaveLocation(
                useFixedLocation = BASettingsStore.loadMediaSaveCustomEnabled(),
                fixedTreeUriRaw = BASettingsStore.loadMediaSaveFixedTreeUri(),
            )
        }

    suspend fun saveFixedTreeUri(treeUriRaw: String) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveMediaSaveFixedTreeUri(treeUriRaw)
        }
    }

    suspend fun clearFixedTreeUri() {
        saveFixedTreeUri("")
    }
}
