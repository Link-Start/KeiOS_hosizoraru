package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import android.graphics.Bitmap
import os.kei.ui.page.main.student.GameKeeMediaImageLoader
import os.kei.ui.page.main.student.catalog.BaGuideCatalogIconCache

internal data class BaGuideCatalogImageLoadResult(
    val bitmaps: Map<String, Bitmap>,
    val missingUrls: Set<String>,
)

internal class BaGuideCatalogImageRepository {
    fun cachedBitmap(imageUrl: String): Bitmap? = BaGuideCatalogIconCache.get(imageUrl)

    suspend fun loadImages(
        context: Context,
        imageUrls: List<String>,
    ): BaGuideCatalogImageLoadResult {
        val appContext = context.applicationContext
        val bitmaps = linkedMapOf<String, Bitmap>()
        val missingUrls = linkedSetOf<String>()
        imageUrls
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { imageUrl ->
                BaGuideCatalogIconCache.get(imageUrl)?.let { bitmap ->
                    bitmaps[imageUrl] = bitmap
                    return@forEach
                }
                val bitmap =
                    GameKeeMediaImageLoader.loadCatalogIcon(
                        context = appContext,
                        imageUrl = imageUrl,
                    )
                if (bitmap == null) {
                    missingUrls.add(imageUrl)
                } else {
                    bitmaps[imageUrl] = bitmap
                }
            }
        return BaGuideCatalogImageLoadResult(
            bitmaps = bitmaps,
            missingUrls = missingUrls,
        )
    }
}
