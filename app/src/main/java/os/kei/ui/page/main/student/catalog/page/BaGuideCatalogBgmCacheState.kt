package os.kei.ui.page.main.student.catalog.page

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import java.util.Locale

internal data class BaGuideCatalogBgmCacheState(
    val summary: String,
    val onCacheAllBgm: () -> Unit,
    val onCleanInvalidBgmCache: () -> Unit
)

@Composable
internal fun rememberBaGuideCatalogBgmCacheState(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    refreshWhenVisible: Boolean
): BaGuideCatalogBgmCacheState {
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val favoritesState = rememberUpdatedState(favorites)
    var revision by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf(BaGuideFavoriteBgmCacheSnapshot()) }

    LaunchedEffect(favorites, revision, refreshWhenVisible) {
        snapshot = loadFavoriteBgmCacheSnapshotAsync(
            context = appContext,
            favorites = favorites
        )
    }

    val summary = stringResource(
        R.string.ba_catalog_bgm_cache_summary,
        snapshot.cachedAudioUrls.size,
        favorites.size,
        formatBgmCacheBytes(snapshot.bytes)
    )
    val cacheAll: () -> Unit = remember(context, appContext, scope) {
        {
            scope.launch {
                val currentFavorites = favoritesState.value
                val targetCount = cacheMissingFavoriteBgmsAsync(
                    context = appContext,
                    favorites = currentFavorites
                )
                revision += 1
                Toast.makeText(
                    context,
                    context.resolveString(R.string.ba_catalog_bgm_cache_batch_done, targetCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val cleanInvalid: () -> Unit = remember(context, appContext, scope) {
        {
            scope.launch {
                val cleaned = cleanInvalidFavoriteBgmCacheAsync(
                    context = appContext,
                    favorites = favoritesState.value
                )
                revision += 1
                Toast.makeText(
                    context,
                    context.resolveString(R.string.ba_catalog_bgm_cache_cleaned, cleaned),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    return remember(summary, cacheAll, cleanInvalid) {
        BaGuideCatalogBgmCacheState(
            summary = summary,
            onCacheAllBgm = cacheAll,
            onCleanInvalidBgmCache = cleanInvalid
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
