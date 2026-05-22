package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmCacheSnapshot
import java.util.Locale

internal data class BaGuideCatalogBgmCacheState(
    val summary: String,
    val onCacheAllBgm: () -> Unit,
    val onCleanInvalidBgmCache: () -> Unit,
)

@Composable
internal fun rememberBaGuideCatalogBgmCacheState(
    snapshot: BaGuideFavoriteBgmCacheSnapshot,
    favoriteCount: Int,
    onCacheAllBgm: () -> Unit,
    onCleanInvalidBgmCache: () -> Unit,
): BaGuideCatalogBgmCacheState {
    val summary =
        stringResource(
            R.string.ba_catalog_bgm_cache_summary,
            snapshot.cachedAudioUrls.size,
            favoriteCount,
            formatBgmCacheBytes(snapshot.bytes),
        )
    return remember(summary, onCacheAllBgm, onCleanInvalidBgmCache) {
        BaGuideCatalogBgmCacheState(
            summary = summary,
            onCacheAllBgm = onCacheAllBgm,
            onCleanInvalidBgmCache = onCleanInvalidBgmCache,
        )
    }
}

private fun formatBgmCacheBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"
    val units = listOf("KB", "MB", "GB")
    var value = safeBytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
