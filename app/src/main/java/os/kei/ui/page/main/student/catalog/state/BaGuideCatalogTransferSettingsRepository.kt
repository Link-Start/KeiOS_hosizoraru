package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore

internal data class BaGuideCatalogTransferSettingsUiState(
    val mediaSaveCustomEnabled: Boolean = false,
    val mediaSaveFixedTreeUri: String = "",
)

internal object BaGuideCatalogTransferSettingsRepository {
    suspend fun loadSettings(): BaGuideCatalogTransferSettingsUiState =
        withContext(AppDispatchers.baFetch) {
            BaGuideCatalogTransferSettingsUiState(
                mediaSaveCustomEnabled = BASettingsStore.loadMediaSaveCustomEnabled(),
                mediaSaveFixedTreeUri = BASettingsStore.loadMediaSaveFixedTreeUri(),
            )
        }

    suspend fun saveMediaSaveCustomEnabled(enabled: Boolean) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveMediaSaveCustomEnabled(enabled)
        }
    }

    suspend fun saveMediaSaveFixedTreeUri(uri: String) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveMediaSaveFixedTreeUri(uri)
        }
    }

    suspend fun clearMediaSaveFixedTreeUri() {
        saveMediaSaveFixedTreeUri("")
    }
}
