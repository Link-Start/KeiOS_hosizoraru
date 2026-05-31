package os.kei.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import os.kei.core.concurrency.AppDispatchers.media

/**
 * Bounded IO dispatchers for KeiOS feature domains.
 *
 * Android 17 introduces fair scheduling where the system can throttle background threads more
 * aggressively. Using unbounded [Dispatchers.IO] (64 threads) across all features risks:
 * - Thread pool exhaustion under concurrent GitHub refresh + BA fetch + MCP serving
 * - Unfair scheduling where one feature starves others
 * - Excessive context switching overhead
 *
 * Each domain gets a bounded dispatcher via [Dispatchers.IO.limitedParallelism]. These share the
 * underlying IO pool but cap how many threads each domain can use concurrently. The sum of all
 * limits intentionally exceeds the IO pool size — the pool itself is the final backstop.
 *
 * Usage:
 * ```kotlin
 * withContext(AppDispatchers.githubNetwork) { ... }
 * ```
 */
object AppDispatchers {
    /**
     * GitHub network operations: release checks, asset fetches, API calls.
     * Bounded to 6 threads — enough for concurrent batch refresh (maxConcurrency=2 per batch)
     * plus share-import and actions update flows running simultaneously.
     */
    val githubNetwork: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(6)

    /**
     * GitHub local IO: MMKV reads/writes, cache persistence, JSON parsing.
     * Bounded to 2 threads — these are fast operations that shouldn't compete with network.
     */
    val githubLocal: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    /**
     * BA (Blue Archive) data fetching: GameKee HTML parsing, calendar/pool data.
     * Bounded to 3 threads — typically 1-2 concurrent fetches plus media cache.
     */
    val baFetch: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * MCP server operations: tool execution, Ktor request handling.
     * Bounded to 4 threads — MCP tools are mostly delegating to other dispatchers,
     * but need headroom for concurrent client sessions.
     */
    val mcpServer: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    /**
     * OS page operations: shell commands, system property reads, Shizuku calls.
     * Bounded to 2 threads — shell commands are sequential by nature.
     */
    val osOperations: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    /**
     * General file IO: log writes, export/import, content resolver reads.
     * Bounded to 3 threads — covers settings, feedback, JSON import flows.
     */
    val fileIo: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * Media operations: image loading, BGM playback prep, gallery export.
     * Bounded to 3 threads — media decode is CPU-bound, don't over-parallelize.
     */
    val media: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * Catalog thumbnail loading: small (≤256px) GameKee icon fetch + decode for grid pages.
     * Bounded to 6 threads — unlike full-size guide images these loads are light and
     * network-latency bound, so overlapping round-trips matters more than decode cost.
     * Kept separate from [media] so full-size decodes and grid thumbnails don't starve each
     * other. The explicit cap is the Android 17 fair-scheduling backstop: the catalog can
     * saturate at most 6 IO threads instead of monopolizing the shared pool during a scroll.
     */
    val catalogThumbnails: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(6)

    /**
     * UI data derivation: filtering, sorting, and grouping already-loaded in-memory models.
     * Bounded to 2 Default threads so expensive page projections leave the main thread without
     * competing with broader CPU work.
     */
    val uiDerivation: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
}
