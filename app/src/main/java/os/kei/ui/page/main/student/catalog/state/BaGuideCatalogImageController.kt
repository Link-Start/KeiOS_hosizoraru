package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class BaGuideCatalogImageController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val repository: BaGuideCatalogImageRepository = BaGuideCatalogImageRepository(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(BaGuideCatalogImageUiState())
    private val bufferLock = Any()
    private val pendingBitmaps = linkedMapOf<String, Bitmap>()
    private val pendingMissing = linkedSetOf<String>()
    private val missingUrlRecordedAtMs = linkedMapOf<String, Long>()
    private var flushJob: Job? = null
    private val queueLock = Any()
    private val queuedUrls = ArrayDeque<String>()
    private var loadingUrls: Set<String> = emptySet()
    private var workerJob: Job? = null

    val state: StateFlow<BaGuideCatalogImageUiState> = mutableState.asStateFlow()

    fun requestImages(imageUrls: List<String>) {
        val normalizedUrls =
            imageUrls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (normalizedUrls.isEmpty()) return

        val nowMs = clock()
        val currentState = mutableState.value
        val cachedBitmaps = linkedMapOf<String, Bitmap>()
        val missingUrls = mutableListOf<String>()
        normalizedUrls.forEach { imageUrl ->
            val alreadyLoading =
                synchronized(queueLock) {
                    loadingUrls.contains(imageUrl)
                }
            if (
                currentState.bitmaps.containsKey(imageUrl) ||
                shouldSkipRecentMissingUrl(imageUrl, nowMs) ||
                alreadyLoading
            ) {
                return@forEach
            }
            val cachedBitmap = repository.cachedBitmap(imageUrl)
            if (cachedBitmap == null) {
                missingUrls.add(imageUrl)
            } else {
                cachedBitmaps[imageUrl] = cachedBitmap
            }
        }
        if (cachedBitmaps.isNotEmpty()) {
            synchronized(bufferLock) {
                cachedBitmaps.keys.forEach(missingUrlRecordedAtMs::remove)
            }
            mutableState.update { state ->
                state.copy(
                    bitmaps =
                        mergeLimitedMap(
                            current = state.bitmaps,
                            added = cachedBitmaps,
                            limit = CATALOG_IMAGE_STATE_LIMIT,
                        ),
                    missingUrls = state.missingUrls - cachedBitmaps.keys,
                )
            }
        }
        if (missingUrls.isEmpty()) return

        enqueueMissingUrls(missingUrls)
        ensureWorker()
    }

    private fun enqueueMissingUrls(missingUrls: List<String>) {
        val orderedMissingUrls = missingUrls.distinct()
        synchronized(queueLock) {
            val priorityUrls =
                orderedMissingUrls
                    .filter { imageUrl -> !loadingUrls.contains(imageUrl) }
                    .toSet()
            if (priorityUrls.isEmpty()) return
            val retainedUrls =
                buildList {
                    while (queuedUrls.isNotEmpty()) {
                        val imageUrl = queuedUrls.removeFirst()
                        if (!priorityUrls.contains(imageUrl)) {
                            add(imageUrl)
                        }
                    }
                }
            orderedMissingUrls
                .filter { priorityUrls.contains(it) }
                .forEach(queuedUrls::addLast)
            retainedUrls.forEach(queuedUrls::addLast)
        }
    }

    private fun ensureWorker() {
        synchronized(queueLock) {
            if (workerJob?.isActive == true) return
            workerJob =
                scope.launch {
                    drainQueue()
                }
        }
    }

    private suspend fun drainQueue() {
        var batchIndex = 0
        while (true) {
            val batch = nextBatch(batchIndex)
            if (batch.isEmpty()) break
            try {
                repository.loadImages(
                    context = appContext,
                    imageUrls = batch,
                    onResult = { imageUrl, bitmap ->
                        enqueueResult(imageUrl, bitmap)
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                batch.forEach { enqueueResult(it, null) }
            } finally {
                synchronized(queueLock) {
                    loadingUrls = loadingUrls - batch.toSet()
                }
                flushPending()
            }
            batchIndex++
            if (hasQueuedUrls()) {
                delay(
                    if (batchIndex == 1) {
                        FIRST_BATCH_REST_MS.milliseconds
                    } else {
                        STEADY_BATCH_REST_MS.milliseconds
                    },
                )
            }
        }
        val shouldRestart =
            synchronized(queueLock) {
                workerJob = null
                queuedUrls.isNotEmpty()
            }
        if (shouldRestart) {
            ensureWorker()
        }
    }

    private fun nextBatch(batchIndex: Int): List<String> =
        synchronized(queueLock) {
            val batchSize =
                if (batchIndex == 0) {
                    FIRST_BATCH_SIZE
                } else {
                    STEADY_BATCH_SIZE
                }
            val batch = mutableListOf<String>()
            while (batch.size < batchSize && queuedUrls.isNotEmpty()) {
                val imageUrl = queuedUrls.removeFirst()
                if (!loadingUrls.contains(imageUrl)) {
                    batch += imageUrl
                }
            }
            if (batch.isNotEmpty()) {
                loadingUrls = loadingUrls + batch
            }
            batch
        }

    private fun hasQueuedUrls(): Boolean =
        synchronized(queueLock) {
            queuedUrls.isNotEmpty()
        }

    private fun enqueueResult(
        imageUrl: String,
        bitmap: Bitmap?,
    ) {
        synchronized(bufferLock) {
            if (bitmap == null) {
                pendingMissing.add(imageUrl)
            } else {
                pendingBitmaps[imageUrl] = bitmap
            }
        }
        scheduleFlush()
    }

    private fun shouldSkipRecentMissingUrl(
        imageUrl: String,
        nowMs: Long,
    ): Boolean =
        synchronized(bufferLock) {
            val recordedAtMs = missingUrlRecordedAtMs[imageUrl] ?: return@synchronized false
            (nowMs - recordedAtMs).coerceAtLeast(0L) < MISSING_URL_RETRY_INTERVAL_MS
        }

    private fun scheduleFlush() {
        synchronized(bufferLock) {
            if (flushJob?.isActive == true) return
            flushJob =
                scope.launch {
                    delay(FLUSH_INTERVAL_MS.milliseconds)
                    flushPending()
                }
        }
    }

    private fun flushPending() {
        val bitmapsToApply: Map<String, Bitmap>
        val missingToApply: Set<String>
        synchronized(bufferLock) {
            if (pendingBitmaps.isEmpty() && pendingMissing.isEmpty()) return
            bitmapsToApply = LinkedHashMap(pendingBitmaps)
            missingToApply = LinkedHashSet(pendingMissing)
            pendingBitmaps.clear()
            pendingMissing.clear()
        }
        synchronized(bufferLock) {
            bitmapsToApply.keys.forEach(missingUrlRecordedAtMs::remove)
            if (missingToApply.isNotEmpty()) {
                val nowMs = clock()
                missingToApply.forEach { imageUrl ->
                    missingUrlRecordedAtMs[imageUrl] = nowMs
                }
                while (missingUrlRecordedAtMs.size > CATALOG_MISSING_URL_STATE_LIMIT) {
                    val eldestKey = missingUrlRecordedAtMs.entries.firstOrNull()?.key ?: break
                    missingUrlRecordedAtMs.remove(eldestKey)
                }
            }
        }
        mutableState.update { state ->
            state.copy(
                bitmaps =
                    if (bitmapsToApply.isEmpty()) {
                        state.bitmaps
                    } else {
                        mergeLimitedMap(
                            current = state.bitmaps,
                            added = bitmapsToApply,
                            limit = CATALOG_IMAGE_STATE_LIMIT,
                        )
                    },
                missingUrls =
                    mergeLimitedSet(
                        current = state.missingUrls - bitmapsToApply.keys,
                        added = missingToApply,
                        limit = CATALOG_MISSING_URL_STATE_LIMIT,
                    ),
            )
        }
    }

    fun clearLoadingState() {
        synchronized(bufferLock) {
            flushJob?.cancel()
            flushJob = null
            pendingBitmaps.clear()
            pendingMissing.clear()
            missingUrlRecordedAtMs.clear()
        }
        synchronized(queueLock) {
            workerJob?.cancel()
            workerJob = null
            queuedUrls.clear()
            loadingUrls = emptySet()
        }
    }

    private fun mergeLimitedMap(
        current: Map<String, Bitmap>,
        added: Map<String, Bitmap>,
        limit: Int,
    ): Map<String, Bitmap> {
        if (added.isEmpty()) return current
        val merged = LinkedHashMap<String, Bitmap>(current.size + added.size)
        current.forEach { (key, value) ->
            if (!added.containsKey(key)) {
                merged[key] = value
            }
        }
        added.forEach { (key, value) ->
            merged[key] = value
        }
        while (merged.size > limit) {
            val eldestKey = merged.entries.firstOrNull()?.key ?: break
            merged.remove(eldestKey)
        }
        return merged
    }

    private fun mergeLimitedSet(
        current: Set<String>,
        added: Set<String>,
        limit: Int,
    ): Set<String> {
        if (added.isEmpty()) return current
        val merged = LinkedHashSet<String>(current.size + added.size)
        current.forEach { value ->
            if (!added.contains(value)) {
                merged += value
            }
        }
        merged += added
        while (merged.size > limit) {
            val eldest = merged.firstOrNull() ?: break
            merged.remove(eldest)
        }
        return merged
    }

    private companion object {
        const val FIRST_BATCH_SIZE = 8
        const val STEADY_BATCH_SIZE = 4
        const val FIRST_BATCH_REST_MS = 48L
        const val STEADY_BATCH_REST_MS = 120L
        const val FLUSH_INTERVAL_MS = 96L
        const val MISSING_URL_RETRY_INTERVAL_MS = 30_000L
        const val CATALOG_IMAGE_STATE_LIMIT = 96
        const val CATALOG_MISSING_URL_STATE_LIMIT = 192
    }
}
