package os.kei.ui.page.main.student

import android.content.Context
import android.net.Uri
import java.io.File

internal class BaGuideTempMediaCacheResolver(
    private val clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
) {
    fun resolveCachedUrl(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): String {
        val normalized = baGuideNormalizeMediaTarget(rawUrl)
        if (normalized.isBlank()) return normalized
        val file = baGuideTempMediaTargetFile(context, sourceUrl, normalized)
        if (BaGuideTempMediaValidation.isUsableCachedMedia(normalized, file)) {
            touchCachedFile(file)
            return Uri.fromFile(file).toString()
        }
        if (file.exists()) {
            runCatching { file.delete() }
        }
        return normalized
    }

    fun cachedMediaMetadata(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): BaGuideMediaCacheMetadata {
        val normalized = baGuideNormalizeMediaTarget(rawUrl)
        if (normalized.isBlank()) return emptyMetadata(sourceUrl)
        val file = baGuideTempMediaTargetFile(context, sourceUrl, normalized)
        val valid = BaGuideTempMediaValidation.isUsableCachedMedia(normalized, file)
        val extension = file.extension.lowercase()
        return BaGuideMediaCacheMetadata(
            sourceUrl = sourceUrl,
            normalizedUrl = normalized,
            fileName = file.name,
            extension = extension,
            mimeType = baGuideTempMediaMimeTypeForExtension(extension),
            bytes = if (valid) file.length().coerceAtLeast(0L) else 0L,
            lastAccessMs = file.lastModified().takeIf { file.exists() } ?: 0L,
            valid = valid,
        )
    }

    private fun emptyMetadata(sourceUrl: String): BaGuideMediaCacheMetadata =
        BaGuideMediaCacheMetadata(
            sourceUrl = sourceUrl,
            normalizedUrl = "",
            fileName = "",
            extension = "",
            mimeType = "",
            bytes = 0L,
            lastAccessMs = 0L,
            valid = false,
        )

    private fun touchCachedFile(file: File) {
        runCatching {
            if (file.exists()) file.setLastModified(clock.nowMs())
        }
    }
}
