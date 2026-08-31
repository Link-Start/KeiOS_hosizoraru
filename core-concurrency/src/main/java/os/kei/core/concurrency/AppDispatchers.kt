package os.kei.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
     * Bounded to 10 threads so large tracked-app refresh batches can finish quickly while
     * Android 17 fair scheduling still keeps GitHub work inside a feature-local cap.
     */
    val githubNetwork: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(10)

    /**
     * User-triggered GitHub managed downloads. The foreground speed profile can run up to 12
     * blocking range readers, while the remaining slots keep the bounded file writer and
     * cancellation path responsive throughout the transfer.
     */
    val githubManagedDownload: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(20)

    /**
     * GitHub notification operations: Live Updates, Xiaomi focus notification payloads, and
     * notification cleanup. Kept separate from network checks so progress surfaces stay responsive
     * while large refresh batches occupy the GitHub network dispatcher.
     */
    val githubNotification: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

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
     *
     * This is the MCP handler pool. kotlin-sdk 0.15.0 dispatches every inbound request onto
     * `ProtocolOptions.handlerCoroutineContext`, which `LocalMcpService` pins to this dispatcher, so
     * the bound here is the single gate on how much MCP work runs at once — the SDK's own admission
     * semaphore sits far above it at 64.
     *
     * Bounded to 12 rather than the original 4. Four was set when the SDK processed inbound messages
     * serially and each tool hopped here from the transport read loop; once handlers began arriving
     * concurrently, four threads shared by the 30s network and 60s deep-scan profiles meant a handful
     * of long tools could park every 4-second cache read behind them. Twelve keeps a feature-local cap
     * for Android 17 fair scheduling while leaving room for the long profiles to overlap short ones.
     * MCP tools mostly delegate onward to [githubNetwork], [baFetch] and friends, so threads here are
     * held briefly and the real work stays inside its own domain's cap.
     */
    val mcpServer: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(12)

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
     * WebDAV network operations: remote metadata probes, downloads, uploads, and sync conflict
     * checks. Kept separate from general file IO so remote sync cannot starve local import/export.
     */
    val webDavNetwork: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * Media operations: image loading, BGM playback prep, gallery export.
     * Bounded to 3 threads — media decode is CPU-bound, don't over-parallelize.
     */
    val media: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * Catalog thumbnail loading: small (≤256px) GameKee icon fetch + decode for grid pages.
     * Bounded to 4 threads so the catalog can overlap network round-trips while leaving enough
     * scheduler room for SystemUI, media, and BA detail hydration during page entry.
     */
    val catalogThumbnails: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    /**
     * UI data derivation: filtering, sorting, and grouping already-loaded in-memory models.
     * Bounded to 2 Default threads so expensive page projections leave the main thread without
     * competing with broader CPU work.
     */
    val uiDerivation: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
}
