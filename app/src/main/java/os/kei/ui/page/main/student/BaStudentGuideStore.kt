package os.kei.ui.page.main.student

import android.content.Context
import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

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
    @Volatile
    private var payloadRootDir: File? = null

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

    internal fun configure(context: Context) {
        payloadRootDir = context.applicationContext.filesDir
    }

    private fun payloadFileStoreOrNull(): BaStudentGuidePayloadFileCacheStore? =
        payloadRootDir?.let(::BaStudentGuidePayloadFileCacheStore)

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
        val payload = encodeGuideV2Payload(normalizedInfo)

        store.removeValueForKey(guideLegacyCacheKey(source))

        val payloadStore = payloadFileStoreOrNull()
        if (payloadStore?.isEnabled() == true) {
            payloadStore.savePayload(source, payload)
            removeGuideV2Payload(store, id)
            val index = readGuideV2Index(store)
            if (index.remove(source)) {
                writeGuideV2Index(index, store)
            }
        } else {
            writeGuideV2Payload(mmkvPayloadKeyValueStore(store), id, payload)
            val index = readGuideV2Index(store)
            index += source
            writeGuideV2Index(index, store)
        }
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

        payloadFileStoreOrNull()?.let { payloadStore ->
            if (payloadStore.contains(source)) {
                val payload = payloadStore.loadPayload(source)
                val info = payload?.let { values ->
                    decodeGuideV2InfoFromPayload(source = source) { suffix -> values[suffix].orEmpty() }
                }
                val complete = isGuideInfoPayloadComplete(info)
                if (complete && info != null) {
                    memoryPut(info)
                }
                return BaStudentGuideCacheSnapshot(
                    info = if (complete) info else null,
                    hasCache = true,
                    isComplete = complete,
                    syncedAtMs = info?.syncedAtMs ?: payload?.get(CACHE_SUFFIX_META)?.let(::readGuideSyncedAtMsFromMetaRaw) ?: 0L,
                )
            }
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
        if (snapshot.info?.galleryParserVersion?.let { it < BA_GUIDE_GALLERY_PARSER_VERSION } == true) return true
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
        removeGuideV2Payload(store, id)
        payloadFileStoreOrNull()?.remove(source)

        val index = readGuideV2Index(store)
        if (index.remove(source)) {
            writeGuideV2Index(index, store)
        }
        memoryRemove(source)
    }

    fun cachedEntryCount(): Int {
        val store = kv()
        val v2Sources = readGuideV2Index(store)
        val fileSources = payloadFileStoreOrNull()?.sourceUrls().orEmpty()
        val legacyCount = store.allKeys()
            .orEmpty()
            .count { key -> key.startsWith(BA_GUIDE_KEY_LEGACY_CACHE_PREFIX) }
        return (v2Sources + fileSources).size + legacyCount
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
        payloadFileStoreOrNull()?.clear()
        memoryClear()
        store.trim()
    }

    fun storageFootprintBytes(): Long = kv().totalSize() + (payloadFileStoreOrNull()?.storageBytes() ?: 0L)

    fun actualDataBytes(): Long = kv().actualSize()

    fun cacheBytesEstimated(): Long {
        val store = kv()
        val mmkvBytes = store.allKeys()
            .orEmpty()
            .filter { key ->
                key.startsWith(BA_GUIDE_KEY_LEGACY_CACHE_PREFIX) ||
                    key.startsWith(BA_GUIDE_KEY_V2_CACHE_PREFIX) ||
                    key == BA_GUIDE_KEY_V2_INDEX
            }
            .sumOf { key -> store.decodeString(key, "").orEmpty().length.toLong() * 2 + 16L }
        return mmkvBytes + (payloadFileStoreOrNull()?.payloadBytes() ?: 0L)
    }

    fun configBytesEstimated(): Long {
        val currentUrlBytes = loadCurrentUrl().length.toLong() * 2 + 16L
        val indexBytes = kv().decodeString(BA_GUIDE_KEY_V2_INDEX, "").orEmpty().length.toLong() * 2 + 16L
        return currentUrlBytes + indexBytes
    }

    fun latestSyncedAtMs(): Long {
        val store = kv()
        var latest = 0L

        val fileLatest = payloadFileStoreOrNull()?.latestSyncedAtMs() ?: 0L
        if (fileLatest > latest) latest = fileLatest

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
        return readGuideV2Index(store).toSet() + payloadFileStoreOrNull()?.sourceUrls().orEmpty()
    }

    internal fun payloadStorageStats(context: Context): BaStudentGuidePayloadStorageStats {
        configure(context)
        val store = kv()
        val mmkvSources = readGuideV2Index(store)
        val fileStore = payloadFileStoreOrNull()
        val fileSources = fileStore?.sourceUrls().orEmpty()
        return BaStudentGuidePayloadStorageStats(
            mmkvEntryCount =
                mmkvSources.count { source ->
                    hasAnyGuideV2Payload(mmkvPayloadKeyValueStore(store), guideCacheId(source))
                },
            fileEntryCount = fileSources.size,
            totalEntryCount = (mmkvSources + fileSources).size,
            mmkvPayloadBytes = mmkvGuidePayloadBytes(mmkvPayloadKeyValueStore(store), mmkvSources),
            filePayloadBytes = fileStore?.payloadBytes() ?: 0L,
            filePayloadEnabled = fileStore?.isEnabled() == true,
        )
    }

    internal fun migratePayloadsToFileStoreIfNeeded(
        context: Context,
        thresholds: BaStudentGuidePayloadMigrationThresholds = BaStudentGuidePayloadMigrationThresholds(),
    ): BaStudentGuidePayloadMigrationResult {
        configure(context)
        val before = payloadStorageStats(context)
        val shouldAttempt = before.filePayloadEnabled || before.shouldMigrateToFiles(thresholds)
        if (!shouldAttempt) {
            return BaStudentGuidePayloadMigrationResult(
                attempted = false,
                migratedEntryCount = 0,
                skippedEntryCount = 0,
                before = before,
                after = before,
            )
        }
        val store = kv()
        val payloadStore = payloadFileStoreOrNull()
            ?: return BaStudentGuidePayloadMigrationResult(
                attempted = false,
                migratedEntryCount = 0,
                skippedEntryCount = 0,
                before = before,
                after = before,
            )
        val migrationWork =
            migrateBaStudentGuidePayloadsToFileStore(
                keyValueStore = mmkvPayloadKeyValueStore(store),
                sourceUrls = readGuideV2Index(store),
                payloadStore = payloadStore,
            )
        writeGuideV2Index(migrationWork.remainingMmkvSources, store)
        if (migrationWork.migratedEntryCount > 0) {
            payloadStore.setEnabled(true)
            store.trim()
        }
        val after = payloadStorageStats(context)
        return BaStudentGuidePayloadMigrationResult(
            attempted = true,
            migratedEntryCount = migrationWork.migratedEntryCount,
            skippedEntryCount = migrationWork.skippedEntryCount,
            before = before,
            after = after,
        )
    }
}

internal fun encodeGuideV2Payload(info: BaStudentGuideInfo): Map<String, String> {
    val source = normalizeStudentGuideSourceUrl(info.sourceUrl)
    val metaRaw = JSONObject().apply {
        put("schema", BA_GUIDE_CACHE_SCHEMA_VERSION)
        put("galleryParserVersion", info.galleryParserVersion)
        put("sourceUrl", source)
        put("title", info.title)
        put("subtitle", info.subtitle)
        put("description", info.description)
        put("imageUrl", info.imageUrl)
        put("summary", info.summary)
        put("syncedAtMs", info.syncedAtMs.coerceAtLeast(0L))
        put("voiceCvJp", info.voiceCvJp)
        put("voiceCvCn", info.voiceCvCn)
        put("voiceCvByLanguage", encodeStringMap(info.voiceCvByLanguage))
        put(
            "voiceLanguageHeaders",
            JSONArray().apply { info.voiceLanguageHeaders.forEach { put(it) } },
        )
        put("tabSkillIconUrl", info.tabSkillIconUrl)
        put("tabProfileIconUrl", info.tabProfileIconUrl)
        put("tabVoiceIconUrl", info.tabVoiceIconUrl)
        put("tabGalleryIconUrl", info.tabGalleryIconUrl)
        put("tabSimulateIconUrl", info.tabSimulateIconUrl)
    }.toString()
    return mapOf(
        CACHE_SUFFIX_META to metaRaw,
        CACHE_SUFFIX_STATS to encodeStats(info.stats).toString(),
        CACHE_SUFFIX_SKILL to encodeGuideRows(info.skillRows).toString(),
        CACHE_SUFFIX_PROFILE to encodeGuideRows(info.profileRows).toString(),
        CACHE_SUFFIX_GALLERY to encodeGalleryItems(info.galleryItems).toString(),
        CACHE_SUFFIX_GROWTH to encodeGuideRows(info.growthRows).toString(),
        CACHE_SUFFIX_SIMULATE to encodeGuideRows(info.simulateRows).toString(),
        CACHE_SUFFIX_VOICE_ROWS to encodeGuideRows(info.voiceRows).toString(),
        CACHE_SUFFIX_VOICE_ENTRIES to encodeVoiceEntries(info.voiceEntries).toString(),
    )
}

internal interface BaStudentGuidePayloadKeyValueStore {
    fun encode(key: String, value: String)
    fun decodeString(key: String): String
    fun containsKey(key: String): Boolean
    fun allKeys(): List<String>
    fun removeValueForKey(key: String)
}

private class MmkvBaStudentGuidePayloadKeyValueStore(
    private val store: MMKV,
) : BaStudentGuidePayloadKeyValueStore {
    override fun encode(key: String, value: String) {
        store.encode(key, value)
    }

    override fun decodeString(key: String): String = store.decodeString(key, "").orEmpty()

    override fun containsKey(key: String): Boolean = store.containsKey(key)

    override fun allKeys(): List<String> = store.allKeys().orEmpty().toList()

    override fun removeValueForKey(key: String) {
        store.removeValueForKey(key)
    }
}

private fun mmkvPayloadKeyValueStore(store: MMKV): BaStudentGuidePayloadKeyValueStore =
    MmkvBaStudentGuidePayloadKeyValueStore(store)

internal data class BaStudentGuidePayloadMigrationWorkResult(
    val migratedEntryCount: Int,
    val skippedEntryCount: Int,
    val remainingMmkvSources: Set<String>,
)

internal fun migrateBaStudentGuidePayloadsToFileStore(
    keyValueStore: BaStudentGuidePayloadKeyValueStore,
    sourceUrls: Set<String>,
    payloadStore: BaStudentGuidePayloadFileCacheStore,
): BaStudentGuidePayloadMigrationWorkResult {
    val remainingMmkvSources = sourceUrls.toMutableSet()
    var migratedCount = 0
    var skippedCount = 0
    sourceUrls.forEach { source ->
        val id = guideCacheId(source)
        val payload = readGuideV2Payload(keyValueStore, id)
        if (payload == null || !payloadStore.savePayload(source, payload)) {
            skippedCount += 1
            return@forEach
        }
        removeGuideV2Payload(keyValueStore, id)
        remainingMmkvSources.remove(source)
        migratedCount += 1
    }
    return BaStudentGuidePayloadMigrationWorkResult(
        migratedEntryCount = migratedCount,
        skippedEntryCount = skippedCount,
        remainingMmkvSources = remainingMmkvSources,
    )
}

internal fun writeGuideV2Payload(
    store: BaStudentGuidePayloadKeyValueStore,
    id: String,
    payload: Map<String, String>,
) {
    BA_GUIDE_CACHE_REQUIRED_SUFFIXES.forEach { suffix ->
        store.encode(guideV2CacheKey(id, suffix), payload[suffix].orEmpty())
    }
}

internal fun readGuideV2Payload(
    store: BaStudentGuidePayloadKeyValueStore,
    id: String,
): Map<String, String>? {
    val payload =
        BA_GUIDE_CACHE_REQUIRED_SUFFIXES.associateWith { suffix ->
            store.decodeString(guideV2CacheKey(id, suffix))
        }
    return payload.takeIf { values -> values.values.all { it.isNotBlank() } }
}

private fun removeGuideV2Payload(
    store: MMKV,
    id: String,
) {
    removeGuideV2Payload(mmkvPayloadKeyValueStore(store), id)
}

private fun removeGuideV2Payload(
    store: BaStudentGuidePayloadKeyValueStore,
    id: String,
) {
    store.allKeys()
        .filter { key -> key.startsWith(guideV2EntryPrefix(id)) }
        .forEach(store::removeValueForKey)
}

private fun hasAnyGuideV2Payload(
    store: BaStudentGuidePayloadKeyValueStore,
    id: String,
): Boolean =
    BA_GUIDE_CACHE_REQUIRED_SUFFIXES.any { suffix ->
        store.containsKey(guideV2CacheKey(id, suffix))
    }

internal fun mmkvGuidePayloadBytes(
    store: BaStudentGuidePayloadKeyValueStore,
    sources: Set<String>,
): Long =
    sources.sumOf { source ->
        val id = guideCacheId(source)
        BA_GUIDE_CACHE_REQUIRED_SUFFIXES.sumOf { suffix ->
            store.decodeString(guideV2CacheKey(id, suffix))
                .toByteArray(StandardCharsets.UTF_8)
                .size
                .toLong()
        }
    }

private fun readGuideSyncedAtMsFromMetaRaw(raw: String): Long {
    if (raw.isBlank()) return 0L
    return runCatching { JSONObject(raw).optLong("syncedAtMs", 0L) }.getOrDefault(0L)
}
