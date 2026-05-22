package os.kei.ui.page.main.student

import android.content.Context
import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal data class BaGuideTempMediaSummary(
    val count: Int,
    val bytes: Long,
    val latest: Long,
)

private data class BaGuideTempMediaSessionSummary(
    val count: Int,
    val bytes: Long,
    val latest: Long,
)

internal class BaGuideTempMediaIndex(
    private val clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
) {
    private val indexStore: MMKV by lazy { KeiMmkv.byId(BA_GUIDE_TEMP_MEDIA_INDEX_KV_ID) }
    private val lastPruneAtMs = AtomicLong(0L)
    private val lastIndexStaleCheckAtMs = AtomicLong(0L)

    fun clearIndex() {
        val kv = indexStore
        loadSessionIds(kv).forEach { id ->
            kv.removeValueForKey(sessionCountKey(id))
            kv.removeValueForKey(sessionBytesKey(id))
            kv.removeValueForKey(sessionLatestKey(id))
        }
        kv.removeValueForKey(BA_GUIDE_TEMP_MEDIA_KEY_SESSION_IDS)
        kv.removeValueForKey(BA_GUIDE_TEMP_MEDIA_KEY_INDEX_VERSION)
        kv.trim()
        lastIndexStaleCheckAtMs.set(clock.nowMs())
    }

    fun removeSessionIndex(sourceUrl: String) {
        val id = baGuideTempMediaSessionId(sourceUrl)
        writeSessionSummary(
            id = id,
            summary = BaGuideTempMediaSessionSummary(count = 0, bytes = 0L, latest = 0L),
        )
    }

    fun rebuildSessionIndex(
        context: Context,
        sourceUrl: String,
    ) {
        val id = baGuideTempMediaSessionId(sourceUrl)
        val summary = scanSessionSummary(baGuideTempMediaSessionDirById(context, id))
        writeSessionSummary(id, summary)
    }

    fun pruneTempCache(context: Context) {
        val now = clock.nowMs()
        val lastPrune = lastPruneAtMs.get()
        if (lastPrune > 0L && now - lastPrune < BA_GUIDE_TEMP_MEDIA_PRUNE_MIN_INTERVAL_MS) return
        if (!lastPruneAtMs.compareAndSet(lastPrune, now)) return

        val root = baGuideTempMediaRootDir(context)
        if (!root.exists()) {
            clearIndex()
            return
        }

        data class SessionCandidate(
            val id: String,
            val dir: File,
            val summary: BaGuideTempMediaSessionSummary,
        )
        val candidates =
            root
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .map { dir ->
                    SessionCandidate(
                        id = dir.name.trim(),
                        dir = dir,
                        summary = scanSessionSummary(dir),
                    )
                }.filter { it.id.isNotBlank() }
                .toMutableList()

        candidates
            .filter { candidate ->
                candidate.summary.count <= 0 ||
                    candidate.summary.bytes <= 0L ||
                    (candidate.summary.latest > 0L && now - candidate.summary.latest > BA_GUIDE_TEMP_MEDIA_SESSION_TTL_MS)
            }.forEach { candidate ->
                runCatching { candidate.dir.deleteRecursively() }
            }

        val survivors =
            candidates
                .filter { it.dir.exists() }
                .sortedBy { it.summary.latest }
                .toMutableList()
        var totalBytes = survivors.sumOf { it.summary.bytes.coerceAtLeast(0L) }
        while (totalBytes > BA_GUIDE_TEMP_MEDIA_MAX_TOTAL_CACHE_BYTES && survivors.isNotEmpty()) {
            val victim = survivors.removeAt(0)
            totalBytes -= victim.summary.bytes.coerceAtLeast(0L)
            runCatching { victim.dir.deleteRecursively() }
        }
        rebuildAllIndex(context)
    }

    fun loadIndexSummary(context: Context): BaGuideTempMediaSummary {
        val kv = indexStore
        val root = baGuideTempMediaRootDir(context)
        val hasIndex = kv.decodeInt(BA_GUIDE_TEMP_MEDIA_KEY_INDEX_VERSION, 0) == BA_GUIDE_TEMP_MEDIA_INDEX_VERSION
        val hasSessions = loadSessionIds(kv).isNotEmpty()
        return when {
            !root.exists() -> {
                if (hasSessions || hasIndex) clearIndex()
                BaGuideTempMediaSummary(count = 0, bytes = 0L, latest = 0L)
            }

            !hasIndex -> {
                rebuildAllIndex(context)
            }

            !hasSessions -> {
                val hasFiles = root.listFiles().orEmpty().any { it.isDirectory }
                if (hasFiles) rebuildAllIndex(context) else BaGuideTempMediaSummary(count = 0, bytes = 0L, latest = 0L)
            }

            shouldCheckIndexedSessions() && hasStaleIndexedSessions(context, kv) -> {
                rebuildAllIndex(context)
            }

            else -> {
                aggregateSummaryFromIndex(kv)
            }
        }
    }

    private fun rebuildAllIndex(context: Context): BaGuideTempMediaSummary {
        val root = baGuideTempMediaRootDir(context)
        if (!root.exists()) {
            clearIndex()
            return BaGuideTempMediaSummary(count = 0, bytes = 0L, latest = 0L)
        }
        val kv = indexStore
        val discoveredIds = mutableSetOf<String>()
        root
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .forEach { dir ->
                val id = dir.name.trim()
                if (id.isBlank()) return@forEach
                val summary = scanSessionSummary(dir)
                discoveredIds.add(id)
                writeSessionSummary(id, summary, kv)
            }
        val staleIds = loadSessionIds(kv) - discoveredIds
        staleIds.forEach { staleId ->
            writeSessionSummary(
                id = staleId,
                summary = BaGuideTempMediaSessionSummary(count = 0, bytes = 0L, latest = 0L),
                kv = kv,
            )
        }
        lastIndexStaleCheckAtMs.set(clock.nowMs())
        return aggregateSummaryFromIndex(kv)
    }

    private fun aggregateSummaryFromIndex(kv: MMKV = indexStore): BaGuideTempMediaSummary {
        val ids = loadSessionIds(kv)
        if (ids.isEmpty()) return BaGuideTempMediaSummary(count = 0, bytes = 0L, latest = 0L)
        var totalCount = 0
        var totalBytes = 0L
        var latest = 0L
        ids.forEach { id ->
            val session = readSessionSummary(id, kv) ?: return@forEach
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

    private fun shouldCheckIndexedSessions(): Boolean {
        val now = clock.nowMs()
        val lastCheck = lastIndexStaleCheckAtMs.get()
        if (lastCheck > 0L && now - lastCheck < BA_GUIDE_TEMP_MEDIA_INDEX_STALE_CHECK_MIN_INTERVAL_MS) return false
        return lastIndexStaleCheckAtMs.compareAndSet(lastCheck, now)
    }

    private fun hasStaleIndexedSessions(
        context: Context,
        kv: MMKV = indexStore,
    ): Boolean =
        loadSessionIds(kv).any { id ->
            val stored = readSessionSummary(id, kv) ?: return@any true
            val scanned = scanSessionSummary(baGuideTempMediaSessionDirById(context, id))
            stored != scanned
        }

    private fun scanSessionSummary(dir: File): BaGuideTempMediaSessionSummary {
        val summary = scanBaGuideMediaCacheSession(dir)
        return BaGuideTempMediaSessionSummary(
            count = summary.count,
            bytes = summary.bytes,
            latest = summary.latest,
        )
    }

    private fun writeSessionSummary(
        id: String,
        summary: BaGuideTempMediaSessionSummary,
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

    private fun readSessionSummary(
        id: String,
        kv: MMKV = indexStore,
    ): BaGuideTempMediaSessionSummary? {
        if (!kv.containsKey(sessionCountKey(id)) || !kv.containsKey(sessionBytesKey(id))) return null
        val count = kv.decodeInt(sessionCountKey(id), -1)
        val bytes = kv.decodeLong(sessionBytesKey(id), -1L)
        if (count < 0 || bytes < 0L) return null
        return BaGuideTempMediaSessionSummary(
            count = count,
            bytes = bytes,
            latest = kv.decodeLong(sessionLatestKey(id), 0L).coerceAtLeast(0L),
        )
    }

    private fun loadSessionIds(kv: MMKV = indexStore): MutableSet<String> {
        val raw = kv.decodeString(BA_GUIDE_TEMP_MEDIA_KEY_SESSION_IDS, "").orEmpty()
        if (raw.isBlank()) return mutableSetOf()
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
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
