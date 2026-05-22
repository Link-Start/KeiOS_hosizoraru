package os.kei.ui.page.main.student

import android.content.Context
import java.io.File

internal class BaGuideTempMediaPruner(
    private val clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
) {
    fun prune(context: Context): Boolean {
        val root = baGuideTempMediaRootDir(context)
        if (!root.exists()) return false

        val now = clock.nowMs()
        val candidates =
            root
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .map { dir ->
                    PruneCandidate(
                        id = dir.name.trim(),
                        dir = dir,
                        summary = scanBaGuideMediaCacheSession(dir),
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
        return true
    }

    private data class PruneCandidate(
        val id: String,
        val dir: File,
        val summary: BaGuideMediaCacheSessionSummary,
    )
}
