package os.kei.ui.page.main.student.catalog.page

import android.content.Context
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

private const val FAVORITE_BGM_CACHE_PARALLELISM = 3
private const val FAVORITE_BGM_CACHE_BATCH_SIZE = FAVORITE_BGM_CACHE_PARALLELISM * 2
private const val FAVORITE_BGM_CLEAN_YIELD_EVERY = 32

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
