package os.kei.feature.home.data

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.defaultHomeOverviewCards

internal object HomeOverviewPrefs {
    const val KV_ID = "home_page_prefs"
    private const val KEY_VISIBLE_OVERVIEW_CARDS = "home_visible_overview_cards"
    private const val KEY_WEBDAV_OVERVIEW_CARD_MIGRATED = "home_webdav_overview_card_migrated"
    private const val KEY_SHOW_CACHE_FRESHNESS_IN_CARDS = "home_show_cache_freshness_in_cards"

    fun loadVisibleOverviewCards(
        store: HomeOverviewKeyValueStore = mmkvStore()
    ): Set<HomeOverviewCard> {
        if (!store.contains(KEY_VISIBLE_OVERVIEW_CARDS)) return defaultHomeOverviewCards()
        val cards = decodeVisibleOverviewCards(
            raw = store.decodeString(KEY_VISIBLE_OVERVIEW_CARDS, "").orEmpty(),
            missingFallback = emptySet()
        )
        if (store.decodeBool(KEY_WEBDAV_OVERVIEW_CARD_MIGRATED, false)) return cards
        val migratedCards = cards + HomeOverviewCard.WEBDAV
        saveVisibleOverviewCards(migratedCards, store)
        return migratedCards
    }

    fun saveVisibleOverviewCards(
        cards: Set<HomeOverviewCard>,
        store: HomeOverviewKeyValueStore = mmkvStore()
    ) {
        store.encode(KEY_VISIBLE_OVERVIEW_CARDS, encodeVisibleOverviewCards(cards))
        store.encode(KEY_WEBDAV_OVERVIEW_CARD_MIGRATED, true)
    }

    fun loadCacheFreshnessVisibleInCards(
        store: HomeOverviewKeyValueStore = mmkvStore()
    ): Boolean {
        return store.decodeBool(KEY_SHOW_CACHE_FRESHNESS_IN_CARDS, false)
    }

    fun saveCacheFreshnessVisibleInCards(
        visible: Boolean,
        store: HomeOverviewKeyValueStore = mmkvStore()
    ) {
        store.encode(KEY_SHOW_CACHE_FRESHNESS_IN_CARDS, visible)
    }

    internal fun encodeVisibleOverviewCards(cards: Set<HomeOverviewCard>): String {
        return cards.joinToString(",") { it.name }
    }

    internal fun decodeVisibleOverviewCards(
        raw: String?,
        missingFallback: Set<HomeOverviewCard> = defaultHomeOverviewCards()
    ): Set<HomeOverviewCard> {
        val normalized = raw.orEmpty().trim()
        if (normalized.isBlank()) return missingFallback
        return normalized.split(',')
            .mapNotNull { name ->
                HomeOverviewCard.entries.firstOrNull { it.name == name.trim() }
            }
            .toSet()
    }

    private fun mmkvStore(): HomeOverviewKeyValueStore {
        return MmkvHomeOverviewKeyValueStore(KeiMmkv.byId(KV_ID))
    }
}

internal interface HomeOverviewKeyValueStore {
    fun contains(key: String): Boolean
    fun decodeString(key: String, defaultValue: String): String?
    fun encode(key: String, value: String)
    fun decodeBool(key: String, defaultValue: Boolean): Boolean
    fun encode(key: String, value: Boolean)
}

private class MmkvHomeOverviewKeyValueStore(
    private val kv: MMKV
) : HomeOverviewKeyValueStore {
    override fun contains(key: String): Boolean = kv.containsKey(key)

    override fun decodeString(key: String, defaultValue: String): String? {
        return kv.decodeString(key, defaultValue)
    }

    override fun encode(key: String, value: String) {
        kv.encode(key, value)
    }

    override fun decodeBool(key: String, defaultValue: Boolean): Boolean {
        return kv.decodeBool(key, defaultValue)
    }

    override fun encode(key: String, value: Boolean) {
        kv.encode(key, value)
    }
}
