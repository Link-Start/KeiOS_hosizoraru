package os.kei.ui.page.main.student.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BaGuideCatalogFavoritesSynchronizationTest {
    @Test
    fun `each favorite write advances store signal`() {
        val before = BaGuideCatalogFavoritesStoreSignals.version.value

        BaGuideCatalogFavoritesStoreSignals.notifyChanged()
        BaGuideCatalogFavoritesStoreSignals.notifyChanged()

        assertEquals(before + 2L, BaGuideCatalogFavoritesStoreSignals.version.value)
    }

    /**
     * JSON import and WebDAV sync write [BaGuideCatalogStore] directly, never through the catalog ViewModel,
     * which outlives them. Before the signal existed the catalog kept the favourites it loaded first. This
     * runs the store's real write paths against the follower the ViewModel's `catalogFavoriteEntries` runs.
     */
    @Test
    fun `a favorite written straight to the store reaches the long lived catalog state`() =
        runTest {
            val kv = FakeFavoritesKeyValueStore()
            val entries = MutableStateFlow<Map<Long, Long>>(emptyMap())
            backgroundScope.launch {
                followBaGuideCatalogFavoritesStore(
                    entries = entries,
                    loadFavorites = { BaGuideCatalogStore.loadFavorites(kv) },
                )
            }
            runCurrent()

            // The shape of WebDAV sync and JSON import: a whole map saved at once.
            BaGuideCatalogStore.saveFavorites(mapOf(7L to 1_000L), kv)
            runCurrent()
            assertEquals(mapOf(7L to 1_000L), entries.value)

            // And the single toggle.
            BaGuideCatalogStore.toggleFavoriteSnapshot(9L, nowMs = 2_000L, favoritesStore = kv)
            runCurrent()
            assertEquals(mapOf(7L to 1_000L, 9L to 2_000L), entries.value)
        }
}

private class FakeFavoritesKeyValueStore : BaGuideCatalogFavoritesKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun decodeString(
        key: String,
        defaultValue: String,
    ): String? = values[key] ?: defaultValue

    override fun encode(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun removeValueForKey(key: String) {
        values.remove(key)
    }
}
