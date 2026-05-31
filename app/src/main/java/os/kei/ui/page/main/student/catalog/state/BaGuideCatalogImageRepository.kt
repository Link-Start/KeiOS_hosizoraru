package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.GameKeeMediaImageLoader
import os.kei.ui.page.main.student.catalog.BaGuideCatalogIconCache
import java.util.concurrent.ConcurrentHashMap

internal data class BaGuideCatalogImageLoadResult(
    val bitmaps: Map<String, Bitmap>,
    val missingUrls: Set<String>,
)

internal class BaGuideCatalogImageRepository {
    fun cachedBitmap(imageUrl: String): Bitmap? = BaGuideCatalogIconCache.get(imageUrl)

    /**
     * Loads every requested icon concurrently instead of one-by-one. Actual IO parallelism is
     * bounded by [AppDispatchers.catalogThumbnails] (6 threads), and per-URL dedup is handled
     * inside [GameKeeMediaImageLoader]. Each completion is streamed back via [onResult] so the
     * caller can paint cards progressively rather than waiting for the whole batch.
     */
    suspend fun loadImages(
        context: Context,
        imageUrls: List<String>,
        onResult: (imageUrl: String, bitmap: Bitmap?) -> Unit = { _, _ -> },
    ): BaGuideCatalogImageLoadResult {
        val appContext = context.applicationContext
        val targets =
            imageUrls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (targets.isEmpty()) {
            return BaGuideCatalogImageLoadResult(
                bitmaps = emptyMap(),
                missingUrls = emptySet(),
            )
        }

        val resolvedBitmaps = ConcurrentHashMap<String, Bitmap>()
        val missingUrls = ConcurrentHashMap.newKeySet<String>()

        coroutineScope {
            targets
                .map { imageUrl ->
                    async {
                        BaGuideCatalogIconCache.get(imageUrl)?.let { cached ->
                            resolvedBitmaps[imageUrl] = cached
                            onResult(imageUrl, cached)
                            return@async
                        }
                        val bitmap =
                            GameKeeMediaImageLoader.loadCatalogIcon(
                                context = appContext,
                                imageUrl = imageUrl,
                                ioDispatcher = AppDispatchers.catalogThumbnails,
                            )
                        if (bitmap == null) {
                            missingUrls.add(imageUrl)
                        } else {
                            resolvedBitmaps[imageUrl] = bitmap
                        }
                        onResult(imageUrl, bitmap)
                    }
                }.awaitAll()
        }

        // Preserve request order in the aggregate result for deterministic callers/tests.
        val orderedBitmaps = linkedMapOf<String, Bitmap>()
        val orderedMissing = linkedSetOf<String>()
        targets.forEach { imageUrl ->
            val bitmap = resolvedBitmaps[imageUrl]
            if (bitmap != null) {
                orderedBitmaps[imageUrl] = bitmap
            } else if (missingUrls.contains(imageUrl)) {
                orderedMissing.add(imageUrl)
            }
        }
        return BaGuideCatalogImageLoadResult(
            bitmaps = orderedBitmaps,
            missingUrls = orderedMissing,
        )
    }
}
