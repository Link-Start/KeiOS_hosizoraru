package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.component.clearFavoriteBgmCache
import os.kei.ui.page.main.student.catalog.component.favoriteBgmCachedBytes
import os.kei.ui.page.main.student.catalog.component.favoriteCacheScope
import os.kei.ui.page.main.student.catalog.component.isFavoriteBgmCached

internal object BaGuideFavoriteBgmCacheRepository {
    private const val CACHE_PARALLELISM = 3
    private const val CACHE_BATCH_SIZE = CACHE_PARALLELISM * 2
    private const val CLEAN_YIELD_EVERY = 32

    suspend fun cacheMissingFavorites(
        context: Context,
        favorites: List<GuideBgmFavoriteItem>,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ): Int {
        val appContext = context.applicationContext
        val targets =
            withContext(ioDispatcher) {
                favorites.filter { favorite ->
                    currentCoroutineContext().ensureActive()
                    favorite.audioUrl.isNotBlank() && !isFavoriteBgmCached(appContext, favorite)
                }
            }
        if (targets.isEmpty()) return 0
        coroutineScope {
            val semaphore = Semaphore(CACHE_PARALLELISM)
            targets.chunked(CACHE_BATCH_SIZE).forEach { batch ->
                currentCoroutineContext().ensureActive()
                batch
                    .map { favorite ->
                        async {
                            semaphore.withPermit {
                                try {
                                    BaGuideTempMediaCache.prefetchForGuide(
                                        context = appContext,
                                        sourceUrl = favoriteCacheScope(favorite),
                                        rawUrls = listOf(favorite.audioUrl),
                                        ioDispatcher = ioDispatcher,
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

    suspend fun cleanInvalidFavorites(
        context: Context,
        favorites: List<GuideBgmFavoriteItem>,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ): Int =
        withContext(ioDispatcher) {
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
                if ((index + 1) % CLEAN_YIELD_EVERY == 0) {
                    yield()
                }
            }
            cleaned
        }

    suspend fun loadCachedAudioUrls(
        context: Context,
        favorites: List<GuideBgmFavoriteItem>,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ): Set<String> =
        loadCacheSnapshot(
            context = context,
            favorites = favorites,
            ioDispatcher = ioDispatcher,
        ).cachedAudioUrls

    suspend fun loadCacheSnapshot(
        context: Context,
        favorites: List<GuideBgmFavoriteItem>,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ): BaGuideFavoriteBgmCacheSnapshot =
        withContext(ioDispatcher) {
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
                if ((index + 1) % CLEAN_YIELD_EVERY == 0) {
                    yield()
                }
            }
            BaGuideFavoriteBgmCacheSnapshot(
                cachedAudioUrls = cachedAudioUrls,
                bytes = bytes.coerceAtLeast(0L),
            )
        }

    suspend fun cacheFavorite(
        context: Context,
        favorite: GuideBgmFavoriteItem,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ): Boolean {
        if (favorite.audioUrl.isBlank()) return false
        val appContext = context.applicationContext
        BaGuideTempMediaCache.prefetchForGuide(
            context = appContext,
            sourceUrl = favoriteCacheScope(favorite),
            rawUrls = listOf(favorite.audioUrl),
            ioDispatcher = ioDispatcher,
        )
        return withContext(ioDispatcher) {
            isFavoriteBgmCached(appContext, favorite)
        }
    }

    suspend fun clearFavorite(
        context: Context,
        favorite: GuideBgmFavoriteItem,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ) = withContext(ioDispatcher) {
        clearFavoriteBgmCache(context.applicationContext, favorite)
    }
}
