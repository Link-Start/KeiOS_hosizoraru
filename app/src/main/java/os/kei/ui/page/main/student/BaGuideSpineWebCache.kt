package os.kei.ui.page.main.student

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object BaGuideSpineWebCache {
    private var instance: BaGuideSpineHttpCache? = null

    @Synchronized
    fun get(context: Context): BaGuideSpineHttpCache = instance ?: BaGuideSpineHttpCache(
        File(context.applicationContext.cacheDir, "ba_spine_web_cache"),
    ).also { instance = it }
}

/** Exact resource URLs retain query/version keys. Wiki HTML, API responses and other media bypass. */
internal class BaGuideSpineResourcePolicy(resource: BaGuideWebMemoryLobby) {
    private val assets = (listOf(resource.atlasUrl, resource.skeletonUrl, resource.jsonUrl) + resource.textureUrls)
        .mapNotNull { gameKeeSpineAssetUrl(it).toHttpUrlOrNull()?.toString() }.toSet()

    fun reuseMs(rawUrl: String): Long? {
        val url = rawUrl.toHttpUrlOrNull() ?: return null
        if (url.toString() in assets) return BA_GUIDE_SPINE_ASSET_REUSE_MS
        if (url.scheme == "https" && url.host == "cdnstatic.gamekee.com" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() && url.encodedPath in SCRIPT_PATHS
        ) return BA_GUIDE_SPINE_SCRIPT_REUSE_MS
        return null
    }

    companion object {
        private val SCRIPT_PATHS = setOf(
            "/wiki/spa/apps/web/client/dist/static/pixi.js",
            "/wiki/spa/apps/web/client/dist/static/spine-pixi-v8.js",
        )
    }
}

/** One WebView owns its in-flight calls and streams; closing it aborts incomplete cache writes. */
internal class BaGuideSpineWebCacheSession(
    resource: BaGuideWebMemoryLobby,
    private val cache: () -> BaGuideSpineHttpCache,
    private val userAgent: String,
    private val revalidate: Boolean,
) : AutoCloseable {
    private val policy = BaGuideSpineResourcePolicy(resource)
    private val closed = AtomicBoolean()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val streams = ConcurrentHashMap.newKeySet<InputStream>()

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame || request.method != "GET" || request.requestHeaders.keys.any {
                it.equals("Range", true) || it.equals("Authorization", true) || it.equals("Cookie", true)
            }) return null
        val url = request.url.toString()
        val reuseMs = policy.reuseMs(url) ?: return null
        if (closed.get()) return unavailable()
        return try {
            val target = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "https://www.gamekee.com/")
                .header("Origin", "https://www.gamekee.com")
                .apply { request.requestHeaders.entries.firstOrNull { it.key.equals("Accept", true) }?.let { header("Accept", it.value) } }
                .build()
            val response = cache().open(target, reuseMs, revalidate, closed::get,
                callStarted = { call -> calls.add(call); if (closed.get()) call.cancel() },
                callFinished = { calls.remove(it) },
            )
            if (response.code in 300..399) { response.close(); return null }
            val body = response.body
            val type = body.contentType()
            val stream = object : FilterInputStream(body.byteStream()) {
                private val released = AtomicBoolean()
                override fun close() {
                    if (!released.compareAndSet(false, true)) return
                    try { response.close() } finally { streams.remove(this) }
                }
            }
            streams.add(stream)
            if (closed.get()) { stream.close(); return unavailable() }
            // The app's bounded cache owns these bytes. Avoid a second Chromium disk copy.
            val headers = response.headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") }
                .filterKeys { it.lowercase() !in setOf("connection", "transfer-encoding", "set-cookie", "cache-control") }
                .toMutableMap().apply { put("Cache-Control", "no-store") }
            WebResourceResponse(
                type?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                type?.charset()?.name(), response.code, response.message.ifBlank { "Resource response" }, headers, stream,
            )
        } catch (_: IOException) {
            // A second browser download would waste bandwidth and defeat close/cancellation.
            unavailable()
        }
    }

    override fun close() {
        closed.set(true)
        calls.forEach(Call::cancel)
        streams.forEach { runCatching { it.close() } }
    }

    private fun unavailable() = WebResourceResponse(
        "text/plain", "UTF-8", 503, "Resource unavailable", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()),
    )
}
