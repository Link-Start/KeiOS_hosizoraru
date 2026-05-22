package os.kei.ui.page.main.student.page.support

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.hasRenderableGalleryMedia
import os.kei.ui.page.main.student.isMemoryHallFileGalleryItem
import os.kei.ui.page.main.student.isRenderableGalleryStaticImageUrl
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration

internal fun normalizeGuidePlaybackSource(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    val scheme = runCatching { value.toUri().scheme.orEmpty() }.getOrDefault("")
    return if (scheme.equals("file", ignoreCase = true)) {
        value
    } else {
        normalizeGuideUrl(value)
    }
}

internal fun isGuideAudioPlaybackUrl(raw: String): Boolean {
    val normalized = normalizeGuidePlaybackSource(raw)
    if (normalized.isBlank()) return false
    val scheme = runCatching { normalized.toUri().scheme.orEmpty() }.getOrDefault("")
    return scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true) ||
        scheme.equals("file", ignoreCase = true)
}

internal fun collectGuideStaticImagePrefetchUrls(
    info: BaStudentGuideInfo,
    maxCount: Int = Int.MAX_VALUE,
): List<String> {
    if (maxCount <= 0) return emptyList()
    val orderedUrls = LinkedHashSet<String>()

    fun addPrefetchUrl(raw: String): Boolean {
        if (orderedUrls.size >= maxCount) return true
        val normalized = normalizeGuideUrl(raw)
        if (isRenderableGalleryStaticImageUrl(normalized)) {
            orderedUrls += normalized
        }
        return orderedUrls.size >= maxCount
    }

    if (addPrefetchUrl(info.imageUrl)) return orderedUrls.toList()
    if (info.galleryItems.isEmpty()) {
        addPrefetchUrl(info.imageUrl)
        return orderedUrls.toList()
    }

    val seenGalleryKeys = HashSet<String>()
    for (item in info.galleryItems) {
        if (!hasRenderableGalleryMedia(item) || isMemoryHallFileGalleryItem(item)) continue
        val itemKey = "${item.mediaType}|${item.mediaUrl.ifBlank { item.imageUrl }}"
        if (!seenGalleryKeys.add(itemKey)) continue
        if (addPrefetchUrl(item.imageUrl)) break
        if (addPrefetchUrl(item.mediaUrl)) break
    }
    return orderedUrls.toList()
}

@Composable
internal fun rememberGuideSyncProgress(
    loading: Boolean,
    animationsEnabled: Boolean,
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(loading, animationsEnabled) {
        if (!animationsEnabled) {
            progress.snapTo(if (loading) 0.9f else 1f)
            return@LaunchedEffect
        }
        if (loading) {
            progress.snapTo(0.12f)
            progress.animateTo(
                targetValue = 0.68f,
                animationSpec =
                    tween(
                        durationMillis = resolvedMotionDuration(520, animationsEnabled),
                        easing = FastOutSlowInEasing,
                    ),
            )
            progress.animateTo(
                targetValue = 0.90f,
                animationSpec =
                    tween(
                        durationMillis = resolvedMotionDuration(1800, animationsEnabled),
                        easing = LinearEasing,
                    ),
            )
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = resolvedMotionDuration(260, animationsEnabled),
                        easing = FastOutSlowInEasing,
                    ),
            )
        }
    }
    return progress.value
}
