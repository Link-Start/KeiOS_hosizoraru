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
) {
    private val mutableState = MutableStateFlow(BaGuideCatalogImageUiState())
    private var loadingUrls: Set<String> = emptySet()

    // Coalescing buffer: streamed per-image results are batched into a single StateFlow emission
    // every [FLUSH_INTERVAL_MS] so a 72-icon scroll paints progressively without triggering one
    // full-grid recomposition per icon. Guarded by [bufferLock] because results are streamed in
    // from concurrent loader coroutines.
    private val bufferLock = Any()
    private val pendingBitmaps = linkedMapOf<String, Bitmap>()
    private val pendingMissing = linkedSetOf<String>()
    private var flushJob: Job? = null

    val state: StateFlow<BaGuideCatalogImageUiState> = mutableState.asStateFlow()

    fun requestImages(imageUrls: List<String>) {
        val normalizedUrls =
            imageUrls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (normalizedUrls.isEmpty()) return

        val currentState = mutableState.value
        val cachedBitmaps = linkedMapOf<String, Bitmap>()
        val missingUrls = mutableListOf<String>()
        normalizedUrls.forEach { imageUrl ->
            if (currentState.bitmaps.containsKey(imageUrl) ||
                currentState.missingUrls.contains(imageUrl) ||
                loadingUrls.contains(imageUrl)
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
            mutableState.update { state ->
                state.copy(bitmaps = state.bitmaps + cachedBitmaps)
            }
        }
        if (missingUrls.isEmpty()) return

        loadingUrls = loadingUrls + missingUrls
        scope.launch {
            try {
                repository.loadImages(
                    context = appContext,
                    imageUrls = missingUrls,
                    onResult = { imageUrl, bitmap ->
                        enqueueResult(imageUrl, bitmap)
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                missingUrls.forEach { enqueueResult(it, null) }
            } finally {
                loadingUrls = loadingUrls - missingUrls.toSet()
                flushPending()
            }
        }
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
        mutableState.update { state ->
            state.copy(
                bitmaps = if (bitmapsToApply.isEmpty()) state.bitmaps else state.bitmaps + bitmapsToApply,
                missingUrls = if (missingToApply.isEmpty()) state.missingUrls else state.missingUrls + missingToApply,
            )
        }
    }

    fun clearLoadingState() {
        synchronized(bufferLock) {
            flushJob?.cancel()
            flushJob = null
            pendingBitmaps.clear()
            pendingMissing.clear()
        }
        loadingUrls = emptySet()
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 80L
    }
}
