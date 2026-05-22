package os.kei.ui.page.main.student

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal data class BaGuideTempMediaSummary(
    val count: Int,
    val bytes: Long,
    val latest: Long,
)

internal class BaGuideTempMediaIndex(
    private val clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
    private val sessionIndexStore: BaGuideTempMediaSessionIndexStore = BaGuideTempMediaSessionIndexStore(),
) {
    private val pruner = BaGuideTempMediaPruner(clock)
    private val lastPruneAtMs = AtomicLong(0L)
    private val lastIndexStaleCheckAtMs = AtomicLong(0L)

    fun clearIndex() {
        sessionIndexStore.clear()
        lastIndexStaleCheckAtMs.set(clock.nowMs())
    }

    fun removeSessionIndex(sourceUrl: String) {
        val id = baGuideTempMediaSessionId(sourceUrl)
        sessionIndexStore.writeSessionSummary(
            id = id,
            summary = BaGuideTempMediaSessionIndexSummary(count = 0, bytes = 0L, latest = 0L),
        )
    }

    fun rebuildSessionIndex(
        context: Context,
        sourceUrl: String,
    ) {
        val id = baGuideTempMediaSessionId(sourceUrl)
        val summary = scanSessionSummary(baGuideTempMediaSessionDirById(context, id))
        sessionIndexStore.writeSessionSummary(id, summary)
    }

    fun pruneTempCache(context: Context) {
        val now = clock.nowMs()
        val lastPrune = lastPruneAtMs.get()
        if (lastPrune > 0L && now - lastPrune < BA_GUIDE_TEMP_MEDIA_PRUNE_MIN_INTERVAL_MS) return
        if (!lastPruneAtMs.compareAndSet(lastPrune, now)) return

        if (!pruner.prune(context)) {
            clearIndex()
            return
        }
        rebuildAllIndex(context)
    }

    fun loadIndexSummary(context: Context): BaGuideTempMediaSummary {
        val root = baGuideTempMediaRootDir(context)
        val hasIndex = sessionIndexStore.hasValidIndex()
        val hasSessions = sessionIndexStore.hasSessions()
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

            shouldCheckIndexedSessions() && hasStaleIndexedSessions(context) -> {
                rebuildAllIndex(context)
            }

            else -> {
                sessionIndexStore.aggregateSummary()
            }
        }
    }

    private fun rebuildAllIndex(context: Context): BaGuideTempMediaSummary {
        val root = baGuideTempMediaRootDir(context)
        if (!root.exists()) {
            clearIndex()
            return BaGuideTempMediaSummary(count = 0, bytes = 0L, latest = 0L)
        }
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
                sessionIndexStore.writeSessionSummary(id, summary)
            }
        val staleIds = sessionIndexStore.loadSessionIds() - discoveredIds
        staleIds.forEach { staleId ->
            sessionIndexStore.writeSessionSummary(
                id = staleId,
                summary = BaGuideTempMediaSessionIndexSummary(count = 0, bytes = 0L, latest = 0L),
            )
        }
        lastIndexStaleCheckAtMs.set(clock.nowMs())
        return sessionIndexStore.aggregateSummary()
    }

    private fun shouldCheckIndexedSessions(): Boolean {
        val now = clock.nowMs()
        val lastCheck = lastIndexStaleCheckAtMs.get()
        if (lastCheck > 0L && now - lastCheck < BA_GUIDE_TEMP_MEDIA_INDEX_STALE_CHECK_MIN_INTERVAL_MS) return false
        return lastIndexStaleCheckAtMs.compareAndSet(lastCheck, now)
    }

    private fun hasStaleIndexedSessions(context: Context): Boolean =
        sessionIndexStore.loadSessionIds().any { id ->
            val stored = sessionIndexStore.readSessionSummary(id) ?: return@any true
            val scanned = scanSessionSummary(baGuideTempMediaSessionDirById(context, id))
            stored != scanned
        }

    private fun scanSessionSummary(dir: File): BaGuideTempMediaSessionIndexSummary {
        val summary = scanBaGuideMediaCacheSession(dir)
        return BaGuideTempMediaSessionIndexSummary(
            count = summary.count,
            bytes = summary.bytes,
            latest = summary.latest,
        )
    }
}
