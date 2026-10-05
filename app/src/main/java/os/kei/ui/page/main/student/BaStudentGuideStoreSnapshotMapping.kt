package os.kei.ui.page.main.student

import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject

internal fun readGuideV2Index(store: MMKV): MutableSet<String> {
    val raw = store.decodeString(BA_GUIDE_KEY_V2_INDEX, "").orEmpty()
    if (raw.isBlank()) return mutableSetOf()
    return runCatching {
        val arr = JSONArray(raw)
        buildSet {
            for (i in 0 until arr.length()) {
                val value = normalizeStudentGuideSourceUrl(arr.optString(i))
                if (value.isNotBlank()) add(value)
            }
        }.toMutableSet()
    }.getOrDefault(mutableSetOf())
}

internal fun writeGuideV2Index(index: Set<String>, store: MMKV) {
    if (index.isEmpty()) {
        store.removeValueForKey(BA_GUIDE_KEY_V2_INDEX)
        return
    }
    val raw = JSONArray().apply {
        index.sorted().forEach { put(it) }
    }.toString()
    store.encode(BA_GUIDE_KEY_V2_INDEX, raw)
}

internal fun isGuideInfoPayloadComplete(info: BaStudentGuideInfo?): Boolean {
    info ?: return false
    if (info.syncedAtMs <= 0L) return false
    val hasIdentity =
        info.title.isNotBlank() ||
            info.subtitle.isNotBlank() ||
            info.summary.isNotBlank() ||
            info.imageUrl.isNotBlank()
    val hasAnySection =
        info.stats.isNotEmpty() ||
            info.skillRows.isNotEmpty() ||
            info.profileRows.isNotEmpty() ||
            info.galleryItems.isNotEmpty() ||
            info.growthRows.isNotEmpty() ||
            info.simulateRows.isNotEmpty() ||
            info.voiceRows.isNotEmpty() ||
            info.voiceEntries.isNotEmpty()
    return hasIdentity && hasAnySection
}

internal fun decodeGuideLegacyInfo(raw: String, source: String): BaStudentGuideInfo? {
    if (raw.isBlank()) return null
    return runCatching {
        val obj = JSONObject(raw)
        val stats = decodeStats(obj.optJSONArray("stats"))
        val voiceCvJp = obj.optString("voiceCvJp").trim()
        val voiceCvCn = obj.optString("voiceCvCn").trim()
        val voiceCvByLanguage = decodeVoiceCvByLanguage(
            obj = obj,
            fallbackJp = voiceCvJp,
            fallbackCn = voiceCvCn
        )
        BaStudentGuideInfo(
            sourceUrl = normalizeStudentGuideSourceUrl(obj.optString("sourceUrl").ifBlank { source }),
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            description = obj.optString("description"),
            imageUrl = obj.optString("imageUrl"),
            summary = obj.optString("summary"),
            stats = stats,
            skillRows = decodeGuideRows(obj, "skillRows"),
            profileRows = decodeGuideRows(obj, "profileRows"),
            galleryItems = decodeGalleryItems(obj, "galleryItems"),
            galleryParserVersion = obj.optInt("galleryParserVersion", 0),
            growthRows = decodeGuideRows(obj, "growthRows"),
            simulateRows = decodeGuideRows(obj, "simulateRows"),
            voiceRows = decodeGuideRows(obj, "voiceRows"),
            voiceCvJp = voiceCvJp,
            voiceCvCn = voiceCvCn,
            voiceCvByLanguage = voiceCvByLanguage,
            voiceLanguageHeaders = decodeHeaders(obj.optJSONArray("voiceLanguageHeaders")),
            voiceEntries = decodeVoiceEntries(obj, "voiceEntries"),
            tabSkillIconUrl = obj.optString("tabSkillIconUrl").trim(),
            tabProfileIconUrl = obj.optString("tabProfileIconUrl").trim(),
            tabVoiceIconUrl = obj.optString("tabVoiceIconUrl").trim(),
            tabGalleryIconUrl = obj.optString("tabGalleryIconUrl").trim(),
            tabSimulateIconUrl = obj.optString("tabSimulateIconUrl").trim(),
            syncedAtMs = obj.optLong("syncedAtMs", 0L)
        )
    }.getOrNull()
}

internal fun decodeGuideV2Info(source: String, id: String, store: MMKV): BaStudentGuideInfo? =
    decodeGuideV2InfoFromPayload(source = source) { suffix ->
        store.decodeString(guideV2CacheKey(id, suffix), "").orEmpty()
    }

internal fun decodeGuideV2InfoFromPayload(
    source: String,
    payload: (String) -> String,
): BaStudentGuideInfo? {
    val metaRaw = payload(CACHE_SUFFIX_META)
    if (metaRaw.isBlank()) return null
    return runCatching {
        val meta = JSONObject(metaRaw)
        if (meta.optInt("schema", 0) < BA_GUIDE_CACHE_SCHEMA_VERSION) return@runCatching null
        val voiceCvJp = meta.optString("voiceCvJp").trim()
        val voiceCvCn = meta.optString("voiceCvCn").trim()
        val stats = decodeStats(
            parseJsonArray(
                payload(CACHE_SUFFIX_STATS)
            )
        )
        val skillRows = decodeGuideRowsFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_SKILL)
            )
        )
        val profileRows = decodeGuideRowsFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_PROFILE)
            )
        )
        val galleryItems = decodeGalleryItemsFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_GALLERY)
            )
        )
        val growthRows = decodeGuideRowsFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_GROWTH)
            )
        )
        val simulateRows = decodeGuideRowsFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_SIMULATE)
            )
        )
        val voiceRows = decodeGuideRowsFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_VOICE_ROWS)
            )
        )
        val voiceEntries = decodeVoiceEntriesFromArray(
            parseJsonArray(
                payload(CACHE_SUFFIX_VOICE_ENTRIES)
            )
        )
        val voiceCvByLanguage = decodeVoiceCvByLanguage(
            obj = meta,
            fallbackJp = voiceCvJp,
            fallbackCn = voiceCvCn
        )
        BaStudentGuideInfo(
            sourceUrl = normalizeStudentGuideSourceUrl(meta.optString("sourceUrl").ifBlank { source }),
            title = meta.optString("title"),
            subtitle = meta.optString("subtitle"),
            description = meta.optString("description"),
            imageUrl = meta.optString("imageUrl"),
            summary = meta.optString("summary"),
            stats = stats,
            skillRows = skillRows,
            profileRows = profileRows,
            galleryItems = galleryItems,
            galleryParserVersion = meta.optInt("galleryParserVersion", 0),
            growthRows = growthRows,
            simulateRows = simulateRows,
            voiceRows = voiceRows,
            voiceCvJp = voiceCvJp,
            voiceCvCn = voiceCvCn,
            voiceCvByLanguage = voiceCvByLanguage,
            voiceLanguageHeaders = decodeHeaders(meta.optJSONArray("voiceLanguageHeaders")),
            voiceEntries = voiceEntries,
            tabSkillIconUrl = meta.optString("tabSkillIconUrl").trim(),
            tabProfileIconUrl = meta.optString("tabProfileIconUrl").trim(),
            tabVoiceIconUrl = meta.optString("tabVoiceIconUrl").trim(),
            tabGalleryIconUrl = meta.optString("tabGalleryIconUrl").trim(),
            tabSimulateIconUrl = meta.optString("tabSimulateIconUrl").trim(),
            syncedAtMs = meta.optLong("syncedAtMs", 0L)
        )
    }.getOrNull()
}

internal fun readGuideSyncedAtMsFromV2Meta(store: MMKV, id: String): Long {
    val raw = store.decodeString(guideV2CacheKey(id, CACHE_SUFFIX_META), "").orEmpty()
    if (raw.isBlank()) return 0L
    return runCatching { JSONObject(raw).optLong("syncedAtMs", 0L) }.getOrDefault(0L)
}
