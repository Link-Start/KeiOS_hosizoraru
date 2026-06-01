package os.kei.ui.page.main.student.catalog

import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import os.kei.core.prefs.KeiMmkv

private const val BA_GUIDE_CATALOG_KV_ID = "ba_guide_catalog"
private const val KEY_CACHE_RAW = "catalog_cache_raw"
private const val KEY_CACHE_SYNC_MS = "catalog_cache_sync_ms"
private const val KEY_CACHE_VERSION = "catalog_cache_version"
private const val KEY_FAVORITES_RAW = "catalog_favorites_raw"
private const val KEY_RELEASE_DATE_INDEX_RAW = "catalog_release_date_index_raw"
private const val BA_GUIDE_CATALOG_CACHE_SCHEMA_VERSION = 4

internal object BaGuideCatalogStore {
    private val store: MMKV by lazy { KeiMmkv.byId(BA_GUIDE_CATALOG_KV_ID) }
    private val favoritesLock = Any()

    private fun kv(): MMKV = store

    fun saveBundle(bundle: BaGuideCatalogBundle) {
        val store = kv()
        val raw =
            JSONObject()
                .apply {
                    put("syncedAtMs", bundle.syncedAtMs.coerceAtLeast(0L))
                    put(
                        "tabs",
                        JSONObject().apply {
                            BaGuideCatalogTab.entries.forEach { tab ->
                                put(
                                    tab.name,
                                    JSONArray().apply {
                                        bundle.entries(tab).forEach { entry ->
                                            put(
                                                JSONObject().apply {
                                                    put("entryId", entry.entryId)
                                                    put("pid", entry.pid)
                                                    put("contentId", entry.contentId)
                                                    put("name", entry.name)
                                                    put("alias", entry.alias)
                                                    put("aliasDisplay", entry.aliasDisplay)
                                                    put("iconUrl", entry.iconUrl)
                                                    put("type", entry.type)
                                                    put("order", entry.order)
                                                    put("createdAtSec", entry.createdAtSec)
                                                    put("releaseDateSec", entry.releaseDateSec)
                                                    put("releaseDateProbeAtMs", entry.releaseDateProbeAtMs)
                                                    put("detailUrl", entry.detailUrl)
                                                    put("filterAttributes", entry.filterAttributes.toJson())
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "filterDefinitions",
                        JSONObject().apply {
                            BaGuideCatalogTab.entries.forEach { tab ->
                                put(
                                    tab.name,
                                    JSONArray().apply {
                                        bundle.filterDefinitions(tab).forEach { definition ->
                                            put(definition.toJson())
                                        }
                                    },
                                )
                            }
                        },
                    )
                }.toString()
        store.encode(KEY_CACHE_RAW, raw)
        store.encode(KEY_CACHE_SYNC_MS, bundle.syncedAtMs.coerceAtLeast(0L))
        store.encode(KEY_CACHE_VERSION, BA_GUIDE_CATALOG_CACHE_SCHEMA_VERSION)
    }

    fun loadBundle(): BaGuideCatalogBundle? {
        val store = kv()
        if (store.decodeInt(KEY_CACHE_VERSION, 0) < BA_GUIDE_CATALOG_CACHE_SCHEMA_VERSION) {
            return null
        }
        val releaseDateIndex = loadReleaseDateIndex(store)
        val raw = store.decodeString(KEY_CACHE_RAW, "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val tabsObj = root.optJSONObject("tabs") ?: JSONObject()
            val definitionsObj = root.optJSONObject("filterDefinitions") ?: JSONObject()
            val filterDefinitionsByTab =
                BaGuideCatalogTab.entries.associateWith { tab ->
                    val arr = definitionsObj.optJSONArray(tab.name) ?: JSONArray()
                    buildList {
                        for (index in 0 until arr.length()) {
                            val definition = arr.optJSONObject(index).toFilterDefinitionOrNull()
                            if (definition != null) add(definition)
                        }
                    }
                }
            val entriesByTab =
                BaGuideCatalogTab.entries.associateWith { tab ->
                    val arr = tabsObj.optJSONArray(tab.name) ?: JSONArray()
                    buildList {
                        for (index in 0 until arr.length()) {
                            val item = arr.optJSONObject(index) ?: continue
                            val contentId = item.optLong("contentId", 0L)
                            val name = item.optString("name").trim()
                            if (contentId <= 0L || name.isBlank()) continue
                            val alias = item.optString("alias").trim()
                            val aliasDisplay =
                                item.optString("aliasDisplay").trim().ifBlank {
                                    alias
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                }
                            add(
                                BaGuideCatalogEntry(
                                    entryId = item.optInt("entryId", 0),
                                    pid = item.optInt("pid", 0),
                                    contentId = contentId,
                                    name = name,
                                    alias = alias,
                                    aliasDisplay = aliasDisplay,
                                    iconUrl = item.optString("iconUrl").trim(),
                                    type = item.optInt("type", 0),
                                    order = item.optInt("order", index),
                                    createdAtSec = item.optLong("createdAtSec", 0L),
                                    releaseDateSec =
                                        item
                                            .optLong("releaseDateSec", 0L)
                                            .takeIf { it > 0L }
                                            ?: releaseDateIndex[contentId]
                                            ?: 0L,
                                    releaseDateProbeAtMs =
                                        item
                                            .optLong("releaseDateProbeAtMs", 0L)
                                            .coerceAtLeast(0L),
                                    detailUrl =
                                        item
                                            .optString("detailUrl")
                                            .trim()
                                            .ifBlank { "https://www.gamekee.com/ba/tj/$contentId.html" },
                                    tab = tab,
                                    filterAttributes =
                                        BaGuideCatalogEntryFilterAttributes.fromJson(
                                            item.optJSONObject("filterAttributes"),
                                        ),
                                ),
                            )
                        }
                    }
                }
            val syncedAtMs =
                root
                    .optLong(
                        "syncedAtMs",
                        store.decodeLong(KEY_CACHE_SYNC_MS, 0L),
                    ).coerceAtLeast(0L)
            BaGuideCatalogBundle(
                entriesByTab = entriesByTab,
                syncedAtMs = syncedAtMs,
                filterDefinitionsByTab = filterDefinitionsByTab,
            )
        }.getOrNull()
    }

    fun clearCache() {
        val store = kv()
        store.removeValueForKey(KEY_CACHE_RAW)
        store.removeValueForKey(KEY_CACHE_SYNC_MS)
        store.removeValueForKey(KEY_CACHE_VERSION)
        store.trim()
    }

    private fun loadReleaseDateIndex(store: MMKV = kv()): Map<Long, Long> {
        val raw = store.decodeString(KEY_RELEASE_DATE_INDEX_RAW, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    val contentId = key.toLongOrNull() ?: continue
                    val releaseDateSec = json.optLong(key, 0L).coerceAtLeast(0L)
                    if (contentId > 0L && releaseDateSec > 0L) {
                        put(contentId, releaseDateSec)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun loadReleaseDateIndexSnapshot(): Map<Long, Long> = loadReleaseDateIndex()

    fun upsertReleaseDateIndex(releaseDateSecByContentId: Map<Long, Long>) {
        if (releaseDateSecByContentId.isEmpty()) return
        val merged = loadReleaseDateIndex().toMutableMap()
        releaseDateSecByContentId.forEach { (contentId, releaseDateSec) ->
            if (contentId > 0L && releaseDateSec > 0L) {
                val current = merged[contentId] ?: 0L
                // 实装日期是稳定信息，只更新为更可信的有效值。
                if (current <= 0L || current != releaseDateSec) {
                    merged[contentId] = releaseDateSec
                }
            }
        }
        if (merged.isEmpty()) return
        val raw =
            JSONObject()
                .apply {
                    merged.forEach { (contentId, releaseDateSec) ->
                        put(contentId.toString(), releaseDateSec)
                    }
                }.toString()
        kv().encode(KEY_RELEASE_DATE_INDEX_RAW, raw)
    }

    fun cachedEntryCount(): Int {
        val bundle = loadBundle() ?: return 0
        return bundle.entriesByTab.values.sumOf { it.size }
    }

    fun cachedEntryCounts(): Map<BaGuideCatalogTab, Int> {
        val bundle = loadBundle()
        return BaGuideCatalogTab.entries.associateWith { tab ->
            bundle?.entries(tab)?.size ?: 0
        }
    }

    fun latestSyncedAtMs(): Long =
        loadBundle()?.syncedAtMs
            ?: kv().decodeLong(KEY_CACHE_SYNC_MS, 0L)

    fun actualDataBytes(): Long = kv().actualSize()

    fun cacheBytesEstimated(): Long {
        val store = kv()
        val raw = store.decodeString(KEY_CACHE_RAW, "").orEmpty()
        val indexRaw = store.decodeString(KEY_RELEASE_DATE_INDEX_RAW, "").orEmpty()
        val rawBytes = if (raw.isBlank()) 0L else raw.length.toLong() * 2L + 16L
        val indexBytes = if (indexRaw.isBlank()) 0L else indexRaw.length.toLong() * 2L + 16L
        return rawBytes + indexBytes
    }

    fun configBytesEstimated(): Long = 0L

    fun loadFavorites(): Map<Long, Long> =
        synchronized(favoritesLock) {
            loadFavoritesLocked(kv())
        }

    private fun loadFavoritesLocked(store: MMKV): Map<Long, Long> {
        val raw = store.decodeString(KEY_FAVORITES_RAW, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    val contentId = key.toLongOrNull() ?: continue
                    val favoritedAtMs = json.optLong(key, 0L).coerceAtLeast(0L)
                    if (contentId > 0L && favoritedAtMs > 0L) {
                        put(contentId, favoritedAtMs)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveFavorites(favorites: Map<Long, Long>) =
        synchronized(favoritesLock) {
            saveFavoritesLocked(kv(), favorites)
        }

    private fun saveFavoritesLocked(
        store: MMKV,
        favorites: Map<Long, Long>,
    ) {
        if (favorites.isEmpty()) {
            store.removeValueForKey(KEY_FAVORITES_RAW)
            return
        }
        val raw =
            JSONObject()
                .apply {
                    favorites.forEach { (contentId, favoritedAtMs) ->
                        if (contentId > 0L && favoritedAtMs > 0L) {
                            put(contentId.toString(), favoritedAtMs)
                        }
                    }
                }.toString()
        store.encode(KEY_FAVORITES_RAW, raw)
    }

    fun toggleFavorite(
        contentId: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean =
        toggleFavoriteSnapshot(
            contentId = contentId,
            nowMs = nowMs,
        )[contentId] != null

    fun toggleFavoriteSnapshot(
        contentId: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Map<Long, Long> {
        val store = kv()
        return synchronized(favoritesLock) {
            if (contentId <= 0L) return@synchronized loadFavoritesLocked(store)
            val current = loadFavoritesLocked(store).toMutableMap()
            if (current.containsKey(contentId)) {
                current.remove(contentId)
            } else {
                current[contentId] = nowMs.coerceAtLeast(1L)
            }
            saveFavoritesLocked(store, current)
            current.toMap()
        }
    }
}

private fun BaGuideCatalogFilterDefinition.toJson(): JSONObject =
    JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put(
            "options",
            JSONArray().apply {
                options.forEach { option ->
                    put(
                        JSONObject().apply {
                            put("id", option.id)
                            put("name", option.name)
                            put("iconUrl", option.iconUrl)
                        },
                    )
                }
            },
        )
    }

private fun JSONObject?.toFilterDefinitionOrNull(): BaGuideCatalogFilterDefinition? {
    this ?: return null
    val id = optInt("id", 0)
    val name = optString("name").trim()
    if (id <= 0 || name.isBlank()) return null
    val rawOptions = optJSONArray("options") ?: JSONArray()
    return BaGuideCatalogFilterDefinition(
        id = id,
        name = name,
        type = optInt("type", 0),
        options =
            buildList {
                for (index in 0 until rawOptions.length()) {
                    val item = rawOptions.optJSONObject(index) ?: continue
                    val optionId = item.optInt("id", 0)
                    val optionName = item.optString("name").trim()
                    if (optionId <= 0 || optionName.isBlank()) continue
                    add(
                        BaGuideCatalogFilterOption(
                            id = optionId,
                            name = optionName,
                            iconUrl = item.optString("iconUrl").trim(),
                        ),
                    )
                }
            },
    )
}
