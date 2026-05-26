package os.kei.ui.page.main.student

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.widget.shape.appSquircleClip

@Stable
internal class GuideMediaProgressState(initialProgress: Float) {
    private val mutableProgress = MutableStateFlow(initialProgress.coerceIn(0f, 1f))
    val progress: StateFlow<Float> = mutableProgress

    fun set(value: Float) {
        mutableProgress.value = value.coerceIn(0f, 1f)
    }
}

internal fun normalizeGuideMediaSource(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    val scheme = runCatching { value.toUri().scheme.orEmpty() }.getOrDefault("")
    return if (scheme.equals("file", ignoreCase = true)) {
        value
    } else {
        normalizeGuideUrl(value)
    }
}


internal fun loadGuideBitmapSource(
    context: Context,
    source: String,
    maxDecodeDimension: Int = 2048,
    onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
): Bitmap? {
    if (source.isBlank()) return null
    return BaGuideImageCache.loadBitmap(
        context = context,
        source = source,
        maxDecodeDimension = maxDecodeDimension,
        onProgress = onProgress
    )
}

internal fun normalizeGalleryDisplayTitle(
    title: String,
    mediaType: String,
    fallbackTitle: String,
    audioBgmPrefix: String
): String {
    val raw = title.trim().ifBlank { fallbackTitle }
    if (mediaType.lowercase() != "audio") return raw
    return if (raw.startsWith("BGM", ignoreCase = true)) {
        raw.replaceFirst(Regex("^BGM", RegexOption.IGNORE_CASE), audioBgmPrefix)
    } else {
        raw
    }
}

@Composable
fun GuideRemoteImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    imageHeight: androidx.compose.ui.unit.Dp = 220.dp,
    maxDecodeDimension: Int = 1920,
    cropAlignment: Alignment = Alignment.Center
) {
    val target = remember(imageUrl) { normalizeGuideMediaSource(imageUrl) }
    if (target.isBlank()) return
    val mediaBitmaps = LocalGuideMediaImageBitmaps.current
    val missingKeys = LocalGuideMediaImageMissingKeys.current
    val requestImages = LocalGuideMediaImageRequester.current
    val key = remember(target, maxDecodeDimension) {
        guideMediaImageKey(target, maxDecodeDimension)
    }
    LaunchedEffect(key, target, maxDecodeDimension, requestImages) {
        requestImages(
            listOf(
                GuideMediaImageRequest(
                    source = target,
                    maxDecodeDimension = maxDecodeDimension,
                ),
            ),
        )
    }
    val bitmap = mediaBitmaps[key]
    if (missingKeys.contains(key)) return
    val rendered = bitmap ?: return
    val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = cropAlignment,
        modifier = modifier
            .fillMaxWidth()
            .height(imageHeight)
            .appSquircleClip(14.dp)
    )
}

@Composable
internal fun GuideRemoteImageAdaptive(
    imageUrl: String,
    modifier: Modifier = Modifier,
    maxDecodeDimension: Int = 2048,
    progressState: GuideMediaProgressState? = null,
    onLoadingChanged: ((Boolean) -> Unit)? = null
) {
    val target = remember(imageUrl) { normalizeGuideMediaSource(imageUrl) }
    if (target.isBlank()) {
        LaunchedEffect(progressState, onLoadingChanged) {
            progressState?.set(1f)
            onLoadingChanged?.invoke(false)
        }
        return
    }
    val fallbackRatio = remember(target) { detectMediaRatioFromUrl(target) ?: (16f / 9f) }
    var stableRatio by remember(target) { mutableStateOf(fallbackRatio.coerceIn(0.4f, 4f)) }
    val isGifSource = remember(target) { isGifMediaSource(target) }
    val mediaBitmaps = LocalGuideMediaImageBitmaps.current
    val missingKeys = LocalGuideMediaImageMissingKeys.current
    val requestImages = LocalGuideMediaImageRequester.current
    val gifTargets = LocalGuideMediaGifTargets.current
    val requestGifTargets = LocalGuideMediaGifTargetRequester.current
    if (isGifSource) {
        LaunchedEffect(target, requestGifTargets) {
            if (isHttpMediaSource(target)) {
                requestGifTargets(listOf(target))
            }
            progressState?.set(0f)
            onLoadingChanged?.invoke(true)
        }
        val resolvedGifTarget = gifTargets[target] ?: target
        val ratio = remember(resolvedGifTarget, target) {
            detectMediaRatioFromUrl(resolvedGifTarget.ifBlank { target }) ?: (16f / 9f)
        }
        AsyncImage(
            model = resolvedGifTarget,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onLoading = {
                progressState?.set(0.24f)
                onLoadingChanged?.invoke(true)
            },
            onSuccess = {
                progressState?.set(1f)
                onLoadingChanged?.invoke(false)
            },
            onError = {
                progressState?.set(1f)
                onLoadingChanged?.invoke(false)
            },
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .appSquircleClip(14.dp)
        )
        return
    }
    val imageKey = remember(target, maxDecodeDimension) {
        guideMediaImageKey(target, maxDecodeDimension)
    }
    val bitmap = mediaBitmaps[imageKey]
    val imageMissing = missingKeys.contains(imageKey)
    LaunchedEffect(imageKey, target, maxDecodeDimension, bitmap, imageMissing, requestImages) {
        if (bitmap == null && !imageMissing) {
            progressState?.set(0f)
            onLoadingChanged?.invoke(true)
            requestImages(
                listOf(
                    GuideMediaImageRequest(
                        source = target,
                        maxDecodeDimension = maxDecodeDimension,
                    ),
                ),
            )
        } else if (bitmap != null || imageMissing) {
            progressState?.set(1f)
            onLoadingChanged?.invoke(false)
        }
    }
    val rendered = bitmap
    if (rendered == null) {
        Spacer(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(stableRatio)
                .appSquircleClip(14.dp)
        )
        return
    }
    val ratio = remember(rendered.width, rendered.height) {
        if (rendered.width > 0 && rendered.height > 0) {
            rendered.width.toFloat() / rendered.height.toFloat()
        } else {
            stableRatio
        }
    }.coerceIn(0.4f, 4f)
    if (kotlin.math.abs(ratio - stableRatio) > 0.001f) {
        stableRatio = ratio
    }
    Image(
        bitmap = remember(rendered) { rendered.asImageBitmap() },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .appSquircleClip(14.dp)
    )
}

internal fun isGifMediaSource(source: String): Boolean {
    val value = source.trim()
    if (value.isBlank()) return false
    if (value.startsWith("data:image/gif", ignoreCase = true)) return true
    if (Regex("""\.gif(\?.*)?(#.*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(value)) return true
    val uri = runCatching { value.toUri() }.getOrNull()
    if (!uri?.scheme.equals("file", ignoreCase = true)) return false
    val path = uri?.path.orEmpty().ifBlank { Uri.decode(uri?.encodedPath.orEmpty()) }
    if (path.isBlank()) return false
    return runCatching {
        java.io.File(path).inputStream().use { input ->
            val header = ByteArray(6)
            if (input.read(header) != 6) return@runCatching false
            val magic = String(header)
            magic == "GIF87a" || magic == "GIF89a"
        }
    }.getOrDefault(false)
}

internal fun isFileMediaSource(source: String): Boolean {
    val value = source.trim()
    if (value.isBlank()) return false
    val uri = runCatching { value.toUri() }.getOrNull() ?: return false
    return uri.scheme.equals("file", ignoreCase = true)
}

internal fun isHttpMediaSource(source: String): Boolean {
    val value = source.trim()
    if (value.isBlank()) return false
    val uri = runCatching { value.toUri() }.getOrNull() ?: return false
    val scheme = uri.scheme.orEmpty()
    return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
}

internal fun detectMediaRatioFromUrl(source: String): Float? {
    val match = Regex("""/w_(\d{1,4})/h_(\d{1,4})/""")
        .find(source)
    val width = match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val height = match?.groupValues?.getOrNull(2)?.toFloatOrNull()
    if (width == null || height == null || width <= 0f || height <= 0f) return null
    val ratio = width / height
    if (ratio.isNaN() || ratio.isInfinite()) return null
    return ratio.coerceIn(0.4f, 4f)
}

@Composable
fun GuideRemoteIcon(
    imageUrl: String,
    modifier: Modifier = Modifier,
    iconWidth: androidx.compose.ui.unit.Dp = 20.dp,
    iconHeight: androidx.compose.ui.unit.Dp = iconWidth
) {
    val density = LocalDensity.current
    val target = remember(imageUrl) { normalizeGuideMediaSource(imageUrl) }
    if (target.isBlank()) return
    val mediaBitmaps = LocalGuideMediaImageBitmaps.current
    val missingKeys = LocalGuideMediaImageMissingKeys.current
    val requestImages = LocalGuideMediaImageRequester.current
    val gifTargets = LocalGuideMediaGifTargets.current
    val requestGifTargets = LocalGuideMediaGifTargetRequester.current
    val isGifSource = remember(target) { isGifMediaSource(target) }
    if (isGifSource) {
        LaunchedEffect(target, requestGifTargets) {
            if (isHttpMediaSource(target)) {
                requestGifTargets(listOf(target))
            }
        }
        val resolvedGifTarget = gifTargets[target] ?: target
        AsyncImage(
            model = resolvedGifTarget,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .width(iconWidth)
                .height(iconHeight)
        )
        return
    }
    val iconDecodeDimension = remember(iconWidth, iconHeight, density) {
        val widthPx = with(density) { iconWidth.roundToPx() }
        val heightPx = with(density) { iconHeight.roundToPx() }
        (maxOf(widthPx, heightPx) * 2).coerceIn(96, 768)
    }
    val imageKey = remember(target, iconDecodeDimension) {
        guideMediaImageKey(target, iconDecodeDimension)
    }
    LaunchedEffect(imageKey, target, iconDecodeDimension, requestImages) {
        requestImages(
            listOf(
                GuideMediaImageRequest(
                    source = target,
                    maxDecodeDimension = iconDecodeDimension,
                ),
            ),
        )
    }
    val bitmap = mediaBitmaps[imageKey]
    if (missingKeys.contains(imageKey)) return
    val rendered = bitmap ?: return
    val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .width(iconWidth)
            .height(iconHeight)
    )
}
