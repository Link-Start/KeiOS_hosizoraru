package os.kei.ui.page.main.student

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import org.json.JSONArray
import org.json.JSONObject

data class BaStudentGuideCacheSnapshot(
    val info: BaStudentGuideInfo?,
    val hasCache: Boolean,
    val isComplete: Boolean,
    val syncedAtMs: Long
) {
    companion object {
        val EMPTY = BaStudentGuideCacheSnapshot(
            info = null,
            hasCache = false,
            isComplete = false,
            syncedAtMs = 0L
        )
    }
}

object BaStudentGuideStore {
    private val store: MMKV by lazy { KeiMmkv.byId(BA_GUIDE_KV_ID) }

    private val memoryCache = object : LinkedHashMap<String, BaStudentGuideInfo>(
        BA_GUIDE_MEMORY_CACHE_LIMIT,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BaStudentGuideInfo>?): Boolean {
            return size > BA_GUIDE_MEMORY_CACHE_LIMIT
        }
    }

    private fun kv(): MMKV = store

    private fun memoryGet(sourceUrl: String): BaStudentGuideInfo? {
        return synchronized(memoryCache) { memoryCache[sourceUrl] }
    }

    private fun memoryPut(info: BaStudentGuideInfo) {
        val source = normalizeStudentGuideSourceUrl(info.sourceUrl)
        if (source.isBlank()) return
        val normalizedInfo = if (source == info.sourceUrl) info else info.copy(sourceUrl = source)
        synchronized(memoryCache) {
            memoryCache[source] = normalizedInfo
        }
    }

    private fun memoryRemove(sourceUrl: String) {
        synchronized(memoryCache) {
            memoryCache.remove(sourceUrl)
        }
    }

    private fun memoryClear() {
        synchronized(memoryCache) {
            memoryCache.clear()
        }
    }

    fun setCurrentUrl(url: String) {
        kv().encode(BA_GUIDE_KEY_CURRENT_URL, normalizeStudentGuideSourceUrl(url))
    }

    fun loadCurrentUrl(): String = normalizeStudentGuideSourceUrl(
        kv().decodeString(BA_GUIDE_KEY_CURRENT_URL, "").orEmpty()
    )

    fun saveInfo(info: BaStudentGuideInfo) {
        val source = normalizeStudentGuideSourceUrl(info.sourceUrl)
        if (source.isBlank()) return
        val normalizedInfo = if (source == info.sourceUrl) info else info.copy(sourceUrl = source)
        val id = guideCacheId(source)
        val store = kv()

        val metaRaw = JSONObject().apply {
            put("schema", BA_GUIDE_CACHE_SCHEMA_VERSION)
            put("sourceUrl", source)
            put("title", normalizedInfo.title)
            put("subtitle", normalizedInfo.subtitle)
            put("description", normalizedInfo.description)
            put("imageUrl", normalizedInfo.imageUrl)
            put("summary", normalizedInfo.summary)
            put("syncedAtMs", normalizedInfo.syncedAtMs.coerceAtLeast(0L))
            put("voiceCvJp", normalizedInfo.voiceCvJp)
            put("voiceCvCn", normalizedInfo.voiceCvCn)
            put("voiceCvByLanguage", encodeStringMap(normalizedInfo.voiceCvByLanguage))
            put(
                "voiceLanguageHeaders",
                JSONArray().apply { normalizedInfo.voiceLanguageHeaders.forEach { put(it) } }
            )
            put("tabSkillIconUrl", normalizedInfo.tabSkillIconUrl)
            put("tabProfileIconUrl", normalizedInfo.tabProfileIconUrl)
            put("tabVoiceIconUrl", normalizedInfo.tabVoiceIconUrl)
            put("tabGalleryIconUrl", normalizedInfo.tabGalleryIconUrl)
            put("tabSimulateIconUrl", normalizedInfo.tabSimulateIconUrl)
        }.toString()

        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_META), metaRaw)
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_STATS), encodeStats(normalizedInfo.stats).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_SKILL), encodeGuideRows(normalizedInfo.skillRows).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_PROFILE), encodeGuideRows(normalizedInfo.profileRows).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_GALLERY), encodeGalleryItems(normalizedInfo.galleryItems).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_GROWTH), encodeGuideRows(normalizedInfo.growthRows).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_SIMULATE), encodeGuideRows(normalizedInfo.simulateRows).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_VOICE_ROWS), encodeGuideRows(normalizedInfo.voiceRows).toString())
        store.encode(guideV2CacheKey(id, CACHE_SUFFIX_VOICE_ENTRIES), encodeVoiceEntries(normalizedInfo.voiceEntries).toString())

        store.removeValueForKey(guideLegacyCacheKey(source))

        val index = readGuideV2Index(store)
        index += source
        writeGuideV2Index(index, store)
        memoryPut(normalizedInfo)
    }

    fun loadInfoSnapshot(url: String): BaStudentGuideCacheSnapshot {
        val source = normalizeStudentGuideSourceUrl(url)
        if (source.isBlank()) return BaStudentGuideCacheSnapshot.EMPTY

        memoryGet(source)?.let { memory ->
            return BaStudentGuideCacheSnapshot(
                info = memory,
                hasCache = true,
                isComplete = true,
                syncedAtMs = memory.syncedAtMs
            )
        }

        val store = kv()
        val id = guideCacheId(source)
        val hasV2Any = BA_GUIDE_CACHE_REQUIRED_SUFFIXES.any { suffix ->
            store.containsKey(guideV2CacheKey(id, suffix))
        }

        if (hasV2Any) {
            val hasAllRequired = BA_GUIDE_CACHE_REQUIRED_SUFFIXES.all { suffix ->
                store.containsKey(guideV2CacheKey(id, suffix))
            }
            val syncedAtMs = readGuideSyncedAtMsFromV2Meta(store, id)
            if (!hasAllRequired) {
                return BaStudentGuideCacheSnapshot(
                    info = null,
                    hasCache = true,
                    isComplete = false,
                    syncedAtMs = syncedAtMs
                )
            }
            val info = decodeGuideV2Info(source = source, id = id, store = store)
            val complete = isGuideInfoPayloadComplete(info)
            if (complete && info != null) {
                memoryPut(info)
            }
            return BaStudentGuideCacheSnapshot(
                info = if (complete) info else null,
                hasCache = true,
                isComplete = complete,
                syncedAtMs = info?.syncedAtMs ?: syncedAtMs
            )
        }

        val legacyRaw = store.decodeString(guideLegacyCacheKey(source), "").orEmpty()
        if (legacyRaw.isNotBlank()) {
            val legacyInfo = decodeGuideLegacyInfo(legacyRaw, source)
            val complete = isGuideInfoPayloadComplete(legacyInfo)
            if (complete && legacyInfo != null) {
                saveInfo(legacyInfo)
                memoryPut(legacyInfo)
            }
            return BaStudentGuideCacheSnapshot(
                info = if (complete) legacyInfo else null,
                hasCache = true,
                isComplete = complete,
                syncedAtMs = legacyInfo?.syncedAtMs ?: 0L
            )
        }

        return BaStudentGuideCacheSnapshot.EMPTY
    }

    fun loadInfo(url: String): BaStudentGuideInfo? {
        return loadInfoSnapshot(url).info
    }

    fun isCacheExpired(
        snapshot: BaStudentGuideCacheSnapshot,
        refreshIntervalHours: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!snapshot.hasCache) return true
        if (snapshot.syncedAtMs <= 0L) return true
        val intervalMs = refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        return (nowMs - snapshot.syncedAtMs).coerceAtLeast(0L) >= intervalMs
    }

    fun clearCachedInfo(url: String) {
        val source = normalizeStudentGuideSourceUrl(url)
        if (source.isBlank()) return
        val id = guideCacheId(source)
        val store = kv()
        store.removeValueForKey(guideLegacyCacheKey(source))
        store.allKeys()
            .orEmpty()
            .filter { key -> key.startsWith(guideV2EntryPrefix(id)) }
            .forEach(store::removeValueForKey)

        val index = readGuideV2Index(store)
        if (index.remove(source)) {
            writeGuideV2Index(index, store)
        }
        memoryRemove(source)
    }

    fun cachedEntryCount(): Int {
        val store = kv()
        val v2Count = readGuideV2Index(store).size
        val legacyCount = store.allKeys()
            .orEmpty()
            .count { key -> key.startsWith(BA_GUIDE_KEY_LEGACY_CACHE_PREFIX) }
        return v2Count + legacyCount
    }

    fun clearAllCachedInfo() {
        val store = kv()
        store.allKeys()
            .orEmpty()
            .filter { key ->
                key.startsWith(BA_GUIDE_KEY_LEGACY_CACHE_PREFIX) ||
                    key.startsWith(BA_GUIDE_KEY_V2_CACHE_PREFIX) ||
                    key == BA_GUIDE_KEY_V2_INDEX
            }
            .forEach(store::removeValueForKey)
        memoryClear()
        store.trim()
    }

    fun storageFootprintBytes(): Long = kv().totalSize()

    fun actualDataBytes(): Long = kv().actualSize()

    fun cacheBytesEstimated(): Long {
        val store = kv()
        return store.allKeys()
            .orEmpty()
            .filter { key ->
                key.startsWith(BA_GUIDE_KEY_LEGACY_CACHE_PREFIX) ||
                    key.startsWith(BA_GUIDE_KEY_V2_CACHE_PREFIX) ||
                    key == BA_GUIDE_KEY_V2_INDEX
            }
            .sumOf { key -> store.decodeString(key, "").orEmpty().length.toLong() * 2 + 16L }
    }

    fun configBytesEstimated(): Long {
        val currentUrlBytes = loadCurrentUrl().length.toLong() * 2 + 16L
        val indexBytes = kv().decodeString(BA_GUIDE_KEY_V2_INDEX, "").orEmpty().length.toLong() * 2 + 16L
        return currentUrlBytes + indexBytes
    }

    fun latestSyncedAtMs(): Long {
        val store = kv()
        var latest = 0L

        readGuideV2Index(store).forEach { source ->
            val synced = readGuideSyncedAtMsFromV2Meta(store, guideCacheId(source))
            if (synced > latest) latest = synced
        }

        store.allKeys()
            .orEmpty()
            .filter { key -> key.startsWith(BA_GUIDE_KEY_LEGACY_CACHE_PREFIX) }
            .forEach { key ->
                val synced = runCatching {
                    JSONObject(store.decodeString(key, "").orEmpty()).optLong("syncedAtMs", 0L)
                }.getOrDefault(0L)
                if (synced > latest) latest = synced
            }

        return latest
    }

    fun cachedSourceUrls(): Set<String> {
        val store = kv()
        return readGuideV2Index(store).toSet()
    }
}
