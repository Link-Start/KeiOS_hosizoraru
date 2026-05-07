package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogIconCache
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
        bitmapLoader: (Context, String) -> Bitmap? = { ctx, url ->
            BaGuideCatalogIconCache.getOrLoad(ctx, url)
        }
    ): ByteArray? {
        return resolveUrl(
            context = context,
            artworkUrl = artworkUrlResolver(favorite),
            bitmapLoader = bitmapLoader
        )
    }

    suspend fun resolveUrl(
        context: Context,
        artworkUrl: String,
        bitmapLoader: (Context, String) -> Bitmap?
    ): ByteArray? {
        val key = artworkUrl.trim()
        if (key.isBlank()) return null
        getCached(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            bitmapLoader(context.applicationContext, key)
        } ?: return null
        val payload = withContext(Dispatchers.Default) {
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
