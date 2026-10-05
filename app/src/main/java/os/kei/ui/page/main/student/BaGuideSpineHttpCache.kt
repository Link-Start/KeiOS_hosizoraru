package os.kei.ui.page.main.student

import androidx.annotation.WorkerThread
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.ForwardingSource
import okio.buffer
import os.kei.core.io.SharedHttpClient
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal const val BA_GUIDE_SPINE_CACHE_MAX_BYTES = 256L * 1024 * 1024
internal const val BA_GUIDE_SPINE_ASSET_REUSE_MS = 24L * 60 * 60 * 1000
internal const val BA_GUIDE_SPINE_SCRIPT_REUSE_MS = 60L * 60 * 1000

internal data class BaGuideSpineCacheStats(
    val bytes: Long,
    val entries: Int,
    val latestModifiedAtMs: Long,
    val hits: Long,
    val networkRequests: Long,
    val downloadedBytes: Long,
)

/** Streaming HTTP cache for the selected lobby's public assets, independent of Chromium's cache. */
internal class BaGuideSpineHttpCache(
    private val directory: File,
    maxBytes: Long = BA_GUIDE_SPINE_CACHE_MAX_BYTES,
    baseClient: OkHttpClient = SharedHttpClient.base,
    private val clock: () -> Long = System::currentTimeMillis,
    maxNetworkBodies: Int = 3,
) : AutoCloseable {
    private val cache = Cache(directory, maxBytes)
    private val client = baseClient.newBuilder()
        .cache(cache)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Error pages and oversized files must not poison or monopolize the asset cache.
            // A 304 updates existing metadata; marking it no-store would discard the validated body.
            val cacheable = if (response.code !in setOf(200, 304) || response.body.contentType()?.subtype == "html" ||
                response.body.contentLength() > 64L * 1024 * 1024
            ) response.newBuilder().header("Cache-Control", "no-store").build() else response
            val body = cacheable.body
            // Count HTTP body bytes before transparent gzip decompression, excluding cached reads.
            val source = object : ForwardingSource(body.source()) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) downloadedBytes.addAndGet(read)
                    return read
                }
            }.buffer()
            cacheable.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        }.build()
    private val locks = ConcurrentHashMap<String, ResourceLock>()
    private val networkBodies = Semaphore(maxNetworkBodies, true)
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val hits = AtomicLong()
    private val requests = AtomicLong()
    private val downloadedBytes = AtomicLong()

    /** Called by WebView's background request callback. The body owns its leases until closed. */
    @WorkerThread
    fun open(
        request: Request,
        reuseMs: Long,
        revalidate: Boolean = false,
        cancelled: () -> Boolean = { false },
        callStarted: (Call) -> Unit = {},
        callFinished: (Call) -> Unit = {},
    ): Response {
        val key = request.url.toString()
        val lock = locks.compute(key) { _, current ->
            (current ?: ResourceLock()).also { it.users.incrementAndGet() }
        }!!
        var acquired = false
        var networkAcquired = false
        val released = AtomicBoolean()
        fun release() {
            if (!released.compareAndSet(false, true)) return
            if (networkAcquired) networkBodies.release()
            if (acquired) lock.permit.release()
            locks.computeIfPresent(key) { _, current ->
                if (current === lock && current.users.decrementAndGet() == 0) null else current
            }
        }
        fun execute(target: Request): Response {
            if (cancelled()) throw InterruptedIOException("Lobby closed")
            val call = client.newCall(target)
            calls.add(call)
            callStarted(call)
            if (cancelled()) call.cancel()
            try {
                val response = call.execute()
                val body = response.body
                val source = object : ForwardingSource(body.source()) {
                    override fun close() {
                        try { super.close() } finally { calls.remove(call); callFinished(call) }
                    }
                }.buffer()
                return response.newBuilder().body(object : ResponseBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun source() = source
                }).build()
            } catch (failure: Exception) {
                calls.remove(call)
                callFinished(call)
                throw failure
            }
        }
        try {
            acquire(lock.permit, cancelled)
            acquired = true
            if (!revalidate) {
                // CDN Age may describe weeks in the edge cache. Limit our reuse by the local
                // receipt timestamp instead. OkHttp still honours no-cache/must-revalidate.
                val cached = execute(request.newBuilder().cacheControl(CACHE_ONLY).build())
                val age = clock() - cached.receivedResponseAtMillis
                if (cached.code == 200 && age in 0..reuseMs) {
                    hits.incrementAndGet()
                    return ownedBody(cached, ::release)
                }
                cached.close()
            }
            acquire(networkBodies, cancelled)
            networkAcquired = true
            // max-age=0 permits OkHttp's ETag/Last-Modified conditional GET. no-cache would
            // bypass that path and unnecessarily download the entire body on every retry.
            val response = execute(request.newBuilder().cacheControl(REVALIDATE).build())
            if (response.networkResponse != null) requests.incrementAndGet()
            if (response.cacheResponse != null) hits.incrementAndGet()
            return ownedBody(response, ::release)
        } catch (failure: Exception) {
            release()
            throw failure
        }
    }

    private fun ownedBody(response: Response, release: () -> Unit): Response {
        val body = response.body
        val source = object : ForwardingSource(body.source()) {
            override fun close() {
                try { super.close() } finally { release() }
            }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = source
        }).build()
    }

    @WorkerThread
    fun stats(): BaGuideSpineCacheStats {
        cache.flush()
        val urls = cache.urls()
        var entries = 0
        while (urls.hasNext()) { urls.next(); entries++ }
        return BaGuideSpineCacheStats(
            cache.size(), entries,
            directory.listFiles().orEmpty().filter { it.name.endsWith(".0") }.maxOfOrNull(File::lastModified) ?: 0,
            hits.get(), requests.get(), downloadedBytes.get(),
        )
    }

    @WorkerThread
    fun clear() {
        calls.forEach(Call::cancel)
        cache.evictAll()
        hits.set(0); requests.set(0); downloadedBytes.set(0)
    }

    @WorkerThread
    fun removeResources(urls: Set<String>) {
        calls.filter { it.request().url.toString() in urls }.forEach(Call::cancel)
        val entries = cache.urls()
        while (entries.hasNext()) if (entries.next() in urls) entries.remove()
    }

    override fun close() { calls.forEach(Call::cancel); cache.close() }

    internal fun activeLockCount(): Int = locks.size

    private fun acquire(permit: Semaphore, cancelled: () -> Boolean) {
        try {
            while (!permit.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                if (cancelled()) throw InterruptedIOException("Lobby closed")
            }
            if (cancelled()) { permit.release(); throw InterruptedIOException("Lobby closed") }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while loading lobby")
        }
    }

    private class ResourceLock(val permit: Semaphore = Semaphore(1, true), val users: AtomicInteger = AtomicInteger())

    companion object {
        private val CACHE_ONLY = CacheControl.Builder().onlyIfCached().maxStale(Int.MAX_VALUE, TimeUnit.SECONDS).build()
        private val REVALIDATE = CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()
    }
}
