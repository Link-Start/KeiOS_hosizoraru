package os.kei.ui.page.main.student

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv

internal data class BaGuideTempMediaSessionIndexSummary(
    val count: Int,
    val bytes: Long,
    val latest: Long,
)

internal class BaGuideTempMediaSessionIndexStore(
    private val indexStoreProvider: () -> MMKV = { KeiMmkv.byId(BA_GUIDE_TEMP_MEDIA_INDEX_KV_ID) },
) {
    private val indexStore: MMKV by lazy(indexStoreProvider)

    fun clear() {
        val kv = indexStore
        loadSessionIds(kv).forEach { id ->
            kv.removeValueForKey(sessionCountKey(id))
            kv.removeValueForKey(sessionBytesKey(id))
            kv.removeValueForKey(sessionLatestKey(id))
        }
        kv.removeValueForKey(BA_GUIDE_TEMP_MEDIA_KEY_SESSION_IDS)
        kv.removeValueForKey(BA_GUIDE_TEMP_MEDIA_KEY_INDEX_VERSION)
        kv.trim()
    }

    fun hasValidIndex(): Boolean = indexStore.decodeInt(BA_GUIDE_TEMP_MEDIA_KEY_INDEX_VERSION, 0) == BA_GUIDE_TEMP_MEDIA_INDEX_VERSION

    fun hasSessions(): Boolean = loadSessionIds().isNotEmpty()

    fun aggregateSummary(): BaGuideTempMediaSummary {
        val ids = loadSessionIds()
        if (ids.isEmpty()) return BaGuideTempMediaSummary(count = 0, bytes = 0L, latest = 0L)
        var totalCount = 0
        var totalBytes = 0L
        var latest = 0L
        ids.forEach { id ->
            val session = readSessionSummary(id) ?: return@forEach
            totalCount += session.count
            totalBytes += session.bytes
            latest = maxOf(latest, session.latest)
        }
        return BaGuideTempMediaSummary(
            count = totalCount.coerceAtLeast(0),
            bytes = totalBytes.coerceAtLeast(0L),
            latest = latest.coerceAtLeast(0L),
        )
    }

    fun loadSessionIds(kv: MMKV = indexStore): MutableSet<String> {
        val raw = kv.decodeString(BA_GUIDE_TEMP_MEDIA_KEY_SESSION_IDS, "").orEmpty()
        if (raw.isBlank()) return mutableSetOf()
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
    }

    fun writeSessionSummary(
        id: String,
        summary: BaGuideTempMediaSessionIndexSummary,
        kv: MMKV = indexStore,
    ) {
        val ids = loadSessionIds(kv)
        if (summary.count <= 0 || summary.bytes <= 0L) {
            ids.remove(id)
            kv.removeValueForKey(sessionCountKey(id))
            kv.removeValueForKey(sessionBytesKey(id))
            kv.removeValueForKey(sessionLatestKey(id))
        } else {
            ids.add(id)
            kv.encode(sessionCountKey(id), summary.count)
            kv.encode(sessionBytesKey(id), summary.bytes)
            kv.encode(sessionLatestKey(id), summary.latest.coerceAtLeast(0L))
        }
        saveSessionIds(ids, kv)
        kv.encode(BA_GUIDE_TEMP_MEDIA_KEY_INDEX_VERSION, BA_GUIDE_TEMP_MEDIA_INDEX_VERSION)
    }

    fun readSessionSummary(
        id: String,
        kv: MMKV = indexStore,
    ): BaGuideTempMediaSessionIndexSummary? {
        if (!kv.containsKey(sessionCountKey(id)) || !kv.containsKey(sessionBytesKey(id))) return null
        val count = kv.decodeInt(sessionCountKey(id), -1)
        val bytes = kv.decodeLong(sessionBytesKey(id), -1L)
        if (count < 0 || bytes < 0L) return null
        return BaGuideTempMediaSessionIndexSummary(
            count = count,
            bytes = bytes,
            latest = kv.decodeLong(sessionLatestKey(id), 0L).coerceAtLeast(0L),
        )
    }

    private fun saveSessionIds(
        ids: Set<String>,
        kv: MMKV = indexStore,
    ) {
        val encoded = ids.filter { it.isNotBlank() }.sorted().joinToString(",")
        kv.encode(BA_GUIDE_TEMP_MEDIA_KEY_SESSION_IDS, encoded)
    }

    private fun sessionCountKey(id: String): String = "s_${id}_c"

    private fun sessionBytesKey(id: String): String = "s_${id}_b"

    private fun sessionLatestKey(id: String): String = "s_${id}_m"
}
