package os.kei.ui.page.main.student.page.state

import os.kei.ui.page.main.ba.support.BASettingsStore

internal data class BaStudentGuideMediaSaveLocation(
    val useFixedLocation: Boolean,
    val fixedTreeUriRaw: String,
)

internal object BaStudentGuideMediaSaveRepository {
    fun loadSaveLocation(): BaStudentGuideMediaSaveLocation =
        BaStudentGuideMediaSaveLocation(
            useFixedLocation = BASettingsStore.loadMediaSaveCustomEnabled(),
            fixedTreeUriRaw = BASettingsStore.loadMediaSaveFixedTreeUri(),
        )

    fun saveFixedTreeUri(treeUriRaw: String) {
        BASettingsStore.saveMediaSaveFixedTreeUri(treeUriRaw)
    }

    fun clearFixedTreeUri() {
        BASettingsStore.saveMediaSaveFixedTreeUri("")
    }
}
