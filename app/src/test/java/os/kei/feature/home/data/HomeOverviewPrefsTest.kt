package os.kei.feature.home.data

import org.junit.Test
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.defaultHomeOverviewCards
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeOverviewPrefsTest {
    @Test
    fun `missing visible cards key uses default cards`() {
        val store = InMemoryHomeOverviewStore()

        assertEquals(defaultHomeOverviewCards(), HomeOverviewPrefs.loadVisibleOverviewCards(store))
    }

    @Test
    fun `blank visible cards value means all cards hidden`() {
        val store = InMemoryHomeOverviewStore()
        HomeOverviewPrefs.saveVisibleOverviewCards(emptySet(), store)

        assertEquals(emptySet(), HomeOverviewPrefs.loadVisibleOverviewCards(store))
    }

    @Test
    fun `visible cards round trip preserves selected cards`() {
        val store = InMemoryHomeOverviewStore()
        val cards = setOf(HomeOverviewCard.GITHUB, HomeOverviewCard.BA)

        HomeOverviewPrefs.saveVisibleOverviewCards(cards, store)

        assertEquals(cards, HomeOverviewPrefs.loadVisibleOverviewCards(store))
    }

    @Test
    fun `legacy visible cards gain webdav card once`() {
        val store = InMemoryHomeOverviewStore()
        store.encode("home_visible_overview_cards", "MCP,GITHUB,BA")

        assertEquals(defaultHomeOverviewCards(), HomeOverviewPrefs.loadVisibleOverviewCards(store))
        HomeOverviewPrefs.saveVisibleOverviewCards(setOf(HomeOverviewCard.MCP), store)
        assertEquals(setOf(HomeOverviewCard.MCP), HomeOverviewPrefs.loadVisibleOverviewCards(store))
    }

    @Test
    fun `cache freshness debug flag defaults off and round trips`() {
        val store = InMemoryHomeOverviewStore()

        assertFalse(HomeOverviewPrefs.loadCacheFreshnessVisibleInCards(store))
        HomeOverviewPrefs.saveCacheFreshnessVisibleInCards(true, store)

        assertTrue(HomeOverviewPrefs.loadCacheFreshnessVisibleInCards(store))
    }

    private class InMemoryHomeOverviewStore : HomeOverviewKeyValueStore {
        private val values = mutableMapOf<String, Any>()

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun decodeString(key: String, defaultValue: String): String {
            return values[key] as? String ?: defaultValue
        }

        override fun encode(key: String, value: String) {
            values[key] = value
        }

        override fun decodeBool(key: String, defaultValue: Boolean): Boolean {
            return values[key] as? Boolean ?: defaultValue
        }

        override fun encode(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
