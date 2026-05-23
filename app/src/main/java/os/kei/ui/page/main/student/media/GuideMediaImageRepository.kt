package os.kei.ui.page.main.student

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers

internal data class GuideMediaImageLoadResult(
    val bitmaps: Map<String, Bitmap>,
    val missingKeys: Set<String>,
)

internal data class GuideMediaGifTargetLoadResult(
    val resolvedTargets: Map<String, String>,
    val missingTargets: Set<String>,
)

internal class GuideMediaImageRepository {
    fun cachedBitmap(request: GuideMediaImageRequest): Bitmap? =
        BaGuideImageCache.peekBitmap(
            source = request.source,
            maxDecodeDimension = request.maxDecodeDimension,
        )

    suspend fun loadImages(
        context: Context,
        requests: List<GuideMediaImageRequest>,
    ): GuideMediaImageLoadResult =
        withContext(AppDispatchers.media) {
            val appContext = context.applicationContext
            val bitmaps = linkedMapOf<String, Bitmap>()
            val missingKeys = linkedSetOf<String>()
            requests
                .mapNotNull { request ->
                    val source = normalizeGuideMediaSource(request.source)
                    if (source.isBlank()) {
                        null
                    } else {
                        GuideMediaImageRequest(
                            source = source,
                            maxDecodeDimension = request.maxDecodeDimension,
                        )
                    }
                }.distinctBy { request ->
                    guideMediaImageKey(request.source, request.maxDecodeDimension)
                }.forEach { request ->
                    val key = guideMediaImageKey(request.source, request.maxDecodeDimension)
                    cachedBitmap(request)?.let { bitmap ->
                        bitmaps[key] = bitmap
                        return@forEach
                    }
                    val bitmap =
                        GameKeeMediaImageLoader.loadGuideBitmap(
                            context = appContext,
                            source = request.source,
                            maxDecodeDimension = request.maxDecodeDimension,
                        )
                    if (bitmap == null) {
                        missingKeys.add(key)
                    } else {
                        bitmaps[key] = bitmap
                    }
                }
            GuideMediaImageLoadResult(
                bitmaps = bitmaps,
                missingKeys = missingKeys,
            )
        }

    suspend fun resolveGifTargets(
        context: Context,
        rawTargets: List<String>,
    ): GuideMediaGifTargetLoadResult =
        withContext(AppDispatchers.media) {
            val appContext = context.applicationContext
            val resolvedTargets = linkedMapOf<String, String>()
            val missingTargets = linkedSetOf<String>()
            rawTargets
                .map { raw -> normalizeGuideMediaSource(raw) }
                .filter { target -> target.isNotBlank() }
                .distinct()
                .forEach { target ->
                    val resolved =
                        runCatching {
                            if (isHttpMediaSource(target)) {
                                GameKeeMediaImageLoader
                                    .resolveInlineGifTarget(appContext, target)
                                    .ifBlank { target }
                            } else {
                                target
                            }
                        }.getOrDefault(target)
                    if (resolved.isBlank()) {
                        missingTargets.add(target)
                    } else {
                        resolvedTargets[target] = resolved
                    }
                }
            GuideMediaGifTargetLoadResult(
                resolvedTargets = resolvedTargets,
                missingTargets = missingTargets,
            )
        }
}
