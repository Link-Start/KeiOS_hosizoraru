package os.kei.ui.page.main.student.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object BaGuideCatalogFavoritesStoreSignals {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { previous -> previous + 1L }
    }
}

/**
 * Keeps [entries] equal to the stored catalog favourites for as long as the caller keeps collecting.
 *
 * The catalog ViewModel outlives the pages that write favourites. JSON import and WebDAV sync write
 * [BaGuideCatalogStore] directly, never through it, so without this the catalog kept showing the favourites
 * it loaded first until the process died. Reloads once on start, then once per store write.
 */
internal suspend fun followBaGuideCatalogFavoritesStore(
    entries: MutableStateFlow<Map<Long, Long>>,
    loadFavorites: suspend () -> Map<Long, Long>,
    storeVersion: Flow<Long> = BaGuideCatalogFavoritesStoreSignals.version,
    onReloaded: () -> Unit = {},
) {
    storeVersion.collect {
        entries.value = loadFavorites()
        onReloaded()
    }
}
