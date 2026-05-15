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
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.component.clearFavoriteBgmCache
import os.kei.ui.page.main.student.catalog.component.favoriteBgmCachedBytes
import os.kei.ui.page.main.student.catalog.component.favoriteCacheScope
import os.kei.ui.page.main.student.catalog.component.isFavoriteBgmCached

internal data class BaGuideFavoriteBgmCacheSnapshot(
    val cachedAudioUrls: Set<String> = emptySet(),
    val bytes: Long = 0L
)

internal data class BaGuideFavoriteBgmOfflineCacheState(
    val offlineAudioUrls: Set<String> = emptySet(),
    val onToggleFavoriteCache: (GuideBgmFavoriteItem) -> Unit = {}
)

private const val FAVORITE_BGM_CACHE_PARALLELISM = 3
private const val FAVORITE_BGM_CACHE_BATCH_SIZE = FAVORITE_BGM_CACHE_PARALLELISM * 2
private const val FAVORITE_BGM_CLEAN_YIELD_EVERY = 32

@Composable
internal fun rememberBaGuideFavoriteBgmOfflineCacheState(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    isPageActive: Boolean
): BaGuideFavoriteBgmOfflineCacheState {
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var cachingAudioUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var offlineAudioUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    val cacheSuccessText = stringResource(R.string.ba_catalog_bgm_cache_success)
    val cacheFailedText = stringResource(R.string.ba_catalog_bgm_cache_failed)
    val cacheRemovedText = stringResource(R.string.ba_catalog_bgm_cache_removed)

    LaunchedEffect(favorites, revision, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        offlineAudioUrls = loadFavoriteBgmCachedAudioUrlsAsync(
            context = appContext,
            favorites = favorites
        )
    }

    val onToggleFavoriteCache: (GuideBgmFavoriteItem) -> Unit = { favorite ->
        if (favorite.audioUrl in offlineAudioUrls) {
            scope.launch {
                clearFavoriteBgmCacheAsync(appContext, favorite)
                offlineAudioUrls = offlineAudioUrls - favorite.audioUrl
                revision += 1
                Toast.makeText(context, cacheRemovedText, Toast.LENGTH_SHORT).show()
            }
        } else if (favorite.audioUrl.isNotBlank() && favorite.audioUrl !in cachingAudioUrls) {
            cachingAudioUrls = cachingAudioUrls + favorite.audioUrl
            scope.launch {
                val success = try {
                    cacheFavoriteBgmAsync(appContext, favorite)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                } finally {
                    cachingAudioUrls = cachingAudioUrls - favorite.audioUrl
                }
                if (success) {
                    offlineAudioUrls = offlineAudioUrls + favorite.audioUrl
                }
                revision += 1
                Toast.makeText(
                    context,
                    if (success) cacheSuccessText else cacheFailedText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    return BaGuideFavoriteBgmOfflineCacheState(
        offlineAudioUrls = offlineAudioUrls,
        onToggleFavoriteCache = onToggleFavoriteCache
    )
}

internal suspend fun cacheMissingFavoriteBgmsAsync(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Int {
    val appContext = context.applicationContext
    val targets = withContext(ioDispatcher) {
        favorites.filter { favorite ->
            currentCoroutineContext().ensureActive()
            favorite.audioUrl.isNotBlank() && !isFavoriteBgmCached(appContext, favorite)
        }
    }
    if (targets.isEmpty()) return 0
    coroutineScope {
        val semaphore = Semaphore(FAVORITE_BGM_CACHE_PARALLELISM)
        targets.chunked(FAVORITE_BGM_CACHE_BATCH_SIZE).forEach { batch ->
            currentCoroutineContext().ensureActive()
            batch.map { favorite ->
                async {
                    semaphore.withPermit {
                        try {
                            BaGuideTempMediaCache.prefetchForGuide(
                                context = appContext,
                                sourceUrl = favoriteCacheScope(favorite),
                                rawUrls = listOf(favorite.audioUrl),
                                ioDispatcher = ioDispatcher
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            Unit
                        }
                    }
                }
            }.awaitAll()
            yield()
        }
    }
    return targets.size
}

internal suspend fun cleanInvalidFavoriteBgmCacheAsync(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Int = withContext(ioDispatcher) {
    val appContext = context.applicationContext
    var cleaned = 0
    favorites.forEachIndexed { index, favorite ->
        currentCoroutineContext().ensureActive()
        val before = favoriteBgmCachedBytes(appContext, favorite)
        isFavoriteBgmCached(appContext, favorite)
        val after = favoriteBgmCachedBytes(appContext, favorite)
        if (before > 0L && after <= 0L) {
            cleaned += 1
        }
        if ((index + 1) % FAVORITE_BGM_CLEAN_YIELD_EVERY == 0) {
            yield()
        }
    }
    cleaned
}

internal suspend fun loadFavoriteBgmCachedAudioUrlsAsync(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Set<String> = loadFavoriteBgmCacheSnapshotAsync(
    context = context,
    favorites = favorites,
    ioDispatcher = ioDispatcher
).cachedAudioUrls

internal suspend fun loadFavoriteBgmCacheSnapshotAsync(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): BaGuideFavoriteBgmCacheSnapshot = withContext(ioDispatcher) {
    val appContext = context.applicationContext
    val cachedAudioUrls = mutableSetOf<String>()
    var bytes = 0L
    favorites.forEachIndexed { index, favorite ->
        currentCoroutineContext().ensureActive()
        if (favorite.audioUrl.isNotBlank()) {
            val cachedBytes = favoriteBgmCachedBytes(appContext, favorite)
            if (cachedBytes > 0L) {
                cachedAudioUrls += favorite.audioUrl
                bytes += cachedBytes
            }
        }
        if ((index + 1) % FAVORITE_BGM_CLEAN_YIELD_EVERY == 0) {
            yield()
        }
    }
    BaGuideFavoriteBgmCacheSnapshot(
        cachedAudioUrls = cachedAudioUrls,
        bytes = bytes.coerceAtLeast(0L)
    )
}

internal suspend fun cacheFavoriteBgmAsync(
    context: Context,
    favorite: GuideBgmFavoriteItem,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Boolean {
    if (favorite.audioUrl.isBlank()) return false
    val appContext = context.applicationContext
    BaGuideTempMediaCache.prefetchForGuide(
        context = appContext,
        sourceUrl = favoriteCacheScope(favorite),
        rawUrls = listOf(favorite.audioUrl),
        ioDispatcher = ioDispatcher
    )
    return withContext(ioDispatcher) {
        isFavoriteBgmCached(appContext, favorite)
    }
}

internal suspend fun clearFavoriteBgmCacheAsync(
    context: Context,
    favorite: GuideBgmFavoriteItem,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) = withContext(ioDispatcher) {
    clearFavoriteBgmCache(context.applicationContext, favorite)
}
