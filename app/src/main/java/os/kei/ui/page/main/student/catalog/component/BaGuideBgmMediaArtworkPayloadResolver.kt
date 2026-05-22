package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GameKeeMediaImageLoader
import java.io.ByteArrayOutputStream

internal object BaGuideBgmMediaArtworkPayloadResolver {
    private const val MAX_PAYLOAD_CACHE_COUNT = 32

    private val payloadCache = object : LinkedHashMap<String, ByteArray>(
        MAX_PAYLOAD_CACHE_COUNT,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            return size > MAX_PAYLOAD_CACHE_COUNT
        }
    }

    suspend fun resolve(
        context: Context,
        favorite: GuideBgmFavoriteItem,
        artworkUrlResolver: (GuideBgmFavoriteItem) -> String = { item ->
            item.resolvePlaybackArtworkImageUrl()
        },
        bitmapLoader: (suspend (Context, String) -> Bitmap?) = { ctx, url ->
            GameKeeMediaImageLoader.loadCatalogIcon(ctx, url)
        },
        defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
    ): ByteArray? {
        return resolveUrl(
            context = context,
            artworkUrl = artworkUrlResolver(favorite),
            bitmapLoader = bitmapLoader,
            defaultDispatcher = defaultDispatcher
        )
    }

    suspend fun resolveUrl(
        context: Context,
        artworkUrl: String,
        bitmapLoader: suspend (Context, String) -> Bitmap? = { ctx, url ->
            GameKeeMediaImageLoader.loadCatalogIcon(ctx, url)
        },
        defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
    ): ByteArray? {
        val key = artworkUrl.trim()
        if (key.isBlank()) return null
        getCached(key)?.let { return it }
        val bitmap = bitmapLoader(context.applicationContext, key) ?: return null
        val payload = withContext(defaultDispatcher) {
            bitmap.toPngByteArray()
        } ?: return null
        synchronized(payloadCache) {
            payloadCache[key] = payload
        }
        return payload
    }

    fun clearMemoryCache() {
        synchronized(payloadCache) {
            payloadCache.clear()
        }
    }

    private fun getCached(key: String): ByteArray? {
        return synchronized(payloadCache) {
            payloadCache[key]
        }
    }

    private fun Bitmap.toPngByteArray(): ByteArray? {
        return ByteArrayOutputStream().use { output ->
            val compressed = compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray().takeIf { compressed && it.isNotEmpty() }
        }
    }
}
