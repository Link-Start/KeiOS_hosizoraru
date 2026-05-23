package os.kei.ui.page.main.student

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf

internal data class GuideMediaImageRequest(
    val source: String,
    val maxDecodeDimension: Int,
    val forceReload: Boolean = false,
)

internal data class GuideMediaImageUiState(
    val bitmaps: Map<String, Bitmap> = emptyMap(),
    val missingKeys: Set<String> = emptySet(),
    val resolvedGifTargets: Map<String, String> = emptyMap(),
    val missingGifTargets: Set<String> = emptySet(),
)

internal val LocalGuideMediaImageBitmaps =
    compositionLocalOf<Map<String, Bitmap>> { emptyMap() }

internal val LocalGuideMediaImageMissingKeys =
    compositionLocalOf<Set<String>> { emptySet() }

internal val LocalGuideMediaGifTargets =
    compositionLocalOf<Map<String, String>> { emptyMap() }

internal val LocalGuideMediaImageRequester =
    compositionLocalOf<(List<GuideMediaImageRequest>) -> Unit> { {} }

internal val LocalGuideMediaGifTargetRequester =
    compositionLocalOf<(List<String>) -> Unit> { {} }

internal fun guideMediaImageKey(
    source: String,
    maxDecodeDimension: Int,
): String =
    "${normalizeGuideMediaSource(source)}|${maxDecodeDimension.coerceIn(128, 4096)}"
