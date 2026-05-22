package os.kei.ui.page.main.student

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class BaGuideTempMediaPrefetcher(
    private val cacheIndex: BaGuideTempMediaIndex,
    private val clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
) {
    private val downloadLocks = ConcurrentHashMap<String, DownloadLock>()

    suspend fun prefetchForGuide(
        context: Context,
        sourceUrl: String,
        rawUrls: List<String>,
        forceReDownload: Boolean,
    ) {
        val dir = baGuideTempMediaSessionDir(context, sourceUrl)
        dir.mkdirs()
        val targets =
            rawUrls
                .map(::baGuideNormalizeMediaTarget)
                .filter { it.isNotBlank() }
                .distinct()
        if (targets.isEmpty()) return

        coroutineScope {
            val semaphore = Semaphore(BA_GUIDE_TEMP_MEDIA_MAX_PARALLEL_DOWNLOADS)
            targets.chunked(BA_GUIDE_TEMP_MEDIA_PREFETCH_TASK_BATCH_SIZE).forEach { batch ->
                ensureActive()
                batch
                    .map { url ->
                        async {
                            semaphore.withPermit {
                                prefetchSingleTarget(
                                    context = context,
                                    sourceUrl = sourceUrl,
                                    normalizedUrl = url,
                                    forceReDownload = forceReDownload,
                                )
                            }
                        }
                    }.awaitAll()
            }
        }
        cacheIndex.rebuildSessionIndex(context, sourceUrl)
        cacheIndex.pruneTempCache(context)
    }

    fun activeDownloadLockCount(): Int = downloadLocks.size

    private suspend fun prefetchSingleTarget(
        context: Context,
        sourceUrl: String,
        normalizedUrl: String,
        forceReDownload: Boolean,
    ) {
        withDownloadLock(sourceUrl = sourceUrl, normalizedUrl = normalizedUrl) {
            currentCoroutineContext().ensureActive()
            val file = baGuideTempMediaTargetFile(context, sourceUrl, normalizedUrl)
            if (!forceReDownload && BaGuideTempMediaValidation.isUsableCachedMedia(normalizedUrl, file)) {
                return@withDownloadLock
            }
            val downloaded =
                BaGuideTempMediaDownload.downloadWithValidation(
                    normalizedUrl = normalizedUrl,
                    targetFile = file,
                    forceReDownload = forceReDownload,
                    clock = clock,
                )
            if (!downloaded) {
                runCatching { file.delete() }
            }
        }
    }

    private suspend fun <T> withDownloadLock(
        sourceUrl: String,
        normalizedUrl: String,
        block: suspend () -> T,
    ): T {
        val key = lockKey(sourceUrl, normalizedUrl)
        val lock =
            downloadLocks.compute(key) { _, current ->
                (current ?: DownloadLock()).also { it.users.incrementAndGet() }
            } ?: error("download lock unavailable")
        return try {
            lock.mutex.withLock { block() }
        } finally {
            downloadLocks.computeIfPresent(key) { _, current ->
                when {
                    current !== lock -> current
                    current.users.decrementAndGet() <= 0 -> null
                    else -> current
                }
            }
        }
    }

    private fun lockKey(
        sourceUrl: String,
        normalizedUrl: String,
    ): String = "${baGuideTempMediaSessionId(sourceUrl)}|${baGuideTempMediaSha1(normalizedUrl)}"

    private data class DownloadLock(
        val mutex: Mutex = Mutex(),
        val users: AtomicInteger = AtomicInteger(0),
    )
}
