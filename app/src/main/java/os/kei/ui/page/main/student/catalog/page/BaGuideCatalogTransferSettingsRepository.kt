package os.kei.ui.page.main.student.catalog.page

import os.kei.ui.page.main.ba.support.BASettingsStore

internal object BaGuideCatalogTransferSettingsRepository {
    fun loadMediaSaveCustomEnabled(): Boolean = BASettingsStore.loadMediaSaveCustomEnabled()

    fun saveMediaSaveCustomEnabled(enabled: Boolean) {
        BASettingsStore.saveMediaSaveCustomEnabled(enabled)
    }

    fun loadMediaSaveFixedTreeUri(): String = BASettingsStore.loadMediaSaveFixedTreeUri()

    fun saveMediaSaveFixedTreeUri(uri: String) {
        BASettingsStore.saveMediaSaveFixedTreeUri(uri)
    }

    fun clearMediaSaveFixedTreeUri() {
        BASettingsStore.saveMediaSaveFixedTreeUri("")
    }
}
