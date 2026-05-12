@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package os.kei.ui.page.main.student

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.tencent.mmkv.MMKV
import os.kei.ui.page.main.ba.support.BASettingsStore
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val GAMEKEE_REFERER = "https://www.gamekee.com/"
private const val GAMEKEE_ORIGIN = "https://www.gamekee.com"
private const val GAMEKEE_FIREFOX_ANDROID_UA =
    "Mozilla/5.0 (Android 15; Mobile; rv:140.0) Gecko/140.0 Firefox/140.0"
private const val GAMEKEE_MEDIA_CACHE_DIR = "ba_gamekee_media3_cache"
private const val GAMEKEE_MEDIA_CACHE_MAX_BYTES = 384L * 1024L * 1024L
private const val GAMEKEE_MEDIA_CACHE_META_KV_ID = "ba_gamekee_media3_cache_meta"
private const val KEY_MEDIA_LAST_CLEANUP_MS = "media_last_cleanup_ms"
private const val KEY_MEDIA_CLEANUP_RUN_COUNT = "media_cleanup_run_count"
private const val KEY_MEDIA_CLEANUP_SCANNED_RESOURCE_COUNT = "media_cleanup_scanned_resource_count"
private const val KEY_MEDIA_CLEANUP_REMOVED_RESOURCE_COUNT = "media_cleanup_removed_resource_count"
private const val KEY_MEDIA_CLEANUP_REMOVED_SPAN_COUNT = "media_cleanup_removed_span_count"
private const val KEY_MEDIA_CLEANUP_REMOVED_BYTES = "media_cleanup_removed_bytes"
private const val KEY_MEDIA_LAST_REMOVED_RESOURCE_COUNT = "media_last_removed_resource_count"
private const val KEY_MEDIA_LAST_REMOVED_SPAN_COUNT = "media_last_removed_span_count"
private const val KEY_MEDIA_LAST_REMOVED_BYTES = "media_last_removed_bytes"
private const val DEFAULT_MEDIA_REFRESH_HOURS = 12
private const val MIN_CLEANUP_CHECK_INTERVAL_MS = 15L * 60L * 1000L
private const val MIN_STALE_THRESHOLD_MS = 6L * 60L * 60L * 1000L

private val GAMEKEE_DEFAULT_HEADERS = mapOf(
    "Accept" to "*/*",
    "Accept-Language" to "zh-CN",
    "Referer" to GAMEKEE_REFERER,
    "Origin" to GAMEKEE_ORIGIN,
    "User-Agent" to GAMEKEE_FIREFOX_ANDROID_UA,
    "device-num" to "1",
    "game-alias" to "ba"
)

internal data class GameKeeMediaCacheDiagnostics(
    val fileCount: Int = 0,
    val diskBytes: Long = 0L,
    val latestModifiedAtMs: Long = 0L,
    val lastCleanupAtMs: Long = 0L,
    val cleanupRunCount: Long = 0L,
    val scannedResourceCount: Long = 0L,
    val removedResourceCount: Long = 0L,
    val removedSpanCount: Long = 0L,
    val removedBytes: Long = 0L,
    val lastRemovedResourceCount: Long = 0L,
    val lastRemovedSpanCount: Long = 0L,
    val lastRemovedBytes: Long = 0L
)

private object GameKeeMediaPlayerCache {
    @Volatile
    private var simpleCache: SimpleCache? = null
    private val cleanupStore: MMKV by lazy { MMKV.mmkvWithID(GAMEKEE_MEDIA_CACHE_META_KV_ID) }
    private val cleanupRunning = AtomicBoolean(false)

    private data class DiskSummary(
        val fileCount: Int,
        val bytes: Long,
        val latestModifiedAtMs: Long
    )

    private data class ResourceState(
        val latestTouchMs: Long,
        val spanCount: Int,
        val spanBytes: Long
    )

    private data class CleanupResult(
        val scannedResourceCount: Long = 0L,
        val removedResourceCount: Long = 0L,
        val removedSpanCount: Long = 0L,
        val removedBytes: Long = 0L
    )

    @Synchronized
    fun get(context: Context): SimpleCache {
        simpleCache?.let { cache ->
            scheduleCleanupIfNeeded(cache)
            return cache
        }
        val appContext = context.applicationContext
        val cacheDir = File(appContext.cacheDir, GAMEKEE_MEDIA_CACHE_DIR).apply { mkdirs() }
        val cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(GAMEKEE_MEDIA_CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(appContext)
        )
        simpleCache = cache
        scheduleCleanupIfNeeded(cache)
        return cache
    }

    private fun scheduleCleanupIfNeeded(cache: SimpleCache) {
        val now = System.currentTimeMillis()
        val checkIntervalMs = cleanupCheckIntervalMs()
        val lastCleanupMs = cleanupStore.decodeLong(KEY_MEDIA_LAST_CLEANUP_MS, 0L)
        if ((now - lastCleanupMs).coerceAtLeast(0L) < checkIntervalMs) return
        if (!cleanupRunning.compareAndSet(false, true)) return

        thread(name = "KeiOS-GameKeeMediaCacheCleanup", isDaemon = true) {
            try {
                val runNow = System.currentTimeMillis()
                val lastRun = cleanupStore.decodeLong(KEY_MEDIA_LAST_CLEANUP_MS, 0L)
                if ((runNow - lastRun).coerceAtLeast(0L) < checkIntervalMs) return@thread

                val staleThresholdMs = staleThresholdMs()
                val result = pruneStaleResources(cache, runNow, staleThresholdMs)
                recordCleanupResult(runNow, result)
            } finally {
                cleanupRunning.set(false)
            }
        }
    }

    private fun pruneStaleResources(
        cache: SimpleCache,
        nowMs: Long,
        staleThresholdMs: Long
    ): CleanupResult {
        val keys = runCatching { cache.keys }.getOrDefault(emptySet())
        if (keys.isEmpty()) return CleanupResult()
        var removedResourceCount = 0L
        var removedSpanCount = 0L
        var removedBytes = 0L
        keys.forEach { key ->
            val state = analyzeResourceState(cache, key)
            if (state.latestTouchMs <= 0L) return@forEach
            if ((nowMs - state.latestTouchMs).coerceAtLeast(0L) >= staleThresholdMs) {
                val removed = runCatching {
                    cache.removeResource(key)
                    true
                }.getOrDefault(false)
                if (removed) {
                    removedResourceCount += 1L
                    removedSpanCount += state.spanCount.toLong().coerceAtLeast(0L)
                    removedBytes += state.spanBytes.coerceAtLeast(0L)
                }
            }
        }
        return CleanupResult(
            scannedResourceCount = keys.size.toLong().coerceAtLeast(0L),
            removedResourceCount = removedResourceCount,
            removedSpanCount = removedSpanCount,
            removedBytes = removedBytes
        )
    }

    private fun analyzeResourceState(cache: SimpleCache, key: String): ResourceState {
        val spans = runCatching { cache.getCachedSpans(key) }.getOrDefault(emptySet<CacheSpan>())
        if (spans.isEmpty()) {
            return ResourceState(
                latestTouchMs = 0L,
                spanCount = 0,
                spanBytes = 0L
            )
        }
        var latest = 0L
        var spanBytes = 0L
        spans.forEach { span ->
            val fromTouch = span.lastTouchTimestamp.takeIf { it > 0L } ?: 0L
            val fromFile = span.file?.lastModified()?.takeIf { it > 0L } ?: 0L
            latest = maxOf(latest, fromTouch, fromFile)
            spanBytes += span.length.coerceAtLeast(0L)
        }
        return ResourceState(
            latestTouchMs = latest,
            spanCount = spans.size,
            spanBytes = spanBytes
        )
    }

    private fun cleanupCheckIntervalMs(): Long {
        val refreshMs = refreshIntervalMs()
        return (refreshMs / 2L).coerceAtLeast(MIN_CLEANUP_CHECK_INTERVAL_MS)
    }

    private fun staleThresholdMs(): Long {
        val refreshMs = refreshIntervalMs()
        return (refreshMs * 2L).coerceAtLeast(MIN_STALE_THRESHOLD_MS)
    }

    private fun refreshIntervalMs(): Long {
        val hours = runCatching { BASettingsStore.loadCalendarRefreshIntervalHours() }
            .getOrDefault(DEFAULT_MEDIA_REFRESH_HOURS)
            .coerceAtLeast(1)
        return hours.toLong() * 60L * 60L * 1000L
    }

    private fun recordCleanupResult(
        cleanupAtMs: Long,
        result: CleanupResult
    ) {
        cleanupStore.encode(KEY_MEDIA_LAST_CLEANUP_MS, cleanupAtMs.coerceAtLeast(0L))
        cleanupStore.encode(
            KEY_MEDIA_CLEANUP_RUN_COUNT,
            cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_RUN_COUNT, 0L) + 1L
        )
        cleanupStore.encode(
            KEY_MEDIA_CLEANUP_SCANNED_RESOURCE_COUNT,
            cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_SCANNED_RESOURCE_COUNT, 0L) + result.scannedResourceCount
        )
        cleanupStore.encode(
            KEY_MEDIA_CLEANUP_REMOVED_RESOURCE_COUNT,
            cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_REMOVED_RESOURCE_COUNT, 0L) + result.removedResourceCount
        )
        cleanupStore.encode(
            KEY_MEDIA_CLEANUP_REMOVED_SPAN_COUNT,
            cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_REMOVED_SPAN_COUNT, 0L) + result.removedSpanCount
        )
        cleanupStore.encode(
            KEY_MEDIA_CLEANUP_REMOVED_BYTES,
            cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_REMOVED_BYTES, 0L) + result.removedBytes
        )
        cleanupStore.encode(KEY_MEDIA_LAST_REMOVED_RESOURCE_COUNT, result.removedResourceCount)
        cleanupStore.encode(KEY_MEDIA_LAST_REMOVED_SPAN_COUNT, result.removedSpanCount)
        cleanupStore.encode(KEY_MEDIA_LAST_REMOVED_BYTES, result.removedBytes)
    }

    fun loadDiagnostics(context: Context): GameKeeMediaCacheDiagnostics {
        val disk = scanDiskSummary(mediaCacheDir(context.applicationContext))
        return GameKeeMediaCacheDiagnostics(
            fileCount = disk.fileCount,
            diskBytes = disk.bytes,
            latestModifiedAtMs = disk.latestModifiedAtMs,
            lastCleanupAtMs = cleanupStore.decodeLong(KEY_MEDIA_LAST_CLEANUP_MS, 0L),
            cleanupRunCount = cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_RUN_COUNT, 0L),
            scannedResourceCount = cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_SCANNED_RESOURCE_COUNT, 0L),
            removedResourceCount = cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_REMOVED_RESOURCE_COUNT, 0L),
            removedSpanCount = cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_REMOVED_SPAN_COUNT, 0L),
            removedBytes = cleanupStore.decodeLong(KEY_MEDIA_CLEANUP_REMOVED_BYTES, 0L),
            lastRemovedResourceCount = cleanupStore.decodeLong(KEY_MEDIA_LAST_REMOVED_RESOURCE_COUNT, 0L),
            lastRemovedSpanCount = cleanupStore.decodeLong(KEY_MEDIA_LAST_REMOVED_SPAN_COUNT, 0L),
            lastRemovedBytes = cleanupStore.decodeLong(KEY_MEDIA_LAST_REMOVED_BYTES, 0L)
        )
    }

    @Synchronized
    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        val cache = simpleCache
        if (cache != null) {
            runCatching {
                cache.keys.toList().forEach { key ->
                    runCatching { cache.removeResource(key) }
                }
            }
        } else {
            runCatching {
                SimpleCache.delete(
                    mediaCacheDir(appContext),
                    StandaloneDatabaseProvider(appContext)
                )
            }
        }
    }

    private fun mediaCacheDir(context: Context): File {
        return File(context.cacheDir, GAMEKEE_MEDIA_CACHE_DIR)
    }

    private fun scanDiskSummary(root: File): DiskSummary {
        if (!root.exists()) {
            return DiskSummary(
                fileCount = 0,
                bytes = 0L,
                latestModifiedAtMs = 0L
            )
        }
        var fileCount = 0
        var bytes = 0L
        var latest = 0L
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                fileCount += 1
                bytes += file.length()
                latest = maxOf(latest, file.lastModified())
            }
        return DiskSummary(
            fileCount = fileCount,
            bytes = bytes,
            latestModifiedAtMs = latest
        )
    }
}

internal fun loadGameKeeMediaCacheDiagnostics(context: Context): GameKeeMediaCacheDiagnostics {
    return GameKeeMediaPlayerCache.loadDiagnostics(context)
}

internal fun clearGameKeeMediaPlaybackCache(context: Context) {
    GameKeeMediaPlayerCache.clearAll(context)
}

internal fun createGameKeeHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
    return DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(12_000)
        .setUserAgent(GAMEKEE_FIREFOX_ANDROID_UA)
        .setDefaultRequestProperties(GAMEKEE_DEFAULT_HEADERS)
}

internal fun createGameKeeMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
    val httpFactory = createGameKeeHttpDataSourceFactory()
    val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)
    val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(GameKeeMediaPlayerCache.get(context))
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    return DefaultMediaSourceFactory(cacheDataSourceFactory)
}
